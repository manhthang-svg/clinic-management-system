import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
    RefreshCw,
    Banknote,
    QrCode,
    CheckCircle2,
    Clock,
    AlertCircle,
    User,
    Building2,
    ArrowUpRight,
    Search,
    X,
    ExternalLink,
    FileText,
    ChevronsLeft,
    ChevronsRight,
} from 'lucide-react';
import paymentApi from '../../apis/paymentApi';
import {
    AWAITING_CONCLUSION_LABEL, MSG, confirmErrorMessage, confirmedNoticeText, queueTicketVisitId as ticketVisitOf,
} from '../../components/payments/paymentConfirm';
import { PayosQrCountdown } from '../../components/payments/PayosQrCountdown';
import { QueueTicketDialog } from '../../components/visits/QueueTicketDialog';

// so dong moi trang cua 2 bang (cho thu / da thu)
const PENDING_PAGE_SIZE = 20;
// the thong ke: tai tung trang 100 phieu da thu (moi thu truoc) cho den khi het phieu cua hom nay
const PAID_LOAD_SIZE = 100;
// chan tren de khong lap vo han neu du lieu bat thuong
const MAX_PAID_PAGES = 50;
// 2 nua cua trang tong quan
const VIEW_PENDING = 'PENDING';
const VIEW_PAID = 'PAID';

// lay mang phieu tu ket qua Page cua API
function contentOf(res) {
    return res?.content
        || res?.data?.content
        || (Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []);
}

// tong so phieu khop bo loc (server dem)
function totalOf(res) {
    const page = res?.data ?? res;
    return page?.totalElements ?? page?.page?.totalElements ?? contentOf(res).length;
}

// tat ca phieu thu trong hom nay (cho the "Da thu hom nay" va doanh thu)
async function loadPaidToday() {
    const today = [];
    for (let page = 0; page < MAX_PAID_PAGES; page += 1) {
        const rows = contentOf(await paymentApi.list({
            status: 'PAID', page, size: PAID_LOAD_SIZE, sort: 'paidAt,desc',
        }));
        const paidToday = rows.filter((p) => isToday(p.paidAt));
        today.push(...paidToday);
        // het trang, hoac da gap phieu thu tu hom truoc -> dung
        if (rows.length < PAID_LOAD_SIZE || paidToday.length < rows.length) break;
    }
    return today;
}

// cung ngay (theo gio may) voi hom nay
function isToday(dateStr, today = new Date()) {
    if (!dateStr) return false;
    const d = new Date(dateStr);
    return d.getFullYear() === today.getFullYear()
        && d.getMonth() === today.getMonth()
        && d.getDate() === today.getDate();
}
const PAGE_BUTTON_CLASS =
    'flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300';

function formatMoney(amount) {
    if (amount === undefined || amount === null) return '0 ₫';
    return Number(amount).toLocaleString('vi-VN') + ' ₫';
}

function formatDate(dateStr) {
    if (!dateStr) return '—';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        return d.toLocaleTimeString('vi-VN', {
            hour: '2-digit',
            minute: '2-digit',
        }) + ' - ' + d.toLocaleDateString('vi-VN', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
        });
    } catch {
        return dateStr;
    }
}

