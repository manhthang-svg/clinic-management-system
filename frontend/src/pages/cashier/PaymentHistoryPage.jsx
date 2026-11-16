import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ChevronsLeft, ChevronsRight } from 'lucide-react';

import defaultPaymentApi from '../../apis/paymentApi';
import { confirmErrorMessage } from '../../components/payments/paymentConfirm';
import { PAYMENT_PAGE_SIZE, SEARCH_MSG, historyParams } from '../../components/payments/paymentSearch';

const PAGE_BUTTON_CLASS =
    'flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300';

const money = (value) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value || 0);
const today = () => new Date().toLocaleDateString('sv-SE');

const STATUS_LABEL = { PAID: 'Đã thanh toán', CANCELLED: 'Đã hủy', FEE_EXEMPT: 'Miễn phí' };
const TYPE_LABEL = { EXAMINATION_FEE: 'Phí khám', PARACLINICAL_FEE: 'Cận lâm sàng' };
const METHOD_LABEL = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', CARD: 'Thẻ' };
const SORTS = [
    { value: 'paidAt,desc', label: 'Thời gian (mới nhất)' },
    { value: 'paidAt,asc', label: 'Thời gian (cũ nhất)' },
    { value: 'patientTotalAmount,desc', label: 'Số tiền (cao nhất)' },
    { value: 'patientTotalAmount,asc', label: 'Số tiền (thấp nhất)' },
];

const fieldClass = 'min-h-10 w-full rounded-xl border border-[#d5e0dc] bg-white px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';

function Select({ id, label, value, onChange, options }) {
    return (
        <label htmlFor={id} className="block">
            <span className="mb-1 block text-xs font-semibold text-[#60737d]">{label}</span>
            <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className={fieldClass}>
                {options.map(([key, text]) => <option key={key} value={key}>{text}</option>)}
            </select>
        </label>
    );
}

