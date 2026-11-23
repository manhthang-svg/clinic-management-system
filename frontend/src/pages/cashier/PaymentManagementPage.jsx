import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import {
    Search,
    RefreshCw,
    CreditCard,
    Banknote,
    QrCode,
    CheckCircle2,
    Eye,
    Clock,
    User,
    FileText,
    Activity,
    X,
    ChevronsLeft,
    ChevronsRight,
} from 'lucide-react';
import paymentApi from '../../apis/paymentApi';
import {
    AWAITING_CONCLUSION_LABEL, MSG, confirmErrorMessage, confirmedNoticeText, queueTicketVisitId as ticketVisitOf,
} from '../../components/payments/paymentConfirm';
import { PayosQrCountdown } from '../../components/payments/PayosQrCountdown';
import { QueueTicketDialog } from '../../components/visits/QueueTicketDialog';
import { PAYMENT_PAGE_SIZE, SEARCH_MSG, searchParams } from '../../components/payments/paymentSearch';
import { clinicToday } from '../../components/visits/visitForm';

// tong so phieu server dem (Page cua /payments hoac PageResponse cua /payments/history)
function totalOf(res) {
    const page = res?.data ?? res;
    return page?.totalElements ?? page?.page?.totalElements ?? 0;
}

const PAGE_BUTTON_CLASS =
    'flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300';

const money = (n) =>
    new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        maximumFractionDigits: 0,
    }).format(n || 0);

function StatusBadge({ value }) {
    if (value === 'PAID') {
        return (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-teal-50 px-3 py-1 text-xs font-semibold text-teal-700 ring-1 ring-inset ring-teal-200">
                <span className="h-1.5 w-1.5 rounded-full bg-teal-600" />
                Đã thanh toán
            </span>
        );
    }
    if (value === 'PENDING') {
        return (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700 ring-1 ring-inset ring-amber-200">
                <span className="h-1.5 w-1.5 rounded-full bg-amber-600 animate-pulse" />
                Chờ thanh toán
            </span>
        );
    }
    if (value === 'FEE_EXEMPT') {
        return (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700 ring-1 ring-inset ring-blue-200">
                Miễn phí
            </span>
        );
    }
    return (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-inset ring-slate-200">
            {value}
        </span>
    );
}

function TypeBadge({ type }) {
    if (type === 'EXAMINATION_FEE') {
        return (
            <span className="inline-flex items-center rounded-md bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700 ring-1 ring-inset ring-blue-200">
                Lần 1: Phí khám
            </span>
        );
    }
    return (
        <span className="inline-flex items-center rounded-md bg-purple-50 px-2.5 py-1 text-xs font-semibold text-purple-700 ring-1 ring-inset ring-purple-200">
            Lần 2: Cận lâm sàng (CLS)
        </span>
    );
}

