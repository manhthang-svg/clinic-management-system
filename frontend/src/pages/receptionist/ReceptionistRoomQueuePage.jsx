import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ArrowLeft, ChevronLeft, ChevronRight, DoorOpen, RefreshCw } from 'lucide-react';

import defaultVisitApi from '../../apis/visitApi';
import { VISIT_STATUS_LABELS } from '../../constants/visitData';
import ROUTES, { roomQueuePath } from '../../routes/routes';
import { MSG12, MSG13, MSG80, MSG92, msg39 } from '../../components/services/serviceMessages';
import { MSG102, msg56 as msg53 } from '../../components/visits/visitErrors';

const REFRESH_MS = 10_000;
const NO_DEPARTMENT = 'Chưa thuộc khoa';

const COUNT_COLUMNS = [
    ['waitingPaymentCount', 'Chờ thanh toán', 'text-orange-700'],
    ['waitingCount', 'Chờ khám', 'text-amber-700'],
    ['inExaminationCount', 'Đang khám', 'text-blue-700'],
    ['waitingParaclinicalCount', 'Chờ CLS', 'text-violet-700'],
    ['waitingConclusionCount', 'Chờ kết luận', 'text-teal-700'],
    ['skippedCount', 'Bỏ lượt', 'text-rose-700'],
];

/**
 * EF-02 / EF-03: MSG12 when the session has expired, MSG13 without permission (BR-02), MSG80 for any other failure,
 * including a lost connection.
 */
function loadErrorMessage(error) {
    if (error?.status === 401) return MSG12;
    if (error?.status === 403) return MSG13;
    return MSG80;
}

/** AF-03: a room's waiting list can no longer be viewed: MSG39 for an unknown room, MSG53 otherwise. */
function roomQueueErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'ROOM_NOT_FOUND') return msg39('phòng khám');
    if (code === 'ROOM_NOT_AVAILABLE') return msg53();
    return loadErrorMessage(error);
}

function formatTime(value) {
    return value ? value.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';
}

function formatRegistrationTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
        ? '—'
        : date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

/**
 * "Bác sĩ còn phải khám", as on 'Đăng ký khám' (UC-40 step 8): today's visits of the room that have a queue number
 * and have not ended. Visits waiting for payment have no number yet and are not counted.
 */
function roomTotal(room) {
    return COUNT_COLUMNS
        .filter(([key]) => key !== 'waitingPaymentCount')
        .reduce((sum, [key]) => sum + (room[key] ?? 0), 0);
}

/** Groups rooms by department in the order the backend returns them (UC-42 step 2). */
function groupByDepartment(rooms) {
    const groups = [];
    const byName = new Map();
    rooms.forEach((room) => {
        const name = room.departmentName || NO_DEPARTMENT;
        if (!byName.has(name)) {
            const group = { name, rooms: [] };
            byName.set(name, group);
            groups.push(group);
        }
        byName.get(name).rooms.push(room);
    });
    return groups;
}

const PAGE_SIZE = 10;

/** Previous / page numbers / next under the waiting list (same look as the paraclinical service list). */
function Pagination({ page, pageCount, total, onChange }) {
    const from = total === 0 ? 0 : (page - 1) * PAGE_SIZE + 1;
    const to = Math.min(page * PAGE_SIZE, total);
    const pageButton = 'flex h-8 min-w-8 cursor-pointer items-center justify-center rounded-full px-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40';
    return (
        <nav aria-label="Phân trang danh sách chờ" className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-4 py-3 text-xs text-slate-500">
            <span>Hiển thị {from} – {to} / {total}</span>
            <div className="flex items-center gap-1">
                <button type="button" aria-label="Trang trước" disabled={page === 1} onClick={() => onChange(page - 1)} className={`${pageButton} text-slate-600 hover:bg-slate-100`}>
                    <ChevronLeft aria-hidden="true" className="h-4 w-4" />
                </button>
                {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
                    <button
                        key={number}
                        type="button"
                        aria-label={`Trang ${number}`}
                        aria-current={number === page ? 'page' : undefined}
                        onClick={() => onChange(number)}
                        className={`${pageButton} ${number === page ? 'border border-teal-600 text-teal-700' : 'text-slate-600 hover:bg-slate-100'}`}
                    >
                        {number}
                    </button>
                ))}
                <button type="button" aria-label="Trang sau" disabled={page === pageCount} onClick={() => onChange(page + 1)} className={`${pageButton} text-slate-600 hover:bg-slate-100`}>
                    <ChevronRight aria-hidden="true" className="h-4 w-4" />
                </button>
            </div>
        </nav>
    );
}

