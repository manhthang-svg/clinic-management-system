import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import {
    ArrowLeft,
    Printer,
    Banknote,
    QrCode,
    CheckCircle2,
    Clock,
    Copy,
    Check,
    X,
    RefreshCw,
} from 'lucide-react';
import paymentApi from '../../apis/paymentApi';
import PaymentReceipt from '../../components/payments/PaymentReceipt';
import { PRINT_MSG, confirmErrorMessage, confirmedNoticeText, printErrorMessage } from '../../components/payments/paymentConfirm';

const money = (n) =>
    new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        maximumFractionDigits: 0,
    }).format(n || 0);

function StatusBadge({ value }) {
    if (value === 'PAID') {
        return (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-teal-50 px-3.5 py-1.5 text-xs font-bold text-teal-700 ring-1 ring-inset ring-teal-200">
                <span className="h-2 w-2 rounded-full bg-teal-600" />
                ĐÃ THANH TOÁN
            </span>
        );
    }
    if (value === 'PENDING') {
        return (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3.5 py-1.5 text-xs font-bold text-amber-700 ring-1 ring-inset ring-amber-200">
                <span className="h-2 w-2 rounded-full bg-amber-600 animate-pulse" />
                CHỜ THANH TOÁN
            </span>
        );
    }
    return (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-3.5 py-1.5 text-xs font-bold text-slate-600 ring-1 ring-inset ring-slate-200">
            {value}
        </span>
    );
}

function TypeBadge({ type }) {
    if (type === 'EXAMINATION_FEE') {
        return (
            <span className="inline-flex items-center rounded-lg bg-blue-50 px-3 py-1 text-xs font-bold text-blue-700 ring-1 ring-inset ring-blue-200">
                THANH TOÁN LẦN 1: PHÍ KHÁM BAN ĐẦU
            </span>
        );
    }
    return (
        <span className="inline-flex items-center rounded-lg bg-purple-50 px-3 py-1 text-xs font-bold text-purple-700 ring-1 ring-inset ring-purple-200">
            THANH TOÁN LẦN 2: CHỈ ĐỊNH CẬN LÂM SÀNG (CLS)
        </span>
    );
}