// UC-70: trang quan ly / tim kiem phieu thu
export default function PaymentManagementPage() {
    const [payments, setPayments] = useState([]);
    // Phan trang: trang hien tai (dem tu 0) va tong so phieu khop bo loc
    const [pageNumber, setPageNumber] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [typeTab, setTypeTab] = useState('ALL'); // ALL, EXAMINATION_FEE, PARACLINICAL_FEE
    const [fromDate, setFromDate] = useState('');
    const [toDate, setToDate] = useState('');
    const [searchError, setSearchError] = useState('');
    // 3 the thong ke: dem tren toan bo phieu (server dem), khong phu thuoc trang dang xem
    const [stats, setStats] = useState({ all: 0, pending: 0, paidToday: 0 });
    const [notice, setNotice] = useState('');
    // luot kham can in phieu so thu tu sau khi thu phi kham
    const [queueTicketVisitId, setQueueTicketVisitId] = useState(null);

    // Modal state
    const [cashModalPayment, setCashModalPayment] = useState(null);
    const [cashNote, setCashNote] = useState('');
    const [submittingCash, setSubmittingCash] = useState(false);

    // PayOS QR Modal
    const [qrModalPayment, setQrModalPayment] = useState(null);
    const [qrData, setQrData] = useState(null);
    const [loadingQr, setLoadingQr] = useState(false);
    const [checkingQr, setCheckingQr] = useState(false);

    // tai 1 trang phieu thu theo bo loc; luon ra danh sach, bam phieu moi mo chi tiet
    const fetchPayments = useCallback(async (pageToLoad = 0) => {
        const { params, error } = searchParams({
            search, status: statusFilter, type: typeTab, from: fromDate, to: toDate, page: pageToLoad,
        });
        setSearchError(error || '');
        if (error) return;
        setLoading(true);
        try {
            const res = await paymentApi.list(params);
            const content = res?.data?.content || res?.content || (Array.isArray(res?.data) ? res.data : []);
            const page = res?.data ?? res;
            setPayments(content);
            setTotalElements(page?.totalElements ?? content.length);
            setPageNumber(pageToLoad);
        } catch (err) {
            setNotice(confirmErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }, [search, statusFilter, typeTab, fromDate, toDate]);

    // dem tren toan bo phieu: chi lay size=1 de doc totalElements; da thu hom nay tinh theo gio thu tien
    const fetchStats = useCallback(async () => {
        const today = clinicToday();
        try {
            const [allRes, pendingRes, todayRes] = await Promise.all([
                paymentApi.list({ page: 0, size: 1 }),
                paymentApi.list({ status: 'PENDING', page: 0, size: 1 }),
                paymentApi.history({ status: 'PAID', from: today, to: today, mine: 'false', page: 0, size: 1 }),
            ]);
            setStats({
                all: totalOf(allRes),
                pending: totalOf(pendingRes),
                paidToday: totalOf((todayRes?.data ?? todayRes)?.receipts),
            });
        } catch {
            // loi dem thi giu so cu, danh sach van dung duoc
        }
    }, []);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchStats();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchStats]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchPayments();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchPayments]);

    const filtered = useMemo(() => {
        return payments;
    }, [payments]);

    // sau khi thu tien / lam moi: dem lai + tai lai dung trang dang xem
    const reloadAll = useCallback((page) => {
        void fetchStats();
        void fetchPayments(page);
    }, [fetchStats, fetchPayments]);
    const reloadCurrentPage = () => reloadAll(pageNumber);
    const totalPages = Math.max(1, Math.ceil(totalElements / PAYMENT_PAGE_SIZE));
    const firstRow = totalElements === 0 ? 0 : pageNumber * PAYMENT_PAGE_SIZE + 1;
    const lastRow = pageNumber * PAYMENT_PAGE_SIZE + payments.length;

    // Xu ly xac nhan tien mat
    const handleConfirmCash = async () => {
        if (!cashModalPayment || submittingCash) return;
        setSubmittingCash(true);
        try {
            const res = await paymentApi.confirm(cashModalPayment.id, {
                paymentMethod: 'CASH',
                note: cashNote.trim() || undefined,
            });
            const confirmed = res?.data || res;
            setNotice(confirmedNoticeText(confirmed));
            setQueueTicketVisitId(ticketVisitOf(confirmed));
            setCashModalPayment(null);
            setCashNote('');
            reloadAll(pageNumber);
        } catch (err) {
            alert(confirmErrorMessage(err));
            setCashModalPayment(null);
            reloadAll(pageNumber);
        } finally {
            setSubmittingCash(false);
        }
    };

    // Mo modal QR PayOS
    const handleOpenPayosQr = async (payment) => {
        setQrModalPayment(payment);
        setLoadingQr(true);
        setQrData(null);
        try {
            const res = await paymentApi.createPayosQr(payment.id);
            const data = res?.data || res;
            setQrData(data);
        } catch (err) {
            alert(err?.errorResponse?.code === 'PAYMENT_AWAITS_CONCLUSION'
                ? MSG.AWAITS_CONCLUSION
                : err?.message || 'Không thể tạo mã QR PayOS');
            setQrModalPayment(null);
        } finally {
            setLoadingQr(false);
        }
    };

    // Kiem tra trang thai PayOS
    const handleCheckPayosStatus = async () => {
        if (!qrModalPayment || checkingQr) return;
        setCheckingQr(true);
        try {
            const res = await paymentApi.syncPayos(qrModalPayment.id);
            const data = res?.data || res;
            if (data?.status === 'PAID') {
                setNotice(`Phiếu ${qrModalPayment.paymentCode} đã thanh toán thành công qua PayOS!`);
                setQueueTicketVisitId(ticketVisitOf(qrModalPayment));
                setQrModalPayment(null);
                setQrData(null);
                reloadAll(pageNumber);
            } else {
                setNotice('Chưa nhận được giao dịch chuyển khoản. Vui lòng thử lại sau khi chuyển tiền.');
            }
        } catch (err) {
            alert(err?.message || 'Lỗi khi kiểm tra trạng thái thanh toán');
        } finally {
            setCheckingQr(false);
        }
    };

    // Polling tu dong khi mo QR modal
    useEffect(() => {
        if (!qrModalPayment || qrModalPayment.status === 'PAID') return;
        const timer = setInterval(async () => {
            try {
                const res = await paymentApi.syncPayos(qrModalPayment.id);
                const data = res?.data || res;
                if (data?.status === 'PAID') {
                    setNotice(`Thanh toán PayOS thành công cho phiếu ${qrModalPayment.paymentCode}!`);
                    setQueueTicketVisitId(ticketVisitOf(qrModalPayment));
                    setQrModalPayment(null);
                    setQrData(null);
                    reloadAll(pageNumber);
                }
            } catch {
                // loi khi hoi ngam thi bo qua
            }
        }, 3000);
        return () => clearInterval(timer);
    }, [qrModalPayment, reloadAll, pageNumber]);

    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-7xl space-y-6">
                <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
                    <div>
                        <p className="text-sm font-semibold text-teal-700">METPH / THU NGÂN VIỆN PHÍ</p>
                        <h1 className="mt-1 text-2xl font-bold tracking-tight">Quản lý thu ngân & thanh toán</h1>
                        <p className="mt-2 text-sm text-[#60737d]">
                            Thu phí khám dịch vụ: <strong>Lần 1 (Khám lâm sàng)</strong> & <strong>Lần 2 (Chỉ định cận lâm sàng)</strong> bằng Tiền mặt hoặc Chuyển khoản QR PayOS.
                        </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <button
                            onClick={reloadCurrentPage}
                            disabled={loading}
                            className="inline-flex min-h-10 items-center justify-center gap-2 rounded-xl border border-[#d5e0dc] bg-white px-4 py-2 text-sm font-semibold text-[#142534] hover:bg-[#edf5f3]"
                        >
                            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
                            Làm mới
                        </button>
                    </div>
                </header>

                {notice && (
                    <div role="status" className="flex items-center justify-between rounded-xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-800">
                        <span>{notice}</span>
                        <button className="ml-3 font-bold underline" onClick={() => setNotice('')}>
                            Đóng
                        </button>
                    </div>
                )}

                {/* Thong ke nhanh */}
                <section className="grid gap-4 sm:grid-cols-3">
                    <div className="rounded-2xl border border-[#e3ece9] bg-white p-5 shadow-sm">
                        <p className="text-sm text-[#60737d]">Tổng số phiếu thu</p>
                        <p className="mt-2 text-3xl font-bold text-slate-800">{stats.all}</p>
                    </div>
                    <div className="rounded-2xl border border-amber-200 bg-amber-50/50 p-5 shadow-sm">
                        <p className="text-sm font-medium text-amber-700">Chờ thanh toán (Cần thu)</p>
                        <p className="mt-2 text-3xl font-bold text-amber-800">
                            {stats.pending}
                        </p>
                    </div>
                    <div className="rounded-2xl border border-teal-200 bg-teal-50/50 p-5 shadow-sm">
                        <p className="text-sm font-medium text-teal-700">Đã thanh toán hôm nay</p>
                        <p className="mt-2 text-3xl font-bold text-teal-800">
                            {stats.paidToday}
                        </p>
                    </div>
                </section>

                {/* Danh sach & Bo loc */}
                <section className="overflow-hidden rounded-2xl border border-[#e3ece9] bg-white shadow-sm">
                    {/* Tab chuyen doi Lan 1 / Lan 2 */}
                    <div className="flex flex-wrap items-center gap-2 border-b border-[#edf2f0] bg-[#f8fbfa] p-4">
                        {[
                            { key: 'ALL', label: 'Tất cả phiếu' },
                            { key: 'EXAMINATION_FEE', label: 'Lần 1: Phí khám ban đầu' },
                            { key: 'PARACLINICAL_FEE', label: 'Lần 2: Phí cận lâm sàng (CLS)' },
                        ].map((tab) => (
                            <button
                                key={tab.key}
                                onClick={() => setTypeTab(tab.key)}
                                className={`rounded-xl px-4 py-2 text-sm font-semibold transition ${
                                    typeTab === tab.key
                                        ? 'bg-[#0d9488] text-white shadow-sm'
                                        : 'bg-white text-slate-600 hover:bg-slate-100'
                                }`}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </div>

                    <div className="flex flex-col gap-4 border-b border-[#edf2f0] p-5 lg:flex-row lg:items-end lg:justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-slate-800">Danh sách phiếu thanh toán viện phí</h2>
                            <p className="mt-1 text-sm text-[#60737d]">Tìm thấy {totalElements} phiếu phù hợp.</p>
                        </div>
                        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                            <label className="block">
                                <span className="mb-1 block text-xs font-semibold text-[#60737d]">Tìm kiếm</span>
                                <div className="relative">
                                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                                    <input
                                        value={search}
                                        onChange={(e) => setSearch(e.target.value)}
                                        placeholder="Số phiếu, mã lượt khám, mã BN, họ tên"
                                        aria-describedby={searchError ? 'payment-search-error' : undefined}
                                        className="min-h-10 w-full rounded-xl border border-[#d5e0dc] pl-9 pr-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                                    />
                                </div>
                                {searchError && (
                                    <span id="payment-search-error" role="alert" className="mt-1 block text-xs text-red-600">{searchError}</span>
                                )}
                            </label>
                            <label className="block">
                                <span className="mb-1 block text-xs font-semibold text-[#60737d]">Từ ngày</span>
                                <input
                                    type="date"
                                    value={fromDate}
                                    onChange={(e) => setFromDate(e.target.value)}
                                    className="min-h-10 w-full rounded-xl border border-[#d5e0dc] bg-white px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                                />
                            </label>
                            <label className="block">
                                <span className="mb-1 block text-xs font-semibold text-[#60737d]">Đến ngày</span>
                                <input
                                    type="date"
                                    value={toDate}
                                    onChange={(e) => setToDate(e.target.value)}
                                    className="min-h-10 w-full rounded-xl border border-[#d5e0dc] bg-white px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                                />
                            </label>
                            <label className="block">
                                <span className="mb-1 block text-xs font-semibold text-[#60737d]">Trạng thái</span>
                                <select
                                    value={statusFilter}
                                    onChange={(e) => setStatusFilter(e.target.value)}
                                    className="min-h-10 w-full rounded-xl border border-[#d5e0dc] bg-white px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                                >
                                    <option value="ALL">Tất cả trạng thái</option>
                                    <option value="PENDING">Chờ thanh toán</option>
                                    <option value="PAID">Đã thanh toán</option>
                                </select>
                            </label>
                        </div>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[1000px] text-left text-sm">
                            <thead className="bg-[#f8fbfa] text-xs uppercase tracking-wide text-[#60737d]">
                                <tr>
                                    <th className="px-5 py-3 font-semibold">Mã phiếu / Lần khám</th>
                                    <th className="px-5 py-3 font-semibold">Bệnh nhân</th>
                                    <th className="px-5 py-3 font-semibold">Loại thanh toán</th>
                                    <th className="px-5 py-3 font-semibold">Khoản thu</th>
                                    <th className="px-5 py-3 font-semibold">Số tiền cần thu</th>
                                    <th className="px-5 py-3 font-semibold">Trạng thái</th>
                                    <th className="px-5 py-3 font-semibold text-center">Thao tác thanh toán</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-[#edf2f0]">
                                {filtered.map((p) => {
                                    const serviceTitle =
                                        p.type === 'EXAMINATION_FEE'
                                            ? `Khám bệnh (${p.roomName || 'Phòng khám'})`
                                            : `Chỉ định CLS (${p.items?.length || 1} dịch vụ)`;

                                    return (
                                        <tr key={p.id} className="hover:bg-[#f8fbfa] transition">
                                            <td className="px-5 py-4">
                                                <Link to={p.id} className="font-semibold text-teal-700 hover:underline">
                                                    {p.paymentCode}
                                                </Link>
                                                {p.sovaovien && (
                                                    <p className="mt-0.5 text-xs text-[#8c9da6]">Số VV: {p.sovaovien}</p>
                                                )}
                                                {p.receiptNumber && (
                                                    <p className="mt-0.5 text-xs font-mono text-teal-600">BL: {p.receiptNumber}</p>
                                                )}
                                            </td>
                                            <td className="px-5 py-4">
                                                <p className="font-semibold text-slate-800">{p.patientName}</p>
                                                <p className="mt-0.5 text-xs text-[#60737d]">
                                                    {p.patientCode} {p.patientGender ? `• ${p.patientGender}` : ''}
                                                </p>
                                            </td>
                                            <td className="px-5 py-4">
                                                <TypeBadge type={p.type} />
                                            </td>
                                            <td className="px-5 py-4">
                                                <p className="font-medium text-slate-700">{serviceTitle}</p>
                                                {p.items && p.items.length > 0 && (
                                                    <p className="mt-0.5 text-xs text-slate-400 truncate max-w-xs">
                                                        {p.items.map((i) => i.tenchidinh).join(', ')}
                                                    </p>
                                                )}
                                            </td>
                                            <td className="px-5 py-4 font-bold text-slate-900 tabular-nums">
                                                {money(p.patientTotalAmount)}
                                            </td>
                                            <td className="px-5 py-4">
                                                <StatusBadge value={p.status} />
                                            </td>
                                            <td className="px-5 py-4 text-center">
                                                <div className="flex items-center justify-center gap-2">
                                                    {p.status === 'PENDING' && p.awaitingConclusion ? (
                                                        <span className="inline-flex items-center rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs font-semibold text-amber-800" title={MSG.AWAITS_CONCLUSION}>{AWAITING_CONCLUSION_LABEL}</span>
                                                    ) : p.status === 'PENDING' ? (
                                                        <>
                                                            <button
                                                                onClick={() => setCashModalPayment(p)}
                                                                className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700"
                                                                title="Thu tiền mặt tại quầy"
                                                            >
                                                                <Banknote size={14} />
                                                                Tiền mặt
                                                            </button>
                                                            <button
                                                                onClick={() => handleOpenPayosQr(p)}
                                                                className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-teal-700"
                                                                title="Thanh toán chuyển khoản quét QR PayOS"
                                                            >
                                                                <QrCode size={14} />
                                                                QR PayOS
                                                            </button>
                                                        </>
                                                    ) : (
                                                        <span className="text-xs text-emerald-700 font-semibold flex items-center gap-1">
                                                            <CheckCircle2 size={14} />
                                                            {p.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản' : 'Tiền mặt'}
                                                        </span>
                                                    )}
                                                    <Link
                                                        to={p.id}
                                                        className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                                                    >
                                                        <Eye size={13} />
                                                        Chi tiết
                                                    </Link>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                                {filtered.length === 0 && (
                                    <tr>
                                        <td colSpan="7" className="px-5 py-12 text-center text-sm text-[#60737d]">
                                            {loading ? 'Đang tải dữ liệu...' : SEARCH_MSG.NO_RESULT}
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>

                    {totalElements > 0 && (
                        <div className="flex flex-col items-center justify-between gap-3 border-t border-[#e3ece9] px-5 py-4 text-sm text-[#60737d] sm:flex-row">
                            <span>Hiển thị {firstRow}–{lastRow} trên tổng số {totalElements} phiếu thu</span>
                            <nav aria-label="Phân trang" className="flex items-center gap-1.5">
                                <button
                                    type="button"
                                    aria-label="Trang trước"
                                    disabled={loading || pageNumber === 0}
                                    onClick={() => void fetchPayments(pageNumber - 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsLeft size={14} />
                                </button>
                                <span className="px-2">Trang {pageNumber + 1}/{totalPages}</span>
                                <button
                                    type="button"
                                    aria-label="Trang sau"
                                    disabled={loading || pageNumber + 1 >= totalPages}
                                    onClick={() => void fetchPayments(pageNumber + 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsRight size={14} />
                                </button>
                            </nav>
                        </div>
                    )}
                </section>
            </div>

            {/* MODAL XAC NHAN TIEN MAT */}
            {cashModalPayment && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs">
                    <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
                        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                            <h3 className="text-lg font-bold text-slate-800">Xác nhận thu tiền mặt</h3>
                            <button onClick={() => setCashModalPayment(null)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100">
                                <X size={18} />
                            </button>
                        </div>

                        <div className="mt-4 space-y-3">
                            <div className="rounded-xl bg-slate-50 p-4 space-y-2">
                                <div className="flex justify-between text-sm">
                                    <span className="text-slate-500">Mã phiếu:</span>
                                    <span className="font-semibold text-slate-800">{cashModalPayment.paymentCode}</span>
                                </div>
                                <div className="flex justify-between text-sm">
                                    <span className="text-slate-500">Bệnh nhân:</span>
                                    <span className="font-semibold text-slate-800">{cashModalPayment.patientName}</span>
                                </div>
                                <div className="flex justify-between text-sm">
                                    <span className="text-slate-500">Loại phí:</span>
                                    <TypeBadge type={cashModalPayment.type} />
                                </div>
                                <div className="flex justify-between text-base font-bold border-t border-slate-200 pt-2">
                                    <span className="text-slate-700">Tổng tiền thu:</span>
                                    <span className="text-emerald-700">{money(cashModalPayment.patientTotalAmount)}</span>
                                </div>
                            </div>

                            <div>
                                <label className="block text-xs font-semibold text-slate-600 mb-1">Ghi chú (tùy chọn)</label>
                                <input
                                    value={cashNote}
                                    onChange={(e) => setCashNote(e.target.value)}
                                    placeholder="Nhập ghi chú thu tiền mặt..."
                                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm outline-none focus:border-teal-500"
                                />
                            </div>
                        </div>

                        <div className="mt-6 flex justify-end gap-3">
                            <button
                                type="button"
                                onClick={() => setCashModalPayment(null)}
                                disabled={submittingCash}
                                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                            >
                                Hủy bỏ
                            </button>
                            <button
                                type="button"
                                onClick={handleConfirmCash}
                                disabled={submittingCash}
                                className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700"
                            >
                                <CheckCircle2 size={16} />
                                {submittingCash ? 'Đang xử lý...' : 'Xác nhận đã thu'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* MODAL THANH TOAN QR PAYOS */}
            {qrModalPayment && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs">
                    <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
                        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                            <div>
                                <h3 className="text-lg font-bold text-slate-800">Thanh toán qua QR PayOS</h3>
                                <p className="text-xs text-slate-400">Quét mã bằng ứng dụng Ngân hàng hoặc Ví điện tử</p>
                            </div>
                            <button
                                onClick={() => {
                                    setQrModalPayment(null);
                                    setQrData(null);
                                }}
                                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
                            >
                                <X size={18} />
                            </button>
                        </div>

                        {loadingQr ? (
                            <div className="py-12 text-center space-y-3">
                                <RefreshCw size={32} className="mx-auto animate-spin text-teal-600" />
                                <p className="text-sm font-medium text-slate-600">Đang khởi tạo liên kết PayOS...</p>
                            </div>
                        ) : qrData ? (
                            <div className="mt-4 space-y-4">
                                <div className="flex flex-col items-center justify-center rounded-2xl bg-teal-50/50 p-5 border border-teal-100">
                                    <div className="overflow-hidden rounded-xl bg-white p-3 shadow-md">
                                        {qrData.qrCode ? (
                                            <img
                                                src={
                                                    qrData.qrCode.startsWith('http') || qrData.qrCode.startsWith('data:')
                                                        ? qrData.qrCode
                                                        : `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(
                                                              qrData.qrCode
                                                          )}`
                                                }
                                                alt="PayOS QR"
                                                className="h-56 w-56 object-contain"
                                                onError={(e) => {
                                                    // anh QR du phong
                                                    e.currentTarget.src = `https://quickchart.io/qr?size=300&text=${encodeURIComponent(qrData.qrCode)}`;
                                                }}
                                            />
                                        ) : (
                                            <div className="flex h-56 w-56 items-center justify-center bg-slate-100 text-xs text-slate-400">
                                                Chưa có mã QR
                                            </div>
                                        )}
                                    </div>
                                    <p className="mt-3 text-lg font-extrabold text-teal-800">
                                        {money(qrModalPayment.patientTotalAmount)}
                                    </p>
                                    <p className="text-xs text-slate-500 font-medium">
                                        Mã phiếu: <strong>{qrModalPayment.paymentCode}</strong> • Mã đơn:{' '}
                                        <strong>{qrData.orderCode}</strong>
                                    </p>
                                </div>

                                <PayosQrCountdown
                                    expiredAt={qrData.expiredAt}
                                    onRenew={() => handleOpenPayosQr(qrModalPayment)}
                                    renewing={loadingQr}
                                />

                                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
                                    <p>
                                        • Hệ thống đang <strong>tự động lắng nghe thanh toán</strong> (cập nhật sau mỗi 3 giây).
                                    </p>
                                    <p>• Sau khi bệnh nhân quét mã và chuyển tiền, giao diện sẽ tự động chuyển thành công.</p>
                                </div>

                                <div className="flex justify-between items-center gap-3">
                                    {qrData.checkoutUrl && (
                                        <a
                                            href={qrData.checkoutUrl}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            className="text-xs font-semibold text-teal-700 underline"
                                        >
                                            Mở cổng PayOS ngoài tab →
                                        </a>
                                    )}
                                    <div className="flex gap-2 ml-auto">
                                        <button
                                            type="button"
                                            onClick={() => {
                                                setQrModalPayment(null);
                                                setQrData(null);
                                            }}
                                            className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                                        >
                                            Đóng
                                        </button>
                                        <button
                                            type="button"
                                            onClick={handleCheckPayosStatus}
                                            disabled={checkingQr}
                                            className="inline-flex items-center gap-1.5 rounded-xl bg-teal-600 px-4 py-2.5 text-xs font-semibold text-white hover:bg-teal-700 shadow-sm"
                                        >
                                            <RefreshCw size={13} className={checkingQr ? 'animate-spin' : ''} />
                                            Kiểm tra thanh toán ngay
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ) : null}
                    </div>
                </div>
            )}


            {queueTicketVisitId && (
                <QueueTicketDialog visitId={queueTicketVisitId} onClose={() => setQueueTicketVisitId(null)} />
            )}
        </main>
    );
}
