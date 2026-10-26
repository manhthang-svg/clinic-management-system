import { useMemo, useState } from 'react';
import { Link } from "react-router";
const initialPayments = [
    { id: 'PT-20261026-001', patientCode: 'BN00021', patient: 'Nguyễn Minh Anh', service: 'Khám Nội tổng quát', amount: 350000, method: 'Tiền mặt', status: 'Đã thanh toán', createdAt: '26/10/2026 09:42' },
    { id: 'PT-20261026-002', patientCode: 'BN00038', patient: 'Trần Quốc Bảo', service: 'Xét nghiệm máu', amount: 520000, method: 'Chuyển khoản', status: 'Chờ thanh toán', createdAt: '26/10/2026 09:35' },
    { id: 'PT-20261026-003', patientCode: 'BN00012', patient: 'Lê Thu Hà', service: 'Siêu âm ổ bụng', amount: 450000, method: 'Thẻ', status: 'Đã thanh toán', createdAt: '26/10/2026 09:18' },
    { id: 'PT-20261026-004', patientCode: 'BN00055', patient: 'Phạm Đức Long', service: 'Khám Tai Mũi Họng', amount: 200000, method: '—', status: 'Miễn giảm', createdAt: '26/10/2026 08:56' },
];

const money = (n) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(n);
function Status({ value }) {
    const cls = value === 'Đã thanh toán' ? 'bg-teal-50 text-teal-700 ring-teal-200' : value === 'Chờ thanh toán' ? 'bg-amber-50 text-amber-700 ring-amber-200' : 'bg-slate-100 text-slate-600 ring-slate-200';
    return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ring-1 ring-inset ${cls}`}>{value}</span>;
}

export default function PaymentManagementPage() {
    const [search, setSearch] = useState('');
    const [status, setStatus] = useState('Tất cả');
    const [payments, setPayments] = useState(initialPayments);
    const [notice, setNotice] = useState('');
    const filtered = useMemo(() => payments.filter((p) => {
        const q = search.trim().toLowerCase();
        return (!q || [p.id, p.patientCode, p.patient, p.service].some((v) => v.toLowerCase().includes(q))) && (status === 'Tất cả' || p.status === status);
    }), [payments, search, status]);

    function markPaid(id) {
        setPayments((prev) => prev.map((p) => p.id === id && p.status === 'Chờ thanh toán' ? { ...p, status: 'Đã thanh toán', method: p.method === '—' ? 'Tiền mặt' : p.method } : p));
        setNotice(`Đã cập nhật trạng thái phiếu ${id} (dữ liệu minh họa trên giao diện).`);
    }

    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-7xl space-y-6">
                <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
                    <div><p className="text-sm font-semibold text-teal-700">METPH / THU NGÂN</p><h1 className="mt-1 text-2xl font-bold tracking-tight">Quản lý thanh toán</h1><p className="mt-2 text-sm text-[#60737d]">Tra cứu phiếu thu, kiểm tra trạng thái và ghi nhận thanh toán.</p></div>
                    <Link to="../payment-history" className="inline-flex min-h-10 items-center justify-center rounded-lg border border-[#d5e0dc] bg-white px-4 py-2 text-sm font-semibold text-[#142534] hover:bg-[#edf5f3]">Xem lịch sử giao dịch</Link>
                </header>

                {notice && <div role="status" className="rounded-lg border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-800">{notice}<button className="ml-3 font-bold underline" onClick={() => setNotice('')}>Đóng</button></div>}

                <section className="grid gap-4 sm:grid-cols-3">
                    {[['Tổng phiếu', payments.length], ['Chờ thanh toán', payments.filter(p => p.status === 'Chờ thanh toán').length], ['Đã thanh toán', payments.filter(p => p.status === 'Đã thanh toán').length]].map(([label, value]) => <div key={label} className="rounded-xl border border-[#e3ece9] bg-white p-5 shadow-sm"><p className="text-sm text-[#60737d]">{label}</p><p className="mt-2 text-2xl font-bold">{value}</p></div>)}
                </section>

                <section className="overflow-hidden rounded-xl border border-[#e3ece9] bg-white shadow-sm">
                    <div className="flex flex-col gap-4 border-b border-[#edf2f0] p-5 lg:flex-row lg:items-end lg:justify-between">
                        <div><h2 className="text-lg font-semibold">Danh sách phiếu thanh toán</h2><p className="mt-1 text-sm text-[#60737d]">Có {filtered.length} phiếu phù hợp với điều kiện lọc.</p></div>
                        <div className="grid gap-3 sm:grid-cols-2">
                            <label className="block"><span className="mb-1 block text-xs font-semibold text-[#60737d]">Tìm kiếm</span><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Mã phiếu, mã BN, họ tên..." className="min-h-10 w-full rounded-lg border border-[#d5e0dc] px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100" /></label>
                            <label className="block"><span className="mb-1 block text-xs font-semibold text-[#60737d]">Trạng thái</span><select value={status} onChange={e => setStatus(e.target.value)} className="min-h-10 w-full rounded-lg border border-[#d5e0dc] bg-white px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"><option>Tất cả</option><option>Chờ thanh toán</option><option>Đã thanh toán</option><option>Miễn giảm</option></select></label>
                        </div>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[950px] text-left text-sm">
                            <thead className="bg-[#f8fbfa] text-xs uppercase tracking-wide text-[#60737d]"><tr>{['Mã phiếu / Thời gian', 'Bệnh nhân', 'Dịch vụ', 'Số tiền', 'Phương thức', 'Trạng thái', 'Thao tác'].map(h => <th key={h} className="px-5 py-3 font-semibold">{h}</th>)}</tr></thead>
                            <tbody className="divide-y divide-[#edf2f0]">
                                {filtered.map(p => <tr key={p.id} className="hover:bg-[#f8fbfa]"><td className="px-5 py-4"><Link to={p.id} className="font-semibold text-teal-700 hover:underline">{p.id}</Link><p className="mt-1 text-xs text-[#8c9da6]">{p.createdAt}</p></td><td className="px-5 py-4"><p className="font-medium">{p.patient}</p><p className="mt-1 text-xs text-[#60737d]">{p.patientCode}</p></td><td className="px-5 py-4">{p.service}</td><td className="px-5 py-4 font-semibold tabular-nums">{money(p.amount)}</td><td className="px-5 py-4">{p.method}</td><td className="px-5 py-4"><Status value={p.status} /></td><td className="px-5 py-4"><div className="flex items-center gap-2"><Link to={p.id} className="font-semibold text-teal-700 hover:underline">Chi tiết</Link>{p.status === 'Chờ thanh toán' && <button onClick={() => markPaid(p.id)} className="rounded-md bg-[#0d9488] px-3 py-1.5 text-xs font-semibold text-white hover:bg-[#0f766e]">Xác nhận</button>}</div></td></tr>)}
                                {filtered.length === 0 && <tr><td colSpan="7" className="px-5 py-12 text-center text-sm text-[#60737d]">Không tìm thấy phiếu thanh toán phù hợp.</td></tr>}
                            </tbody>
                        </table>
                    </div>
                </section>
                <p className="text-xs text-[#8c9da6]">Lưu ý: dữ liệu hiện tại là dữ liệu mẫu phía giao diện, chưa kết nối API backend.</p>
            </div>
        </main>
    );
}
