import { Link } from 'react-router';

const stats = [
    { label: 'Doanh thu hôm nay', value: '12.850.000 ₫', note: '+12,5% so với hôm qua', tone: 'teal' },
    { label: 'Chờ thanh toán', value: '08', note: 'Bệnh nhân cần xử lý', tone: 'amber' },
    { label: 'Đã thanh toán', value: '32', note: 'Giao dịch hôm nay', tone: 'blue' },
    { label: 'Miễn giảm viện phí', value: '03', note: 'Phiếu được miễn giảm', tone: 'slate' },
];

const recentPayments = [
    { code: 'PT-20261026-001', patient: 'Nguyễn Minh Anh', service: 'Khám Nội tổng quát', amount: '350.000 ₫', time: '09:42', status: 'Đã thanh toán' },
    { code: 'PT-20261026-002', patient: 'Trần Quốc Bảo', service: 'Xét nghiệm máu', amount: '520.000 ₫', time: '09:35', status: 'Chờ thanh toán' },
    { code: 'PT-20261026-003', patient: 'Lê Thu Hà', service: 'Siêu âm ổ bụng', amount: '450.000 ₫', time: '09:18', status: 'Đã thanh toán' },
    { code: 'PT-20261026-004', patient: 'Phạm Đức Long', service: 'Khám Tai Mũi Họng', amount: '200.000 ₫', time: '08:56', status: 'Miễn giảm' },
];

function Status({ value }) {
    const styles = {
        'Đã thanh toán': 'bg-teal-50 text-teal-700 ring-teal-200',
        'Chờ thanh toán': 'bg-amber-50 text-amber-700 ring-amber-200',
        'Miễn giảm': 'bg-slate-100 text-slate-600 ring-slate-200',
    };
    return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ring-1 ring-inset ${styles[value] || styles['Miễn giảm']}`}>{value}</span>;
}

export default function CashierOverviewPage() {
    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-7xl space-y-6">
                <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
                    <div>
                        <p className="text-sm font-semibold text-teal-700">METPH / THU NGÂN</p>
                        <h1 className="mt-1 text-2xl font-bold tracking-tight">Tổng quan thu ngân</h1>
                        <p className="mt-2 text-sm text-[#60737d]">Theo dõi tình hình thu phí và các giao dịch trong ngày.</p>
                    </div>
                    <Link to="payments" className="inline-flex min-h-10 items-center justify-center rounded-lg bg-[#0d9488] px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-[#0f766e]">+ Tạo / xử lý thanh toán</Link>
                </header>

                <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    {stats.map((item) => (
                        <article key={item.label} className="rounded-xl border border-[#e3ece9] bg-white p-5 shadow-sm">
                            <p className="text-sm font-medium text-[#60737d]">{item.label}</p>
                            <p className="mt-3 text-2xl font-bold tracking-tight">{item.value}</p>
                            <p className="mt-2 text-xs text-[#8c9da6]">{item.note}</p>
                        </article>
                    ))}
                </section>

                <section className="grid gap-6 xl:grid-cols-[1.6fr_1fr]">
                    <div className="overflow-hidden rounded-xl border border-[#e3ece9] bg-white shadow-sm">
                        <div className="flex items-center justify-between gap-3 border-b border-[#edf2f0] p-5">
                            <div><h2 className="text-lg font-semibold">Giao dịch gần đây</h2><p className="mt-1 text-sm text-[#60737d]">Các phiếu thu mới nhất trong ngày</p></div>
                            <Link to="payment-history" className="text-sm font-semibold text-teal-700 hover:text-teal-900">Xem lịch sử →</Link>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[720px] text-left text-sm">
                                <thead className="bg-[#f8fbfa] text-xs uppercase tracking-wide text-[#60737d]"><tr><th className="px-5 py-3 font-semibold">Mã phiếu</th><th className="px-5 py-3 font-semibold">Bệnh nhân</th><th className="px-5 py-3 font-semibold">Số tiền</th><th className="px-5 py-3 font-semibold">Trạng thái</th></tr></thead>
                                <tbody className="divide-y divide-[#edf2f0]">
                                    {recentPayments.map((p) => <tr key={p.code} className="hover:bg-[#f8fbfa]"><td className="px-5 py-4"><p className="font-semibold text-[#142534]">{p.code}</p><p className="mt-1 text-xs text-[#8c9da6]">{p.time}</p></td><td className="px-5 py-4"><p className="font-medium">{p.patient}</p><p className="mt-1 text-xs text-[#60737d]">{p.service}</p></td><td className="px-5 py-4 font-semibold tabular-nums">{p.amount}</td><td className="px-5 py-4"><Status value={p.status} /></td></tr>)}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <div className="rounded-xl border border-[#e3ece9] bg-white p-5 shadow-sm">
                        <h2 className="text-lg font-semibold">Truy cập nhanh</h2>
                        <p className="mt-1 text-sm text-[#60737d]">Các nghiệp vụ thường dùng</p>
                        <div className="mt-5 space-y-3">
                            <Link to="payments" className="flex items-center justify-between rounded-lg border border-[#e3ece9] p-4 hover:border-teal-300 hover:bg-[#e8faf6]"><div><p className="font-semibold">Quản lý thanh toán</p><p className="mt-1 text-xs text-[#60737d]">Tra cứu và xử lý phiếu thu</p></div><span className="text-teal-700">→</span></Link>
                            <Link to="payment-history" className="flex items-center justify-between rounded-lg border border-[#e3ece9] p-4 hover:border-teal-300 hover:bg-[#e8faf6]"><div><p className="font-semibold">Lịch sử giao dịch</p><p className="mt-1 text-xs text-[#60737d]">Xem các giao dịch đã ghi nhận</p></div><span className="text-teal-700">→</span></Link>
                        </div>
                        <div className="mt-5 rounded-lg bg-[#e8faf6] p-4"><p className="text-sm font-semibold text-teal-800">Lưu ý nghiệp vụ</p><p className="mt-1 text-sm leading-6 text-teal-900">Đối chiếu số tiền và trạng thái thanh toán trước khi xác nhận hoàn tất phiếu thu.</p></div>
                    </div>
                </section>
            </div>
        </main>
    );
}
