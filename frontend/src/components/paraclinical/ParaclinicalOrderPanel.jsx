import { useEffect, useId, useMemo, useState } from 'react';
import { CheckCircle2, Info as InfoIcon, LoaderCircle, Search, X } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import {
    MSG,
    TYPE_LABEL,
    estimate,
    filterServices,
    formatAmount,
    groupOptions,
    orderErrorMessage,
    toServiceList,
} from './paraclinicalOrder';

const controlClass = 'h-10 rounded-lg border border-slate-200 bg-white px-3 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';
const thClass = 'whitespace-nowrap px-3 py-2.5 text-left text-[11px] font-bold uppercase tracking-wide text-slate-500';
const moneyThClass = `${thClass} text-right`;
const tdClass = 'px-3 py-2.5 align-middle';
const moneyTdClass = `${tdClass} whitespace-nowrap text-right tabular-nums`;

/**
 * UC-49 Order Paraclinical Services, shown inline in the clinical examination: the service catalogue on the left,
 * the selection with its estimated cost on the right.
 */
export function ParaclinicalOrderPanel({ visit, preliminaryDiagnosis = '', api = visitApi, onCancel, onCreated }) {
    const searchId = useId();
    const typeId = useId();
    const groupId = useId();
    const diagnosisId = useId();
    const [services, setServices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [keyword, setKeyword] = useState('');
    const [type, setType] = useState('');
    const [group, setGroup] = useState('');
    const [selectedIds, setSelectedIds] = useState([]);
    const [diagnosis, setDiagnosis] = useState(preliminaryDiagnosis || visit.lydovaovien || '');
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

    const groups = useMemo(() => groupOptions(services), [services]);
    const rows = useMemo(
        () => filterServices(services, { keyword, type, groupId: group }),
        [services, keyword, type, group],
    );
    const insured = visit.doituongbnLoai === 'BHYT';
    const coverage = useMemo(() => (insured && visit.insuranceCoverageRate ? {
        cardRate: visit.insuranceCoverageRate,
        scopeTotal: visit.insuranceScopeTotal ?? 0,
        threshold: visit.coPaymentExemptionThreshold ?? null,
    } : null), [insured, visit.insuranceCoverageRate, visit.insuranceScopeTotal, visit.coPaymentExemptionThreshold]);
    const cost = useMemo(() => estimate(services, selectedIds, coverage), [services, selectedIds, coverage]);
    const coverageNote = coverage
        ? ` (mức hưởng áp dụng ${cost.appliedRate}%, thẻ ${coverage.cardRate}%)`
        : '';

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
        <form noValidate onSubmit={handleSubmit} className="space-y-3">
            <div className="grid items-start gap-3 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
                <section aria-labelledby="cls-catalog-title" className="min-w-0 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                    <h2 id="cls-catalog-title" className="text-sm font-bold">Danh sách dịch vụ</h2>

                    <div className="mt-3 flex flex-wrap gap-2">
                        <label htmlFor={searchId} className="sr-only">Tìm dịch vụ</label>
                        <span className="relative min-w-56 flex-1">
                            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input
                                id={searchId}
                                type="search"
                                value={keyword}
                                onChange={(event) => setKeyword(event.target.value)}
                                placeholder="Tìm theo mã hoặc tên dịch vụ"
                                className={`${controlClass} w-full pl-9`}
                            />
                        </span>
                        <label htmlFor={typeId} className="sr-only">Loại dịch vụ</label>
                        <select id={typeId} value={type} onChange={(event) => setType(event.target.value)} className={controlClass}>
                            <option value="">Tất cả loại</option>
                            {Object.entries(TYPE_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                        <label htmlFor={groupId} className="sr-only">Nhóm dịch vụ</label>
                        <select id={groupId} value={group} onChange={(event) => setGroup(event.target.value)} className={controlClass}>
                            <option value="">Tất cả nhóm</option>
                            {groups.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
                        </select>
                    </div>

                    <div className="mt-3 max-h-[480px] overflow-auto rounded-lg border border-slate-200">
                        <table className="w-full min-w-[640px] text-xs">
                            <thead className="sticky top-0 bg-slate-50">
                                <tr>
                                    <th scope="col" className={`${thClass} w-14 text-center`}>Chọn</th>
                                    <th scope="col" className={thClass}>Mã dịch vụ</th>
                                    <th scope="col" className={thClass}>Tên dịch vụ</th>
                                    <th scope="col" className={thClass}>Nhóm</th>
                                    <th scope="col" className={moneyThClass}>Giá viện phí</th>
                                    <th scope="col" className={moneyThClass}>Giá BHYT</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {loading && (
                                    <tr><td colSpan={6} className="p-6 text-center text-slate-500"><LoaderCircle aria-hidden="true" className="mr-2 inline h-4 w-4 animate-spin" />Đang tải danh mục dịch vụ…</td></tr>
                                )}
                                {!loading && rows.length === 0 && (
                                    <tr><td colSpan={6} className="p-6 text-center text-slate-500">Không có dịch vụ phù hợp.</td></tr>
                                )}
                                {!loading && rows.map((service) => {
                                    const checked = selectedIds.includes(service.id);
                                    return (
                                        <tr key={service.id} className={checked ? 'bg-teal-50/60' : 'hover:bg-slate-50'}>
                                            <td className={`${tdClass} text-center`}>
                                                <input
                                                    type="checkbox"
                                                    checked={checked}
                                                    onChange={() => toggle(service.id)}
                                                    aria-label={`Chọn ${service.name}`}
                                                    className="h-4 w-4 cursor-pointer accent-teal-700"
                                                />
                                            </td>
                                            <td className={`${tdClass} whitespace-nowrap font-mono text-[11px] text-slate-600`}>{service.code}</td>
                                            <td className={`${tdClass} font-medium text-slate-800`}>{service.name}</td>
                                            <td className={`${tdClass} text-slate-600`}>{service.serviceGroupName || '—'}</td>
                                            <td className={moneyTdClass}>{formatAmount(service.clinicPrice)}</td>
                                            <td className={moneyTdClass}>
                                                {service.insurancePrice == null
                                                    ? <span className="text-slate-400">Không thuộc BHYT</span>
                                                    : formatAmount(service.insurancePrice)}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                    <p className="mt-2 text-[11px] text-slate-400">Đơn vị: đồng</p>
                </section>

                <section aria-labelledby="cls-selected-title" className="min-w-0 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                    <h2 id="cls-selected-title" className="text-sm font-bold">Dịch vụ đã chọn ({cost.lines.length})</h2>

                    <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full min-w-[480px] text-xs">
                            <thead className="bg-slate-50">
                                <tr>
                                    <th scope="col" className={thClass}>Dịch vụ</th>
                                    <th scope="col" className={moneyThClass}>Giá viện phí</th>
                                    <th scope="col" className={moneyThClass}>Giá BHYT</th>
                                    {insured ? (
                                        <>
                                            <th scope="col" className={moneyThClass}>BHYT trả</th>
                                            <th scope="col" className={moneyThClass}>Bệnh nhân trả</th>
                                        </>
                                    ) : <th scope="col" className={moneyThClass}>Thành tiền</th>}
                                    <th scope="col" className={`${thClass} w-12`}><span className="sr-only">Thao tác</span></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {cost.lines.length === 0 && (
                                    <tr><td colSpan={insured ? 6 : 5} className="p-6 text-center text-slate-500">Chưa chọn dịch vụ nào.</td></tr>
                                )}
                                {cost.lines.map((line) => (
                                    <tr key={line.id}>
                                        <td className={`${tdClass} text-slate-800`}><span className="font-mono text-[11px] text-slate-500">{line.code}</span> – {line.name}</td>
                                        <td className={moneyTdClass}>{formatAmount(line.clinicPrice)}</td>
                                        <td className={moneyTdClass}>
                                            {line.insurancePrice == null
                                                ? <span className="text-slate-400">Không thuộc BHYT</span>
                                                : formatAmount(line.insurancePrice)}
                                        </td>
                                        {insured ? (
                                            <>
                                                <td className={moneyTdClass}>{formatAmount(line.insuranceAmount)}</td>
                                                <td className={moneyTdClass}>{formatAmount(line.patientAmount)}</td>
                                            </>
                                        ) : <td className={moneyTdClass}>{formatAmount(line.clinicPrice)}</td>}
                                        <td className={`${tdClass} text-center`}>
                                            <button
                                                type="button"
                                                onClick={() => toggle(line.id)}
                                                aria-label={`Gỡ ${line.name}`}
                                                className="rounded-md p-1 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                                            >
                                                <X aria-hidden="true" className="h-4 w-4" />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                            <tfoot className="border-t border-slate-200 bg-slate-50 font-bold">
                                <tr>
                                    <th scope="row" className={`${tdClass} text-left`}>Tổng cộng</th>
                                    <td className={moneyTdClass}>{formatAmount(cost.clinicTotal)}</td>
                                    <td className={moneyTdClass}>{formatAmount(cost.insuranceTotal)}</td>
                                    {insured ? (
                                        <>
                                            <td className={moneyTdClass} data-testid="cls-insurance-total">{formatAmount(cost.insuranceAmount)}</td>
                                            <td className={`${moneyTdClass} text-teal-800`} data-testid="cls-total">{formatAmount(cost.patientAmount)}</td>
                                        </>
                                    ) : <td className={`${moneyTdClass} text-teal-800`} data-testid="cls-total">{formatAmount(cost.total)}</td>}
                                    <td />
                                </tr>
                            </tfoot>
                        </table>
                    </div>

                    <p className="mt-3 flex gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-[11px] leading-5 text-sky-900">
                        <InfoIcon aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-sky-600" />
                        <span>
                            {insured
                                ? `Lượt khám BHYT${coverageNote}: hưởng 100% khi tổng chi phí trong phạm vi BHYT của lượt khám dưới 15% mức lương cơ sở; dịch vụ không thuộc BHYT bệnh nhân trả theo giá viện phí.${cost.rateChanges ? ' Mức hưởng thay đổi nên phí khám và các phiếu trước của lượt khám cũng được tính lại.' : ''} Chỉ định được chuyển tới phòng cận lâm sàng ngay; bệnh nhân thanh toán sau khi có kết luận.`
                                : 'Khám dịch vụ: bệnh nhân trả theo giá viện phí tại thời điểm chỉ định, thanh toán tại quầy thu ngân.'}
                        </span>
                    </p>

                    <label htmlFor={diagnosisId} className="mt-4 block text-xs font-semibold text-slate-700">
                        Chẩn đoán sơ bộ / ghi chú cho kỹ thuật viên
                        <textarea
                            id={diagnosisId}
                            rows={2}
                            maxLength={500}
                            value={diagnosis}
                            onChange={(event) => setDiagnosis(event.target.value)}
                            className="mt-1 w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-normal leading-5 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                        />
                    </label>
                </section>
            </div>

            {error && (
                <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-800">{error}</p>
            )}

            <div className="flex flex-wrap justify-end gap-2">
                <button
                    type="button"
                    onClick={onCancel}
                    disabled={submitting}
                    className="min-h-11 rounded-lg border border-slate-300 px-5 py-2.5 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                >
                    Hủy
                </button>
                <button
                    type="submit"
                    disabled={submitting || selectedIds.length === 0}
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-teal-700 px-6 py-2.5 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {submitting
                        ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />
                        : <CheckCircle2 aria-hidden="true" className="h-4 w-4" />}
                    {submitting ? 'Đang tạo chỉ định…' : 'Xác nhận chỉ định'}
                </button>
            </div>
        </form>
    );
}

export default ParaclinicalOrderPanel;
