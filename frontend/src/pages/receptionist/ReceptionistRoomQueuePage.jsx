import { useCallback, useEffect, useMemo, useState } from 'react';
import { DoorOpen, RefreshCw } from 'lucide-react';
import defaultVisitApi from '../../apis/visitApi';

const REFRESH_MS = 10_000;

const COUNT_COLUMNS = [
    ['waitingCount', 'Chờ khám', 'text-amber-700'],
    ['inExaminationCount', 'Đang khám', 'text-blue-700'],
    ['waitingParaclinicalCount', 'Chờ CLS', 'text-violet-700'],
    ['waitingConclusionCount', 'Chờ kết luận', 'text-teal-700'],
    ['skippedCount', 'Bỏ lượt', 'text-rose-700'],
];

function formatTime(value) {
    return value ? value.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';
}

function SummaryCard({ label, value, className }) {
    return (
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs text-slate-500">{label}</p>
            <p className={`mt-1 text-3xl font-extrabold ${className}`}>{value}</p>
        </div>
    );
}

/** UC-42 Room queue status: counts of queued visits per active examination room. */
export default function ReceptionistRoomQueuePage({ visitApi = defaultVisitApi }) {
    const [rooms, setRooms] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [lastUpdated, setLastUpdated] = useState(null);

    const fetchStatus = useCallback(async (signal) => {
        try {
            const list = await visitApi.getQueueStatus(signal);
            setRooms(Array.isArray(list) ? list : []);
            setLoadError('');
            setLastUpdated(new Date());
        } catch (error) {
            if (error?.name !== 'AbortError') setLoadError('Không thể tải tình trạng hàng đợi. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    }, [visitApi]);

    useEffect(() => {
        const controller = new AbortController();
        const first = window.setTimeout(() => void fetchStatus(controller.signal), 0);
        const timer = window.setInterval(() => void fetchStatus(controller.signal), REFRESH_MS);
        return () => {
            controller.abort();
            window.clearTimeout(first);
            window.clearInterval(timer);
        };
    }, [fetchStatus]);

    const totals = useMemo(() => rooms.reduce((sum, room) => ({
        waiting: sum.waiting + room.waitingCount,
        inExamination: sum.inExamination + room.inExaminationCount,
        busiest: Math.max(sum.busiest, room.waitingCount),
    }), { waiting: 0, inExamination: 0, busiest: 0 }), [rooms]);

    return (
        <div className="mx-auto max-w-[1400px] space-y-4 pb-8 text-slate-800">
            <header className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                    <DoorOpen className="mt-1 h-7 w-7 text-teal-600" />
                    <div>
                        <h1 className="text-2xl font-extrabold tracking-tight text-[#142534]">Tình trạng hàng đợi phòng khám</h1>
                        <p className="mt-1 text-sm text-slate-500">Số bệnh nhân theo từng phòng trong ngày. Lượt khám dịch vụ chỉ được tính sau khi đã thanh toán phí khám.</p>
                    </div>
                </div>
                <button type="button" onClick={() => void fetchStatus()} className="flex cursor-pointer items-center gap-2 rounded-lg border border-teal-300 px-3 py-2 text-xs font-semibold text-teal-700 hover:bg-teal-50">
                    <RefreshCw className="h-4 w-4" />
                    Tải lại
                </button>
            </header>

            <div className="grid gap-3 sm:grid-cols-3">
                <SummaryCard label="Phòng khám đang hoạt động" value={rooms.length} className="text-[#142534]" />
                <SummaryCard label="Tổng bệnh nhân chờ khám" value={totals.waiting} className="text-amber-600" />
                <SummaryCard label="Đang khám" value={totals.inExamination} className="text-blue-600" />
            </div>

            <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                {loadError && <p role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2.5 text-xs text-rose-700">{loadError}</p>}

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[760px] border-collapse text-left text-sm">
                        <thead>
                            <tr className="border-b border-slate-200 bg-[#f2f8f7] text-xs text-slate-600">
                                <th scope="col" className="px-4 py-3 font-bold">Phòng khám</th>
                                {COUNT_COLUMNS.map(([key, label]) => (
                                    <th key={key} scope="col" className="px-4 py-3 text-right font-bold">{label}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {rooms.map((room) => {
                                const busiest = totals.busiest > 0 && room.waitingCount === totals.busiest;
                                return (
                                    <tr key={room.roomId} className="border-b border-slate-100 hover:bg-slate-50">
                                        <td className="px-4 py-3">
                                            <p className="font-semibold text-slate-800">
                                                {room.roomName || room.roomCode}
                                                {busiest && <span className="ml-2 rounded-md border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-[11px] font-semibold text-amber-700">Đông nhất</span>}
                                            </p>
                                            <p className="text-xs text-slate-500">{[room.roomCode, room.location].filter(Boolean).join(' · ')}</p>
                                        </td>
                                        {COUNT_COLUMNS.map(([key, , className]) => (
                                            <td key={key} className={`px-4 py-3 text-right text-base font-bold tabular-nums ${room[key] > 0 ? className : 'text-slate-300'}`}>{room[key]}</td>
                                        ))}
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                    {rooms.length === 0 && (
                        <div className="flex min-h-48 flex-col items-center justify-center px-6 text-center">
                            <h3 className="text-sm font-bold">{loading ? 'Đang tải…' : 'Chưa có phòng khám đang hoạt động'}</h3>
                        </div>
                    )}
                </div>

                <p className="border-t border-slate-100 px-4 py-3 text-[11px] text-slate-400">
                    Tự động cập nhật mỗi 10 giây · Cập nhật lần cuối: {formatTime(lastUpdated)}
                </p>
            </div>
        </div>
    );
}