export default function PaymentDetailPage() {
    const { id } = useParams();
    const [payment, setPayment] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [copied, setCopied] = useState(false);
    const [notice, setNotice] = useState('');

    // Modal Thu tiền mặt
    const [cashModalOpen, setCashModalOpen] = useState(false);
    const [cashNote, setCashNote] = useState('');
    const [submittingCash, setSubmittingCash] = useState(false);

    // Modal PayOS QR
    const [qrModalOpen, setQrModalOpen] = useState(false);
    const [qrData, setQrData] = useState(null);
    const [loadingQr, setLoadingQr] = useState(false);
    const [checkingQr, setCheckingQr] = useState(false);
    const isPrintable = payment?.status === 'PAID' || payment?.status === 'FEE_EXEMPT';
    const [receipt, setReceipt] = useState(null);

    // UC-72: lấy nội dung phiếu (ghi log in / bản sao), hiện bản xem trước rồi mở hộp thoại in
    const handlePrint = async () => {
        try {
            setReceipt(await paymentApi.prepareReceiptPrint(payment.id));
            window.setTimeout(() => window.print(), 0);
        } catch (err) {
            setNotice(printErrorMessage(err));
        }
    };

    const loadPayment = useCallback(async () => {
        if (!id) return;
        setLoading(true);
        setError('');
        try {
            const res = await paymentApi.getById(id);
            const data = res?.data || res;
            setPayment(data);
        } catch (err) {
            setError(err?.message || 'Không thể tải thông tin phiếu thu');
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void loadPayment();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [loadPayment]);

    const handleCopy = (text) => {
        if (!text) return;
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    // Xác nhận tiền mặt
    const handleConfirmCash = async () => {
        if (submittingCash) return;
        setSubmittingCash(true);
        try {
            const res = await paymentApi.confirm(payment.id, {
                paymentMethod: 'CASH',
                note: cashNote.trim() || undefined,
            });
            setNotice(confirmedNoticeText(res?.data || res));
            setCashModalOpen(false);
            await loadPayment();
            // UC-71 bước 9: mở bản in phiếu thu ngay sau khi xác nhận (UC-72)
            await handlePrint();
        } catch (err) {
            alert(confirmErrorMessage(err));
            setCashModalOpen(false);
            loadPayment();
        } finally {
            setSubmittingCash(false);
        }
    };

    // Mở PayOS QR
    const handleOpenPayosQr = async () => {
        setQrModalOpen(true);
        setLoadingQr(true);
        try {
            const res = await paymentApi.createPayosQr(payment.id);
            const data = res?.data || res;
            setQrData(data);
        } catch (err) {
            alert(err?.message || 'Không thể tạo mã QR PayOS');
            setQrModalOpen(false);
        } finally {
            setLoadingQr(false);
        }
    };

    // Kiểm tra trạng thái PayOS
    const handleCheckPayosStatus = async () => {
        if (checkingQr) return;
        setCheckingQr(true);
        try {
            const res = await paymentApi.syncPayos(payment.id);
            const data = res?.data || res;
            if (data?.status === 'PAID') {
                setNotice('Thanh toán thành công qua PayOS!');
                setQrModalOpen(false);
                loadPayment();
            } else {
                alert('Chưa nhận được tiền chuyển khoản. Vui lòng kiểm tra lại sau ít giây.');
            }
        } catch (err) {
            alert(err?.message || 'Lỗi kiểm tra thanh toán');
        } finally {
            setCheckingQr(false);
        }
    };

    // Polling tự động khi mở QR modal
    useEffect(() => {
        if (!qrModalOpen || payment?.status === 'PAID') return;
        const timer = setInterval(async () => {
            try {
                const res = await paymentApi.syncPayos(payment.id);
                const data = res?.data || res;
                if (data?.status === 'PAID') {
                    setNotice('Thanh toán thành công qua PayOS!');
                    setQrModalOpen(false);
                    loadPayment();
                }
            } catch {
                // Ignore
            }
        }, 3000);
        return () => clearInterval(timer);
    }, [qrModalOpen, payment?.status, payment?.id, loadPayment]);

    if (loading) {
        return (
            <main className="min-h-screen bg-[#f4f8f7] p-8 flex items-center justify-center">
                <div className="flex items-center gap-3 text-teal-700">
                    <RefreshCw size={24} className="animate-spin" />
                    <span className="font-medium">Đang tải chi tiết phiếu thu...</span>
                </div>
            </main>
        );
    }

    if (error || !payment) {
        return (
            <main className="min-h-screen bg-[#f4f8f7] p-8">
                <div className="mx-auto max-w-xl rounded-2xl bg-white p-6 shadow-sm text-center space-y-4">
                    <p className="text-red-600 font-semibold">{error || 'Không tìm thấy phiếu thu'}</p>
                    <Link
                        to="../payments"
                        className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-700"
                    >
                        <ArrowLeft size={16} /> Quay lại danh sách
                    </Link>
                </div>
            </main>
        );
    }

    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-5xl space-y-6">
                <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-[#60737d]">
                    <div className="flex items-center gap-2">
                        <Link to="../payments" className="hover:text-teal-700 flex items-center gap-1 font-medium">
                            <ArrowLeft size={14} /> Quản lý thanh toán
                        </Link>
                        <span>/</span>
                        <span className="text-[#142534] font-semibold">Chi tiết phiếu thu</span>
                    </div>
                    <TypeBadge type={payment.type} />
                </div>

                {notice && (
                    <div className="flex items-center justify-between rounded-xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-800">
                        <span>{notice}</span>
                        <button onClick={() => setNotice('')} className="font-bold underline ml-2">
                            Đóng
                        </button>
                    </div>
                )}

                <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
                    <div>
                        <p className="text-sm font-semibold text-teal-700">METPH / THU NGÂN VIỆN PHÍ</p>
                        <h1 className="mt-1 text-2xl font-bold tracking-tight">Chi tiết phiếu thanh toán viện phí</h1>
                        <p className="mt-1 text-sm text-[#60737d]">
                            Mã phiếu: <span className="font-mono font-bold text-slate-800">{payment.paymentCode}</span>
                        </p>
                    </div>
                    <StatusBadge value={payment.status} />
                </header>

                {/* THÔNG TIN TÀI CHÍNH TỔNG QUAN */}
                <section className="rounded-2xl border border-[#e3ece9] bg-white p-5 shadow-sm sm:p-6">
                    <div className="flex flex-col justify-between gap-4 border-b border-[#edf2f0] pb-5 sm:flex-row sm:items-center">
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-wider text-[#60737d]">
                                Số tiền bệnh nhân phải thanh toán
                            </p>
                            <p className="mt-1 text-3xl font-extrabold tracking-tight text-teal-800">
                                {money(payment.patientTotalAmount)}
                            </p>
                        </div>
                        <div className="rounded-xl bg-[#e8faf6] px-4 py-3 text-sm text-teal-900 space-y-1">
                            <p className="font-bold flex items-center gap-1.5">
                                <Clock size={15} /> Phiếu tạo lúc:
                            </p>
                            <p className="font-mono text-xs">
                                {payment.createdAt ? new Date(payment.createdAt).toLocaleString('vi-VN') : '—'}
                            </p>
                            {payment.receiptNumber && (
                                <p className="text-xs pt-1 border-t border-teal-200">
                                    Số biên lai: <strong className="font-mono">{payment.receiptNumber}</strong>
                                </p>
                            )}
                        </div>
                    </div>

                    {/* THÔNG TIN BỆNH NHÂN & LƯỢT KHÁM */}
                    <div className="grid gap-x-8 gap-y-4 pt-5 sm:grid-cols-2 lg:grid-cols-3">
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-wide text-[#8c9da6]">Họ và tên bệnh nhân</p>
                            <p className="mt-1 font-bold text-base text-slate-800">{payment.patientName}</p>
                        </div>
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-wide text-[#8c9da6]">Mã bệnh nhân</p>
                            <p className="mt-1 font-mono font-semibold text-slate-700">{payment.patientCode}</p>
                        </div>
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-wide text-[#8c9da6]">Số vào viện (VV)</p>
                            <p className="mt-1 font-mono font-semibold text-teal-700">{payment.sovaovien || '—'}</p>
                        </div>
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-wide text-[#8c9da6]">Giới tính / Ngày sinh</p>
                            <p className="mt-1 font-medium text-slate-700">
                                {payment.patientGender || '—'}{' '}
                                {payment.patientDob ? `• ${new Date(`${payment.patientDob}T00:00:00`).toLocaleDateString('vi-VN')}` : ''}
                            </p>
                        </div>
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-wide text-[#8c9da6]">Số điện thoại</p>
                            <p className="mt-1 font-medium text-slate-700">{payment.patientPhone || '—'}</p>
                        </div>
                        <div>
                            <p className="text-xs font-semibold uppercase tracking-wide text-[#8c9da6]">Phòng khám chỉ định</p>
                            <p className="mt-1 font-medium text-slate-700">{payment.roomName || '—'}</p>
                        </div>
                    </div>

                    {/* VISIT ID VỚI NÚT SAO CHÉP */}
                    {payment.visitId && (
                        <div className="mt-4 flex items-center gap-2 rounded-xl bg-slate-50 px-3.5 py-2 text-xs text-slate-500 border border-slate-100">
                            <span>Mã lượt khám (Visit ID):</span>
                            <span className="font-mono text-slate-700 select-all">{payment.visitId}</span>
                            <button
                                onClick={() => handleCopy(payment.visitId)}
                                className="ml-auto inline-flex items-center gap-1 rounded-md bg-white border border-slate-200 px-2 py-1 text-slate-600 hover:bg-slate-50 text-[11px]"
                            >
                                {copied ? <Check size={12} className="text-teal-600" /> : <Copy size={12} />}
                                {copied ? 'Đã sao chép' : 'Sao chép'}
                            </button>
                        </div>
                    )}
                </section>

                {/* CHI TIẾT CÁC KHOẢN THU */}
                <section className="overflow-hidden rounded-2xl border border-[#e3ece9] bg-white shadow-sm">
                    <div className="border-b border-[#edf2f0] p-5">
                        <h2 className="text-lg font-bold text-slate-800">Bảng chi tiết các dịch vụ thu phí</h2>
                        <p className="mt-0.5 text-xs text-[#60737d]">
                            {payment.type === 'EXAMINATION_FEE'
                                ? 'Phí khám bệnh ban đầu tại phòng khám'
                                : 'Các dịch vụ cận lâm sàng (xét nghiệm, siêu âm, chẩn đoán hình ảnh) do Bác sĩ chỉ định'}
                        </p>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[650px] text-left text-sm">
                            <thead className="bg-[#f8fbfa] text-xs uppercase tracking-wide text-[#60737d]">
                                <tr>
                                    <th className="px-5 py-3 font-semibold">STT</th>
                                    <th className="px-5 py-3 font-semibold">Tên dịch vụ</th>
                                    <th className="px-5 py-3 font-semibold">Mã DV</th>
                                    <th className="px-5 py-3 text-right font-semibold">Số lượng</th>
                                    <th className="px-5 py-3 text-right font-semibold">Đơn giá</th>
                                    <th className="px-5 py-3 text-right font-semibold">BHYT trả</th>
                                    <th className="px-5 py-3 text-right font-semibold">Bệnh nhân trả</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-[#edf2f0]">
                                {payment.items && payment.items.length > 0 ? (
                                    payment.items.map((item, index) => (
                                        <tr key={item.id} className="hover:bg-slate-50/50">
                                            <td className="px-5 py-3.5 text-slate-400 font-medium">{index + 1}</td>
                                            <td className="px-5 py-3.5 font-semibold text-slate-800">{item.tenchidinh}</td>
                                            <td className="px-5 py-3.5 font-mono text-xs text-slate-500">{item.serviceCode}</td>
                                            <td className="px-5 py-3.5 text-right font-medium">{item.soluong}</td>
                                            <td className="px-5 py-3.5 text-right tabular-nums text-slate-600">{money(item.dongia)}</td>
                                            <td className="px-5 py-3.5 text-right tabular-nums text-slate-400">{money(item.bhytChitra)}</td>
                                            <td className="px-5 py-3.5 text-right tabular-nums font-bold text-slate-800">
                                                {money(item.benhnhanphaitra)}
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan="7" className="px-5 py-6 text-center text-slate-400">
                                            Không có danh sách chi tiết dịch vụ.
                                        </td>
                                    </tr>
                                )}
                                <tr className="bg-slate-50 font-bold border-t-2 border-slate-200">
                                    <td colSpan="6" className="px-5 py-4 text-right text-slate-700">
                                        TỔNG CỘNG THANH TOÁN:
                                    </td>
                                    <td className="px-5 py-4 text-right text-lg text-teal-800 tabular-nums">
                                        {money(payment.patientTotalAmount)}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </section>

                {/* NÚT THAO TÁC */}
                <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
                    <Link
                        to="../payments"
                        className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-[#d5e0dc] bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 hover:bg-[#edf5f3]"
                    >
                        <ArrowLeft size={16} /> Quay lại danh sách
                    </Link>

                    <div className="flex flex-wrap items-center gap-3">
                        <button
                            onClick={() => void handlePrint()}
                            disabled={!isPrintable}
                            className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 shadow-xs disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            <Printer size={16} /> In phiếu thu
                        </button>
                        {!isPrintable && (
                            <span className="text-xs text-slate-500">{PRINT_MSG.NOT_PAID}</span>
                        )}

                        {payment.status === 'PENDING' && (
                            <>
                                <button
                                    onClick={() => setCashModalOpen(true)}
                                    className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-emerald-700"
                                >
                                    <Banknote size={16} /> Thu tiền mặt tại quầy
                                </button>
                                <button
                                    onClick={handleOpenPayosQr}
                                    className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-teal-700"
                                >
                                    <QrCode size={16} /> Thanh toán QR PayOS
                                </button>
                            </>
                        )}
                    </div>
                </div>
            </div>

            {/* MODAL TIỀN MẶT */}
            {cashModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs">
                    <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
                        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                            <h3 className="text-lg font-bold text-slate-800">Xác nhận thu tiền mặt</h3>
                            <button onClick={() => setCashModalOpen(false)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100">
                                <X size={18} />
                            </button>
                        </div>
                        <div className="mt-4 space-y-3">
                            <div className="rounded-xl bg-slate-50 p-4 space-y-2">
                                <div className="flex justify-between text-sm">
                                    <span className="text-slate-500">Mã phiếu:</span>
                                    <span className="font-semibold text-slate-800">{payment.paymentCode}</span>
                                </div>
                                <div className="flex justify-between text-sm">
                                    <span className="text-slate-500">Bệnh nhân:</span>
                                    <span className="font-semibold text-slate-800">{payment.patientName}</span>
                                </div>
                                <div className="flex justify-between text-base font-bold border-t border-slate-200 pt-2">
                                    <span className="text-slate-700">Tổng tiền thu:</span>
                                    <span className="text-emerald-700">{money(payment.patientTotalAmount)}</span>
                                </div>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-slate-600 mb-1">Ghi chú (tùy chọn)</label>
                                <input
                                    value={cashNote}
                                    onChange={(e) => setCashNote(e.target.value)}
                                    placeholder="Ghi chú thu tiền..."
                                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm outline-none focus:border-teal-500"
                                />
                            </div>
                        </div>
                        <div className="mt-6 flex justify-end gap-3">
                            <button
                                onClick={() => setCashModalOpen(false)}
                                disabled={submittingCash}
                                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                            >
                                Hủy bỏ
                            </button>
                            <button
                                onClick={handleConfirmCash}
                                disabled={submittingCash}
                                className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-700 shadow-sm"
                            >
                                <CheckCircle2 size={16} />
                                {submittingCash ? 'Đang xử lý...' : 'Xác nhận đã thu'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* MODAL QR PAYOS */}
            {qrModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs">
                    <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
                        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                            <div>
                                <h3 className="text-lg font-bold text-slate-800">Thanh toán qua QR PayOS</h3>
                                <p className="text-xs text-slate-400">Quét mã bằng ứng dụng Ngân hàng</p>
                            </div>
                            <button onClick={() => setQrModalOpen(false)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100">
                                <X size={18} />
                            </button>
                        </div>

                        {loadingQr ? (
                            <div className="py-12 text-center space-y-3">
                                <RefreshCw size={32} className="mx-auto animate-spin text-teal-600" />
                                <p className="text-sm font-medium text-slate-600">Đang khởi tạo mã QR...</p>
                            </div>
                        ) : qrData ? (
                            <div className="mt-4 space-y-4">
                                <div className="flex flex-col items-center justify-center rounded-2xl bg-teal-50/50 p-5 border border-teal-100">
                                    <div className="overflow-hidden rounded-xl bg-white p-3 shadow-md">
                                        <img
                                            src={
                                                qrData.qrCode?.startsWith('http') || qrData.qrCode?.startsWith('data:')
                                                    ? qrData.qrCode
                                                    : `https://img.vietqr.io/image/MB-0348888888-compact2.png?amount=${payment.patientTotalAmount}&addInfo=METPH%20${payment.paymentCode}`
                                            }
                                            alt="PayOS QR"
                                            className="h-56 w-56 object-contain"
                                        />
                                    </div>
                                    <p className="mt-3 text-lg font-extrabold text-teal-800">
                                        {money(payment.patientTotalAmount)}
                                    </p>
                                    <p className="text-xs text-slate-500 font-medium">
                                        Mã phiếu: <strong>{payment.paymentCode}</strong> • Mã đơn:{' '}
                                        <strong>{qrData.orderCode}</strong>
                                    </p>
                                </div>

                                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
                                    <p>• Hệ thống tự động kiểm tra mỗi 3 giây.</p>
                                    <p>• Sau khi quét mã chuyển tiền, phiếu sẽ tự động cập nhật.</p>
                                </div>

                                <div className="flex justify-between items-center gap-3">
                                    {qrData.checkoutUrl && (
                                        <a
                                            href={qrData.checkoutUrl}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            className="text-xs font-semibold text-teal-700 underline"
                                        >
                                            Mở link PayOS →
                                        </a>
                                    )}
                                    <div className="flex gap-2 ml-auto">
                                        <button
                                            type="button"
                                            onClick={() => setQrModalOpen(false)}
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
                                            Kiểm tra đã thanh toán
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ) : null}
                    </div>
                </div>
            )}
            {receipt && (
                <section aria-label="Xem trước phiếu thu" className="mx-auto mt-6 max-w-2xl">
                    <PaymentReceipt receipt={receipt} />
                </section>
            )}
        </main>
    );
}
