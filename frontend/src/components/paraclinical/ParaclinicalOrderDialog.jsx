import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, FlaskConical, RefreshCw, Search, X } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import {
    MSG,
    estimate,
    formatMoney,
    groupServices,
    orderErrorMessage,
    toServiceList,
} from './paraclinicalOrder';

/** UC-49 Order Paraclinical Services. */
export function ParaclinicalOrderDialog({ visit, api = visitApi, onClose, onCreated }) {
    const [services, setServices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [keyword, setKeyword] = useState('');
    const [selectedIds, setSelectedIds] = useState([]);
    const [diagnosis, setDiagnosis] = useState(visit.lydovaovien || '');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let active = true;
        api.getParaclinicalServices()
            .then((response) => {
                if (active) setServices(toServiceList(response));
            })
            .catch(() => {
                if (active) setError(MSG.LOAD_FAILED);
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [api, reloadKey]);

    const reloadServices = () => {
        setLoading(true);
        setReloadKey((key) => key + 1);
    };

    const groups = useMemo(() => groupServices(services, keyword), [services, keyword]);
    const cost = useMemo(() => estimate(services, selectedIds), [services, selectedIds]);

    const toggle = (id) => setSelectedIds((current) => (
        current.includes(id) ? current.filter((item) => item !== id) : [...current, id]
    ));

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (selectedIds.length === 0) {
            setError(MSG.NONE_SELECTED);
            return;
        }
        setSubmitting(true);
        setError('');
        try {
            const order = await api.orderParaclinical({
                visitId: visit.id,
                chandoansobo: diagnosis.trim() || undefined,
                serviceIds: selectedIds,
            });
            onCreated(order);
        } catch (err) {
            setError(orderErrorMessage(err));
            // EF-02: bỏ dịch vụ vừa ngừng hoạt động khỏi lựa chọn và tải lại danh mục
            const inactiveId = err?.errorResponse?.code === 'SERVICE_INACTIVE'
                ? err.errorResponse.details?.serviceId
                : null;
            if (inactiveId) {
                setSelectedIds((current) => current.filter((id) => id !== inactiveId));
                reloadServices();
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="cls-order-title"
                className="relative flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
            >
                <div className="flex items-center justify-between border-b border-slate-100 bg-indigo-700 px-6 py-4 text-white">
                    <div className="flex items-center gap-2.5">
                        <FlaskConical className="h-6 w-6 text-indigo-200" aria-hidden="true" />
                        <div>
                            <h3 id="cls-order-title" className="font-bold">Chỉ định cận lâm sàng</h3>
                            <p className="text-xs text-indigo-100">
                                Bệnh nhân: {visit.patientName} • Mã BN: {visit.patientCode}
                            </p>
                        </div>
                    </div>
                    <button
                        type="button"
                        aria-label="Đóng"
                        onClick={onClose}
                        className="rounded-lg p-1 text-indigo-100 hover:bg-indigo-800 hover:text-white"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} noValidate className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-6">
                    <div>
                        <label htmlFor="cls-diagnosis" className="block text-xs font-semibold text-slate-700">
                            Chẩn đoán sơ bộ
                        </label>
                        <input
                            id="cls-diagnosis"
                            type="text"
                            maxLength={500}
                            value={diagnosis}
                            onChange={(e) => setDiagnosis(e.target.value)}
                            className="mt-1.5 w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                        />
                    </div>

                    <div>
                        <label htmlFor="cls-search" className="block text-xs font-semibold text-slate-700">
                            Tìm dịch vụ
                        </label>
                        <div className="relative mt-1.5">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                            <input
                                id="cls-search"
                                type="search"
                                value={keyword}
                                onChange={(e) => setKeyword(e.target.value)}
                                placeholder="Tên hoặc mã dịch vụ"
                                className="w-full rounded-xl border border-slate-200 py-2.5 pl-9 pr-3.5 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                            />
                        </div>
                    </div>

                    <div className="max-h-64 overflow-y-auto rounded-xl border border-slate-200">
                        {loading && <p className="p-4 text-center text-xs text-slate-500">Đang tải danh mục dịch vụ…</p>}
                        {!loading && groups.length === 0 && (
                            <p className="p-4 text-center text-xs text-slate-500">Không có dịch vụ phù hợp.</p>
                        )}
                        {!loading && groups.map((group) => (
                            <fieldset key={group.type} className="border-b border-slate-100 last:border-b-0">
                                <legend className="w-full bg-slate-50 px-3 py-1.5 text-[11px] font-bold uppercase tracking-wide text-slate-500">
                                    {group.label}
                                </legend>
                                {group.services.map((service) => (
                                    <label
                                        key={service.id}
                                        className="flex cursor-pointer items-center justify-between gap-3 px-3 py-2.5 text-xs hover:bg-slate-50"
                                    >
                                        <span className="flex items-center gap-2.5">
                                            <input
                                                type="checkbox"
                                                checked={selectedIds.includes(service.id)}
                                                onChange={() => toggle(service.id)}
                                                className="h-4 w-4 accent-indigo-600"
                                            />
                                            <span>
                                                <span className="block font-medium text-slate-800">{service.name}</span>
                                                <span className="block font-mono text-[11px] text-slate-500">
                                                    {service.code}{service.serviceGroupName ? ` • ${service.serviceGroupName}` : ''}
                                                </span>
                                            </span>
                                        </span>
                                        <span className="font-bold tabular-nums text-indigo-700">{formatMoney(service.clinicPrice)}</span>
                                    </label>
                                ))}
                            </fieldset>
                        ))}
                    </div>

                    <section aria-labelledby="cls-cost-title" className="rounded-xl border border-indigo-100 bg-indigo-50/60 p-3 text-xs text-indigo-950">
                        <h4 id="cls-cost-title" className="font-bold">Chi phí dự kiến ({cost.lines.length} dịch vụ)</h4>
                        {cost.lines.length === 0 ? (
                            <p className="mt-1 text-indigo-900">Chưa chọn dịch vụ nào.</p>
                        ) : (
                            <ul className="mt-1.5 space-y-1">
                                {cost.lines.map((service) => (
                                    <li key={service.id} className="flex justify-between gap-3">
                                        <span>{service.name}</span>
                                        <span className="tabular-nums">{formatMoney(service.clinicPrice)}</span>
                                    </li>
                                ))}
                            </ul>
                        )}
                        <p className="mt-2 flex justify-between border-t border-indigo-100 pt-2 font-bold">
                            <span>Bệnh nhân trả</span>
                            <span className="tabular-nums" data-testid="cls-total">{formatMoney(cost.total)}</span>
                        </p>
                    </section>

                    {error && (
                        <p role="alert" className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-700">
                            {error}
                        </p>
                    )}

                    <div className="flex items-center justify-end gap-3">
                        <button
                            type="button"
                            onClick={onClose}
                            className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                        >
                            Hủy
                        </button>
                        <button
                            type="submit"
                            disabled={submitting || selectedIds.length === 0}
                            className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-5 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-indigo-700 disabled:opacity-50"
                        >
                            {submitting
                                ? <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />
                                : <CheckCircle2 className="h-4 w-4" aria-hidden="true" />}
                            {submitting ? 'Đang tạo chỉ định…' : 'Xác nhận chỉ định'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default ParaclinicalOrderDialog;
