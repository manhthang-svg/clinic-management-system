import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import paymentApi from '../../apis/paymentApi';

const money = (value) =>
    new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        maximumFractionDigits: 0,
    }).format(value || 0);

export default function PaymentHistoryPage() {
    const [payments, setPayments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        paymentApi.list({ status: 'PAID', size: 100 })
            .then((response) => {
                if (!active) return;
                setPayments(response?.data?.content || response?.content || []);
            })
            .catch((err) => {
                if (active) setError(err?.message || 'Không thể tải lịch sử thanh toán');
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, []);

    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-6xl space-y-6">
                <header>
                    <Link to="../payments" className="text-sm text-teal-700 hover:underline">← Quản lý thanh toán</Link>
                    <h1 className="mt-3 text-2xl font-bold">Lịch sử thanh toán</h1>
                    <p className="mt-1 text-sm text-[#60737d]">Các phiếu đã được xác nhận thanh toán.</p>
                </header>
                {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}
                <section className="overflow-x-auto rounded-xl border border-[#e3ece9] bg-white shadow-sm">
                    <table className="w-full min-w-[760px] text-left text-sm">
                        <thead className="bg-[#f8fbfa] text-[#60737d]">
                            <tr>
                                {['Mã phiếu', 'Bệnh nhân', 'Loại thu', 'Số tiền', 'Số biên lai', 'Thời gian'].map((heading) => (
                                    <th key={heading} className="px-5 py-3 font-semibold">{heading}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[#edf2f0]">
                            {payments.map((payment) => (
                                <tr key={payment.id} className="hover:bg-teal-50/30">
                                    <td className="px-5 py-4"><Link className="font-semibold text-teal-700 hover:underline" to={`../payments/${payment.id}`}>{payment.paymentCode}</Link></td>
                                    <td className="px-5 py-4">{payment.patientName || '—'}<div className="text-xs text-slate-500">{payment.patientCode || '—'}</div></td>
                                    <td className="px-5 py-4">{payment.type === 'EXAMINATION_FEE' ? 'Phí khám' : 'Cận lâm sàng'}</td>
                                    <td className="px-5 py-4 font-semibold">{money(payment.patientTotalAmount)}</td>
                                    <td className="px-5 py-4">{payment.receiptNumber || '—'}</td>
                                    <td className="px-5 py-4">{payment.paidAt ? new Date(payment.paidAt).toLocaleString('vi-VN') : '—'}</td>
                                </tr>
                            ))}
                            {!loading && payments.length === 0 && (
                                <tr><td colSpan="6" className="px-5 py-12 text-center text-slate-500">Chưa có lịch sử thanh toán.</td></tr>
                            )}
                            {loading && (
                                <tr><td colSpan="6" className="px-5 py-12 text-center text-slate-500">Đang tải lịch sử...</td></tr>
                            )}
                        </tbody>
                    </table>
                </section>
            </div>
        </main>
    );
}
