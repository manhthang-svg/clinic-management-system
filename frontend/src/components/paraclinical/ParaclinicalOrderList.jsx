import { Fragment, useEffect, useId, useState } from 'react';
import { ChevronDown, ClipboardList, LoaderCircle } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import { ORDERED_SERVICE_STATUS_LABELS as SERVICE_STATUS_LABELS } from '../../constants/visitData';
import { resultApi as defaultResultApi } from '../../apis/paraclinicalResultApi';
import { ResultPrintView } from '../paraclinicalResult/ResultPrintView';
import { formForSave, resultToForm } from '../paraclinicalResult/resultForm';
import { cancelErrorMessage, cancelledMessage, isPrintable } from './paraclinicalOrder';
import { PrintOrderButton } from './ParaclinicalOrderSlip';

const LOAD_FAILED = 'Không tải được danh sách chỉ định cận lâm sàng.';
const RESULT_LOAD_FAILED = 'Không tải được phiếu kết quả. Vui lòng thử lại.';
const REASON_LENGTH = 'Lý do hủy từ 5 đến 500 ký tự.';

const thClass = 'whitespace-nowrap px-3 py-2.5 text-left text-[11px] font-bold uppercase tracking-wide text-slate-500';
const tdClass = 'px-3 py-2.5 align-middle';