// UC-69: trang Tong quan thu ngan (phieu cho thu, thu tien mat / QR)
export default function CashierOverviewPage() {
    // trang phieu cho thu dang xem + tong so phieu cho thu (server dem, khong gioi han)
    const [pendingRows, setPendingRows] = useState([]);
    const [pendingTotal, setPendingTotal] = useState(0);
    const [pendingCounts, setPendingCounts] = useState({ exam: 0, para: 0 });
    // phieu thu trong hom nay (cho 2 the thong ke)
    const [paidToday, setPaidToday] = useState([]);
    // nua "Da thu tien": trang dang xem + tong so phieu da thu (server dem)
    const [paidRows, setPaidRows] = useState([]);
    const [paidTotal, setPaidTotal] = useState(0);
    const [paidPage, setPaidPage] = useState(0);
    const [view, setView] = useState(VIEW_PENDING);
    const [loading, setLoading] = useState(false);
    const [filterType, setFilterType] = useState('ALL'); // ALL, EXAMINATION_FEE, PARACLINICAL_FEE
    const [pendingPage, setPendingPage] = useState(0);
    const [notice, setNotice] = useState('');
    // luot kham can in phieu so thu tu sau khi thu phi kham
    const [queueTicketVisitId, setQueueTicketVisitId] = useState(null);
    const [error, setError] = useState('');

    // Modal Tien mat
    const [cashModal, setCashModal] = useState(null);
    const [cashReceived, setCashReceived] = useState('');
    const [confirmingCash, setConfirmingCash] = useState(false);

    // Modal PayOS QR
    const [qrModal, setQrModal] = useState(null);
    const [loadingQr, setLoadingQr] = useState(false);
    const [syncingQr, setSyncingQr] = useState(false);

    // Tai du lieu (goi song song): 1 trang phieu cho thu, 1 trang phieu da thu (moi thu truoc),
    // so phieu cho thu theo loai, phieu thu hom nay. Phan trang o server nen khong bo sot phieu nao.
    const fetchPayments = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const [pageRes, paidRes, examRes, paraRes, todayRows] = await Promise.all([
                paymentApi.list({ status: 'PENDING', type: filterType, page: pendingPage, size: PENDING_PAGE_SIZE }),
                paymentApi.list({ status: 'PAID', page: paidPage, size: PENDING_PAGE_SIZE, sort: 'paidAt,desc' }),
                paymentApi.list({ status: 'PENDING', type: 'EXAMINATION_FEE', size: 1 }),
                paymentApi.list({ status: 'PENDING', type: 'PARACLINICAL_FEE', size: 1 }),
                loadPaidToday(),
            ]);
            const total = totalOf(pageRes);
            const lastPage = Math.max(0, Math.ceil(total / PENDING_PAGE_SIZE) - 1);
            // trang dang xem khong con phieu (vd vua thu phieu cuoi) -> lui ve trang cuoi
            if (pendingPage > lastPage) {
                setPendingPage(lastPage);
                return;
            }
            setPendingRows(contentOf(pageRes));
            setPendingTotal(total);
            setPaidRows(contentOf(paidRes));
            setPaidTotal(totalOf(paidRes));
            setPendingCounts({ exam: totalOf(examRes), para: totalOf(paraRes) });
            setPaidToday(todayRows);
        } catch (err) {
            setError(err?.message || 'Không thể tải danh sách phiếu thu');
        } finally {
            setLoading(false);
        }
    }, [filterType, pendingPage, paidPage]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchPayments();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchPayments]);

    // Thong ke nhanh: so cho thu do server dem; da thu / doanh thu = cac phieu thu hom nay
    const stats = useMemo(() => ({
        pendingExam: pendingCounts.exam,
        pendingPara: pendingCounts.para,
        pendingTotal: pendingCounts.exam + pendingCounts.para,
        paidCount: paidToday.length,
        totalRevenue: paidToday.reduce(
            (sum, p) => sum + Number(p.patientTotalAmount ?? p.totalAmount ?? p.amount ?? 0), 0),
    }), [pendingCounts, paidToday]);

    // phan trang (o server): 20 dong / trang, moi nua 1 bo dem trang rieng
    const pendingPageCount = Math.max(1, Math.ceil(pendingTotal / PENDING_PAGE_SIZE));
    const pendingCurrentPage = pendingPage;
    const pendingPageRows = pendingRows;
    const paidPageCount = Math.max(1, Math.ceil(paidTotal / PENDING_PAGE_SIZE));

    // doi loai phieu thi ve trang dau
    const chooseFilter = (type) => {
        setFilterType(type);
        setPendingPage(0);
    };

    // Xu ly mo Modal tien mat
    const handleOpenCashModal = (payment) => {
        setCashModal(payment);
        const amount = payment.patientTotalAmount ?? payment.totalAmount ?? payment.amount ?? 0;
        setCashReceived(String(amount || ''));
    };

    // Xac nhan thu tien mat
    const handleConfirmCash = async () => {
        if (!cashModal) return;
        const due = Number(cashModal.patientTotalAmount ?? cashModal.totalAmount ?? cashModal.amount ?? 0);
        const given = Number(cashReceived || 0);
        if (given < due) {
            alert('Số tiền khách đưa chưa đủ');
            return;
        }

        setConfirmingCash(true);
        try {
            const res = await paymentApi.confirm(cashModal.id, {
                paymentMethod: 'CASH',
                note: 'Thu tiền mặt tại quầy',
            });

            const confirmed = res?.data || res;
            setNotice(confirmedNoticeText(confirmed));
            setQueueTicketVisitId(ticketVisitOf(confirmed));
            setCashModal(null);
            fetchPayments();
        } catch (err) {
            alert(confirmErrorMessage(err));
            setCashModal(null);
            fetchPayments();
        } finally {
            setConfirmingCash(false);
        }
    };

    // Xu ly mo Modal PayOS QR
    const handleOpenQrModal = async (payment) => {
        setQrModal({ payment, qrData: null });
        setLoadingQr(true);
        try {
            const res = await paymentApi.createPayosQr(payment.id);
            const qrData = res?.data || res;
            setQrModal({ payment, qrData });
        } catch (err) {
            alert(err?.errorResponse?.code === 'PAYMENT_AWAITS_CONCLUSION'
                ? MSG.AWAITS_CONCLUSION
                : err?.message || 'Không thể tạo mã QR PayOS');
            setQrModal(null);
        } finally {
            setLoadingQr(false);
        }
    };

    // Kiem tra dong bo PayOS
    const handleSyncQr = async () => {
        if (!qrModal?.payment) return;
        setSyncingQr(true);
        try {
            const res = await paymentApi.syncPayos(qrModal.payment.id);
            const data = res?.data || res;
            if (data?.status === 'PAID') {
                const isExam = (qrModal.payment.type || qrModal.payment.paymentType) === 'EXAMINATION_FEE';
                setNotice(
                    isExam
                        ? `Thanh toán PayOS thành công cho ${qrModal.payment.patientName}! Bệnh nhân đã sẵn sàng vào phòng khám.`
                        : `Thanh toán PayOS thành công cho ${qrModal.payment.patientName}! Các chỉ định CLS đã sẵn sàng thực hiện.`
                );
                setQueueTicketVisitId(ticketVisitOf(qrModal.payment));
                setQrModal(null);
                fetchPayments();
            } else {
                setNotice('Hệ thống chưa ghi nhận tiền về cho mã giao dịch này. Vui lòng thử lại sau giây lát.');
            }
        } catch (err) {
            console.error('Lỗi sync PayOS:', err);
        } finally {
            setSyncingQr(false);
        }
    };

    // Polling tu dong khi modal QR mo
    useEffect(() => {
        if (!qrModal?.payment || qrModal?.qrData?.status === 'PAID') return undefined;

        const timer = setInterval(async () => {
            try {
                const res = await paymentApi.syncPayos(qrModal.payment.id);
                const data = res?.data || res;
                if (data?.status === 'PAID') {
                    const isExam = (qrModal.payment.type || qrModal.payment.paymentType) === 'EXAMINATION_FEE';
                    setNotice(
                        isExam
                            ? `Thanh toán PayOS thành công cho ${qrModal.payment.patientName}! Bệnh nhân đã sẵn sàng vào phòng khám.`
                            : `Thanh toán PayOS thành công cho ${qrModal.payment.patientName}! Các chỉ định CLS đã sẵn sàng thực hiện.`
                    );
                    setQueueTicketVisitId(ticketVisitOf(qrModal.payment));
                    setQrModal(null);
                    fetchPayments();
                }
            } catch {
                // loi khi hoi ngam thi bo qua
            }
        }, 3000);

        return () => clearInterval(timer);
    }, [qrModal, fetchPayments]);

    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-7xl space-y-6">
                {/* Header */}
                <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                    <div>
                        <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-teal-700">
                            <span>METPH</span>
                            <span>/</span>
                            <span>BỘ PHẬN THU NGÂN</span>
                        </div>
                        <h1 className="mt-1 text-2xl font-bold tracking-tight">Tổng quan thu ngân</h1>
                        <p className="mt-1 text-sm text-[#60737d]">
                            Tiếp nhận và xử lý các lượt khám chuyển sang từ <strong>Lễ tân (Tiền khám Lần 1)</strong> và <strong>Bác sĩ (Tiền CLS Lần 2)</strong>.
                        </p>
                    </div>

                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            onClick={fetchPayments}
                            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50"
                        >
                            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin text-teal-600' : 'text-slate-500'}`} />
                            Làm mới dữ liệu
                        </button>
                        <Link
                            to="payments"
                            className="inline-flex items-center gap-2 rounded-xl bg-[#0d9488] px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-[#0f766e]"
                        >
                            Quản lý toàn bộ phiếu thu →
                        </Link>
                    </div>
                </header>

                {/* Banner thong bao */}
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

                {/* Thong ke 4 the */}
                <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    {/* Cho thu tien kham Lan 1 */}
                    <article className="rounded-2xl border border-amber-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-amber-700">Tiền khám (Lần 1)</p>
                            <span className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-bold text-amber-700">Lễ tân gửi sang</span>
                        </div>
                        <p className="mt-3 text-3xl font-extrabold tracking-tight text-amber-600">
                            {String(stats.pendingExam).padStart(2, '0')}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Lượt khám mới cần thu tiền để vào khám</p>
                    </article>

                    {/* Cho thu tien CLS Lan 2 */}
                    <article className="rounded-2xl border border-indigo-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-indigo-700">Cận lâm sàng (Lần 2)</p>
                            <span className="rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-bold text-indigo-700">Bác sĩ chỉ định</span>
                        </div>
                        <p className="mt-3 text-3xl font-extrabold tracking-tight text-indigo-600">
                            {String(stats.pendingPara).padStart(2, '0')}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Đơn CLS cần thu tiền để đi xét nghiệm</p>
                    </article>

                    {/* Da thanh toan hom nay */}
                    <article className="rounded-2xl border border-emerald-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">Đã thu hôm nay</p>
                            <span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-bold text-emerald-700">Hoàn tất</span>
                        </div>
                        <p className="mt-3 text-3xl font-extrabold tracking-tight text-emerald-600">
                            {String(stats.paidCount).padStart(2, '0')}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Số giao dịch đã ghi nhận</p>
                    </article>

                    {/* Tong doanh thu */}
                    <article className="rounded-2xl border border-teal-200 bg-white p-5 shadow-sm">
                        <div className="flex items-center justify-between">
                            <p className="text-xs font-semibold uppercase tracking-wider text-teal-700">Tổng doanh thu</p>
                            <span className="rounded-full bg-teal-50 px-2.5 py-0.5 text-xs font-bold text-teal-700">Thực thu</span>
                        </div>
                        <p className="mt-3 text-2xl font-extrabold tracking-tight text-teal-700">
                            {formatMoney(stats.totalRevenue)}
                        </p>
                        <p className="mt-1 text-xs text-[#60737d]">Doanh thu tiền mặt & PayOS</p>
                    </article>
                </section>

                {/* TRANG CHIA 2 NUA: CHO THU TIEN / DA THU TIEN */}
                <section className="overflow-hidden rounded-2xl border border-[#e3ece9] bg-white shadow-sm">
                    <div role="tablist" aria-label="Phiếu thu" className="flex border-b border-[#edf2f0] bg-[#f8fbfa]">
                        <button
                            type="button"
                            role="tab"
                            aria-selected={view === VIEW_PENDING}
                            onClick={() => setView(VIEW_PENDING)}
                            className={`flex flex-1 items-center justify-center gap-2 border-b-2 px-4 py-3.5 text-sm font-bold transition ${
                                view === VIEW_PENDING
                                    ? 'border-amber-500 bg-white text-amber-800'
                                    : 'border-transparent text-slate-500 hover:text-slate-800'
                            }`}
                        >
                            <Clock className="h-4 w-4" />
                            Chờ thu tiền ({pendingTotal})
                        </button>
                        <button
                            type="button"
                            role="tab"
                            aria-selected={view === VIEW_PAID}
                            onClick={() => setView(VIEW_PAID)}
                            className={`flex flex-1 items-center justify-center gap-2 border-b-2 px-4 py-3.5 text-sm font-bold transition ${
                                view === VIEW_PAID
                                    ? 'border-emerald-500 bg-white text-emerald-800'
                                    : 'border-transparent text-slate-500 hover:text-slate-800'
                            }`}
                        >
                            <CheckCircle2 className="h-4 w-4" />
                            Đã thu tiền ({paidTotal})
                        </button>
                    </div>

                    {view === VIEW_PENDING && (
                    <>
                    <div className="flex flex-col justify-between gap-3 border-b border-[#edf2f0] p-5 sm:flex-row sm:items-center">
                        <div>
                            <h2 className="text-lg font-bold text-[#142534]">
                                Lượt khám chờ thu tiền
                                <span className="ml-2.5 rounded-full bg-amber-100 px-3 py-1 text-xs font-bold text-amber-800">
                                    {pendingTotal} đơn chờ xử lý
                                </span>
                            </h2>
                            <p className="mt-1 text-xs text-[#60737d]">
                                Danh sách các lượt khám vừa được Lễ tân hoặc Bác sĩ gửi sang, sẵn sàng để thu tiền mặt hoặc quét mã VietQR PayOS.
                            </p>
                        </div>

                        {/* Tabs loc loai don */}
                        <div className="flex flex-wrap items-center gap-1.5 rounded-xl bg-slate-100 p-1 text-xs font-medium">
                            <button
                                type="button"
                                onClick={() => chooseFilter('ALL')}
                                className={`rounded-lg px-3 py-1.5 transition ${
                                    filterType === 'ALL'
                                        ? 'bg-white font-bold text-teal-800 shadow-sm'
                                        : 'text-slate-600 hover:text-slate-900'
                                }`}
                            >
                                Tất cả ({stats.pendingTotal})
                            </button>
                            <button
                                type="button"
                                onClick={() => chooseFilter('EXAMINATION_FEE')}
                                className={`rounded-lg px-3 py-1.5 transition ${
                                    filterType === 'EXAMINATION_FEE'
                                        ? 'bg-white font-bold text-amber-800 shadow-sm'
                                        : 'text-slate-600 hover:text-slate-900'
                                }`}
                            >
                                📋 Lễ tân gửi (Lần 1: {stats.pendingExam})
                            </button>
                            <button
                                type="button"
                                onClick={() => chooseFilter('PARACLINICAL_FEE')}
                                className={`rounded-lg px-3 py-1.5 transition ${
                                    filterType === 'PARACLINICAL_FEE'
                                        ? 'bg-white font-bold text-indigo-800 shadow-sm'
                                        : 'text-slate-600 hover:text-slate-900'
                                }`}
                            >
                                🔬 Bác sĩ gửi (Lần 2: {stats.pendingPara})
                            </button>
                        </div>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[950px] text-left text-sm">
                            <thead className="bg-[#f8fbfa] text-xs font-semibold uppercase tracking-wider text-[#60737d]">
                                <tr>
                                    <th className="px-5 py-3.5">Mã phiếu / Số vào viện</th>
                                    <th className="px-5 py-3.5">Bệnh nhân</th>
                                    <th className="px-5 py-3.5">Nguồn đơn & Giai đoạn</th>
                                    <th className="px-5 py-3.5">Phòng khám</th>
                                    <th className="px-5 py-3.5">Số tiền cần thu</th>
                                    <th className="px-5 py-3.5">Thời gian gửi</th>
                                    <th className="px-5 py-3.5 text-right">Tạo đơn / Thu tiền</th>
                                </tr>
                            </thead>

                            <tbody className="divide-y divide-[#edf2f0]">
                                {loading ? (
                                    <tr>
                                        <td colSpan={7} className="py-12 text-center text-slate-500">
                                            <RefreshCw className="mx-auto h-6 w-6 animate-spin text-teal-600" />
                                            <p className="mt-2 text-sm font-medium">Đang tải dữ liệu phiếu thu...</p>
                                        </td>
                                    </tr>
                                ) : pendingPageRows.length === 0 ? (
                                    <tr>
                                        <td colSpan={7} className="py-14 text-center">
                                            <CheckCircle2 className="mx-auto h-8 w-8 text-emerald-500" />
                                            <p className="mt-2 text-base font-semibold text-slate-800">Không có đơn chờ thanh toán nào</p>
                                            <p className="text-sm text-slate-500">
                                                Toàn bộ lượt khám chuyển sang từ Lễ tân và Bác sĩ đã được xử lý xong.
                                            </p>
                                        </td>
                                    </tr>
                                ) : (
                                    pendingPageRows.map((payment) => {
                                        const pType = payment.type || payment.paymentType;
                                        const isExam = pType === 'EXAMINATION_FEE';
                                        const amountToPay = payment.patientTotalAmount ?? payment.totalAmount ?? payment.amount ?? 0;
                                        const soVaoVienCode = payment.sovaovien || payment.soVaoVien;

                                        return (
                                            <tr key={payment.id} className="transition hover:bg-[#f8fbfa]">
                                                {/* Ma phieu / So vao vien */}
                                                <td className="px-5 py-4">
                                                    <p className="font-mono text-xs font-bold text-teal-800">
                                                        {payment.paymentCode}
                                                    </p>
                                                    {soVaoVienCode && (
                                                        <p className="mt-0.5 font-mono text-[11px] text-slate-500">
                                                            VV: {soVaoVienCode}
                                                        </p>
                                                    )}
                                                </td>

                                                {/* Thong tin benh nhan */}
                                                <td className="px-5 py-4">
                                                    <p className="font-semibold text-[#142534]">
                                                        {payment.patientName || 'Bệnh nhân'}
                                                    </p>
                                                    <p className="mt-0.5 text-xs text-slate-500">
                                                        Mã BN: {payment.patientCode || '—'} {payment.patientPhone ? `• ${payment.patientPhone}` : ''}
                                                    </p>
                                                </td>

                                                {/* Nguon don & Giai doan */}
                                                <td className="px-5 py-4">
                                                    {isExam ? (
                                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-800 ring-1 ring-inset ring-amber-200">
                                                            <span>📋</span> Lễ tân gửi sang (Tiền khám Lần 1)
                                                        </span>
                                                    ) : (
                                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-800 ring-1 ring-inset ring-indigo-200">
                                                            <span>🔬</span> Bác sĩ chỉ định (CLS Lần 2)
                                                        </span>
                                                    )}
                                                </td>

                                                {/* Phong kham */}
                                                <td className="px-5 py-4 text-xs font-medium text-slate-700">
                                                    <div className="flex items-center gap-1.5">
                                                        <Building2 className="h-3.5 w-3.5 text-slate-400" />
                                                        <span>{payment.roomName || 'Phòng khám chỉ định'}</span>
                                                    </div>
                                                </td>

                                                {/* So tien can thu */}
                                                <td className="px-5 py-4">
                                                    <p className="text-base font-bold tabular-nums text-teal-700">
                                                        {formatMoney(amountToPay)}
                                                    </p>
                                                </td>

                                                {/* Thoi gian */}
                                                <td className="px-5 py-4 text-xs text-slate-500">
                                                    {formatDate(payment.createdAt)}
                                                </td>

                                                {/* Thao tac tao don & thu tien */}
                                                <td className="px-5 py-4 text-right">
                                                    <div className="flex items-center justify-end gap-2">
                                                        {payment.awaitingConclusion ? (
                                                            <span className="inline-flex items-center rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs font-semibold text-amber-800" title={MSG.AWAITS_CONCLUSION}>{AWAITING_CONCLUSION_LABEL}</span>
                                                        ) : (
                                                        <>
                                                        <button
                                                            type="button"
                                                            onClick={() => handleOpenCashModal(payment)}
                                                            className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-emerald-700"
                                                            title="Thu tiền mặt tại quầy"
                                                        >
                                                            <Banknote className="h-4 w-4" />
                                                            Tiền mặt
                                                        </button>

                                                        <button
                                                            type="button"
                                                            onClick={() => handleOpenQrModal(payment)}
                                                            className="inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-3 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-blue-700"
                                                            title="Tạo mã PayOS VietQR chuyển khoản"
                                                        >
                                                            <QrCode className="h-4 w-4" />
                                                            PayOS QR
                                                        </button>
                                                        </>
                                                        )}

                                                        <Link
                                                            to={`payments/${payment.id}`}
                                                            className="inline-flex items-center rounded-xl border border-slate-200 bg-white p-2 text-xs font-medium text-slate-600 hover:bg-slate-50"
                                                            title="Xem chi tiết các khoản mục"
                                                        >
                                                            <FileText className="h-4 w-4" />
                                                        </Link>
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })
                                )}
                            </tbody>
                        </table>
                    </div>

                    {pendingTotal > PENDING_PAGE_SIZE && (
                        <div className="flex flex-col items-center justify-between gap-3 border-t border-[#e3ece9] px-5 py-4 text-sm text-[#60737d] sm:flex-row">
                            <span>
                                Hiển thị {pendingCurrentPage * PENDING_PAGE_SIZE + 1}–{pendingCurrentPage * PENDING_PAGE_SIZE + pendingPageRows.length} trên tổng số {pendingTotal} phiếu chờ thu
                            </span>
                            <nav aria-label="Phân trang" className="flex items-center gap-1.5">
                                <button
                                    type="button"
                                    aria-label="Trang trước"
                                    disabled={pendingCurrentPage === 0}
                                    onClick={() => setPendingPage(pendingCurrentPage - 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsLeft size={14} />
                                </button>
                                <span className="px-2">Trang {pendingCurrentPage + 1}/{pendingPageCount}</span>
                                <button
                                    type="button"
                                    aria-label="Trang sau"
                                    disabled={pendingCurrentPage + 1 >= pendingPageCount}
                                    onClick={() => setPendingPage(pendingCurrentPage + 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsRight size={14} />
                                </button>
                            </nav>
                        </div>
                    )}
                    </>
                    )}

                    {/* NUA 2: PHIEU DA THU TIEN (moi thu truoc) */}
                    {view === VIEW_PAID && (
                    <>
                    <div className="flex items-center justify-between border-b border-[#edf2f0] p-5">
                        <div>
                            <h2 className="text-lg font-bold text-[#142534]">Phiếu đã thu tiền</h2>
                            <p className="mt-0.5 text-xs text-[#60737d]">Các phiếu thu đã xác nhận thanh toán, mới thu xếp trước</p>
                        </div>
                        <Link to="payment-history" className="text-xs font-semibold text-teal-700 hover:text-teal-900">
                            Xem tất cả lịch sử →
                        </Link>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[700px] text-left text-sm">
                            <thead className="bg-[#f8fbfa] text-xs font-semibold uppercase text-slate-500">
                                <tr>
                                    <th className="px-5 py-3">Mã phiếu</th>
                                    <th className="px-5 py-3">Bệnh nhân</th>
                                    <th className="px-5 py-3">Loại dịch vụ</th>
                                    <th className="px-5 py-3">Số tiền</th>
                                    <th className="px-5 py-3">Thời gian thanh toán</th>
                                    <th className="px-5 py-3 text-right">Trạng thái</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-[#edf2f0]">
                                {loading ? (
                                    <tr>
                                        <td colSpan={6} className="py-12 text-center text-slate-500">
                                            <RefreshCw className="mx-auto h-6 w-6 animate-spin text-teal-600" />
                                            <p className="mt-2 text-sm font-medium">Đang tải dữ liệu phiếu thu...</p>
                                        </td>
                                    </tr>
                                ) : paidRows.length === 0 ? (
                                    <tr>
                                        <td colSpan={6} className="py-8 text-center text-xs text-slate-400">
                                            Chưa có phiếu nào đã thu tiền.
                                        </td>
                                    </tr>
                                ) : (
                                    paidRows.map((p) => (
                                        <tr key={p.id} className="hover:bg-slate-50/50">
                                            <td className="px-5 py-3 font-mono text-xs font-semibold text-slate-800">
                                                {p.paymentCode}
                                            </td>
                                            <td className="px-5 py-3 font-medium text-slate-900">
                                                {p.patientName}
                                            </td>
                                            <td className="px-5 py-3 text-xs text-slate-600">
                                                {(p.type || p.paymentType) === 'EXAMINATION_FEE' ? 'Tiền khám (Lần 1)' : 'Cận lâm sàng (Lần 2)'}
                                            </td>
                                            <td className="px-5 py-3 font-bold tabular-nums text-teal-700">
                                                {formatMoney(p.patientTotalAmount ?? p.totalAmount ?? p.amount ?? 0)}
                                            </td>
                                            <td className="px-5 py-3 text-xs text-slate-500">
                                                {formatDate(p.paidAt ?? p.createdAt)}
                                            </td>
                                            <td className="px-5 py-3 text-right">
                                                <div className="flex items-center justify-end gap-2">
                                                    <span className="inline-flex rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-200">
                                                        Đã thanh toán
                                                    </span>
                                                    <Link
                                                        to={`payments/${p.id}`}
                                                        className="inline-flex items-center rounded-xl border border-slate-200 bg-white p-2 text-xs font-medium text-slate-600 hover:bg-slate-50"
                                                        title="Xem chi tiết phiếu thu"
                                                    >
                                                        <FileText className="h-4 w-4" />
                                                    </Link>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>

                    {paidTotal > PENDING_PAGE_SIZE && (
                        <div className="flex flex-col items-center justify-between gap-3 border-t border-[#e3ece9] px-5 py-4 text-sm text-[#60737d] sm:flex-row">
                            <span>
                                Hiển thị {paidPage * PENDING_PAGE_SIZE + 1}–{paidPage * PENDING_PAGE_SIZE + paidRows.length} trên tổng số {paidTotal} phiếu đã thu
                            </span>
                            <nav aria-label="Phân trang" className="flex items-center gap-1.5">
                                <button
                                    type="button"
                                    aria-label="Trang trước"
                                    disabled={paidPage === 0}
                                    onClick={() => setPaidPage(paidPage - 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsLeft size={14} />
                                </button>
                                <span className="px-2">Trang {paidPage + 1}/{paidPageCount}</span>
                                <button
                                    type="button"
                                    aria-label="Trang sau"
                                    disabled={paidPage + 1 >= paidPageCount}
                                    onClick={() => setPaidPage(paidPage + 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsRight size={14} />
                                </button>
                            </nav>
                        </div>
                    )}
                    </>
                    )}
                </section>
            </div>

            {/* MODAL: THU TIEN MAT */}
            {cashModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
                    <div className="relative w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl">
                        <div className="flex items-center justify-between border-b border-slate-100 bg-emerald-700 px-6 py-4 text-white">
                            <div className="flex items-center gap-2.5">
                                <Banknote className="h-6 w-6 text-emerald-200" />
                                <div>
                                    <h3 className="font-bold">Thu tiền mặt tại quầy</h3>
                                    <p className="text-xs text-emerald-100">Mã phiếu: {cashModal.paymentCode}</p>
                                </div>
                            </div>
                            <button
                                type="button"
                                onClick={() => setCashModal(null)}
                                className="rounded-lg p-1 text-emerald-100 hover:bg-emerald-800 hover:text-white"
                            >
                                <X className="h-5 w-5" />
                            </button>
                        </div>

                        <div className="p-6 space-y-4">
                            {/* Tom tat thong tin */}
                            <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5 text-xs space-y-1.5">
                                <div className="flex justify-between">
                                    <span className="text-slate-500">Bệnh nhân:</span>
                                    <span className="font-bold text-slate-800">{cashModal.patientName}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-slate-500">Loại phí:</span>
                                    <span className="font-semibold text-slate-800">
                                        {(cashModal.type || cashModal.paymentType) === 'EXAMINATION_FEE' ? 'Tiền khám ban đầu (Lần 1)' : 'Cận lâm sàng (Lần 2)'}
                                    </span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-slate-500">Phòng khám:</span>
                                    <span className="text-slate-800">{cashModal.roomName || '—'}</span>
                                </div>
                                <div className="flex justify-between border-t border-slate-200 pt-2 text-sm">
                                    <span className="font-bold text-slate-700">Tổng tiền phải thu:</span>
                                    <span className="font-extrabold text-teal-700">
                                        {formatMoney(cashModal.patientTotalAmount ?? cashModal.totalAmount ?? cashModal.amount ?? 0)}
                                    </span>
                                </div>
                            </div>

                            {/* Tien khach dua & tien thoi */}
                            <div>
                                <label className="block text-xs font-semibold text-slate-700">
                                    Số tiền khách đưa (VNĐ)
                                </label>
                                <input
                                    type="number"
                                    value={cashReceived}
                                    onChange={(e) => setCashReceived(e.target.value)}
                                    className="mt-1.5 w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-base font-bold text-slate-900 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                                />
                            </div>

                            <div className="flex justify-between rounded-xl bg-slate-100 p-3 text-xs">
                                <span className="font-medium text-slate-600">Tiền thối lại cho khách:</span>
                                <span className="font-bold text-emerald-700">
                                    {formatMoney(Math.max(0, Number(cashReceived || 0) - Number(cashModal.patientTotalAmount ?? cashModal.totalAmount ?? cashModal.amount ?? 0)))}
                                </span>
                            </div>

                            <div className="flex items-center justify-end gap-3 pt-2">
                                <button
                                    type="button"
                                    onClick={() => setCashModal(null)}
                                    className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                                >
                                    Hủy
                                </button>
                                <button
                                    type="button"
                                    disabled={confirmingCash}
                                    onClick={handleConfirmCash}
                                    className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-emerald-700 disabled:opacity-50"
                                >
                                    {confirmingCash ? <RefreshCw className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                                    Xác nhận đã thu tiền
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* MODAL: PAYOS QR CODE */}
            {qrModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
                    <div className="relative w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl">
                        <div className="flex items-center justify-between border-b border-slate-100 bg-blue-700 px-6 py-4 text-white">
                            <div className="flex items-center gap-2.5">
                                <QrCode className="h-6 w-6 text-blue-200" />
                                <div>
                                    <h3 className="font-bold">Quét mã PayOS VietQR</h3>
                                    <p className="text-xs text-blue-100">Bệnh nhân: {qrModal.payment?.patientName}</p>
                                </div>
                            </div>
                            <button
                                type="button"
                                onClick={() => setQrModal(null)}
                                className="rounded-lg p-1 text-blue-100 hover:bg-blue-800 hover:text-white"
                            >
                                <X className="h-5 w-5" />
                            </button>
                        </div>

                        <div className="p-6 text-center space-y-4">
                            {loadingQr ? (
                                <div className="py-12">
                                    <RefreshCw className="mx-auto h-8 w-8 animate-spin text-blue-600" />
                                    <p className="mt-3 text-sm text-slate-500">Đang khởi tạo mã QR PayOS...</p>
                                </div>
                            ) : qrModal.qrData?.qrCode ? (
                                <>
                                    <div className="mx-auto w-64 overflow-hidden rounded-2xl border-2 border-blue-600 bg-white p-3 shadow-md">
                                        <img
                                            src={
                                                qrModal.qrData.qrCode.startsWith('http') || qrModal.qrData.qrCode.startsWith('data:')
                                                    ? qrModal.qrData.qrCode
                                                    : `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(
                                                          qrModal.qrData.qrCode
                                                      )}`
                                            }
                                            alt="PayOS VietQR"
                                            className="h-full w-full object-contain"
                                            onError={(e) => {
                                                e.currentTarget.src = `https://quickchart.io/qr?size=300&text=${encodeURIComponent(qrModal.qrData.qrCode)}`;
                                            }}
                                        />
                                    </div>

                                    <div className="text-center">
                                        <p className="text-xs text-slate-500">Số tiền cần thanh toán:</p>
                                        <p className="text-2xl font-extrabold text-blue-700">
                                            {formatMoney(qrModal.payment.patientTotalAmount ?? qrModal.payment.totalAmount ?? qrModal.payment.amount ?? 0)}
                                        </p>
                                    </div>

                                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-left text-xs space-y-1.5">
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Ngân hàng:</span>
                                            <span className="font-bold text-slate-800">{qrModal.qrData.accountName || 'PayOS VietQR'}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Số tài khoản:</span>
                                            <span className="font-mono font-bold text-slate-800">{qrModal.qrData.accountNumber || '—'}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-500">Nội dung CK:</span>
                                            <span className="font-mono font-bold text-blue-700">{qrModal.qrData.description || qrModal.payment.paymentCode}</span>
                                        </div>
                                    </div>

                                    <PayosQrCountdown
                                        expiredAt={qrModal.qrData.expiredAt}
                                        onRenew={() => handleOpenQrModal(qrModal.payment)}
                                        renewing={loadingQr}
                                    />

                                    <div className="flex items-center justify-center gap-2 text-xs text-slate-500">
                                        <Clock className="h-4 w-4 animate-spin text-blue-500" />
                                        <span>Đang tự động kiểm tra thanh toán mỗi 3 giây...</span>
                                    </div>

                                    <div className="flex items-center gap-2 pt-2">
                                        {qrModal.qrData.checkoutUrl && (
                                            <a
                                                href={qrModal.qrData.checkoutUrl}
                                                target="_blank"
                                                rel="noreferrer"
                                                className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl border border-slate-200 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                                            >
                                                <ExternalLink className="h-3.5 w-3.5" />
                                                Mở cổng PayOS
                                            </a>
                                        )}
                                        <button
                                            type="button"
                                            disabled={syncingQr}
                                            onClick={handleSyncQr}
                                            className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl bg-blue-600 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
                                        >
                                            {syncingQr ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}
                                            Kiểm tra ngay
                                        </button>
                                    </div>
                                </>
                            ) : (
                                <div className="py-8 text-rose-600">
                                    <AlertCircle className="mx-auto h-8 w-8" />
                                    <p className="mt-2 text-sm font-semibold">Không thể tải mã QR PayOS</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {queueTicketVisitId && (
                <QueueTicketDialog visitId={queueTicketVisitId} onClose={() => setQueueTicketVisitId(null)} />
            )}
        </main>
    );
}