/** UC-73 View Payment History. */
export default function PaymentHistoryPage({ paymentApi = defaultPaymentApi }) {
    const [draft, setDraft] = useState(() => ({
        from: today(), to: today(), status: 'ALL', type: 'ALL', method: 'ALL', mine: true, sort: SORTS[0].value,
        search: '',
    }));
    const [filters, setFilters] = useState(draft);
    // Phân trang: trang hiện tại (đếm từ 0); bấm "Xem" với bộ lọc mới thì về trang đầu
    const [pageNumber, setPageNumber] = useState(0);
    const [history, setHistory] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const { params } = historyParams({ ...filters, page: pageNumber });
        let active = true;
        paymentApi.history(params)
            .then((response) => {
                if (active) setHistory(response?.data || response);
            })
            .catch((err) => {
                if (active) setError(confirmErrorMessage(err));
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [filters, pageNumber, paymentApi]);

    const set = (key) => (value) => setDraft((current) => ({ ...current, [key]: value }));

    // Bước 3: bấm Xem; khoảng ngày sai thì MSG53 (EF-01)
    const handleView = (event) => {
        event.preventDefault();
        const { error: rangeError } = historyParams(draft);
        if (rangeError) {
            setError(rangeError);
            return;
        }
        setError('');
        setLoading(true);
        setPageNumber(0);
        setFilters(draft);
    };

    const goToPage = (target) => {
        setLoading(true);
        setPageNumber(target);
    };

    const receipts = history?.receipts?.content || [];
    const totalElements = history?.receipts?.page?.totalElements ?? receipts.length;
    const totalPages = Math.max(1, history?.receipts?.page?.totalPages ?? 1);
    const firstRow = totalElements === 0 ? 0 : pageNumber * PAYMENT_PAGE_SIZE + 1;
    const lastRow = pageNumber * PAYMENT_PAGE_SIZE + receipts.length;

    return (
        <main className="min-h-screen bg-[#f4f8f7] p-4 text-[#142534] sm:p-6 lg:p-8">
            <div className="mx-auto max-w-6xl space-y-6">
                <header>
                    <Link to="../payments" className="text-sm text-teal-700 hover:underline">← Quản lý thanh toán</Link>
                    <h1 className="mt-3 text-2xl font-bold">Lịch sử thanh toán</h1>
                    <p className="mt-1 text-sm text-[#60737d]">Các phiếu đã thanh toán hoặc đã hủy.</p>
                </header>

                <form onSubmit={handleView} noValidate className="grid gap-3 rounded-xl border border-[#e3ece9] bg-white p-5 shadow-sm sm:grid-cols-2 lg:grid-cols-4">
                    <label htmlFor="history-search" className="block sm:col-span-2 lg:col-span-4">
                        <span className="mb-1 block text-xs font-semibold text-[#60737d]">Tìm kiếm</span>
                        <input
                            id="history-search"
                            type="search"
                            value={draft.search}
                            onChange={(e) => set('search')(e.target.value)}
                            placeholder="Số phiếu, số biên lai, mã lượt khám, mã BN hoặc tên bệnh nhân (ít nhất 2 ký tự)"
                            className={fieldClass}
                        />
                    </label>
                    <label htmlFor="history-from" className="block">
                        <span className="mb-1 block text-xs font-semibold text-[#60737d]">Từ ngày</span>
                        <input id="history-from" type="date" value={draft.from} onChange={(e) => set('from')(e.target.value)} className={fieldClass} />
                    </label>
                    <label htmlFor="history-to" className="block">
                        <span className="mb-1 block text-xs font-semibold text-[#60737d]">Đến ngày</span>
                        <input id="history-to" type="date" value={draft.to} onChange={(e) => set('to')(e.target.value)} className={fieldClass} />
                    </label>
                    <Select id="history-status" label="Trạng thái" value={draft.status} onChange={set('status')}
                        options={[['ALL', 'Tất cả'], ['PAID', 'Đã thanh toán'], ['CANCELLED', 'Đã hủy']]} />
                    <Select id="history-type" label="Loại phiếu" value={draft.type} onChange={set('type')}
                        options={[['ALL', 'Tất cả'], ['EXAMINATION_FEE', 'Phí khám'], ['PARACLINICAL_FEE', 'Cận lâm sàng']]} />
                    <Select id="history-method" label="Phương thức" value={draft.method} onChange={set('method')}
                        options={[['ALL', 'Tất cả'], ['CASH', 'Tiền mặt'], ['BANK_TRANSFER', 'Chuyển khoản']]} />
                    <Select id="history-cashier" label="Thu ngân" value={draft.mine ? 'ME' : 'ALL'}
                        onChange={(value) => set('mine')(value === 'ME')}
                        options={[['ME', 'Của tôi'], ['ALL', 'Tất cả thu ngân']]} />
                    <Select id="history-sort" label="Sắp xếp" value={draft.sort} onChange={set('sort')}
                        options={SORTS.map((sort) => [sort.value, sort.label])} />
                    <div className="flex items-end">
                        <button type="submit" className="min-h-10 w-full rounded-xl bg-teal-600 px-4 text-sm font-semibold text-white hover:bg-teal-700">
                            Xem
                        </button>
                    </div>
                </form>

                {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p>}

                {history && history.totalsByMethod.length > 0 && (
                    <section aria-label="Tổng thu theo phương thức" className="grid gap-3 sm:grid-cols-3">
                        {history.totalsByMethod.map((row) => (
                            <div key={row.method} className="rounded-xl border border-[#e3ece9] bg-white p-4 shadow-sm">
                                <p className="text-xs font-semibold text-[#60737d]">{METHOD_LABEL[row.method] || row.method} ({row.count} phiếu)</p>
                                <p className="mt-1 text-lg font-bold">{money(row.total)}</p>
                            </div>
                        ))}
                        <div className="rounded-xl border border-teal-200 bg-teal-50 p-4">
                            <p className="text-xs font-semibold text-teal-800">Tổng bệnh nhân trả</p>
                            <p className="mt-1 text-lg font-bold text-teal-900" data-testid="history-grand-total">{money(history.totalPatientAmount)}</p>
                        </div>
                    </section>
                )}

                <section className="overflow-x-auto rounded-xl border border-[#e3ece9] bg-white shadow-sm">
                    <table className="w-full min-w-[900px] text-left text-sm">
                        <thead className="bg-[#f8fbfa] text-[#60737d]">
                            <tr>
                                {['Số phiếu', 'Loại', 'Trạng thái', 'Bệnh nhân', 'Mã lượt khám', 'Thời gian', 'Phương thức', 'BN trả', 'BHYT trả'].map((heading) => (
                                    <th key={heading} className="px-4 py-3 font-semibold">{heading}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[#edf2f0]">
                            {!loading && receipts.map((payment) => (
                                <tr key={payment.id} className="hover:bg-teal-50/30">
                                    <td className="px-4 py-3">
                                        <Link className="font-semibold text-teal-700 hover:underline" to={`../payments/${payment.id}`}>
                                            {payment.receiptNumber || payment.paymentCode}
                                        </Link>
                                    </td>
                                    <td className="px-4 py-3">{TYPE_LABEL[payment.type] || payment.type}</td>
                                    <td className="px-4 py-3">{STATUS_LABEL[payment.status] || payment.status}</td>
                                    <td className="px-4 py-3">{payment.patientName}<div className="text-xs text-slate-500">{payment.patientCode}</div></td>
                                    <td className="px-4 py-3 font-mono text-xs">{payment.sovaovien}</td>
                                    <td className="px-4 py-3">{payment.paidAt ? new Date(payment.paidAt).toLocaleString('vi-VN') : '—'}</td>
                                    <td className="px-4 py-3">{METHOD_LABEL[payment.paymentMethod] || '—'}</td>
                                    <td className="px-4 py-3 font-semibold tabular-nums">{money(payment.patientTotalAmount)}</td>
                                    <td className="px-4 py-3 tabular-nums">{money(payment.insuranceTotalAmount)}</td>
                                </tr>
                            ))}
                            {!loading && receipts.length === 0 && (
                                <tr><td colSpan="9" className="px-4 py-12 text-center text-slate-500">{SEARCH_MSG.NO_RESULT}</td></tr>
                            )}
                            {loading && (
                                <tr><td colSpan="9" className="px-4 py-12 text-center text-slate-500">Đang tải lịch sử…</td></tr>
                            )}
                        </tbody>
                    </table>
                    {!loading && totalElements > 0 && (
                        <div className="flex flex-col items-center justify-between gap-3 border-t border-[#e3ece9] px-4 py-3 text-sm text-[#60737d] sm:flex-row">
                            <span>Hiển thị {firstRow}–{lastRow} trên tổng số {totalElements} phiếu</span>
                            <nav aria-label="Phân trang" className="flex items-center gap-1.5">
                                <button
                                    type="button"
                                    aria-label="Trang trước"
                                    disabled={pageNumber === 0}
                                    onClick={() => goToPage(pageNumber - 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsLeft size={14} />
                                </button>
                                <span className="px-2">Trang {pageNumber + 1}/{totalPages}</span>
                                <button
                                    type="button"
                                    aria-label="Trang sau"
                                    disabled={pageNumber + 1 >= totalPages}
                                    onClick={() => goToPage(pageNumber + 1)}
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    <ChevronsRight size={14} />
                                </button>
                            </nav>
                        </div>
                    )}
                </section>
            </div>
        </main>
    );
}