function formatDateTime(value) {
    if (!value) return '—';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

/** BR-25, BR-41: the Doctor gives the mandatory reason before a refused service is cancelled. */
function CancelServiceDialog({ detail, api, onClose, onCancelled }) {
    const reasonId = useId();
    const [reason, setReason] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const trimmed = reason.trim();

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (trimmed.length < 5) { // BR-97
            setError(REASON_LENGTH);
            return;
        }
        setSubmitting(true);
        setError('');
        try {
            onCancelled(await api.cancelParaclinicalService(detail.id, trimmed));
        } catch (err) {
            setError(cancelErrorMessage(err));
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
            <form
                role="dialog"
                aria-modal="true"
                aria-label={`Hủy dịch vụ ${detail.tenchidinh}`}
                noValidate
                onSubmit={handleSubmit}
                className="w-full max-w-md space-y-4 rounded-2xl bg-white p-6 shadow-xl"
            >
                <h3 className="text-base font-bold">Hủy dịch vụ {detail.tenchidinh}</h3>
                <p className="text-xs text-slate-600">Chỉ hủy khi bệnh nhân từ chối thực hiện. Khoản thu sẽ được tính lại.</p>
                <label htmlFor={reasonId} className="block text-xs font-semibold text-slate-700">
                    Lý do hủy *
                    <textarea
                        id={reasonId}
                        rows={3}
                        maxLength={500}
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        className="mt-1 block w-full rounded-lg border border-slate-300 p-2 text-sm font-normal"
                    />
                </label>
                {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
                <div className="flex justify-end gap-2">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={submitting}
                        className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700"
                    >
                        Đóng
                    </button>
                    <button
                        type="submit"
                        disabled={submitting || trimmed.length === 0}
                        className="rounded-lg bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                    >
                        {submitting ? 'Đang hủy…' : 'Xác nhận hủy'}
                    </button>
                </div>
            </form>
        </div>
    );
}

/**
 * Submitted results by service: each is requested once (a failed request may be tried again), and `ready` gives a
 * result that has already arrived.
 */
function createResultCache(resultApi) {
    const entries = new Map();
    return {
        ready: (detailId) => entries.get(detailId)?.order ?? null,
        load(detailId) {
            if (!entries.has(detailId)) {
                const entry = {};
                entry.promise = resultApi.viewForDoctor(detailId).then(
                    (order) => { entry.order = order; return order; },
                    (error) => { entries.delete(detailId); throw error; },
                );
                entries.set(detailId, entry);
            }
            return entries.get(detailId).promise;
        },
    };
}

/**
 * The submitted result of one service, shown exactly as the technician prints it (UC-68 ResultPrintView, read-only).
 * Hidden when printing, so it never mixes into the order slip.
 */
function ResultPreview({ detailId, results }) {
    // A result loaded before (prefetched or opened earlier) shows at once, without a loading step.
    const [state, setState] = useState(() => {
        const ready = results.ready(detailId);
        return ready ? { loading: false, order: ready, error: '' } : { loading: true, order: null, error: '' };
    });

    useEffect(() => {
        if (!state.loading) return undefined;
        let active = true;
        results.load(detailId)
            .then((order) => active && setState({ loading: false, order, error: '' }))
            .catch(() => active && setState({ loading: false, order: null, error: RESULT_LOAD_FAILED }));
        return () => { active = false; };
    }, [detailId, results, state.loading]);

    if (state.loading) {
        return (
            <p role="status" className="flex items-center gap-2 text-xs text-slate-600">
                <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />Đang tải phiếu kết quả…
            </p>
        );
    }
    if (state.error) return <p role="alert" className="text-xs text-rose-700">{state.error}</p>;
    return (
        <div className="overflow-auto rounded-xl border border-slate-200 bg-slate-100 p-4">
            <div className="mx-auto w-fit shadow-md">
                <ResultPrintView order={state.order} form={formForSave(state.order, resultToForm(state.order))} />
            </div>
        </div>
    );
}

function OrderCard({ order, api, results, onCancel }) {
    // The service whose result is open below its row.
    const [openId, setOpenId] = useState(null);
    const toggle = (detail) => setOpenId((current) => (current === detail.id ? null : detail.id));

    return (
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <header className="flex flex-wrap items-start justify-between gap-2">
                <div>
                    <h3 className="text-sm font-bold">Phiếu chỉ định {order.sophieu}</h3>
                    <p className="mt-1 text-xs text-slate-600">Chẩn đoán sơ bộ: <strong>{order.chandoansobo || '—'}</strong></p>
                </div>
                <div className="flex flex-col items-end gap-2">
                    <p className="text-xs text-slate-500">Chỉ định lúc {formatDateTime(order.createdAt)}</p>
                    {/* UC-50: in phiếu chỉ định khi phiếu chưa hủy và còn dịch vụ cần làm (BR-50-05) */}
                    {/* All services cancelled: nothing left to print, so the button is disabled. */}
                    <PrintOrderButton orderId={order.id} api={api} className="text-right" disabled={!isPrintable(order)} />
                </div>
            </header>

            <div className="mt-3 overflow-x-auto">
                <table className="w-full text-xs">
                    <thead className="border-b border-slate-200 bg-slate-50">
                        <tr>
                            <th scope="col" className={thClass}>Mã dịch vụ</th>
                            <th scope="col" className={thClass}>Tên dịch vụ</th>
                            <th scope="col" className={thClass}>Nơi thực hiện</th>
                            <th scope="col" className={thClass}>Trạng thái</th>
                            <th scope="col" className={thClass}><span className="sr-only">Thao tác</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        {order.details.map((detail) => {
                            // A service with a submitted result opens it below its row.
                            const hasResult = detail.status === 'COMPLETED';
                            const open = openId === detail.id;
                            return (
                            <Fragment key={detail.id}>
                            <tr
                                className={`border-b border-slate-100 ${hasResult ? 'cursor-pointer hover:bg-teal-50/60' : ''} ${open ? 'bg-teal-50/60' : ''}`}
                                onClick={hasResult ? () => toggle(detail) : undefined}
                                onKeyDown={hasResult ? (event) => { if (event.key === 'Enter') toggle(detail); } : undefined}
                                tabIndex={hasResult ? 0 : undefined}
                                aria-expanded={hasResult ? open : undefined}
                                aria-label={hasResult ? `Xem kết quả ${detail.tenchidinh}` : undefined}
                            >
                                <td className={tdClass}>{detail.machidinh}</td>
                                <td className={tdClass}>{detail.tenchidinh}</td>
                                <td className={tdClass}>
                                    {detail.roomName ?? 'Chưa xếp phòng'}
                                    {detail.roomLocation && (
                                        <span className="block text-[11px] text-slate-500">{detail.roomLocation}</span>
                                    )}
                                </td>
                                <td className={tdClass}>
                                    {SERVICE_STATUS_LABELS[detail.status] ?? '—'}
                                    {detail.cancelReason && (
                                        <span className="block text-[11px] text-slate-500">Lý do: {detail.cancelReason}</span>
                                    )}
                                </td>
                                <td className={`${tdClass} text-right`}>
                                    {detail.cancellable && (
                                        <button
                                            type="button"
                                            onClick={() => onCancel(detail)}
                                            aria-label={`Hủy ${detail.tenchidinh}`}
                                            className="rounded-lg border border-rose-200 px-2.5 py-1 text-[11px] font-semibold text-rose-700 hover:bg-rose-50"
                                        >
                                            Hủy
                                        </button>
                                    )}
                                    {hasResult && (
                                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-teal-700">
                                            {open ? 'Ẩn kết quả' : 'Xem kết quả'}
                                            <ChevronDown aria-hidden="true" className={`h-3.5 w-3.5 transition-transform ${open ? 'rotate-180' : ''}`} />
                                        </span>
                                    )}
                                </td>
                            </tr>
                            {open && (
                                <tr className="border-b border-slate-100 print:hidden">
                                    <td colSpan={5} className="bg-slate-50 p-3">
                                        <ResultPreview detailId={detail.id} results={results} />
                                    </td>
                                </tr>
                            )}
                            </Fragment>
                            );
                        })}
                    </tbody>
                </table>
            </div>

        </article>
    );
}

/**
 * UC-49: the paraclinical orders already made for a visit. BR-25, BR-41: the Doctor cancels a service the patient
 * refused while no Technician has accepted it and its receipt is unpaid.
 */
export function ParaclinicalOrderList({ visitId, api = visitApi, resultApi = defaultResultApi, onCancelled }) {
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [cancelling, setCancelling] = useState(null);
    const [notice, setNotice] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    // Submitted results, loaded once per service: reopening a result shows it at once.
    const [results] = useState(() => createResultCache(resultApi));

    useEffect(() => {
        const controller = new AbortController();
        api.getParaclinicalOrders(visitId, controller.signal)
            .then((response) => {
                setOrders(response ?? []);
                // Load every submitted result in the background, so opening one needs no wait.
                (response ?? []).forEach((item) => item.details
                    ?.filter((detail) => detail.status === 'COMPLETED')
                    .forEach((detail) => results.load(detail.id).catch(() => {})));
            })
            .catch((err) => {
                if (err?.name !== 'AbortError') setError(LOAD_FAILED);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [api, visitId, reloadKey, results]);

    const handleCancelled = (result) => {
        setCancelling(null);
        setNotice(cancelledMessage(result));
        setReloadKey((key) => key + 1);
        onCancelled?.(result);
    };

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
            {notice && (
                <p role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</p>
            )}
            {orders.length === 0
                ? <p className="text-sm text-slate-500">Chưa có chỉ định cận lâm sàng.</p>
                : orders.map((order) => <OrderCard key={order.id} order={order} api={api} results={results} onCancel={setCancelling} />)}
            {cancelling && (
                <CancelServiceDialog
                    detail={cancelling}
                    api={api}
                    onClose={() => setCancelling(null)}
                    onCancelled={handleCancelled}
                />
            )}
        </section>
    );
}

export default ParaclinicalOrderList;