/**
 * UC-42 AF-03: the room in the link is not on the overview. The backend tells why: MSG39 when the room does not
 * exist, MSG53 when it is inactive or has no doctor on duty.
 */
function UnavailableRoom({ roomId, visitApi, onBack }) {
    const [message, setMessage] = useState('');

    useEffect(() => {
        const controller = new AbortController();
        visitApi.getQueue({ roomId }, controller.signal)
            // Listed again in the meantime: the overview refresh will open it; until then it is not receivable.
            .then(() => setMessage(msg53()))
            .catch((failure) => {
                if (failure?.name !== 'AbortError') setMessage(roomQueueErrorMessage(failure));
            });
        return () => controller.abort();
    }, [visitApi, roomId]);

    return (
        <div className="flex min-h-48 flex-col items-center justify-center gap-3 rounded-xl border border-slate-200 bg-white px-6 text-center shadow-sm">
            <p role={message ? 'alert' : undefined} className="text-sm font-semibold text-slate-700">
                {message || 'Đang tải hàng đợi của phòng…'}
            </p>
            <button type="button" onClick={onBack} className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50">
                <ArrowLeft className="h-4 w-4" />
                Quay lại
            </button>
        </div>
    );
}

/** UC-42 step 5: examination waiting list of one room, 10 visits per page. */
function RoomDetail({ room, visitApi, refreshKey, onBack }) {
    const [visits, setVisits] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [page, setPage] = useState(1);
    const pageCount = Math.max(1, Math.ceil(visits.length / PAGE_SIZE));
    // The list refreshes every 10 seconds; stay on the last page when it gets shorter.
    const currentPage = Math.min(page, pageCount);
    const pageVisits = visits.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

    useEffect(() => {
        const controller = new AbortController();
        visitApi.getQueue({ roomId: room.roomId }, controller.signal)
            .then((list) => {
                setVisits(Array.isArray(list) ? list : []);
                setError('');
            })
            .catch((failure) => {
                if (failure?.name !== 'AbortError') setError(roomQueueErrorMessage(failure));
            })
            .finally(() => setLoading(false));
        return () => controller.abort();
    }, [visitApi, room.roomId, refreshKey]);

    return (
        <section aria-label={`Hàng đợi ${room.roomName || room.roomCode}`} className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
                <div>
                    <h2 className="text-base font-bold text-slate-800">{room.roomName || room.roomCode}</h2>
                    <p className="text-xs text-slate-500">
                        {[room.roomCode, room.location, room.departmentName].filter(Boolean).join(' · ')}
                    </p>
                    <p className="text-xs text-slate-500">
                        Bác sĩ trực: {room.doctorNames?.length ? room.doctorNames.join(', ') : 'Chưa có bác sĩ'}
                    </p>
                </div>
                <button type="button" onClick={onBack} className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50">
                    <ArrowLeft className="h-4 w-4" />
                    Quay lại
                </button>
            </div>
            {error && (
                <p role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2.5 text-xs text-rose-700">{error}</p>
            )}
            <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] border-collapse text-left text-sm">
                    <thead>
                        <tr className="border-b border-slate-200 bg-[#f2f8f7] text-xs text-slate-600">
                            <th scope="col" className="px-4 py-3 font-bold">STT</th>
                            <th scope="col" className="px-4 py-3 font-bold">Bệnh nhân</th>
                            <th scope="col" className="px-4 py-3 font-bold">Ưu tiên</th>
                            <th scope="col" className="px-4 py-3 font-bold">Giờ đăng ký</th>
                            <th scope="col" className="px-4 py-3 font-bold">Trạng thái</th>
                        </tr>
                    </thead>
                    <tbody>
                        {pageVisits.map((visit) => (
                            <tr key={visit.id} className="border-b border-slate-100">
                                <td className="px-4 py-3 text-base font-bold tabular-nums">{visit.queueNumber}</td>
                                <td className="px-4 py-3">
                                    <p className="font-semibold text-slate-800">{visit.patientName}</p>
                                    <p className="text-xs text-slate-500">{visit.patientCode}</p>
                                </td>
                                <td className="px-4 py-3">
                                    {visit.isPriority
                                        ? <span className="rounded-md border border-rose-200 bg-rose-50 px-1.5 py-0.5 text-[11px] font-semibold text-rose-700">Ưu tiên</span>
                                        : <span className="text-slate-300">—</span>}
                                </td>
                                <td className="px-4 py-3 tabular-nums">{formatRegistrationTime(visit.thoigianvaovien)}</td>
                                <td className="px-4 py-3">
                                    {visit.isSkipped ? 'Bỏ lượt' : VISIT_STATUS_LABELS[visit.status] ?? visit.status}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {visits.length === 0 && (
                    <p className="px-6 py-10 text-center text-sm text-slate-500">
                        {loading ? 'Đang tải danh sách chờ…' : MSG92 /* AF-02 */}
                    </p>
                )}
            </div>
            {visits.length > 0 && (
                <Pagination page={currentPage} pageCount={pageCount} total={visits.length} onChange={setPage} />
            )}
        </section>
    );
}

/** UC-42 View Room Queue Status: queue counts per active examination room, grouped by department. */
export default function ReceptionistRoomQueuePage({ visitApi = defaultVisitApi }) {
    const [rooms, setRooms] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [lastUpdated, setLastUpdated] = useState(null);
    const [refreshing, setRefreshing] = useState(false);
    // UC-42 step 6: the selected room is part of the link, /receptionist/room-queue/{roomId}.
    const { roomId: selectedRoomId } = useParams();
    const navigate = useNavigate();
    const backToOverview = () => navigate(ROUTES.RECEPTIONIST.ROOM_QUEUE);
    const [refreshKey, setRefreshKey] = useState(0);

    const fetchStatus = useCallback(async (signal) => {
        try {
            const list = await visitApi.getQueueStatus(signal);
            setRooms(Array.isArray(list) ? list : []);
            setLoadError('');
            setLastUpdated(new Date());
        } catch (error) {
            if (error?.name !== 'AbortError') setLoadError(loadErrorMessage(error));
        } finally {
            setLoading(false);
        }
    }, [visitApi]);

    const refreshNow = async () => {
        if (refreshing) return;
        setRefreshing(true);
        setRefreshKey((key) => key + 1);
        await fetchStatus();
        setRefreshing(false);
    };

    useEffect(() => {
        const controller = new AbortController();
        const first = window.setTimeout(() => void fetchStatus(controller.signal), 0);
        const timer = window.setInterval(() => {
            setRefreshKey((key) => key + 1);
            void fetchStatus(controller.signal);
        }, REFRESH_MS);
        return () => {
            controller.abort();
            window.clearTimeout(first);
            window.clearInterval(timer);
        };
    }, [fetchStatus]);

    const groups = useMemo(() => groupByDepartment(rooms), [rooms]);
    const receivableRooms = rooms.filter((room) => room.hasDoctor).length;
    const noRoomCanReceive = !loading && lastUpdated !== null && receivableRooms === 0;
    const selectedRoom = rooms.find((room) => room.roomId === selectedRoomId) ?? null;

    return (
        <div className="mx-auto max-w-[1400px] space-y-4 pb-8 text-slate-800">
            <header className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                    <DoorOpen className="mt-1 h-7 w-7 text-teal-600" />
                    <div>
                        <h1 className="text-2xl font-extrabold tracking-tight text-[#142534]">Tình trạng hàng đợi phòng khám</h1>
                        <p className="mt-1 text-sm text-slate-500">Các phòng khám đang có bác sĩ trực và số bệnh nhân của từng phòng tại thời điểm hiện tại, nhóm theo khoa. Chọn một phòng để xem danh sách chờ.</p>
                    </div>
                </div>
                <button type="button" disabled={refreshing || loading} onClick={() => void refreshNow()} className="flex cursor-pointer items-center gap-2 rounded-lg border border-teal-300 px-3 py-2 text-xs font-semibold text-teal-700 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-50">
                    <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
                    {refreshing ? 'Đang tải…' : 'Tải lại'}
                </button>
            </header>

            {noRoomCanReceive && !loadError && (
                <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{MSG102}</p>
            )}

            {selectedRoom ? (
                <RoomDetail room={selectedRoom} visitApi={visitApi} refreshKey={refreshKey} onBack={backToOverview} />
            ) : selectedRoomId ? (
                loading ? (
                    <div className="flex min-h-48 items-center justify-center rounded-xl border border-slate-200 bg-white px-6 text-sm font-semibold text-slate-700 shadow-sm">
                        Đang tải hàng đợi của phòng…
                    </div>
                ) : (
                    // AF-03: the room in the link is not (or no longer) listed.
                    <UnavailableRoom roomId={selectedRoomId} visitApi={visitApi} onBack={backToOverview} />
                )
            ) : (
                <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                    {loadError && (
                        <p role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2.5 text-xs text-rose-700">
                            {loadError}
                            {lastUpdated && ` Số liệu bên dưới là lần cập nhật lúc ${formatTime(lastUpdated)}.`}
                        </p>
                    )}
                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[880px] border-collapse text-left text-sm">
                            <thead>
                                <tr className="border-b border-slate-200 bg-[#f2f8f7] text-xs text-slate-600">
                                    <th scope="col" className="px-4 py-3 font-bold">Phòng khám</th>
                                    {COUNT_COLUMNS.map(([key, label]) => (
                                        <th key={key} scope="col" className="px-4 py-3 text-right font-bold">{label}</th>
                                    ))}
                                    <th scope="col" className="px-4 py-3 text-right font-bold text-slate-800">Bác sĩ còn phải khám</th>
                                </tr>
                            </thead>
                            {groups.map((group) => (
                                <tbody key={group.name}>
                                    <tr className="border-b border-slate-200 bg-slate-50">
                                        <th scope="colgroup" colSpan={COUNT_COLUMNS.length + 2} className="px-4 py-2 text-xs font-bold uppercase tracking-wide text-slate-500">
                                            {group.name}
                                        </th>
                                    </tr>
                                    {group.rooms.map((room) => {
                                        const total = roomTotal(room);
                                        return (
                                            <tr
                                                key={room.roomId}
                                                // The whole row opens the room; Enter does the same from the keyboard.
                                                onClick={() => navigate(roomQueuePath(room.roomId))}
                                                onKeyDown={(event) => {
                                                    if (event.key === 'Enter') navigate(roomQueuePath(room.roomId));
                                                }}
                                                tabIndex={0}
                                                aria-label={`Xem hàng đợi ${room.roomName || room.roomCode}`}
                                                className={`cursor-pointer border-b border-slate-100 hover:bg-teal-50/60 focus-visible:bg-teal-50/60 focus-visible:outline-none ${room.hasDoctor ? '' : 'bg-slate-50 opacity-60'}`}
                                            >
                                                <td className="px-4 py-3">
                                                    <span className="font-semibold text-slate-800">{room.roomName || room.roomCode}</span>
                                                    <p className="text-xs text-slate-500">{[room.roomCode, room.location].filter(Boolean).join(' · ')}</p>
                                                    <p className="text-xs text-slate-500">
                                                        {room.hasDoctor ? `Bác sĩ trực: ${room.doctorNames.join(', ')}` : 'Chưa có bác sĩ'}
                                                    </p>
                                                </td>
                                                {COUNT_COLUMNS.map(([key, , className]) => (
                                                    <td key={key} className={`px-4 py-3 text-right text-base font-bold tabular-nums ${room[key] > 0 ? className : 'text-slate-300'}`}>{room[key] ?? 0}</td>
                                                ))}
                                                <td className={`px-4 py-3 text-right text-base font-extrabold tabular-nums ${total > 0 ? 'text-slate-900' : 'text-slate-300'}`}>{total}</td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            ))}
                        </table>
                        {/* EF-01 shows only MSG102 above the table; the table itself stays empty. */}
                        {rooms.length === 0 && (loading || (loadError && !lastUpdated)) && (
                            <div className="flex min-h-48 flex-col items-center justify-center px-6 text-center">
                                <h3 className="text-sm font-bold">
                                    {loading ? 'Đang tải tình trạng hàng đợi…' : 'Chưa tải được dữ liệu'}
                                </h3>
                            </div>
                        )}
                    </div>
                    <p className="border-t border-slate-100 px-4 py-3 text-[11px] text-slate-400">
                        Tự động cập nhật mỗi 10 giây · Cập nhật lần cuối: {formatTime(lastUpdated)}
                    </p>
                </div>
            )}
        </div>
    );
}
