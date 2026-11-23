import { useEffect, useId, useMemo, useState } from 'react';
import { CheckCircle2, ChevronLeft, ChevronRight, LoaderCircle, Search, X } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import {
    MSG,
    TYPE_LABEL,
    defaultRoomId,
    estimate,
    filterServices,
    formatAmount,
    groupOptions,
    hasOnDutyRoom,
    orderErrorMessage,
    orderItems,
    roomOptionLabel,
    toServiceList,
} from './paraclinicalOrder';
import { PrintOrderButton } from './ParaclinicalOrderSlip';

const controlClass = 'h-10 rounded-lg border border-slate-200 bg-white px-3 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100';
const thClass = 'whitespace-nowrap px-3 py-2.5 text-left text-[11px] font-bold uppercase tracking-wide text-slate-500';
const moneyThClass = `${thClass} text-right`;
const tdClass = 'px-3 py-2.5 align-middle';
const moneyTdClass = `${tdClass} whitespace-nowrap text-right tabular-nums`;
const PAGE_SIZE = 10;

/** Previous / page numbers / next under the service list. */
function Pagination({ page, pageCount, total, onChange }) {
    const from = total === 0 ? 0 : (page - 1) * PAGE_SIZE + 1;
    const to = Math.min(page * PAGE_SIZE, total);
    const pageButton = 'flex h-8 min-w-8 cursor-pointer items-center justify-center rounded-full px-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40';
    return (
        <nav aria-label="Phân trang dịch vụ" className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
            <span>Hiển thị {from} – {to} / {total}</span>
            <div className="flex items-center gap-1">
                <button type="button" aria-label="Trang trước" disabled={page === 1} onClick={() => onChange(page - 1)} className={`${pageButton} text-slate-600 hover:bg-slate-100`}>
                    <ChevronLeft aria-hidden="true" className="h-4 w-4" />
                </button>
                {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
                    <button
                        key={number}
                        type="button"
                        aria-label={`Trang ${number}`}
                        aria-current={number === page ? 'page' : undefined}
                        onClick={() => onChange(number)}
                        className={`${pageButton} ${number === page ? 'border border-teal-600 text-teal-700' : 'text-slate-600 hover:bg-slate-100'}`}
                    >
                        {number}
                    </button>
                ))}
                <button type="button" aria-label="Trang sau" disabled={page === pageCount} onClick={() => onChange(page + 1)} className={`${pageButton} text-slate-600 hover:bg-slate-100`}>
                    <ChevronRight aria-hidden="true" className="h-4 w-4" />
                </button>
            </div>
        </nav>
    );
}

/** Shown for a self-pay visit: its rooms are assigned when the Cashier confirms the payment (BR-50-02). */
const ROOM_AFTER_PAYMENT = 'Phân phòng sau khi thanh toán';

/**
 * The performing room the system assigns to a selected service (staffed room, fewest waiting, lower room code).
 * The Doctor does not choose it; the backend confirms the room when the order is created. A self-pay visit has no
 * room until the payment (BR-50-01).
 */
function AssignedRoom({ line, insured }) {
    if (!insured) return <span className="text-slate-500">{ROOM_AFTER_PAYMENT}</span>;
    const rooms = line.rooms ?? [];
    if (rooms.length === 0) return <span className="text-slate-400">Chưa có phòng hoạt động</span>;
    const room = rooms.find((option) => option.roomId === defaultRoomId(line)) ?? rooms[0];
    return (
        <span data-testid={`assigned-room-${line.id}`}>
            {roomOptionLabel(room)}
            {rooms.length > 1 && <span className="block text-[10px] text-slate-400">Hệ thống tự xếp phòng</span>}
        </span>
    );
}

/** UC-49 step 10 and UC-50 AF-01: the order is created; the Doctor prints the slip before leaving. */
function OrderCreated({ order, api, onDone }) {
    return (
        <section aria-labelledby="cls-created-title" className="space-y-3 rounded-xl border border-emerald-200 bg-white p-4 shadow-sm">
            <h2 id="cls-created-title" className="flex items-center gap-2 text-sm font-bold text-emerald-800">
                <CheckCircle2 aria-hidden="true" className="h-4 w-4" />Đã tạo chỉ định cận lâm sàng {order.sophieu}.
            </h2>
            <p className="text-xs text-slate-600">In phiếu chỉ định để bệnh nhân mang đến các phòng cận lâm sàng.</p>
            <PrintOrderButton orderId={order.id} api={api} />
            <div className="flex justify-end">
                <button
                    type="button"
                    onClick={onDone}
                    className="min-h-11 rounded-lg bg-teal-700 px-6 py-2.5 text-xs font-semibold text-white hover:bg-teal-800"
                >
                    Về danh sách chờ
                </button>
            </div>
        </section>
    );
}

