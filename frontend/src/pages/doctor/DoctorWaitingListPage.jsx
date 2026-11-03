import { useCallback, useEffect, useState } from 'react';
import {
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    FilterX,
    Home,
    RefreshCw,
    Search,
    Stethoscope,
    UsersRound,
} from 'lucide-react';
import { toast } from 'sonner';
import defaultVisitApi from '../../apis/visitApi';
import { createAccountApi } from '../../apis/accountApi';
import { apiClient } from '../../services/session';
import { PRIORITY_REASON_LABELS } from '../../constants/visitData';

const REFRESH_MS = 10_000;
const PAGE_SIZES = [10, 20, 50];

const TABS = [
    { id: 'waiting', apiValue: 'WAITING', countKey: 'waitingCount', label: 'Chờ khám' },
    { id: 'in-examination', apiValue: 'IN_EXAMINATION', countKey: 'inExaminationCount', label: 'Đang khám' },
    { id: 'conclusion', apiValue: 'WAITING_CONCLUSION', countKey: 'waitingConclusionCount', label: 'Chờ kết luận' },
    { id: 'skipped', apiValue: 'SKIPPED', countKey: 'skippedCount', label: 'Vắng mặt' },
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

const COLUMNS = ['Họ tên', 'Mã BN', 'Tuổi', 'Giới tính', 'Loại khám', 'Thời gian đăng ký',
    'Ghi chú', 'Trạng thái', 'Thao tác'];

const defaultProfileLoader = (signal) => createAccountApi(apiClient).profile(signal);

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

function TypeBadge({ type }) {
    if (type === 'BHYT') return <Badge className="border-blue-100 bg-blue-50 text-blue-700">BHYT</Badge>;
    if (type === 'DV') return <Badge className="border-pink-100 bg-pink-50 text-pink-600">Dịch vụ</Badge>;
    return '—';
}

function StatusBadge({ visit }) {
    const [label, className] = visit.isSkipped ? SKIPPED_BADGE : STATUS_BADGE[visit.status] ?? [visit.status, 'border-slate-200 bg-slate-50 text-slate-600'];
    return <Badge className={className}>{label}</Badge>;
}

function VisitNotes({ visit }) {
    const notes = [];
    if (visit.isPriority) {
        notes.push(`Ưu tiên: ${PRIORITY_REASON_LABELS[visit.priorityReason] ?? visit.priorityReason ?? 'Có'}`);
    }
    if (visit.sourceRoomName) notes.push(`Chuyển từ: ${visit.sourceRoomName}`);

    return notes.length > 0
        ? <div className="space-y-1 text-slate-700">{notes.map((note) => <p key={note}>{note}</p>)}</div>
        : <span className="text-slate-400">—</span>;
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
    const [queueInfo, setQueueInfo] = useState(null);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [lastUpdated, setLastUpdated] = useState(null);
    const [activeTab, setActiveTab] = useState(initialTab);
    const [search, setSearch] = useState('');
    const [priority, setPriority] = useState('ALL');
    const [visitType, setVisitType] = useState('ALL');
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [calling, setCalling] = useState(false);
    const [processingVisitId, setProcessingVisitId] = useState(null);

    useEffect(() => {
        const controller = new AbortController();
        loadProfile(controller.signal).then(setProfile).catch(() => {});
        return () => controller.abort();
    }, [loadProfile]);

    const fetchQueue = useCallback(async (signal) => {
        try {
            const tab = TABS.find(({ id }) => id === activeTab) ?? TABS[0];
            const response = await visitApi.getDoctorQueue({
                tab: tab.apiValue,
                keyword: search.trim(),
                priority: priority === 'ALL' ? undefined : priority === 'PRIORITY',
                visitType,
                page: page - 1,
                size: pageSize,
            }, signal);
            const pageData = response?.visits;
            setVisits(Array.isArray(pageData?.content) ? pageData.content : []);
            setQueueInfo(response ?? null);
            setTotalElements(pageData?.page?.totalElements ?? 0);
            setTotalPages(pageData?.page?.totalPages ?? 0);
            setLoadError('');
            setLastUpdated(response?.refreshedAt ? new Date(response.refreshedAt) : new Date());
        } catch (error) {
            if (error?.name !== 'AbortError') setLoadError('Không thể tải danh sách chờ khám. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    }, [activeTab, page, pageSize, priority, search, visitApi, visitType]);

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

    const counts = Object.fromEntries(TABS.map((tab) => [tab.id, queueInfo?.[tab.countKey] ?? 0]));
    const pageCount = Math.max(1, totalPages);
    const currentPage = Math.min(page, pageCount);
    const rows = visits;
    const nextPatient = activeTab === 'waiting' || activeTab === 'conclusion'
        ? visits.find((visit) => ['WAITING', 'WAITING_CONCLUSION'].includes(visit.status) && !visit.isSkipped) ?? null
        : null;
    const resetFilters = () => {
        setSearch('');
        setPriority('ALL');
        setVisitType('ALL');
        setPage(1);
    };

    const confirmPresent = useCallback(async (visit) => {
        if (!visit || calling) return;
        setCalling(true);
        setProcessingVisitId(visit.id);
        try {
            await visitApi.startExamination(visit.id);
            toast.success(visit.status === 'WAITING_CONCLUSION'
                ? `Bệnh nhân ${visit.patientName} đã có mặt, chuyển sang bước kết luận.`
                : `Bệnh nhân ${visit.patientName} đã có mặt, bắt đầu khám.`);
            await fetchQueue();
        } catch {
            toast.error('Không thể xác nhận bệnh nhân. Vui lòng tải lại danh sách rồi thử lại.');
        } finally {
            setCalling(false);
            setProcessingVisitId(null);
        }
    }, [calling, fetchQueue, visitApi]);

    const skipPatient = useCallback(async (visit) => {
        if (!visit || calling) return;
        setCalling(true);
        setProcessingVisitId(visit.id);
        try {
            await visitApi.skipVisit(visit.id);
            toast.success(`Đã chuyển bệnh nhân ${visit.patientName} sang danh sách vắng mặt.`);
            await fetchQueue();
        } catch {
            toast.error('Không thể bỏ lượt bệnh nhân. Vui lòng tải lại danh sách rồi thử lại.');
        } finally {
            setCalling(false);
            setProcessingVisitId(null);
        }
    }, [calling, fetchQueue, visitApi]);

    const [updatedTime, updatedDate] = formatDateTime(lastUpdated);
    const doctorName = queueInfo?.doctorName || (profile ? [profile.academicTitle, profile.fullName].filter(Boolean).join(' ') : '—');
    const roomName = queueInfo?.roomName || profile?.roomName;
    const emptyCopy = EMPTY_COPY[activeTab];

    return (
        <div className="mx-auto max-w-[1600px] space-y-4 pb-8 text-slate-800">
            <header className="space-y-3">
                <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-sm text-slate-500">
                    <Home className="h-4 w-4" />
                    <ChevronRight className="h-3.5 w-3.5" />
                    <span className="font-semibold text-[#142534]">Quản lý khám bệnh</span>
                </nav>
                <div className="flex items-start gap-3">
                    <UsersRound className="mt-1 h-7 w-7 text-teal-600" />
                    <div>
                        <h1 className="text-2xl font-extrabold tracking-tight text-[#142534]">Quản lý khám bệnh</h1>
                        <p className="mt-1 text-sm text-slate-500">{roomName || 'Chưa được phân công phòng khám'}</p>
                    </div>
                </div>
            </header>

            <section>
                <div className="min-w-0 space-y-4">
                    <div className="grid overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm sm:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,0.7fr)_minmax(0,1fr)]">
                        <div className="flex min-w-0 items-center gap-4 border-b border-slate-100 p-4 sm:border-r xl:border-b-0">
                            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-teal-50 text-teal-600"><Stethoscope className="h-7 w-7" /></div>
                            <div className="min-w-0">
                                <p className="text-xs text-slate-500">Phòng khám hiện tại</p>
                                <p className="mt-1 break-words text-base font-bold text-[#142534]">{roomName || 'Chưa được phân công'}</p>
                            </div>
                        </div>
                        <div className="min-w-0 border-b border-slate-100 p-4 xl:border-b-0 xl:border-r">
                            <p className="text-xs text-slate-500">Bác sĩ phụ trách</p>
                            <p className="mt-1 break-words text-sm font-semibold text-slate-700">{doctorName}</p>
                        </div>
                        <div className="min-w-0 border-b border-slate-100 p-4 sm:border-b-0 sm:border-r xl:border-r">
                            <p className="text-xs text-slate-500">Tổng số bệnh nhân chờ</p>
                            <p className="mt-1 text-3xl font-extrabold text-teal-600">{counts.waiting}</p>
                        </div>
                        <div className="flex min-w-0 flex-col justify-center gap-1 p-4 text-xs">
                            <button type="button" onClick={() => void fetchQueue()} className="flex w-full cursor-pointer items-center gap-2 text-left text-slate-600 hover:text-teal-700" title="Tải lại ngay">
                                <RefreshCw className="h-4 w-4 shrink-0 text-teal-600" />
                                <span className="min-w-0">Tự động cập nhật<br /><span className="text-slate-400">Mỗi 10 giây</span></span>
                            </button>
                            <p className="break-words text-[11px] text-slate-400">Cập nhật lần cuối: {lastUpdated ? `${updatedDate} ${updatedTime}` : '—'}</p>
                        </div>
                    </div>

                    <div className="flex flex-wrap gap-2" role="tablist">
                        {TABS.map((tab) => (
                            <button key={tab.id} type="button" role="tab" aria-selected={activeTab === tab.id} onClick={() => { setActiveTab(tab.id); setPage(1); }} className={`cursor-pointer rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${activeTab === tab.id ? 'border-teal-700 bg-teal-700 text-white' : 'border-slate-300 bg-white text-slate-600 hover:border-teal-400 hover:text-teal-700'}`}>
                                {tab.label} ({counts[tab.id]})
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
                        </div>

                        {loadError && <p role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2.5 text-xs text-rose-700">{loadError}</p>}

                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[980px] border-collapse text-left text-xs">
                                <thead>
                                    <tr className="border-b border-slate-200 bg-[#f2f8f7] text-slate-600">
                                        {COLUMNS.map((column) => <th key={column} scope="col" className="px-3 py-3 font-bold">{column}</th>)}
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((visit) => {
                                        const [time, date] = formatDateTime(visit.thoigianvaovien);
                                        const isFirstPatient = activeTab === 'waiting' && visit.id === nextPatient?.id;
                                        const callable = ['WAITING', 'WAITING_CONCLUSION'].includes(visit.status) && !visit.isSkipped;
                                        return (
                                            <tr key={visit.id} className={`border-b border-slate-100 ${isFirstPatient ? 'bg-teal-50/70' : 'hover:bg-slate-50'}`}>
                                                <td className="px-3 py-3 font-semibold text-slate-800">{visit.patientName}</td>
                                                <td className="px-3 py-3 text-slate-600">{visit.patientCode || '—'}</td>
                                                <td className="px-3 py-3">{ageOf(visit.patientDob) ?? '—'}</td>
                                                <td className="px-3 py-3">{visit.patientGender || '—'}</td>
                                                <td className="px-3 py-3"><TypeBadge type={visit.doituongbnLoai} /></td>
                                                <td className="px-3 py-3 text-slate-600">{time}<br />{date}</td>
                                                <td className="max-w-56 px-3 py-3"><VisitNotes visit={visit} /></td>
                                                <td className="px-3 py-3"><StatusBadge visit={visit} /></td>
                                                <td className="px-3 py-3">
                                                    {callable
                                                        ? <div className="flex min-w-40 gap-2">
                                                            <button type="button" disabled={calling} onClick={() => void confirmPresent(visit)} className={`h-9 flex-1 cursor-pointer rounded-lg border px-3 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${isFirstPatient ? 'border-teal-700 bg-teal-700 text-white hover:bg-teal-800' : 'border-teal-600 text-teal-700 hover:bg-teal-50'}`}>{processingVisitId === visit.id ? 'Đang xử lý…' : 'Có mặt'}</button>
                                                            <button type="button" disabled={calling} onClick={() => void skipPatient(visit)} className="h-9 flex-1 cursor-pointer rounded-lg border border-amber-300 px-3 text-xs font-semibold text-amber-700 hover:bg-amber-50 disabled:cursor-not-allowed disabled:opacity-50">Vắng mặt</button>
                                                        </div>
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
                                {totalElements === 0 ? 'Hiển thị 0 bệnh nhân' : `Hiển thị ${(currentPage - 1) * pageSize + 1} - ${Math.min(currentPage * pageSize, totalElements)} / ${totalElements}`}
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
                            <Badge className="border-blue-100 bg-blue-50 text-blue-700">BHYT</Badge>
                            <Badge className="border-pink-100 bg-pink-50 text-pink-600">DV: Dịch vụ</Badge>
                            <Badge className={STATUS_BADGE.WAITING[1]}>Đang chờ</Badge>
                            <Badge className={STATUS_BADGE.IN_EXAMINATION[1]}>Đang khám (xem tab khác)</Badge>
                            <Badge className={SKIPPED_BADGE[1]}>Bỏ lượt (xem tab khác)</Badge>
                        </div>
                    </div>
                </div>

            </section>

        </div>
    );
}
