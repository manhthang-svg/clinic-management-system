import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
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
import { ApiError } from '../../apis/apiError';
import { clinicalExaminationPath } from '../../routes/routes';

const REFRESH_MS = 10_000;
const PAGE_SIZES = [10, 20, 50];

// "Danh sách bệnh nhân" merges the waiting, in-examination and waiting-for-paraclinical visits;
// a visit leaves it once it is waiting for the conclusion.
const TABS = [
    { id: 'patients', apiValue: 'PATIENT_LIST', countKeys: ['waitingCount', 'inExaminationCount', 'waitingParaclinicalCount'], label: 'Danh sách bệnh nhân' },
    { id: 'conclusion', apiValue: 'WAITING_CONCLUSION', countKeys: ['waitingConclusionCount'], label: 'Chờ kết luận' },
    { id: 'skipped', apiValue: 'SKIPPED', countKeys: ['skippedCount'], label: 'Vắng mặt' },
];
const TAB_HEADINGS = {
    patients: 'Danh sách bệnh nhân',
    conclusion: 'Danh sách chờ kết luận',
    skipped: 'Danh sách bệnh nhân vắng mặt',
};

// Older navigation state may still name the former "Chờ khám" / "Đang khám" tabs.
function resolveTab(tabId) {
    if (tabId === 'waiting' || tabId === 'in-examination') return 'patients';
    return TABS.some(({ id }) => id === tabId) ? tabId : 'patients';
}

// The examination opens for editing while in the room, and read-only while the patient is away for paraclinical services.
const OPENABLE_STATUSES = ['IN_EXAMINATION', 'WAITING_PARACLINICAL'];

function canOpenExamination(visit) {
    return Boolean(visit) && !visit.isSkipped && OPENABLE_STATUSES.includes(visit.status);
}

// System messages of UC-43, word for word from the SRS message list.
const MSG01 = 'Không tìm thấy kết quả.';
const MSG12 = 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.';
const MSG13 = 'Bạn không có quyền thực hiện thao tác này.';
const MSG23 = 'Bệnh nhân này đã được gọi.';
const MSG48 = 'Bạn không có ca làm việc vào lúc này. Bạn chỉ xem được danh sách, không gọi được bệnh nhân mới.';
const msg54 = (patientName) => `${patientName} không có mặt khi được gọi và đã được chuyển sang danh sách bỏ qua.`;
const MSG80 = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
const MSG92 = 'Chưa có dữ liệu để hiển thị.';
const MSG96 = 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
const MSG99 = 'Bạn chưa được phân công vào phòng nào. Vui lòng liên hệ quản trị viên.';

// AF-01 shows MSG92 with this hint per tab; AF-03 (search or filter matches nothing) shows MSG01 with FILTER_HINT.
const EMPTY_HINT = {
    patients: 'Danh sách hiển thị lượt khám BHYT và lượt khám dịch vụ đã thanh toán.',
    conclusion: 'Bệnh nhân hoàn tất cận lâm sàng sẽ xuất hiện tại đây.',
    skipped: 'Bệnh nhân vắng mặt khi được gọi sẽ xuất hiện tại đây.',
};
const FILTER_HINT = 'Hãy sửa điều kiện tìm kiếm, bộ lọc hoặc chọn "Xóa bộ lọc".';

const STATUS_BADGE = {
    WAITING: ['Đang chờ', 'bg-amber-50 text-amber-700 border-amber-200'],
    IN_EXAMINATION: ['Đang khám', 'bg-blue-50 text-blue-700 border-blue-200'],
    WAITING_PARACLINICAL: ['Chờ CLS', 'bg-violet-50 text-violet-700 border-violet-200'],
    WAITING_CONCLUSION: ['Chờ kết luận', 'bg-teal-50 text-teal-700 border-teal-200'],
};

const SKIPPED_BADGE = ['Bỏ lượt', 'bg-rose-50 text-rose-700 border-rose-200'];

