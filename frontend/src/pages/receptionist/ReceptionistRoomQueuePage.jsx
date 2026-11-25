import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ArrowLeft, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';

import defaultVisitApi from '../../apis/visitApi';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { VISIT_STATUS_LABELS } from '../../constants/visitData';
import ROUTES, { roomQueuePath } from '../../routes/routes';
import { MSG12, MSG13, MSG80, MSG92, msg39 } from '../../components/services/serviceMessages';
import { MSG102, msg56 as msg53 } from '../../components/visits/visitErrors';
import { ListPagination } from '../../components/shared/ListPagination';

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

// doi loi tai trang thanh thong bao: het phien MSG12, khong quyen MSG13, con lai MSG80
function loadErrorMessage(error) {
    if (error?.status === 401) return MSG12;
    if (error?.status === 403) return MSG13;
    return MSG80;
}

// loi mo hang doi 1 phong: phong khong ton tai MSG39, phong ngung / khong co bac si MSG53
function roomQueueErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'ROOM_NOT_FOUND') return msg39('phòng khám');
    // MSG53 names the room; the backend sends its name because the room is no longer on the overview.
    if (code === 'ROOM_NOT_AVAILABLE') return msg53(error.errorResponse?.details?.roomName);
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

// "Bac si con phai kham": luot hom nay da co so thu tu va chua xong (chua thanh toan thi chua tinh)
function roomTotal(room) {
    return COUNT_COLUMNS
        .filter(([key]) => key !== 'waitingPaymentCount')
        .reduce((sum, [key]) => sum + (room[key] ?? 0), 0);
}

// nhom phong theo khoa, giu thu tu backend tra ve
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

// thanh phan trang duoi danh sach cho
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

// phong tren link khong co trong danh sach: hoi backend ly do (MSG39 / MSG53)
function UnavailableRoom({ roomId, visitApi, onBack }) {
    const [message, setMessage] = useState('');

    useEffect(() => {
        const controller = new AbortController();
        visitApi.getQueuePage({ roomId, size: 1 }, controller.signal)
            // phong vua mo lai: lan lam moi sau se hien, tam thoi bao MSG53
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

// danh sach cho kham cua 1 phong: phan trang o server, moi lan chi nhan 20 benh nhan dang xem
function RoomDetail({ room, visitApi, refreshKey, onBack }) {
    const [visits, setVisits] = useState([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [page, setPage] = useState(1);
    const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
    // tu lam moi 10 giay; danh sach ngan lai ma dang o trang qua cuoi thi lui ve trang cuoi
    if (!loading && page > pageCount) setPage(pageCount);

    useEffect(() => {
        const controller = new AbortController();
        visitApi.getQueuePage({ roomId: room.roomId, page: page - 1, size: PAGE_SIZE }, controller.signal)
            .then((result) => {
                setVisits(Array.isArray(result?.content) ? result.content : []);
                setTotal(result?.page?.totalElements ?? 0);
                setError('');
            })
            .catch((failure) => {
                if (failure?.name !== 'AbortError') setError(roomQueueErrorMessage(failure));
            })
            .finally(() => setLoading(false));
        return () => controller.abort();
    }, [visitApi, room.roomId, refreshKey, page]);

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
                        {visits.map((visit) => (
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
                {total === 0 && (
                    <p className="px-6 py-10 text-center text-sm text-slate-500">
                        {loading ? 'Đang tải danh sách chờ…' : MSG92 /* AF-02 */}
                    </p>
                )}
            </div>
            {total > 0 && (
                <Pagination page={page} pageCount={pageCount} total={total} onChange={setPage} />
            )}
        </section>
    );
}

// UC-42: trang tinh trang hang doi cac phong kham, nhom theo khoa
export default function ReceptionistRoomQueuePage({ visitApi = defaultVisitApi }) {
    // 1 trang phong tu server (20 phong), tong so phong va so phong tiep nhan duoc
    const [rooms, setRooms] = useState([]);
    const [roomCount, setRoomCount] = useState(0);
    const [receivableRooms, setReceivableRooms] = useState(0);
    const [roomPage, setRoomPage] = useState(1);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [lastUpdated, setLastUpdated] = useState(null);
    const [refreshing, setRefreshing] = useState(false);
    // phong dang chon nam tren link /receptionist/room-queue/{roomId}
    const { roomId: selectedRoomId } = useParams();
    const navigate = useNavigate();
    const backToOverview = () => navigate(ROUTES.RECEPTIONIST.ROOM_QUEUE);
    const [refreshKey, setRefreshKey] = useState(0);

    // tai so lieu hang doi: 1 trang phong; mo phong tu link thi server chi tra phong do
    const fetchStatus = useCallback(async (signal) => {
        try {
            const result = await visitApi.getQueueStatusPage({
                roomId: selectedRoomId,
                page: selectedRoomId ? 0 : roomPage - 1,
                size: PAGE_SIZE,
            }, signal);
            setRooms(Array.isArray(result?.rooms?.content) ? result.rooms.content : []);
            setRoomCount(result?.rooms?.page?.totalElements ?? 0);
            setReceivableRooms(result?.receivableRooms ?? 0);
            setLoadError('');
            setLastUpdated(new Date());
        } catch (error) {
            if (error?.name !== 'AbortError') setLoadError(loadErrorMessage(error));
        } finally {
            setLoading(false);
        }
    }, [visitApi, selectedRoomId, roomPage]);

    // bam Lam moi
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

    // trang phong dang xem, nhom theo khoa (tieu de khoa giu lai cho cac phong tren trang)
    const pageGroups = groupByDepartment(rooms);
    const roomPageCount = Math.max(1, Math.ceil(roomCount / PAGE_SIZE));
    if (!loading && !selectedRoomId && roomPage > roomPageCount) setRoomPage(roomPageCount);
    const pagedRooms = {
        page: roomPage, setPage: setRoomPage, pageCount: roomPageCount, pageSize: PAGE_SIZE, total: roomCount,
    };
    const noRoomCanReceive = !loading && lastUpdated !== null && receivableRooms === 0;
    const selectedRoom = rooms.find((room) => room.roomId === selectedRoomId) ?? null;

    return (
        <div className="mx-auto max-w-[1400px] space-y-4 pb-8 text-slate-800">
            <header className="flex flex-wrap items-start justify-between gap-3">
                <AccountPageHeader
                    title="Tình trạng hàng đợi phòng khám"
                    description="Các phòng khám đang có bác sĩ trực và số bệnh nhân của từng phòng tại thời điểm hiện tại, nhóm theo khoa. Chọn một phòng để xem danh sách chờ."
                    crumbs={[{ label: 'Hàng đợi phòng khám' }]}
                    onHome={() => navigate(ROUTES.RECEPTIONIST.OVERVIEW)}
                />
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
                    // phong tren link khong (con) trong danh sach
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
                            {pageGroups.map((group) => (
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
                                                // bam ca dong (hoac Enter) de mo phong
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
                        <ListPagination paged={pagedRooms} label="Phân trang danh sách phòng" />
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
