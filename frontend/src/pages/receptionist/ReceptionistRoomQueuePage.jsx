import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, DoorOpen, RefreshCw } from 'lucide-react';

import defaultVisitApi from '../../apis/visitApi';
import { VISIT_STATUS_LABELS } from '../../constants/visitData';

const REFRESH_MS = 10_000;
const NO_DEPARTMENT = 'Chưa thuộc khoa';
const NO_ROOM_MESSAGE = 'Hiện không có phòng khám nào có thể tiếp nhận bệnh nhân. Vui lòng báo quản trị viên.';

const COUNT_COLUMNS = [
    ['waitingPaymentCount', 'Chờ thanh toán', 'text-orange-700'],
    ['waitingCount', 'Chờ khám', 'text-amber-700'],
    ['inExaminationCount', 'Đang khám', 'text-blue-700'],
    ['waitingParaclinicalCount', 'Chờ CLS', 'text-violet-700'],
    ['waitingConclusionCount', 'Chờ kết luận', 'text-teal-700'],
    ['skippedCount', 'Bỏ lượt', 'text-rose-700'],
];

function loadErrorMessage(error) {
    if (error?.status === 401 || error?.status === 403) {
        return 'Bạn không có quyền xem tình trạng hàng đợi phòng khám.';
    }
    if (error?.status >= 500) {
        return 'Không thể tải tình trạng hàng đợi do lỗi hệ thống. Vui lòng thử lại sau.';
    }
    return 'Không thể tải tình trạng hàng đợi. Vui lòng thử lại.';
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

function SummaryCard({ label, value, className }) {
    return (
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500">{label}</p>
            <p className={`mt-1 text-3xl font-extrabold ${className}`}>{value}</p>
        </div>
    );
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

/** UC-42 step 5: examination waiting list of one room. */
function RoomDetail({ room, visitApi, refreshKey, onBack }) {
    const [visits, setVisits] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const controller = new AbortController();
        visitApi.getQueue({ roomId: room.roomId }, controller.signal)
            .then((list) => {
                setVisits(Array.isArray(list) ? list : []);
                setError('');
            })
            .catch((failure) => {
                if (failure?.name !== 'AbortError') setError(loadErrorMessage(failure));
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
                        Bác sĩ: {room.doctorNames?.length ? room.doctorNames.join(', ') : 'Chưa có bác sĩ'}
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
                {visits.length === 0 && (
                    <p className="px-6 py-10 text-center text-sm text-slate-500">
                        {loading ? 'Đang tải danh sách chờ…' : 'Phòng chưa có bệnh nhân trong hàng đợi.'}
                    </p>
                )}
            </div>
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
    const [selectedRoomId, setSelectedRoomId] = useState(null);
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

    const totals = useMemo(() => rooms.reduce((sum, room) => ({
        waiting: sum.waiting + room.waitingCount,
        inExamination: sum.inExamination + room.inExaminationCount,
        busiest: room.hasDoctor ? Math.max(sum.busiest, room.waitingCount) : sum.busiest,
    }), { waiting: 0, inExamination: 0, busiest: 0 }), [rooms]);
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
                        <p className="mt-1 text-sm text-slate-500">Số bệnh nhân theo từng phòng trong ngày, nhóm theo khoa. Chọn một phòng để xem danh sách chờ.</p>
                    </div>
                </div>
                <button type="button" disabled={refreshing || loading} onClick={() => void refreshNow()} className="flex cursor-pointer items-center gap-2 rounded-lg border border-teal-300 px-3 py-2 text-xs font-semibold text-teal-700 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-50">
                    <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
                    {refreshing ? 'Đang tải…' : 'Tải lại'}
                </button>
            </header>

            <div className="grid gap-3 sm:grid-cols-3">
                <SummaryCard label="Phòng khám tiếp nhận được" value={receivableRooms} className="text-[#142534]" />
                <SummaryCard label="Tổng bệnh nhân chờ khám" value={totals.waiting} className="text-amber-600" />
                <SummaryCard label="Đang khám" value={totals.inExamination} className="text-blue-600" />
            </div>

            {noRoomCanReceive && !loadError && (
                <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{NO_ROOM_MESSAGE}</p>
            )}

            {selectedRoom ? (
                <RoomDetail room={selectedRoom} visitApi={visitApi} refreshKey={refreshKey} onBack={() => setSelectedRoomId(null)} />
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
                                </tr>
                            </thead>
                            {groups.map((group) => (
                                <tbody key={group.name}>
                                    <tr className="border-b border-slate-200 bg-slate-50">
                                        <th scope="colgroup" colSpan={COUNT_COLUMNS.length + 1} className="px-4 py-2 text-xs font-bold uppercase tracking-wide text-slate-500">
                                            {group.name}
                                        </th>
                                    </tr>
                                    {group.rooms.map((room) => {
                                        const busiest = room.hasDoctor && totals.busiest > 0 && room.waitingCount === totals.busiest;
                                        return (
                                            <tr key={room.roomId} className={`border-b border-slate-100 hover:bg-slate-50 ${room.hasDoctor ? '' : 'bg-slate-50 opacity-60'}`}>
                                                <td className="px-4 py-3">
                                                    <button type="button" onClick={() => setSelectedRoomId(room.roomId)} className="cursor-pointer text-left font-semibold text-slate-800 hover:text-teal-700 hover:underline">
                                                        {room.roomName || room.roomCode}
                                                    </button>
                                                    {busiest && <span className="ml-2 rounded-md border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-[11px] font-semibold text-amber-700">Đông nhất</span>}
                                                    <p className="text-xs text-slate-500">{[room.roomCode, room.location].filter(Boolean).join(' · ')}</p>
                                                    <p className="text-xs text-slate-500">
                                                        {room.hasDoctor ? `Bác sĩ: ${room.doctorNames.join(', ')}` : 'Chưa có bác sĩ'}
                                                    </p>
                                                </td>
                                                {COUNT_COLUMNS.map(([key, , className]) => (
                                                    <td key={key} className={`px-4 py-3 text-right text-base font-bold tabular-nums ${room[key] > 0 ? className : 'text-slate-300'}`}>{room[key] ?? 0}</td>
                                                ))}
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            ))}
                        </table>
                        {rooms.length === 0 && (
                            <div className="flex min-h-48 flex-col items-center justify-center px-6 text-center">
                                <h3 className="text-sm font-bold">
                                    {loading ? 'Đang tải tình trạng hàng đợi…' : loadError && !lastUpdated ? 'Chưa tải được dữ liệu' : 'Chưa có phòng khám đang hoạt động'}
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