const COLUMNS = ['STT', 'Họ tên', 'Mã BN', 'Loại khám', 'Thời gian đăng ký',
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

function Badge({ className, children }) {
    return <span className={`inline-flex items-center justify-center whitespace-nowrap rounded-md border px-2 py-1 text-[11px] font-semibold ${className}`}>{children}</span>;
}

function TypeBadge({ type }) {
    if (type === 'BHYT') return <Badge className="border-blue-100 bg-blue-50 text-blue-700">BHYT</Badge>;
    if (type === 'DV') return <Badge className="border-pink-100 bg-pink-50 text-pink-600">Dịch vụ</Badge>;
    return '—';
}

function StatusBadge({ visit }) {
    const retainedStage = visit.status === 'WAITING_CONCLUSION' ? 'Chờ kết luận' : 'Chờ khám';
    const [label, className] = visit.isSkipped
        ? [`Vắng mặt · ${retainedStage}`, SKIPPED_BADGE[1]]
        : STATUS_BADGE[visit.status] ?? [visit.status, 'border-slate-200 bg-slate-50 text-slate-600'];
    return <Badge className={className}>{label}</Badge>;
}

/** Maps a failed queue load or call action to its message; NOT_FOUND and unexpected errors show MSG80. */
function queueErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'ROOM_ASSIGNMENT_REQUIRED') return MSG99;
    if (code === 'DOCTOR_OFF_DUTY') return MSG48;
    if (code === 'FORBIDDEN') return MSG13;
    if (code === 'CONCURRENT_MODIFICATION') return MSG23;
    if (error?.status === 401) return MSG12;
    if (error instanceof ApiError && !error.errorResponse && error.status === undefined) return MSG96;
    return MSG80;
}

function paginationItems(currentPage, pageCount) {
    if (pageCount <= 7) return Array.from({ length: pageCount }, (_, index) => index + 1);

    const pages = new Set([1, pageCount, currentPage - 1, currentPage, currentPage + 1]);
    const visiblePages = [...pages]
        .filter((pageNumber) => pageNumber >= 1 && pageNumber <= pageCount)
        .sort((left, right) => left - right);
    const items = [];
    visiblePages.forEach((pageNumber, index) => {
        if (index > 0 && pageNumber - visiblePages[index - 1] > 1) items.push(`ellipsis-${pageNumber}`);
        items.push(pageNumber);
    });
    return items;
}

