import { Link, useParams } from 'react-router';

const sample = {
    id: 'PT-20261026-002',
    patientCode: 'BN00038',
    patient: 'Trần Quốc Bảo',
    dob: '12/04/1994',
    service: 'Xét nghiệm máu',
    department: 'Khoa Xét nghiệm',
    amount: 520000,
    method: 'Chuyển khoản',
    status: 'Chờ thanh toán',
    createdAt: '26/10/2026 09:35',
    cashier: 'Nguyễn Thu Trang',
    note: 'Thanh toán dịch vụ cận lâm sàng',
};
const money = (n) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(n);

export default function PaymentDetailPage() {
    const { id } = useParams();
    const displayId = id || sample.id;
    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-5xl space-y-6">
                <div className="flex flex-wrap items-center gap-2 text-sm text-[#60737d]"><Link to="../payments" className="hover:text-teal-700">Quản lý thanh toán</Link><span>/</span><span className="text-[#142534]">Chi tiết phiếu</span></div>
                <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
                    <div><p className="text-sm font-semibold text-teal-700">METPH / THU NGÂN</p><h1 className="mt-1 text-2xl font-bold tracking-tight">Chi tiết phiếu thanh toán</h1><p className="mt-2 text-sm text-[#60737d]">Mã phiếu: <span className="font-semibold text-[#142534]">{displayId}</span></p></div>
                    <span className="inline-flex w-fit rounded-full bg-amber-50 px-3 py-1.5 text-sm font-semibold text-amber-700 ring-1 ring-inset ring-amber-200">{sample.status}</span>
                </header>

                <section className="rounded-xl border border-[#e3ece9] bg-white p-5 shadow-sm sm:p-6">
                    <div className="flex flex-col justify-between gap-4 border-b border-[#edf2f0] pb-5 sm:flex-row sm:items-center"><div><p className="text-sm text-[#60737d]">Tổng tiền cần thanh toán</p><p className="mt-2 text-3xl font-bold tracking-tight text-[#142534]">{money(sample.amount)}</p></div><div className="rounded-lg bg-[#e8faf6] px-4 py-3 text-sm text-teal-800"><p className="font-semibold">Phiếu thu</p><p className="mt-1">{sample.createdAt}</p></div></div>
                    <div className="grid gap-x-8 gap-y-5 pt-5 sm:grid-cols-2">
                        {[['Mã bệnh nhân', sample.patientCode], ['Họ và tên', sample.patient], ['Ngày sinh', sample.dob], ['Dịch vụ', sample.service], ['Khoa / phòng', sample.department], ['Phương thức thanh toán', sample.method], ['Người thu ngân', sample.cashier], ['Ghi chú', sample.note]].map(([label, value]) => <div key={label}><p className="text-xs font-semibold uppercase tracking-wide text-[#8c9da6]">{label}</p><p className="mt-1.5 font-medium">{value}</p></div>)}
                    </div>
                </section>

                <section className="rounded-xl border border-[#e3ece9] bg-white p-5 shadow-sm sm:p-6">
                    <h2 className="text-lg font-semibold">Chi tiết khoản thu</h2>
                    <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[480px] text-left text-sm"><thead className="bg-[#f8fbfa] text-[#60737d]"><tr><th className="px-4 py-3 font-semibold">Nội dung</th><th className="px-4 py-3 text-right font-semibold">Số lượng</th><th className="px-4 py-3 text-right font-semibold">Thành tiền</th></tr></thead><tbody><tr className="border-t border-[#edf2f0]"><td className="px-4 py-4">{sample.service}</td><td className="px-4 py-4 text-right">1</td><td className="px-4 py-4 text-right font-semibold">{money(sample.amount)}</td></tr><tr className="border-t border-[#edf2f0]"><td colSpan="2" className="px-4 py-4 text-right font-semibold">Tổng cộng</td><td className="px-4 py-4 text-right text-lg font-bold">{money(sample.amount)}</td></tr></tbody></table></div>
                </section>
                <div className="flex flex-wrap justify-between gap-3"><Link to="../payments" className="inline-flex min-h-10 items-center rounded-lg border border-[#d5e0dc] bg-white px-4 py-2 text-sm font-semibold hover:bg-[#edf5f3]">← Quay lại danh sách</Link><button onClick={() => window.print()} className="inline-flex min-h-10 items-center rounded-lg bg-[#0d9488] px-4 py-2 text-sm font-semibold text-white hover:bg-[#0f766e]">In phiếu</button></div>
                <p className="text-xs text-[#8c9da6]">Thông tin đang dùng dữ liệu mẫu. Mã trên URL được hiển thị để phục vụ giao diện chi tiết.</p>
            </div>
        </main>
    );
}
