import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    FilterX,
    Home,
    RefreshCw,
    Search,
    Stethoscope,
    UserRound,
    UsersRound,
    Volume2,
    X,
} from 'lucide-react';
import { toast } from 'sonner';
import defaultVisitApi from '../../apis/visitApi';
import { createAccountApi } from '../../apis/accountApi';
import { apiClient } from '../../services/session';

const REFRESH_MS = 10_000;
const PAGE_SIZES = [10, 20, 50];

const TABS = [
    { id: 'waiting', label: 'Chờ khám', match: (v) => v.status === 'WAITING' && !v.isSkipped },
    { id: 'in-examination', label: 'Đang khám', match: (v) => v.status === 'IN_EXAMINATION' },
    { id: 'conclusion', label: 'Chờ kết luận', match: (v) => v.status === 'WAITING_CONCLUSION' },
    { id: 'skipped', label: 'Vắng mặt', match: (v) => v.isSkipped },
];

const EMPTY_COPY = {
    waiting: ['Chưa có bệnh nhân chờ khám', 'Danh sách hiển thị lượt khám BHYT và lượt khám dịch vụ đã thanh toán.'],
    'in-examination': ['Chưa có bệnh nhân đang khám', 'Bệnh nhân đã được gọi vào khám sẽ hiển thị tại đây.'],
    conclusion: ['Chưa có bệnh nhân chờ kết luận', 'Bệnh nhân hoàn tất cận lâm sàng sẽ xuất hiện tại đây.'],
    skipped: ['Chưa có bệnh nhân bỏ lượt', 'Bệnh nhân vắng mặt khi được gọi sẽ xuất hiện tại đây.'],
};

const STATUS_BADGE = {
    WAITING: ['Đang chờ', 'bg-amber-50 text-amber-700 border-amber-200'],
    IN_EXAMINATION: ['Đang khám', 'bg-blue-50 text-blue-700 border-blue-200'],
    WAITING_PARACLINICAL: ['Chờ CLS', 'bg-violet-50 text-violet-700 border-violet-200'],
    WAITING_CONCLUSION: ['Chờ kết luận', 'bg-teal-50 text-teal-700 border-teal-200'],
};

const SKIPPED_BADGE = ['Bỏ lượt', 'bg-rose-50 text-rose-700 border-rose-200'];

const COLUMNS = ['STT', 'Số thứ tự', 'Họ tên', 'Mã BN', 'Tuổi', 'Giới tính', 'Ưu tiên', 'Loại khám',
    'Lý do khám', 'Thời gian đăng ký', 'Nguồn / Phòng chuyển đến', 'Trạng thái', 'Thao tác'];

const defaultProfileLoader = (signal) => createAccountApi(apiClient).profile(signal);

function pad(value) {
    return String(value ?? '').padStart(3, '0');
}

function formatDateTime(value) {
    if (!value) return ['—', ''];
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return ['—', ''];
    return [
        date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
        date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }),
    ];
}

function ageOf(dob) {
    if (!dob) return null;
    const birth = new Date(`${dob}T00:00:00`);
    if (Number.isNaN(birth.getTime())) return null;
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    const beforeBirthday = today.getMonth() < birth.getMonth()
        || (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate());
    return beforeBirthday ? age - 1 : age;
}

function Badge({ className, children }) {
    return <span className={`inline-flex items-center justify-center whitespace-nowrap rounded-md border px-2 py-1 text-[11px] font-semibold ${className}`}>{children}</span>;
}

function PriorityBadge({ visit }) {
    return visit.isPriority
        ? <Badge className="border-rose-200 bg-rose-50 text-rose-600">Ưu tiên</Badge>
        : <Badge className="border-emerald-200 bg-emerald-50 text-emerald-700">Thường</Badge>;
}

function TypeBadge({ type }) {
    if (type === 'BHYT') return <Badge className="border-blue-100 bg-blue-50 text-blue-700">BHYT</Badge>;
    if (type === 'DV') return <Badge className="border-pink-100 bg-pink-50 text-pink-600">Dịch vụ</Badge>;
    return '—';
}

