import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Stethoscope,
    Users,
    Clock,
    AlertCircle,
    CheckCircle2,
    RefreshCw,
    Search,
    Building2,
    Calendar,
    Phone,
    User,
    ArrowRight,
    FlaskConical,
    X,
    FileText,
    Star,
} from 'lucide-react';
import visitApi from '../../apis/visitApi';
import ParaclinicalOrderDialog from '../../components/paraclinical/ParaclinicalOrderDialog';
import { createdMessage } from '../../components/paraclinical/paraclinicalOrder';

function formatTime(dateStr) {
    if (!dateStr) return '—';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        return d.toLocaleTimeString('vi-VN', {
            hour: '2-digit',
            minute: '2-digit',
        });
    } catch {
        return dateStr;
    }
}

function calculateAge(dobStr) {
    if (!dobStr) return '';
    try {
        const birthYear = new Date(`${dobStr}T00:00:00`).getFullYear();
        if (isNaN(birthYear)) return '';
        const currentYear = new Date().getFullYear();
        return `${currentYear - birthYear} tuổi`;
    } catch {
        return '';
    }
}

export default function DoctorQueuePage() {
    const [rooms, setRooms] = useState([]);
    const [selectedRoomId, setSelectedRoomId] = useState('');
    const [visits, setVisits] = useState([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL'); // ALL, WAITING, IN_EXAMINATION, WAITING_PARACLINICAL
    const [notice, setNotice] = useState('');
    const [error, setError] = useState('');

    // Modal Chỉ định CLS (Lần 2)
    const [clsModalVisit, setClsModalVisit] = useState(null);

    // Tải danh sách phòng khám hoạt động
    useEffect(() => {
        async function loadRooms() {
            try {
                const res = await visitApi.getActiveExaminationRooms();
                const list = res?.data || res || [];
                setRooms(list);
                if (list.length > 0 && !selectedRoomId) {
                    setSelectedRoomId(list[0].id);
                }
            } catch (err) {
                console.error('Không thể tải danh sách phòng khám:', err);
            }
        }
        loadRooms();
    }, [selectedRoomId]);

    // Tải hàng chờ lượt khám của phòng
    const fetchQueue = useCallback(async () => {
        if (!selectedRoomId) return;
        setLoading(true);
        setError('');
        try {
            const res = await visitApi.getQueue({ roomId: selectedRoomId });
            const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
            setVisits(list);
        } catch (err) {
            setError(err?.message || 'Không thể tải hàng chờ khám bệnh.');
        } finally {
            setLoading(false);
        }
    }, [selectedRoomId]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchQueue();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchQueue]);

    // Thống kê nhanh hàng chờ
    const stats = useMemo(() => {
        let waitingCount = 0;
        let priorityCount = 0;
        let inExamCount = 0;
        let waitingClsCount = 0;

        visits.forEach((v) => {
            if (v.status === 'WAITING') {
                waitingCount++;
                if (v.isPriority) priorityCount++;
            } else if (v.status === 'IN_EXAMINATION') {
                inExamCount++;
            } else if (v.status === 'WAITING_PARACLINICAL') {
                waitingClsCount++;
            }
        });

        return {
            waitingCount,
            priorityCount,
            inExamCount,
            waitingClsCount,
            total: visits.length,
        };
    }, [visits]);

    // Lọc danh sách lượt khám
    const filteredVisits = useMemo(() => {
        const q = search.trim().toLowerCase();
        return visits.filter((v) => {
            // Lọc trạng thái
            if (statusFilter !== 'ALL' && v.status !== statusFilter) {
                return false;
            }
            // Lọc tìm kiếm
            if (!q) return true;
            return [
                v.patientName,
                v.patientCode,
                v.sovaovien,
                v.patientPhone,
                v.lydovaovien,
            ].some((f) => f && String(f).toLowerCase().includes(q));
        });
    }, [visits, statusFilter, search]);

    // Gọi bệnh nhân vào khám
    const handleStartExam = async (visit) => {
        try {
            await visitApi.startExamination(visit.id);
            setNotice(`Đã tiếp nhận bệnh nhân ${visit.patientName || 'bệnh nhân'} vào phòng khám!`);
            fetchQueue();
        } catch (err) {
            alert(err?.message || 'Không thể gọi bệnh nhân vào khám.');
        }
    };

    // UC-49: mở màn chỉ định CLS; tạo xong hiện MSG67 và tải lại hàng chờ
    const handleOpenClsModal = (visit) => setClsModalVisit(visit);

    const handleClsCreated = (order) => {
        setNotice(createdMessage(order));
        setClsModalVisit(null);
        fetchQueue();
    };

    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-7xl space-y-6">
                {/* Header */}
                <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                    <div>
                        <div className="flex items-center gap-3">
                            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-teal-600 text-white shadow-md shadow-teal-600/20">
                                <Stethoscope className="h-6 w-6" />
                            </div>
                            <div>
                                <h1 className="text-2xl font-black tracking-tight text-[#142534]">
                                    Hàng chờ khám bệnh (Doctor Queue)
                                </h1>
                                <p className="text-xs text-[#60737d]">
                                    Tiếp nhận và gọi bệnh nhân vào khám theo số thứ tự ưu tiên
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Bộ chọn phòng khám & nút làm mới */}
                    <div className="flex flex-wrap items-center gap-3">
                        <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 shadow-sm">
                            <Building2 className="h-4 w-4 text-teal-600" />
                            <select
                                value={selectedRoomId}
                                onChange={(e) => setSelectedRoomId(e.target.value)}
                                className="bg-transparent text-sm font-bold text-slate-800 outline-none"
                            >
                                {rooms.map((r) => (
                                    <option key={r.id} value={r.id}>
                                        {r.roomName} ({r.roomCode})
                                    </option>
                                ))}
                            </select>
                        </div>

                        <button
                            type="button"
                            onClick={fetchQueue}
                            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50"
                        >
                            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin text-teal-600' : 'text-slate-500'}`} />
                            Làm mới
                        </button>
                    </div>
                </header>

                {/* Banner thông báo */}
                {notice && (
                    <div className="flex items-center justify-between gap-3 rounded-2xl border border-teal-200 bg-teal-50 p-4 text-sm text-teal-900 shadow-sm">
                        <div className="flex items-center gap-3">
                            <CheckCircle2 className="h-5 w-5 flex-shrink-0 text-teal-600" />
                            <span className="font-medium">{notice}</span>
                        </div>
                        <button
                            type="button"
                            onClick={() => setNotice('')}
                            className="rounded-lg p-1 text-teal-700 hover:bg-teal-100"
                        >
                            <X className="h-4 w-4" />
                        </button>
                    </div>
                )}

                {error && (
                    <div className="flex items-center justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 shadow-sm">
                        <div className="flex items-center gap-3">
                            <AlertCircle className="h-5 w-5 flex-shrink-0 text-rose-600" />
                            <span>{error}</span>
                        </div>
                        <button
                            type="button"
                            onClick={() => setError('')}
                            className="rounded-lg p-1 text-rose-700 hover:bg-rose-100"
                        >
                            <X className="h-4 w-4" />
                        </button>
                    </div>
                )}

                {/* Thống kê 4 thẻ */}
                <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    {/* Đang chờ khám */}
                    <article className="rounded-2xl border border-amber-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-amber-700">Đang chờ khám</p>
                            <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-bold text-amber-700">Hàng đợi</span>
                        </div>
                        <p className="mt-3 text-3xl font-extrabold tracking-tight text-amber-600">
                            {String(stats.waitingCount).padStart(2, '0')}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Bệnh nhân đã nộp phí Lần 1 & sẵn sàng</p>
                    </article>

                    {/* Bệnh nhân ưu tiên */}
                    <article className="rounded-2xl border border-rose-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-rose-700">Ca ưu tiên</p>
                            <span className="rounded-full bg-rose-50 px-2.5 py-0.5 text-xs font-bold text-rose-700">VIP / Cấp tốc</span>
                        </div>
                        <p className="mt-3 text-3xl font-extrabold tracking-tight text-rose-600">
                            {String(stats.priorityCount).padStart(2, '0')}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Người cao tuổi ≥ 75 hoặc khuyết tật nặng</p>
                    </article>

                    {/* Đang khám */}
                    <article className="rounded-2xl border border-teal-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-teal-700">Đang khám</p>
                            <span className="rounded-full bg-teal-50 px-2.5 py-0.5 text-xs font-bold text-teal-700">Trong phòng</span>
                        </div>
                        <p className="mt-3 text-3xl font-extrabold tracking-tight text-teal-700">
                            {String(stats.inExamCount).padStart(2, '0')}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Bệnh nhân đang được bác sĩ thăm khám</p>
                    </article>

                    {/* Chờ kết quả / Chờ CLS */}
                    <article className="rounded-2xl border border-indigo-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-indigo-700">Chờ làm CLS</p>
                            <span className="rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-bold text-indigo-700">Cận lâm sàng</span>
                        </div>
                        <p className="mt-3 text-3xl font-extrabold tracking-tight text-indigo-600">
                            {String(stats.waitingClsCount).padStart(2, '0')}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Đang nộp phí Lần 2 hoặc làm xét nghiệm</p>
                    </article>
                </section>

                {/* BẢNG CHÍNH: HÀNG CHỜ KHÁM BỆNH */}
                <section className="overflow-hidden rounded-2xl border border-[#e3ece9] bg-white shadow-sm">
                    <div className="flex flex-col justify-between gap-3 border-b border-[#edf2f0] p-5 sm:flex-row sm:items-center">
                        <div>
                            <h2 className="text-lg font-bold text-[#142534]">
                                Danh sách bệnh nhân trong phòng
                                <span className="ml-2.5 rounded-full bg-teal-100 px-3 py-1 text-xs font-bold text-teal-800">
                                    {filteredVisits.length} ca
                                </span>
                            </h2>
                            <p className="mt-1 text-xs text-[#60737d]">
                                Danh sách sắp xếp theo quy tắc ưu tiên (BR-18): Ca ưu tiên hiển thị trước, sau đó theo số thứ tự (STT).
                            </p>
                        </div>

                        {/* Thanh tìm kiếm & Tabs lọc trạng thái */}
                        <div className="flex flex-wrap items-center gap-2">
                            <div className="relative">
                                <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                <input
                                    type="text"
                                    placeholder="Tìm tên, mã BN, số VV..."
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                    className="w-56 rounded-xl border border-slate-200 pl-9 pr-3 py-2 text-xs outline-none focus:border-teal-500"
                                />
                            </div>

                            <div className="flex items-center gap-1 rounded-xl bg-slate-100 p-1 text-xs font-medium">
                                <button
                                    type="button"
                                    onClick={() => setStatusFilter('ALL')}
                                    className={`rounded-lg px-3 py-1.5 transition ${
                                        statusFilter === 'ALL' ? 'bg-white font-bold text-teal-800 shadow-sm' : 'text-slate-600'
                                    }`}
                                >
                                    Tất cả ({visits.length})
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setStatusFilter('WAITING')}
                                    className={`rounded-lg px-3 py-1.5 transition ${
                                        statusFilter === 'WAITING' ? 'bg-white font-bold text-amber-800 shadow-sm' : 'text-slate-600'
                                    }`}
                                >
                                    Chờ khám ({stats.waitingCount})
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setStatusFilter('IN_EXAMINATION')}
                                    className={`rounded-lg px-3 py-1.5 transition ${
                                        statusFilter === 'IN_EXAMINATION' ? 'bg-white font-bold text-teal-800 shadow-sm' : 'text-slate-600'
                                    }`}
                                >
                                    Đang khám ({stats.inExamCount})
                                </button>
                            </div>
                        </div>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[950px] text-left text-sm">
                            <thead className="bg-[#f8fbfa] text-xs font-semibold uppercase tracking-wider text-[#60737d]">
                                <tr>
                                    <th className="px-5 py-3.5 text-center">STT</th>
                                    <th className="px-5 py-3.5">Mã BN / Số vào viện</th>
                                    <th className="px-5 py-3.5">Bệnh nhân</th>
                                    <th className="px-5 py-3.5">Đối tượng</th>
                                    <th className="px-5 py-3.5">Lý do khám bệnh</th>
                                    <th className="px-5 py-3.5">Trạng thái</th>
                                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                                </tr>
                            </thead>

                            <tbody className="divide-y divide-[#edf2f0]">
                                {loading ? (
                                    <tr>
                                        <td colSpan={7} className="py-12 text-center text-slate-500">
                                            <RefreshCw className="mx-auto h-6 w-6 animate-spin text-teal-600" />
                                            <p className="mt-2 text-sm font-medium">Đang tải danh sách hàng chờ...</p>
                                        </td>
                                    </tr>
                                ) : filteredVisits.length === 0 ? (
                                    <tr>
                                        <td colSpan={7} className="py-14 text-center">
                                            <CheckCircle2 className="mx-auto h-8 w-8 text-emerald-500" />
                                            <p className="mt-2 text-base font-semibold text-slate-800">Không có bệnh nhân nào trong hàng chờ</p>
                                            <p className="text-sm text-slate-500">
                                                Khi bệnh nhân hoàn tất nộp tiền khám Lần 1 tại Thu ngân, họ sẽ tự động xuất hiện tại đây.
                                            </p>
                                        </td>
                                    </tr>
                                ) : (
                                    filteredVisits.map((v) => {
                                        const isWaiting = v.status === 'WAITING';
                                        const isInExam = v.status === 'IN_EXAMINATION';
                                        const isWaitingCls = v.status === 'WAITING_PARACLINICAL';

                                        return (
                                            <tr
                                                key={v.id}
                                                className={`transition ${
                                                    isInExam
                                                        ? 'bg-teal-50/40 hover:bg-teal-50/70'
                                                        : v.isPriority
                                                        ? 'bg-rose-50/30 hover:bg-rose-50/50'
                                                        : 'hover:bg-[#f8fbfa]'
                                                }`}
                                            >
                                                {/* STT & Huy hiệu ưu tiên */}
                                                <td className="px-5 py-4 text-center">
                                                    <div className="inline-flex flex-col items-center">
                                                        <span className="font-mono text-base font-black text-slate-800">
                                                            #{String(v.queueNumber).padStart(2, '0')}
                                                        </span>
                                                        {v.isPriority && (
                                                            <span
                                                                className="mt-1 inline-flex items-center gap-1 rounded-full bg-rose-100 px-2 py-0.5 text-[10px] font-extrabold text-rose-700"
                                                                title={`Ưu tiên: ${v.priorityReason || 'Người cao tuổi/khuyết tật'}`}
                                                            >
                                                                <Star className="h-3 w-3 fill-rose-600 text-rose-600" />
                                                                ƯU TIÊN
                                                            </span>
                                                        )}
                                                    </div>
                                                </td>

                                                {/* Mã BN & Số vào viện */}
                                                <td className="px-5 py-4">
                                                    <p className="font-mono text-xs font-bold text-teal-800">
                                                        {v.patientCode || '—'}
                                                    </p>
                                                    <p className="mt-0.5 font-mono text-[11px] text-slate-500">
                                                        VV: {v.sovaovien}
                                                    </p>
                                                </td>

                                                {/* Bệnh nhân */}
                                                <td className="px-5 py-4">
                                                    <p className="font-semibold text-[#142534]">
                                                        {v.patientName}
                                                    </p>
                                                    <p className="mt-0.5 text-xs text-slate-500">
                                                        {v.patientGender || '—'}{' '}
                                                        {v.patientDob ? `• ${calculateAge(v.patientDob)}` : ''}{' '}
                                                        {v.patientPhone ? `• ${v.patientPhone}` : ''}
                                                    </p>
                                                </td>

                                                {/* Đối tượng */}
                                                <td className="px-5 py-4">
                                                    {v.doituongbnLoai === 'DV' ? (
                                                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-800 ring-1 ring-emerald-200">
                                                            <span>✓</span> Khám Dịch vụ (Đã nộp Lần 1)
                                                        </span>
                                                    ) : (
                                                        <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-800 ring-1 ring-blue-200">
                                                            <span>🏥</span> BHYT
                                                        </span>
                                                    )}
                                                </td>

                                                {/* Lý do vào viện */}
                                                <td className="px-5 py-4 text-xs font-medium text-slate-700 max-w-xs truncate">
                                                    <p className="font-medium">{v.lydovaovien || 'Khám tổng quát'}</p>
                                                    <p className="mt-0.5 text-[11px] text-slate-400">
                                                        Đến lúc: {formatTime(v.thoigianvaovien)}
                                                    </p>
                                                </td>

                                                {/* Trạng thái */}
                                                <td className="px-5 py-4">
                                                    {isWaiting && (
                                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-700 ring-1 ring-amber-200">
                                                            <Clock className="h-3.5 w-3.5" />
                                                            Chờ khám
                                                        </span>
                                                    )}
                                                    {isInExam && (
                                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-teal-50 px-3 py-1 text-xs font-bold text-teal-800 ring-1 ring-teal-200 animate-pulse">
                                                            <Stethoscope className="h-3.5 w-3.5" />
                                                            Đang khám bệnh
                                                        </span>
                                                    )}
                                                    {isWaitingCls && (
                                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-700 ring-1 ring-indigo-200">
                                                            <FlaskConical className="h-3.5 w-3.5" />
                                                            Đang đi làm CLS
                                                        </span>
                                                    )}
                                                    {v.status === 'WAITING_FEE_PAYMENT' && (
                                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">
                                                            Chờ thu ngân
                                                        </span>
                                                    )}
                                                </td>

                                                {/* Thao tác của Bác sĩ */}
                                                <td className="px-5 py-4 text-right">
                                                    <div className="flex items-center justify-end gap-2">
                                                        {isWaiting && (
                                                            <button
                                                                type="button"
                                                                onClick={() => handleStartExam(v)}
                                                                className="inline-flex items-center gap-1.5 rounded-xl bg-teal-600 px-3.5 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-teal-700"
                                                                title="Tiếp nhận bệnh nhân vào phòng khám"
                                                            >
                                                                <ArrowRight className="h-4 w-4" />
                                                                Gọi vào khám
                                                            </button>
                                                        )}

                                                        {isInExam && (
                                                            <button
                                                                type="button"
                                                                onClick={() => handleOpenClsModal(v)}
                                                                className="inline-flex items-center gap-1.5 rounded-xl bg-indigo-600 px-3.5 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-indigo-700"
                                                                title="Chỉ định xét nghiệm, chụp chiếu cận lâm sàng (Lần 2)"
                                                            >
                                                                <FlaskConical className="h-4 w-4" />
                                                                Chỉ định CLS (Lần 2)
                                                            </button>
                                                        )}

                                                        {isWaitingCls && (
                                                            <span className="text-xs font-medium text-indigo-600">
                                                                Chờ kết quả CLS...
                                                            </span>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })
                                )}
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>

            {clsModalVisit && (
                <ParaclinicalOrderDialog
                    visit={clsModalVisit}
                    onClose={() => setClsModalVisit(null)}
                    onCreated={handleClsCreated}
                />
            )}
        </main>
    );
}
