import { useEffect, useState } from 'react';
import { useSearchParams, Link, useNavigate } from 'react-router';
import {
    CheckCircle2,
    XCircle,
    RefreshCw,
    Printer,
    ArrowLeft,
    Building2,
    User,
    Calendar,
    Phone,
    FileText,
    CreditCard,
    Home,
    AlertCircle,
} from 'lucide-react';
import paymentApi from '../../apis/paymentApi';

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
            second: '2-digit',
        }) + ' - ' + d.toLocaleDateString('vi-VN', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
        });
    } catch {
        return dateStr;
    }
}

// trang ket qua sau khi thanh toan PayOS (trang cong khai)
export default function PaymentResultPage() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    const orderCode = searchParams.get('orderCode');
    const code = searchParams.get('code');
    const statusParam = searchParams.get('status');
    const cancelParam = searchParams.get('cancel') === 'true';

    const [loading, setLoading] = useState(true);
    const [payment, setPayment] = useState(null);
    const [error, setError] = useState('');

    const isSuccess = (code === '00' || statusParam === 'PAID') && !cancelParam;

    useEffect(() => {
        let isMounted = true;

        async function verifyPayment() {
            if (!orderCode) {
                setError('Không tìm thấy mã đơn hàng PayOS.');
                setLoading(false);
                return;
            }

            try {
                let data = null;
                if (isSuccess) {
                    // Goi API xac nhan ket qua thanh toan PayOS
                    const res = await paymentApi.confirmPayosResult({
                        orderCode: Number(orderCode),
                        code: code || '00',
                        status: statusParam || 'PAID',
                        cancel: false,
                    });
                    data = res?.data || res;
                } else {
                    // Goi lay thong tin phieu thu neu huy
                    const res = await paymentApi.getByOrderCode(Number(orderCode));
                    data = res?.data || res;
                }

                if (isMounted) {
                    setPayment(data);
                }
            } catch (err) {
                console.error('Lỗi xác nhận thanh toán:', err);
                if (isMounted) {
                    setError('Không thể đồng bộ thông tin thanh toán. Vui lòng liên hệ quầy thu ngân.');
                }
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        }

        verifyPayment();

        return () => {
            isMounted = false;
        };
    }, [orderCode, code, statusParam, isSuccess]);

    const isExam = payment?.type === 'EXAMINATION_FEE';

    return (
        <main className="min-h-screen bg-[#f1f6f5] py-8 px-4 sm:px-6 lg:px-8 text-slate-800">
            <div className="mx-auto max-w-2xl space-y-6">
                {/* Header dieu huong */}
                <div className="flex items-center justify-between no-print">
                    <button
                        type="button"
                        onClick={() => navigate(-1)}
                        className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-teal-700"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        Quay lại
                    </button>

                    <div className="flex items-center gap-2">
                        <Link
                            to="/cashier"
                            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
                        >
                            Quầy Thu Ngân
                        </Link>
                        <Link
                            to="/"
                            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
                        >
                            <Home className="h-3.5 w-3.5" />
                            Trang chủ
                        </Link>
                    </div>
                </div>

                {/* Trang thai tai */}
                {loading && (
                    <div className="rounded-3xl border border-slate-200 bg-white p-12 text-center shadow-lg">
                        <RefreshCw className="mx-auto h-12 w-12 animate-spin text-teal-600" />
                        <h2 className="mt-4 text-lg font-bold text-slate-900">
                            Đang xử lý & xác nhận kết quả thanh toán...
                        </h2>
                        <p className="mt-1 text-sm text-slate-500">
                            Vui lòng đợi trong giây lát, hệ thống đang đồng bộ dữ liệu giao dịch từ cổng PayOS.
                        </p>
                    </div>
                )}

                {/* THANH CONG */}
                {!loading && (isSuccess || payment?.status === 'PAID') && (
                    <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-xl print:border-none print:shadow-none">
                        {/* Banner dau trang */}
                        <div className="bg-gradient-to-r from-emerald-600 to-teal-700 px-6 py-8 text-center text-white">
                            <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-white/20 backdrop-blur-sm ring-8 ring-white/10">
                                <CheckCircle2 className="h-12 w-12 text-white" />
                            </div>
                            <h1 className="mt-4 text-2xl font-black uppercase tracking-wider">
                                Thanh toán thành công!
                            </h1>
                            <p className="mt-1 text-sm text-emerald-100">
                                Giao dịch qua VietQR PayOS đã được hệ thống ghi nhận và hoàn tất.
                            </p>
                            <p className="mt-3 text-3xl font-extrabold tabular-nums tracking-tight">
                                {formatMoney(payment?.patientTotalAmount ?? payment?.totalAmount ?? payment?.amount)}
                            </p>
                        </div>

                        {/* Huong dan tiep theo cho benh nhan */}
                        <div className="border-b border-emerald-100 bg-emerald-50/70 p-5 text-sm text-emerald-950">
                            <div className="flex items-start gap-3">
                                <div className="mt-0.5 flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-emerald-200 font-bold text-emerald-800 text-xs">
                                    ✓
                                </div>
                                <div>
                                    <p className="font-bold">
                                        {isExam
                                            ? 'Lượt khám đã sẵn sàng vào phòng bác sĩ!'
                                            : 'Các chỉ định cận lâm sàng đã được phát lệnh thực hiện!'}
                                    </p>
                                    <p className="mt-1 text-xs leading-relaxed text-emerald-900">
                                        {isExam ? (
                                            <>
                                                Bệnh nhân vui lòng di chuyển đến trước cửa{' '}
                                                <strong>{payment?.roomName || 'Phòng khám chỉ định'}</strong> và theo dõi màn hình gọi số. Bác sĩ sẽ gọi vào khám theo số thứ tự.
                                            </>
                                        ) : (
                                            <>
                                                Bệnh nhân vui lòng đến các phòng thực hiện cận lâm sàng (Xét nghiệm / Siêu âm / X-Quang) để kỹ thuật viên tiến hành dịch vụ.
                                            </>
                                        )}
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* Phieu thu / Bien lai chi tiet */}
                        <div className="p-6 sm:p-8 space-y-6">
                            {/* Tieu de bien lai */}
                            <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                                <div>
                                    <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                                        BIÊN LAI THU TIỀN ĐIỆN TỬ
                                    </p>
                                    <p className="font-mono text-base font-bold text-slate-800">
                                        Mã phiếu: {payment?.paymentCode || '—'}
                                    </p>
                                </div>
                                <div className="text-right">
                                    <span className="inline-flex rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700 ring-1 ring-emerald-200">
                                        ĐÃ THANH TOÁN
                                    </span>
                                    {payment?.receiptNumber && (
                                        <p className="mt-1 font-mono text-[11px] text-slate-400">
                                            {payment.receiptNumber}
                                        </p>
                                    )}
                                </div>
                            </div>

                            {/* Thong tin thanh toan (trang cong khai: khong hien thong tin benh nhan) */}
                            <div className="space-y-1.5 rounded-2xl bg-slate-50 p-4 text-xs">
                                <p className="font-bold text-slate-500 uppercase tracking-wide">Thông tin thanh toán</p>
                                <p className="text-slate-700">
                                    Loại phí: <strong>{isExam ? 'Phí khám ban đầu (Lần 1)' : 'Phí cận lâm sàng (Lần 2)'}</strong>
                                </p>
                                <p className="text-slate-700">
                                    Hình thức: <strong>Chuyển khoản VietQR PayOS</strong>
                                </p>
                                <p className="text-slate-500 text-[11px]">
                                    Thời gian: {formatDate(payment?.paidAt || payment?.createdAt)}
                                </p>
                            </div>

                            {/* Chi tiet dich vu */}
                            {payment?.items && payment.items.length > 0 && (
                                <div className="overflow-hidden rounded-2xl border border-slate-200">
                                    <table className="w-full text-left text-xs">
                                        <thead className="bg-slate-50 font-bold uppercase text-slate-500">
                                            <tr>
                                                <th className="px-4 py-3">Nội dung dịch vụ</th>
                                                <th className="px-4 py-3 text-center">SL</th>
                                                <th className="px-4 py-3 text-right">Đơn giá</th>
                                                <th className="px-4 py-3 text-right">Thành tiền</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-slate-100">
                                            {payment.items.map((item, index) => (
                                                <tr key={item.id || index}>
                                                    <td className="px-4 py-3 font-medium text-slate-900">
                                                        {item.tenchidinh || item.serviceName || item.serviceCode || item.serviceCodeSnapshot || 'Dịch vụ y tế'}
                                                    </td>
                                                    <td className="px-4 py-3 text-center text-slate-600">
                                                        {item.soluong || item.quantity || 1}
                                                    </td>
                                                    <td className="px-4 py-3 text-right tabular-nums text-slate-600">
                                                        {formatMoney(item.dongia ?? item.unitPrice)}
                                                    </td>
                                                    <td className="px-4 py-3 text-right font-bold tabular-nums text-slate-900">
                                                        {formatMoney(item.benhnhanphaitra ?? item.patientAmount ?? item.thanhtien ?? item.totalAmount)}
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                        <tfoot className="border-t border-slate-200 bg-slate-50/50 font-bold">
                                            <tr>
                                                <td colSpan={3} className="px-4 py-3 text-right text-slate-600">
                                                    Tổng tiền bệnh nhân đã nộp:
                                                </td>
                                                <td className="px-4 py-3 text-right text-sm text-teal-700">
                                                    {formatMoney(payment?.patientTotalAmount ?? payment?.totalAmount ?? payment?.amount)}
                                                </td>
                                            </tr>
                                        </tfoot>
                                    </table>
                                </div>
                            )}

                            {/* Thao tac cuoi trang */}
                            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5 no-print">
                                <button
                                    type="button"
                                    onClick={() => window.print()}
                                    className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
                                >
                                    <Printer className="h-4 w-4" />
                                    In biên lai
                                </button>

                                <div className="flex items-center gap-2">
                                    <Link
                                        to={`/cashier/payments/${payment?.id}`}
                                        className="inline-flex items-center gap-1.5 rounded-xl border border-teal-200 bg-teal-50 px-4 py-2.5 text-xs font-semibold text-teal-800 hover:bg-teal-100"
                                    >
                                        <FileText className="h-4 w-4" />
                                        Xem chi tiết phiếu
                                    </Link>
                                    <Link
                                        to="/cashier"
                                        className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-teal-700"
                                    >
                                        Về quầy Thu ngân →
                                    </Link>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* THAT BAI / BI HUY */}
                {!loading && !isSuccess && payment?.status !== 'PAID' && (
                    <div className="overflow-hidden rounded-3xl border border-rose-200 bg-white shadow-xl">
                        <div className="bg-gradient-to-r from-rose-600 to-red-700 px-6 py-8 text-center text-white">
                            <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-white/20 backdrop-blur-sm ring-8 ring-white/10">
                                <XCircle className="h-12 w-12 text-white" />
                            </div>
                            <h1 className="mt-4 text-2xl font-black uppercase tracking-wider">
                                Thanh toán chưa hoàn tất
                            </h1>
                            <p className="mt-1 text-sm text-rose-100">
                                {cancelParam
                                    ? 'Giao dịch đã bị hủy bởi người dùng hoặc quá thời gian thanh toán.'
                                    : error || 'Chưa ghi nhận được khoản thanh toán từ cổng PayOS.'}
                            </p>
                        </div>

                        <div className="p-6 text-center space-y-4">
                            {payment && (
                                <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-left space-y-1.5">
                                    <div className="flex justify-between">
                                        <span className="text-slate-500">Mã phiếu thu:</span>
                                        <span className="font-mono font-bold text-slate-800">{payment.paymentCode}</span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span className="text-slate-500">Số tiền:</span>
                                        <span className="font-bold text-teal-700">{formatMoney(payment.patientTotalAmount)}</span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span className="text-slate-500">Trạng thái hiện tại:</span>
                                        <span className="font-bold text-amber-600">Chờ thanh toán</span>
                                    </div>
                                </div>
                            )}

                            <p className="text-xs text-slate-500">
                                Nếu bạn đã hoàn tất chuyển khoản thành công, hãy bấm nút <strong>"Kiểm tra lại"</strong> bên dưới để hệ thống đối soát lại với PayOS.
                            </p>

                            <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
                                <button
                                    type="button"
                                    onClick={() => window.location.reload()}
                                    className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                                >
                                    <RefreshCw className="h-4 w-4" />
                                    Kiểm tra lại
                                </button>
                                <Link
                                    to="/cashier"
                                    className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-teal-700"
                                >
                                    Quay về quầy Thu ngân
                                </Link>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </main>
    );
}