function StatusBadge({ visit }) {
    const [label, className] = visit.isSkipped ? SKIPPED_BADGE : STATUS_BADGE[visit.status] ?? [visit.status, 'border-slate-200 bg-slate-50 text-slate-600'];
    return <Badge className={className}>{label}</Badge>;
}

function SelectFilter({ id, label, value, onChange, options }) {
    return (
        <div className="relative">
            <label htmlFor={id} className="sr-only">{label}</label>
            <select id={id} value={value} onChange={(event) => onChange(event.target.value)} className="h-10 min-w-40 cursor-pointer appearance-none rounded-lg border border-slate-200 bg-white pl-3 pr-9 text-xs font-medium text-slate-600 outline-none hover:border-teal-300 focus:border-teal-500 focus:ring-2 focus:ring-teal-100">
                {options.map(([optionValue, optionLabel]) => <option key={optionValue} value={optionValue}>{optionLabel}</option>)}
            </select>
            <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
        </div>
    );
}

/** UC-42/UC-43 Doctor waiting list; UC-44 call the patient into the examination room. */
export default function DoctorWaitingListPage({
    visitApi = defaultVisitApi,
    loadProfile = defaultProfileLoader,
    initialTab = 'waiting',
}) {
    const [profile, setProfile] = useState(null);
    const [visits, setVisits] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [lastUpdated, setLastUpdated] = useState(null);
    const [activeTab, setActiveTab] = useState(initialTab);
    const [search, setSearch] = useState('');
    const [priority, setPriority] = useState('ALL');
    const [visitType, setVisitType] = useState('ALL');
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [callingVisit, setCallingVisit] = useState(null);
    const [calling, setCalling] = useState(false);

    useEffect(() => {
        const controller = new AbortController();
        loadProfile(controller.signal).then(setProfile).catch(() => {});
        return () => controller.abort();
    }, [loadProfile]);

    // Without roomId the backend uses the signed-in doctor's assigned room.
    const fetchQueue = useCallback(async (signal) => {
        try {
            const list = await visitApi.getQueue({}, signal);
            setVisits(Array.isArray(list) ? list : []);
            setLoadError('');
            setLastUpdated(new Date());
        } catch (error) {
            if (error?.name !== 'AbortError') setLoadError('Không thể tải danh sách chờ khám. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    }, [visitApi]);

    useEffect(() => {
        const controller = new AbortController();
        const first = window.setTimeout(() => void fetchQueue(controller.signal), 0);
        const timer = window.setInterval(() => void fetchQueue(controller.signal), REFRESH_MS);
        return () => {
            controller.abort();
            window.clearTimeout(first);
            window.clearInterval(timer);
        };
    }, [fetchQueue]);

    const counts = useMemo(
        () => Object.fromEntries(TABS.map((tab) => [tab.id, visits.filter(tab.match).length])),
        [visits],
    );

    const filtered = useMemo(() => {
        const tab = TABS.find(({ id }) => id === activeTab);
        const query = search.trim().toLowerCase();
        return visits
            .filter(tab.match)
            .filter((v) => priority === 'ALL' || (priority === 'PRIORITY') === v.isPriority)
            .filter((v) => visitType === 'ALL' || v.doituongbnLoai === visitType)
            .filter((v) => !query || [v.patientName, v.patientCode, v.mabhyt, v.patientPhone]
                .some((field) => field && String(field).toLowerCase().includes(query)))
            .sort((a, b) => (a.queueNumber ?? 0) - (b.queueNumber ?? 0));
    }, [visits, activeTab, search, priority, visitType]);

    const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, pageCount);
    const rows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
    const nextPatient = activeTab === 'waiting' || activeTab === 'conclusion'
        ? filtered.find((visit) => ['WAITING', 'WAITING_CONCLUSION'].includes(visit.status) && !visit.isSkipped) ?? null
        : null;
    const canCall = Boolean(nextPatient);

    const resetFilters = () => {
        setSearch('');
        setPriority('ALL');
        setVisitType('ALL');
        setPage(1);
    };

    const callPatient = useCallback((visit) => {
        if (!visit || calling) return;
        setCallingVisit(visit);
    }, [calling]);

    const confirmPresent = useCallback(async () => {
        if (!callingVisit || calling) return;
        setCalling(true);
        try {
            await visitApi.startExamination(callingVisit.id);
            toast.success(callingVisit.status === 'WAITING_CONCLUSION'
                ? `Bệnh nhân ${callingVisit.patientName} đã có mặt, chuyển sang bước kết luận.`
                : `Bệnh nhân ${callingVisit.patientName} đã có mặt, bắt đầu khám.`);
            setCallingVisit(null);
            await fetchQueue();
        } catch {
            toast.error('Không thể xác nhận bệnh nhân. Vui lòng tải lại danh sách rồi thử lại.');
        } finally {
            setCalling(false);
        }
    }, [calling, callingVisit, fetchQueue, visitApi]);

    const skipPatient = useCallback(async () => {
        if (!callingVisit || calling) return;
        setCalling(true);
        try {
            await visitApi.skipVisit(callingVisit.id);
            toast.success(`Đã chuyển bệnh nhân ${callingVisit.patientName} sang danh sách bỏ lượt.`);
            setCallingVisit(null);
            await fetchQueue();
        } catch {
            toast.error('Không thể bỏ lượt bệnh nhân. Vui lòng tải lại danh sách rồi thử lại.');
        } finally {
            setCalling(false);
        }
    }, [calling, callingVisit, fetchQueue, visitApi]);

    useEffect(() => {
        const onKeyDown = (event) => {
            if (event.key === 'F2' && canCall) {
                event.preventDefault();
                void callPatient(nextPatient);
            }
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [callPatient, canCall, nextPatient]);

    const [updatedTime, updatedDate] = formatDateTime(lastUpdated);
    const doctorName = profile ? [profile.academicTitle, profile.fullName].filter(Boolean).join(' ') : '—';
    const emptyCopy = EMPTY_COPY[activeTab];

    return (
        <div className="mx-auto max-w-[1600px] space-y-4 pb-8 text-slate-800">
            <header className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                    <UsersRound className="mt-1 h-7 w-7 text-teal-600" />
                    <div>
                        <h1 className="text-2xl font-extrabold tracking-tight text-[#142534]">Quản lý khám bệnh</h1>
                        <p className="mt-1 text-sm text-slate-500">{profile?.roomName || 'Chưa được phân công phòng khám'}</p>
                    </div>
                </div>
                <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-sm text-slate-500">
                    <Home className="h-4 w-4" />
                    <ChevronRight className="h-3.5 w-3.5" />
                    <span>Khám bệnh</span>
                    <ChevronRight className="h-3.5 w-3.5" />
                    <span className="font-semibold text-[#142534]">Quản lý khám bệnh</span>
                </nav>
            </header>

            <section>
                <div className="min-w-0 space-y-4">
                    <div className="grid rounded-xl border border-slate-200 bg-white shadow-sm md:grid-cols-[1.2fr_1fr_0.8fr_1fr]">
                        <div className="flex items-center gap-4 border-b border-slate-100 p-4 md:border-b-0 md:border-r">
                            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-teal-50 text-teal-600"><Stethoscope className="h-7 w-7" /></div>
                            <div className="min-w-0">
                                <p className="text-xs text-slate-500">Phòng khám hiện tại</p>
                                <p className="mt-1 truncate text-base font-bold text-[#142534]">{profile?.roomName || 'Chưa được phân công'}</p>
                            </div>
                        </div>
                        <div className="border-b border-slate-100 p-4 md:border-b-0 md:border-r">
                            <p className="text-xs text-slate-500">Bác sĩ phụ trách</p>
                            <p className="mt-1 text-sm font-semibold text-slate-700">{doctorName}</p>
                        </div>
                        <div className="border-b border-slate-100 p-4 md:border-b-0 md:border-r">
                            <p className="text-xs text-slate-500">Tổng số bệnh nhân chờ</p>
                            <p className="mt-1 text-3xl font-extrabold text-teal-600">{counts.waiting}</p>
                        </div>
                        <div className="flex flex-col justify-center gap-1 p-4 text-xs">
                            <button type="button" onClick={() => void fetchQueue()} className="flex w-fit cursor-pointer items-center gap-2 text-slate-600 hover:text-teal-700" title="Tải lại ngay">
                                <RefreshCw className="h-4 w-4 text-teal-600" />
                                <span>Tự động cập nhật<br /><span className="text-slate-400">Mỗi 10 giây</span></span>
                            </button>
                            <p className="text-[11px] text-slate-400">Cập nhật lần cuối: {lastUpdated ? `${updatedDate} ${updatedTime}` : '—'}</p>
                        </div>
                    </div>

                    <div className="flex flex-wrap gap-2" role="tablist">
                        {TABS.map((tab) => (
                            <button key={tab.id} type="button" role="tab" aria-selected={activeTab === tab.id} onClick={() => { setActiveTab(tab.id); setPage(1); }} className={`cursor-pointer rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${activeTab === tab.id ? 'border-teal-700 bg-teal-700 text-white' : 'border-slate-300 bg-white text-slate-600 hover:border-teal-400 hover:text-teal-700'}`}>
                                {tab.label}
                            </button>
                        ))}
                    </div>

                    <div>
                        <h2 className="text-base font-bold text-[#142534]">{TABS.find((tab) => tab.id === activeTab)?.label === 'Vắng mặt' ? 'Danh sách bệnh nhân vắng mặt' : `Danh sách ${TABS.find((tab) => tab.id === activeTab)?.label.toLowerCase()}`}</h2>
                        <p className="mt-1 text-xs text-slate-500">Hiển thị bệnh nhân và thao tác tương ứng với trạng thái này.</p>
                    </div>

                    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                        <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 p-3">
                            <div className="relative min-w-56 flex-1">
                                <label htmlFor="waiting-search" className="sr-only">Tìm bệnh nhân</label>
                                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                <input id="waiting-search" value={search} onChange={(event) => { setSearch(event.target.value); setPage(1); }} placeholder="Tìm theo tên, mã BN, số thẻ BHYT..." className="h-10 w-full rounded-lg border border-slate-200 pl-9 pr-3 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100" />
                            </div>
                            <SelectFilter id="waiting-priority" label="Lọc ưu tiên" value={priority} onChange={(value) => { setPriority(value); setPage(1); }} options={[['ALL', 'Tất cả ưu tiên'], ['PRIORITY', 'Ưu tiên'], ['NORMAL', 'Thường']]} />
                            <SelectFilter id="waiting-type" label="Lọc loại khám" value={visitType} onChange={(value) => { setVisitType(value); setPage(1); }} options={[['ALL', 'Tất cả loại khám'], ['BHYT', 'BHYT'], ['DV', 'Dịch vụ']]} />
                            <button type="button" onClick={resetFilters} className="flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-teal-300 px-3 text-xs font-semibold text-teal-700 hover:bg-teal-50"><FilterX className="h-4 w-4" />Xóa bộ lọc</button>
                            <button type="button" disabled={!canCall || calling} onClick={() => void callPatient(nextPatient)} className="flex h-10 cursor-pointer items-center gap-2 rounded-lg bg-teal-700 px-4 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400">
                                <Volume2 className="h-4 w-4" />{calling ? 'Đang gọi…' : 'Gọi tiếp theo (F2)'}
                            </button>
                        </div>

                        {loadError && <p role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2.5 text-xs text-rose-700">{loadError}</p>}

                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[1180px] border-collapse text-left text-xs">
                                <thead>
                                    <tr className="border-b border-slate-200 bg-[#f2f8f7] text-slate-600">
                                        {COLUMNS.map((column) => <th key={column} scope="col" className="px-3 py-3 font-bold">{column}</th>)}
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((visit, index) => {
                                        const [time, date] = formatDateTime(visit.thoigianvaovien);
                                        const isFirstPatient = activeTab === 'waiting' && visit.id === nextPatient?.id;
                                        const callable = ['WAITING', 'WAITING_CONCLUSION'].includes(visit.status) && !visit.isSkipped;
                                        return (
                                            <tr key={visit.id} className={`border-b border-slate-100 ${isFirstPatient ? 'bg-teal-50/70' : 'hover:bg-slate-50'}`}>
                                                <td className="px-3 py-3 text-slate-500">{(currentPage - 1) * pageSize + index + 1}</td>
                                                <td className={`px-3 py-3 font-bold ${visit.isPriority ? 'text-rose-600' : 'text-slate-700'}`}>{pad(visit.queueNumber)}</td>
                                                <td className="px-3 py-3 font-semibold text-slate-800">{visit.patientName}</td>
                                                <td className="px-3 py-3 text-slate-600">{visit.patientCode || '—'}</td>
                                                <td className="px-3 py-3">{ageOf(visit.patientDob) ?? '—'}</td>
                                                <td className="px-3 py-3">{visit.patientGender || '—'}</td>
                                                <td className="px-3 py-3"><PriorityBadge visit={visit} /></td>
                                                <td className="px-3 py-3"><TypeBadge type={visit.doituongbnLoai} /></td>
                                                <td className="max-w-48 px-3 py-3 text-slate-700">{visit.lydovaovien || '—'}</td>
                                                <td className="px-3 py-3 text-slate-600">{time}<br />{date}</td>
                                                <td className="px-3 py-3 text-center text-slate-400">—</td>
                                                <td className="px-3 py-3"><StatusBadge visit={visit} /></td>
                                                <td className="px-3 py-3">
                                                    {callable
                                                        ? <button type="button" disabled={calling} onClick={() => void callPatient(visit)} className={`h-9 w-20 cursor-pointer rounded-lg border text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${isFirstPatient ? 'border-teal-700 bg-teal-700 text-white hover:bg-teal-800' : 'border-teal-600 text-teal-700 hover:bg-teal-50'}`}>Gọi</button>
                                                        : <span className="text-slate-300">—</span>}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                            {rows.length === 0 && (
                                <div className="flex min-h-64 flex-col items-center justify-center px-6 text-center">
                                    <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-teal-50 text-teal-600"><UsersRound className="h-7 w-7" /></div>
                                    <h3 className="text-sm font-bold">{loading ? 'Đang tải danh sách…' : emptyCopy[0]}</h3>
                                    {!loading && <p className="mt-1.5 max-w-md text-xs leading-5 text-slate-500">{emptyCopy[1]}</p>}
                                </div>
                            )}
                        </div>

                        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-4 py-3 text-xs text-slate-500">
                            <span>
                                {filtered.length === 0 ? 'Hiển thị 0 bệnh nhân' : `Hiển thị ${(currentPage - 1) * pageSize + 1} - ${Math.min(currentPage * pageSize, filtered.length)} / ${filtered.length}`}
                            </span>
                            <div className="flex items-center gap-4">
                                <div className="flex items-center gap-1">
                                    <button type="button" aria-label="Trang trước" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)} className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft className="h-4 w-4" /></button>
                                    {Array.from({ length: pageCount }, (_, i) => i + 1).map((n) => (
                                        <button key={n} type="button" aria-current={n === currentPage ? 'page' : undefined} onClick={() => setPage(n)} className={`h-8 w-8 cursor-pointer rounded-full text-xs font-semibold ${n === currentPage ? 'border border-teal-600 text-teal-700' : 'text-slate-600 hover:bg-slate-100'}`}>{n}</button>
                                    ))}
                                    <button type="button" aria-label="Trang sau" disabled={currentPage === pageCount} onClick={() => setPage(currentPage + 1)} className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"><ChevronRight className="h-4 w-4" /></button>
                                </div>
                                <label htmlFor="waiting-page-size" className="flex items-center gap-2">
                                    Số dòng/trang
                                    <select id="waiting-page-size" value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1); }} className="h-8 cursor-pointer rounded-lg border border-slate-200 px-2 text-xs text-slate-700">
                                        {PAGE_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
                                    </select>
                                </label>
                            </div>
                        </div>

                        <div className="flex flex-wrap gap-2 border-t border-slate-100 px-4 py-3">
                            <Badge className="border-rose-200 bg-rose-50 text-rose-600">Ưu tiên</Badge>
                            <Badge className="border-emerald-200 bg-emerald-50 text-emerald-700">Thường</Badge>
                            <Badge className="border-blue-100 bg-blue-50 text-blue-700">BHYT</Badge>
                            <Badge className="border-pink-100 bg-pink-50 text-pink-600">DV: Dịch vụ</Badge>
                            <Badge className={STATUS_BADGE.WAITING[1]}>Đang chờ</Badge>
                            <Badge className={STATUS_BADGE.IN_EXAMINATION[1]}>Đang khám (xem tab khác)</Badge>
                            <Badge className={SKIPPED_BADGE[1]}>Bỏ lượt (xem tab khác)</Badge>
                        </div>
                    </div>
                </div>

            </section>

            {callingVisit && (
                <div role="dialog" aria-modal="true" aria-labelledby="calling-patient-title" className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
                    <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
                        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
                            <div>
                                <p className="text-xs font-semibold uppercase tracking-wide text-teal-600">UC-44 · Đang gọi bệnh nhân</p>
                                <h2 id="calling-patient-title" className="mt-1 text-lg font-extrabold text-[#142534]">Xác nhận bệnh nhân có mặt</h2>
                            </div>
                            <button type="button" aria-label="Đóng" disabled={calling} onClick={() => setCallingVisit(null)} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600 disabled:opacity-50">
                                <X className="h-5 w-5" />
                            </button>
                        </div>

                        <div className="p-5">
                            <div className="flex items-start gap-4 rounded-xl bg-teal-50/70 p-4">
                                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-white text-teal-600 shadow-sm"><UserRound className="h-7 w-7" /></div>
                                <div className="min-w-0 flex-1">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <p className="text-base font-bold text-[#142534]">{callingVisit.patientName}</p>
                                        {callingVisit.isPriority && <Badge className="border-rose-200 bg-rose-50 text-rose-600">Ưu tiên</Badge>}
                                        <TypeBadge type={callingVisit.doituongbnLoai} />
                                    </div>
                                    <p className="mt-1 text-xs text-slate-500">Mã BN: {callingVisit.patientCode || '—'}</p>
                                    <p className="mt-1 text-xs text-slate-500">{callingVisit.patientGender || '—'} · {ageOf(callingVisit.patientDob) ?? '—'} tuổi</p>
                                </div>
                                <div className="text-center">
                                    <p className="text-[11px] text-slate-500">Số thứ tự</p>
                                    <p className="mt-1 text-2xl font-extrabold text-rose-600">{pad(callingVisit.queueNumber)}</p>
                                </div>
                            </div>

                            <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                                <div><dt className="text-xs text-slate-500">Lý do khám</dt><dd className="mt-1 font-medium text-slate-700">{callingVisit.lydovaovien || '—'}</dd></div>
                                <div><dt className="text-xs text-slate-500">Thời gian đăng ký</dt><dd className="mt-1 font-medium text-slate-700">{formatDateTime(callingVisit.thoigianvaovien).join(' · ')}</dd></div>
                                <div><dt className="text-xs text-slate-500">Bước hiện tại</dt><dd className="mt-1 font-medium text-slate-700">{callingVisit.status === 'WAITING_CONCLUSION' ? 'Chờ kết luận' : 'Chờ khám'}</dd></div>
                                <div><dt className="text-xs text-slate-500">Phòng khám</dt><dd className="mt-1 font-medium text-slate-700">{profile?.roomName || '—'}</dd></div>
                            </dl>

                            <p className="mt-5 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">Sau khi gọi, chọn “Có mặt” để tiếp tục hoặc “Vắng / Bỏ lượt” nếu bệnh nhân chưa đến.</p>
                        </div>

                        <div className="flex flex-col-reverse gap-3 border-t border-slate-100 px-5 py-4 sm:flex-row sm:justify-end">
                            <button type="button" disabled={calling} onClick={() => void skipPatient()} className="h-11 rounded-lg border border-amber-300 px-5 text-sm font-semibold text-amber-700 hover:bg-amber-50 disabled:opacity-50">Vắng / Bỏ lượt</button>
                            <button type="button" disabled={calling} onClick={() => void confirmPresent()} className="h-11 rounded-lg bg-teal-700 px-6 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50">{calling ? 'Đang xử lý…' : callingVisit.status === 'WAITING_CONCLUSION' ? 'Có mặt · Kết luận' : 'Có mặt · Bắt đầu khám'}</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
