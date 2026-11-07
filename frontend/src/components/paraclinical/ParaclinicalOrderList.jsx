import { useEffect, useState } from 'react';
import { ClipboardList, LoaderCircle } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import { formatAmount } from './paraclinicalOrder';

// Labels from docs/state-transitions.md section 3.1. PENDING_RESULT has no documented label yet (TBD).
const SERVICE_STATUS_LABELS = {
    PENDING_PAYMENT: 'Chờ thanh toán',
    WAITING: 'Chờ thực hiện',
    IN_PROGRESS: 'Đang thực hiện',
    COMPLETED: 'Hoàn thành',
    CANCELLED: 'Đã hủy',
    ABANDONED: 'Đã hủy',
};

const LOAD_FAILED = 'Không tải được danh sách chỉ định cận lâm sàng.';

const thClass = 'whitespace-nowrap px-3 py-2.5 text-left text-[11px] font-bold uppercase tracking-wide text-slate-500';
const tdClass = 'px-3 py-2.5 align-middle';

function formatDateTime(value) {
    if (!value) return '—';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

function OrderCard({ order }) {
    return (
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <header className="flex flex-wrap items-baseline justify-between gap-2">
                <h3 className="text-sm font-bold">Phiếu chỉ định {order.sophieu}</h3>
                <p className="text-xs text-slate-500">Chỉ định lúc {formatDateTime(order.createdAt)}</p>
            </header>
            <p className="mt-1 text-xs text-slate-600">Chẩn đoán sơ bộ: <strong>{order.chandoansobo || '—'}</strong></p>

            <div className="mt-3 overflow-x-auto">
                <table className="w-full text-xs">
                    <thead className="border-b border-slate-200 bg-slate-50">
                        <tr>
                            <th scope="col" className={thClass}>Mã dịch vụ</th>
                            <th scope="col" className={thClass}>Tên dịch vụ</th>
                            <th scope="col" className={`${thClass} text-right`}>Thành tiền (₫)</th>
                            <th scope="col" className={thClass}>Trạng thái</th>
                        </tr>
                    </thead>
                    <tbody>
                        {order.details.map((detail) => (
                            <tr key={detail.id} className="border-b border-slate-100">
                                <td className={tdClass}>{detail.machidinh}</td>
                                <td className={tdClass}>{detail.tenchidinh}</td>
                                <td className={`${tdClass} text-right tabular-nums`}>{formatAmount(detail.thanhtien)}</td>
                                <td className={tdClass}>{SERVICE_STATUS_LABELS[detail.status] ?? '—'}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <p className="mt-3 text-right text-xs text-slate-600">
                Tổng cộng: <strong className="tabular-nums">{formatAmount(order.tongcong)} ₫</strong>
            </p>
        </article>
    );
}

/** UC-49: read-only list of the paraclinical orders already made for a visit. */
export function ParaclinicalOrderList({ visitId, api = visitApi }) {
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const controller = new AbortController();
        api.getParaclinicalOrders(visitId, controller.signal)
            .then((response) => setOrders(response ?? []))
            .catch((err) => {
                if (err?.name !== 'AbortError') setError(LOAD_FAILED);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [api, visitId]);

    if (loading) {
        return (
            <p role="status" className="flex items-center gap-2 text-sm text-slate-600">
                <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />Đang tải chỉ định cận lâm sàng…
            </p>
        );
    }

    if (error) {
        return <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>;
    }

    return (
        <section aria-labelledby="cls-orders-title" className="space-y-3">
            <h2 id="cls-orders-title" className="flex items-center gap-2 text-sm font-bold">
                <ClipboardList aria-hidden="true" className="h-4 w-4 text-teal-600" />Chỉ định cận lâm sàng đã chỉ định
            </h2>
            {orders.length === 0
                ? <p className="text-sm text-slate-500">Chưa có chỉ định cận lâm sàng.</p>
                : orders.map((order) => <OrderCard key={order.id} order={order} />)}
        </section>
    );
}

export default ParaclinicalOrderList;