function VisitNotes({ visit }) {
    if (visit.note) {
        return <div className="whitespace-pre-line break-words text-slate-700">{visit.note}</div>;
    }

    const notes = [];
    if (visit.isPriority) {
        notes.push(`Ưu tiên: ${PRIORITY_REASON_LABELS[visit.priorityReason] ?? visit.priorityReason ?? 'Có'}`);
    }
    if (visit.sourceRoomName) notes.push(`Chuyển từ phòng ${visit.sourceRoomName}`);

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
    initialTab = 'patients',
}) {
    const location = useLocation();
    const navigate = useNavigate();
    const [profile, setProfile] = useState(null);
    const [visits, setVisits] = useState([]);
    const [queueInfo, setQueueInfo] = useState(null);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [lastUpdated, setLastUpdated] = useState(null);
    const [activeTab, setActiveTab] = useState(() => resolveTab(location.state?.activeTab ?? initialTab));
    const [search, setSearch] = useState('');
    const [priority, setPriority] = useState('ALL');
    const [visitType, setVisitType] = useState('ALL');
    const [interruptedStage, setInterruptedStage] = useState('ALL');
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [calling, setCalling] = useState(false);
    const [processingVisitId, setProcessingVisitId] = useState(null);
    const latestQueueRequest = useRef(0);

    useEffect(() => {
        const controller = new AbortController();
        loadProfile(controller.signal).then(setProfile).catch(() => {});
        return () => controller.abort();
    }, [loadProfile]);

    const fetchQueue = useCallback(async (signal) => {
        const requestId = latestQueueRequest.current + 1;
        latestQueueRequest.current = requestId;
        try {
            const tab = TABS.find(({ id }) => id === activeTab) ?? TABS[0];
            const response = await visitApi.getDoctorQueue({
                tab: tab.apiValue,
                keyword: search.trim(),
                priority: priority === 'ALL' ? undefined : priority === 'PRIORITY',
                visitType: visitType === 'ALL' ? undefined : visitType,
                interruptedStage: activeTab === 'skipped' ? interruptedStage : undefined,
                page: page - 1,
                size: pageSize,
            }, signal);
            if (requestId !== latestQueueRequest.current) return;
            const pageData = response?.visits;
            setVisits(Array.isArray(pageData?.content) ? pageData.content : []);
            setQueueInfo(response ?? null);
            setTotalElements(pageData?.page?.totalElements ?? 0);
            setTotalPages(pageData?.page?.totalPages ?? 0);
            setLoadError('');
            setLastUpdated(response?.refreshedAt ? new Date(response.refreshedAt) : new Date());
        } catch (error) {
            if (error?.name !== 'AbortError' && requestId === latestQueueRequest.current) {
                setLoadError(queueErrorMessage(error));
            }
        } finally {
            if (requestId === latestQueueRequest.current) setLoading(false);
        }
    }, [activeTab, interruptedStage, page, pageSize, priority, search, visitApi, visitType]);

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

    const counts = Object.fromEntries(TABS.map((tab) => [
        tab.id,
        tab.countKeys.reduce((sum, key) => sum + (queueInfo?.[key] ?? 0), 0),
    ]));
    const waitingCount = queueInfo?.waitingCount ?? 0;
    const pageCount = Math.max(1, totalPages);
    const currentPage = Math.min(page, pageCount);
    const rows = visits;
    const nextPatient = activeTab === 'patients' || activeTab === 'conclusion'
        ? visits.find((visit) => ['WAITING', 'WAITING_CONCLUSION'].includes(visit.status) && !visit.isSkipped) ?? null
        : null;
    const resetFilters = () => {
        setSearch('');
        setPriority('ALL');
        setVisitType('ALL');
        setInterruptedStage('ALL');
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
            navigate(
                clinicalExaminationPath(visit.id, visit.status === 'WAITING_CONCLUSION' ? 'conclusion' : 'clinical'),
                { state: { visitId: visit.id, stage: visit.status, visit, queueInfo, returnTab: activeTab } },
            );
        } catch (error) {
            toast.error(queueErrorMessage(error));
            await fetchQueue();
        } finally {
            setCalling(false);
            setProcessingVisitId(null);
        }
    }, [activeTab, calling, fetchQueue, navigate, queueInfo, visitApi]);

    const skipPatient = useCallback(async (visit) => {
        if (!visit || calling) return;
        setCalling(true);
        setProcessingVisitId(visit.id);
        try {
            await visitApi.skipVisit(visit.id);
            toast.success(msg54(visit.patientName));
            await fetchQueue();
        } catch (error) {
            toast.error(queueErrorMessage(error));
            await fetchQueue();
        } finally {
            setCalling(false);
            setProcessingVisitId(null);
        }
    }, [calling, fetchQueue, visitApi]);

    const acceptSkippedPatient = useCallback(async (visit) => {
        if (!visit || calling) return;
        setCalling(true);
        setProcessingVisitId(visit.id);
        try {
            await visitApi.acceptSkippedVisit(visit.id);
            toast.success(`Đã tiếp nhận lại bệnh nhân ${visit.patientName}.`);
            navigate(
                clinicalExaminationPath(visit.id, visit.status === 'WAITING_CONCLUSION' ? 'conclusion' : 'clinical'),
                { state: { visitId: visit.id, stage: visit.status, visit, queueInfo, acceptedFromSkipped: true, returnTab: activeTab } },
            );
        } catch (error) {
            toast.error(queueErrorMessage(error));
            await fetchQueue();
        } finally {
            setCalling(false);
            setProcessingVisitId(null);
        }
    }, [activeTab, calling, fetchQueue, navigate, queueInfo, visitApi]);

    const openClinicalExamination = useCallback((visit) => {
        if (!canOpenExamination(visit)) return;
        navigate(clinicalExaminationPath(visit.id), {
            state: { visitId: visit.id, stage: visit.status, visit, queueInfo, returnTab: activeTab },
        });
    }, [activeTab, navigate, queueInfo]);

    const [updatedTime, updatedDate] = formatDateTime(lastUpdated);
    const doctorName = queueInfo?.doctorName || (profile ? [profile.academicTitle, profile.fullName].filter(Boolean).join(' ') : '—');
    const roomName = queueInfo?.roomName || profile?.roomName;
    const filtersActive = search.trim() !== '' || priority !== 'ALL' || visitType !== 'ALL'
        || (activeTab === 'skipped' && interruptedStage !== 'ALL');
    // A first load that failed has no list to call empty; EF-02 shows only the error then.
    const showEmptyState = rows.length === 0 && !(loadError && !lastUpdated);
    const actionsBlocked = queueInfo?.onDuty === false;

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
                            <p className="mt-1 text-3xl font-extrabold text-teal-600">{waitingCount}</p>
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
                        <h2 className="text-base font-bold text-[#142534]">{TAB_HEADINGS[activeTab]}</h2>
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
                            {activeTab === 'skipped' && (
                                <SelectFilter
                                    id="waiting-interrupted-stage"
                                    label="Lọc giai đoạn bị gián đoạn"
                                    value={interruptedStage}
                                    onChange={(value) => { setInterruptedStage(value); setPage(1); }}
                                    options={[['ALL', 'Tất cả giai đoạn'], ['WAITING', 'Chờ khám'], ['WAITING_CONCLUSION', 'Chờ kết luận']]}
                                />
                            )}
                            <button type="button" onClick={resetFilters} className="flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-teal-300 px-3 text-xs font-semibold text-teal-700 hover:bg-teal-50"><FilterX className="h-4 w-4" />Xóa bộ lọc</button>
                        </div>

                        {loadError && <p role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2.5 text-xs text-rose-700">{loadError}</p>}
                        {actionsBlocked && (
                            <p role="status" className="border-b border-amber-100 bg-amber-50 px-4 py-2.5 text-xs text-amber-700">
                                {MSG48}
                            </p>
                        )}

                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[1280px] table-fixed border-collapse text-center text-xs">
                                <colgroup>
                                    <col className="w-[64px]" />
                                    <col className="w-[200px]" />
                                    <col className="w-[190px]" />
                                    <col className="w-[100px]" />
                                    <col className="w-[140px]" />
                                    <col className="w-[210px]" />
                                    <col className="w-[160px]" />
                                    <col className="w-[210px]" />
                                </colgroup>
                                <thead>
                                    <tr className="border-b border-slate-200 bg-[#f2f8f7] text-slate-600">
                                        {COLUMNS.map((column) => <th key={column} scope="col" className="px-3 py-3 font-bold">{column}</th>)}
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((visit) => {
                                        const [time, date] = formatDateTime(visit.thoigianvaovien);
                                        const isFirstPatient = activeTab === 'patients' && visit.id === nextPatient?.id;
                                        const callable = ['WAITING', 'WAITING_CONCLUSION'].includes(visit.status);
                                        const opensExamination = canOpenExamination(visit);
                                        // BR-19, BR-20: after the shift the Doctor still calls back their own patient for the conclusion.
                                        const rowBlocked = actionsBlocked
                                            && !(visit.status === 'WAITING_CONCLUSION' && visit.assignedToMe);
                                        return (
                                            <tr
                                                key={visit.id}
                                                tabIndex={opensExamination ? 0 : undefined}
                                                title={opensExamination ? `Mở hồ sơ khám của ${visit.patientName}` : undefined}
                                                onClick={opensExamination ? () => openClinicalExamination(visit) : undefined}
                                                onKeyDown={opensExamination ? (event) => {
                                                    if (event.key === 'Enter' || event.key === ' ') {
                                                        event.preventDefault();
                                                        openClinicalExamination(visit);
                                                    }
                                                } : undefined}
                                                className={`border-b border-slate-100 ${isFirstPatient ? 'bg-teal-50/70' : 'hover:bg-slate-50'} ${opensExamination ? 'cursor-pointer outline-none focus-visible:bg-teal-50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-teal-500' : ''}`}
                                            >
                                                <td className={`px-3 py-3 font-bold ${visit.isPriority ? 'text-rose-600' : 'text-slate-700'}`}>{String(visit.queueNumber ?? '—').padStart(2, '0')}</td>
                                                <td className="truncate px-3 py-3 font-semibold text-slate-800">{visit.patientName}</td>
                                                <td className="break-all whitespace-normal px-3 py-3 text-slate-600">{visit.patientCode || '—'}</td>
                                                <td className="px-3 py-3"><TypeBadge type={visit.doituongbnLoai} /></td>
                                                <td className="px-3 py-3 text-slate-600">{time}<br />{date}</td>
                                                <td className="break-words whitespace-normal px-3 py-3"><VisitNotes visit={visit} /></td>
                                                <td className="px-3 py-3"><StatusBadge visit={visit} /></td>
                                                <td className="px-3 py-3">
                                                    {callable && visit.isSkipped && visit.canAcceptAgain
                                                        ? <button type="button" disabled={calling || rowBlocked} title={rowBlocked ? 'Ngoài ca trực' : undefined} onClick={() => void acceptSkippedPatient(visit)} className="h-9 cursor-pointer rounded-lg border border-teal-600 px-3 text-xs font-semibold text-teal-700 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-50">{processingVisitId === visit.id ? 'Đang xử lý…' : 'Tiếp nhận lại'}</button>
                                                        : callable && !visit.isSkipped
                                                            ? <div className="flex min-w-40 justify-center gap-2">
                                                                <button type="button" disabled={calling || rowBlocked} title={rowBlocked ? 'Ngoài ca trực' : undefined} onClick={() => void confirmPresent(visit)} className={`h-9 flex-1 cursor-pointer whitespace-nowrap rounded-lg border px-3 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${isFirstPatient ? 'border-teal-700 bg-teal-700 text-white hover:bg-teal-800' : 'border-teal-600 text-teal-700 hover:bg-teal-50'}`}>{processingVisitId === visit.id ? 'Đang xử lý…' : 'Bắt đầu'}</button>
                                                                <button type="button" disabled={calling || rowBlocked} title={rowBlocked ? 'Ngoài ca trực' : undefined} onClick={() => void skipPatient(visit)} className="h-9 flex-1 cursor-pointer whitespace-nowrap rounded-lg border border-amber-300 px-3 text-xs font-semibold text-amber-700 hover:bg-amber-50 disabled:cursor-not-allowed disabled:opacity-50">Vắng mặt</button>
                                                            </div>
                                                            : <span className="text-slate-300">—</span>}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                            {showEmptyState && (
                                <div className="flex min-h-64 flex-col items-center justify-center px-6 text-center">
                                    <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-teal-50 text-teal-600"><UsersRound className="h-7 w-7" /></div>
                                    <h3 className="text-sm font-bold">{loading ? 'Đang tải danh sách…' : filtersActive ? MSG01 : MSG92}</h3>
                                    {!loading && <p className="mt-1.5 max-w-md text-xs leading-5 text-slate-500">{filtersActive ? FILTER_HINT : EMPTY_HINT[activeTab]}</p>}
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
                                    {paginationItems(currentPage, pageCount).map((item) => (
                                        typeof item === 'number'
                                            ? <button key={item} type="button" aria-label={`Trang ${item}`} aria-current={item === currentPage ? 'page' : undefined} onClick={() => setPage(item)} className={`h-8 w-8 cursor-pointer rounded-full text-xs font-semibold ${item === currentPage ? 'border border-teal-600 text-teal-700' : 'text-slate-600 hover:bg-slate-100'}`}>{item}</button>
                                            : <span key={item} aria-hidden="true" className="flex h-8 w-6 items-center justify-center text-slate-400">…</span>
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

                    </div>
                </div>

            </section>

        </div>
    );
}