/**
 * UC-49 Order Paraclinical Services, shown inline in the clinical examination: the paged service catalogue on top,
 * the selection with its estimated cost and performing room below. UC-50: the slip is printed once the order exists.
 */
export function ParaclinicalOrderPanel({
    visit, preliminaryDiagnosis = '', api = visitApi, initialServices = null, blocked = false, onCancel, onCreated,
}) {
    const searchId = useId();
    const typeId = useId();
    const groupId = useId();
    const [services, setServices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [keyword, setKeyword] = useState('');
    const [type, setType] = useState('');
    const [group, setGroup] = useState('');
    const [page, setPage] = useState(1);
    const [selectedIds, setSelectedIds] = useState([]);
    const [created, setCreated] = useState(null);
    // The order carries the preliminary diagnosis of the examination (or the reason for visit); it is not edited here.
    const diagnosis = preliminaryDiagnosis || visit.lydovaovien || '';
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let active = true;
        // The first load can reuse a request the page started earlier (while saving the examination).
        const request = reloadKey === 0 && initialServices ? initialServices : api.getParaclinicalServices();
        request
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
    }, [api, initialServices, reloadKey]);

    const reloadServices = () => {
        setLoading(true);
        setReloadKey((key) => key + 1);
    };

    const groups = useMemo(() => groupOptions(services), [services]);
    const rows = useMemo(
        () => filterServices(services, { keyword, type, groupId: group }),
        [services, keyword, type, group],
    );
    const pageCount = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
    const currentPage = Math.min(page, pageCount);
    const pageRows = rows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
    // A new search or filter starts again from the first page.
    const changeFilter = (setter) => (event) => {
        setter(event.target.value);
        setPage(1);
    };
    const insured = visit.doituongbnLoai === 'BHYT';
    const coverage = useMemo(() => (insured && visit.insuranceCoverageRate ? {
        cardRate: visit.insuranceCoverageRate,
        scopeTotal: visit.insuranceScopeTotal ?? 0,
        threshold: visit.coPaymentExemptionThreshold ?? null,
    } : null), [insured, visit.insuranceCoverageRate, visit.insuranceScopeTotal, visit.coPaymentExemptionThreshold]);
    const cost = useMemo(() => estimate(services, selectedIds, coverage), [services, selectedIds, coverage]);

    const toggle = (id) => {
        const target = services.find((s) => s.id === id);
        if (target && !hasOnDutyRoom(target)) return;
        setSelectedIds((current) => (
            current.includes(id) ? current.filter((item) => item !== id) : [...current, id]
        ));
    };

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
                items: orderItems(selectedIds),
            });
            setCreated(order);
        } catch (err) {
            setError(orderErrorMessage(err));
            // I1-D47: a room changed meanwhile; reload the rooms shown
            if (err?.errorResponse?.code === 'PARACLINICAL_ROOM_NOT_ELIGIBLE') reloadServices();
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

    if (created) {
        return <OrderCreated order={created} api={api} onDone={() => onCreated(created)} />;
    }

    return (
        <form noValidate onSubmit={handleSubmit} className="space-y-3">
            <div className="space-y-3">
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
                                onChange={changeFilter(setKeyword)}
                                placeholder="Tìm theo mã hoặc tên dịch vụ"
                                className={`${controlClass} w-full pl-9`}
                            />
                        </span>
                        <label htmlFor={typeId} className="sr-only">Loại dịch vụ</label>
                        <select id={typeId} value={type} onChange={changeFilter(setType)} className={controlClass}>
                            <option value="">Tất cả loại</option>
                            {Object.entries(TYPE_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                        </select>
                        <label htmlFor={groupId} className="sr-only">Nhóm dịch vụ</label>
                        <select id={groupId} value={group} onChange={changeFilter(setGroup)} className={controlClass}>
                            <option value="">Tất cả nhóm</option>
                            {groups.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
                        </select>
                    </div>

                    {!loading && (
                        <p aria-live="polite" data-testid="cls-count" className="mt-3 text-xs text-slate-500">
                            Hiển thị <strong className="text-slate-700">{rows.length}</strong>
                            {rows.length !== services.length && <> / {services.length}</>} dịch vụ
                            {selectedIds.length > 0 && <> · Đã chọn <strong className="text-teal-700">{selectedIds.length}</strong></>}
                        </p>
                    )}

                    <div className="mt-2 overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full min-w-[640px] text-xs">
                            <thead className="bg-slate-50">
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
                                {!loading && pageRows.map((service) => {
                                    const checked = selectedIds.includes(service.id);
                                    const available = hasOnDutyRoom(service);
                                    return (
                                        <tr
                                            key={service.id}
                                            className={
                                                !available
                                                    ? 'bg-slate-50/70 text-slate-400 opacity-60'
                                                    : checked
                                                        ? 'bg-teal-50/60'
                                                        : 'hover:bg-slate-50'
                                            }
                                            title={!available ? 'Phòng thực hiện hiện chưa có ca trực' : undefined}
                                        >
                                            <td className={`${tdClass} text-center`}>
                                                <input
                                                    type="checkbox"
                                                    checked={checked}
                                                    disabled={!available}
                                                    onChange={() => toggle(service.id)}
                                                    aria-label={`Chọn ${service.name}`}
                                                    title={!available ? 'Phòng thực hiện hiện chưa có ca trực' : undefined}
                                                    className="h-4 w-4 cursor-pointer accent-teal-700 disabled:cursor-not-allowed disabled:opacity-40"
                                                />
                                            </td>
                                            <td className={`${tdClass} whitespace-nowrap font-mono text-[11px] ${available ? 'text-slate-600' : 'text-slate-400'}`}>{service.code}</td>
                                            <td className={`${tdClass} font-medium ${available ? 'text-slate-800' : 'text-slate-500'}`}>
                                                <div className="flex flex-wrap items-center gap-1.5">
                                                    <span>{service.name}</span>
                                                    {!available && (
                                                        <span
                                                            className="inline-flex items-center rounded-md border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700"
                                                            title="Phòng thực hiện hiện chưa có ca trực"
                                                        >
                                                            Chưa có ca trực
                                                        </span>
                                                    )}
                                                </div>
                                            </td>
                                            <td className={`${tdClass} ${available ? 'text-slate-600' : 'text-slate-400'}`}>{service.serviceGroupName || '—'}</td>
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
                    {!loading && rows.length > 0 && (
                        <Pagination page={currentPage} pageCount={pageCount} total={rows.length} onChange={setPage} />
                    )}
                    <p className="mt-2 text-[11px] text-slate-400">Đơn vị: đồng</p>
                </section>

                <section aria-labelledby="cls-selected-title" className="min-w-0 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                    <h2 id="cls-selected-title" className="text-sm font-bold">Dịch vụ đã chọn ({cost.lines.length})</h2>

                    <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full min-w-[640px] text-xs">
                            <thead className="bg-slate-50">
                                <tr>
                                    <th scope="col" className={thClass}>Mã dịch vụ</th>
                                    <th scope="col" className={thClass}>Tên dịch vụ</th>
                                    <th scope="col" className={thClass}>Phòng thực hiện</th>
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
                                    <tr><td colSpan={insured ? 8 : 7} className="p-6 text-center text-slate-500">Chưa chọn dịch vụ nào.</td></tr>
                                )}
                                {cost.lines.map((line) => (
                                    <tr key={line.id}>
                                        <td className={`${tdClass} whitespace-nowrap font-mono text-[11px] text-slate-600`}>{line.code}</td>
                                        <td className={`${tdClass} font-medium text-slate-800`}>{line.name}</td>
                                        <td className={tdClass}>
                                            <AssignedRoom line={line} insured={insured} />
                                        </td>
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
                                    <th scope="row" colSpan={2} className={`${tdClass} text-left`}>Tổng cộng</th>
                                    <td />
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
                    disabled={submitting || blocked || selectedIds.length === 0}
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-teal-700 px-6 py-2.5 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {submitting || blocked
                        ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />
                        : <CheckCircle2 aria-hidden="true" className="h-4 w-4" />}
                    {submitting ? 'Đang tạo chỉ định…' : blocked ? 'Đang lưu hồ sơ khám…' : 'Xác nhận chỉ định'}
                </button>
            </div>
        </form>
    );
}

export default ParaclinicalOrderPanel;
