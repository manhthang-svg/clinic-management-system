import { useEffect, useId, useRef, useState } from 'react';
import {
    AlertTriangle, ArrowDown, ArrowUp, History, LoaderCircle, Lock, Pencil, Pill, RotateCcw, Save, Search, Send, Trash2,
} from 'lucide-react';
import { toast } from 'sonner';

import defaultVisitApi from '../../apis/visitApi';
import { BUTTON } from './buttonStyles';
import {
    LIMITS, MSG, formFromResponse, newLine, prescriptionError, samePrescription, suggestedQuantity, toPayload,
    validatePrescription,
} from './prescriptionForm';
import { PrescriptionSubmitDialog } from './PrescriptionSubmitDialog';
import { PrescriptionSubmissionHistory } from './PrescriptionSubmissionHistory';
import { SUBMIT_MSG } from './prescriptionSubmission';

const inputClass = 'w-full rounded-lg border bg-white px-2.5 text-sm text-slate-700 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-50';

function FieldError({ children }) {
    if (!children) return null;
    return <span className="mt-1 block text-xs text-rose-600">{children}</span>;
}

const cellClass = 'border border-slate-200 px-2 py-1.5 align-top';

/**
 * An input inside a grid cell: flat until focused, red when invalid. The column header names it on screen; the
 * visually hidden label names it with the medicine, and the message below the row describes it.
 */
function CellInput({ label, value, onChange, invalid, describedBy, type = 'text', disabled, placeholder, align = 'left' }) {
    const id = useId();
    return (
        <>
            <label htmlFor={id} className="sr-only">{label}</label>
            <input
                id={id}
                type={type}
                inputMode={type === 'number' ? 'numeric' : undefined}
                min={type === 'number' ? 1 : undefined}
                step={type === 'number' ? 1 : undefined}
                value={value}
                disabled={disabled}
                placeholder={placeholder}
                onChange={(event) => onChange(event.target.value)}
                aria-invalid={invalid ? 'true' : undefined}
                aria-describedby={invalid ? describedBy : undefined}
                className={`h-8 w-full rounded border px-1.5 text-sm tabular-nums text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-teal-500 focus:bg-white focus:ring-2 focus:ring-teal-100 disabled:bg-transparent ${align === 'right' ? 'text-right' : ''} ${invalid ? 'border-rose-400 bg-rose-50' : 'border-transparent bg-slate-50 hover:border-slate-300'}`}
            />
        </>
    );
}

const LINE_FIELDS = [
    ['medicine', 'Thuốc'], ['dosePerTime', 'Liều mỗi lần'], ['timesPerDay', 'Số lần/ngày'], ['days', 'Số ngày'],
    ['quantity', 'Số lượng'], ['usage', 'Cách dùng'],
];

/**
 * One medicine of the prescription (BR-59-04) as one grid row, like a hospital prescription grid; the catalog
 * columns are read-only. Errors and the more-than-30-days notice go in a row below, so the columns stay narrow.
 */
function MedicineLine({ line, index, count, errors, disabled, longTermAllowed, onChange, onMove, onRemove }) {
    const messagesId = useId();
    const key = (field) => `lines.${index}.${field}`;
    const update = (field) => (value) => {
        const next = { ...line, [field]: value };
        if (field === 'quantity') next.quantityEdited = value !== '';
        // The quantity follows dose × times × days until the Doctor types one.
        if (field !== 'quantity' && !next.quantityEdited) {
            const suggested = suggestedQuantity(next);
            next.quantity = suggested == null ? '' : String(suggested);
        }
        onChange(next);
    };
    const label = (field) => `${field} – ${line.name}`;
    const messages = LINE_FIELDS
        .filter(([field]) => errors[key(field)])
        .map(([field, name]) => ({ key: field, name, text: errors[key(field)], tone: 'error' }));
    // AF-05 / EF-04: more than 30 days is explained only when it is typed, unless the days field has an error.
    if (Number(line.days) > LIMITS.daysWithoutLongTerm && !errors[key('days')]) {
        messages.push(longTermAllowed
            ? { key: 'longTerm', name: 'Số ngày', text: MSG.LONG_TERM_ALLOWED, tone: 'info' }
            : { key: 'longTerm', name: 'Số ngày', text: MSG.LONG_TERM, tone: 'warning' });
    }
    const input = (field, props) => (
        <CellInput
            label={label(props.name)}
            value={line[field]}
            onChange={update(field)}
            invalid={Boolean(errors[key(field)])}
            describedBy={messagesId}
            disabled={disabled}
            {...props}
        />
    );
    const toneClass = { error: 'text-rose-700', warning: 'text-amber-800', info: 'text-teal-800' };
    return (
        <tbody>
            <tr className="hover:bg-slate-50/60">
                <td className={`${cellClass} text-center tabular-nums text-slate-600`}>{index + 1}</td>
                <td className={cellClass}>
                    <span className="block font-medium text-slate-900">{line.name}</span>
                    {line.code && <span className="block text-xs text-slate-500">{line.code}</span>}
                </td>
                <td className={cellClass}>{[line.activeIngredient, line.strength].filter(Boolean).join(' ')}</td>
                <td className={`${cellClass} text-center`}>{line.unit}</td>
                <td className={cellClass}>{line.route}</td>
                <td className={cellClass}>{input('dosePerTime', { name: 'Liều mỗi lần', placeholder: '1 viên' })}</td>
                <td className={cellClass}>{input('timesPerDay', { name: 'Số lần/ngày', type: 'number', align: 'right' })}</td>
                <td className={cellClass}>{input('days', { name: 'Số ngày', type: 'number', align: 'right' })}</td>
                <td className={cellClass}>{input('quantity', { name: 'Số lượng', type: 'number', align: 'right' })}</td>
                <td className={cellClass}>{input('usage', { name: 'Cách dùng', placeholder: 'Uống sau ăn sáng, tối' })}</td>
                <td className={cellClass}>{input('note', { name: 'Ghi chú' })}</td>
                <td className={`${cellClass} whitespace-nowrap text-center`}>
                    {!disabled && (
                        <span className="inline-flex items-center gap-0.5">
                            <button type="button" onClick={() => onMove(-1)} disabled={index === 0} aria-label={`Đưa ${line.name} lên`} className="rounded p-1 text-slate-600 hover:bg-slate-100 disabled:opacity-30">
                                <ArrowUp aria-hidden="true" className="h-4 w-4" />
                            </button>
                            <button type="button" onClick={() => onMove(1)} disabled={index === count - 1} aria-label={`Đưa ${line.name} xuống`} className="rounded p-1 text-slate-600 hover:bg-slate-100 disabled:opacity-30">
                                <ArrowDown aria-hidden="true" className="h-4 w-4" />
                            </button>
                            <button type="button" onClick={onRemove} aria-label={`Xóa ${line.name}`} className="rounded p-1 text-slate-600 hover:bg-rose-50 hover:text-rose-600">
                                <Trash2 aria-hidden="true" className="h-4 w-4" />
                            </button>
                        </span>
                    )}
                </td>
            </tr>
            {messages.length > 0 && (
                <tr>
                    <td className={cellClass} />
                    <td colSpan={11} className={`${cellClass} bg-slate-50/60`}>
                        <ul id={messagesId} className="space-y-0.5 text-xs">
                            {messages.map((message) => (
                                <li key={message.key} className={toneClass[message.tone]}>
                                    <span className="font-semibold">{message.name}: </span><span>{message.text}</span>
                                </li>
                            ))}
                        </ul>
                    </td>
                </tr>
            )}
        </tbody>
    );
}

/** BR-59-03: active ordinary medicines, searched by name, active ingredient, strength, code or registration number. */
function MedicineSearch({ searchMedicines, chosenIds, onPick }) {
    const id = useId();
    const [query, setQuery] = useState('');
    const [options, setOptions] = useState([]);

    useEffect(() => {
        const keyword = query.trim();
        if (keyword.length < 2) return undefined;
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            searchMedicines(keyword, controller.signal)
                .then((result) => setOptions(result ?? []))
                .catch((err) => { if (err?.name !== 'AbortError') setOptions([]); });
        }, 300);
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [query, searchMedicines]);

    const pick = (medicine) => {
        onPick(medicine);
        setQuery('');
        setOptions([]);
    };

    return (
        <div>
            <label htmlFor={id} className="block text-xs font-semibold text-slate-700">Thêm thuốc</label>
            <span className="relative mt-1 block">
                <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                    id={id}
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Nhập tên thuốc, hoạt chất, hàm lượng, mã thuốc hoặc số đăng ký để tìm..."
                    className={`${inputClass} h-10 border-slate-200 pl-9`}
                />
            </span>
            {query.trim().length >= 2 && options.length > 0 && (
                <div role="listbox" aria-label="Kết quả tìm thuốc" className="mt-1 max-h-60 overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-lg">
                    {options.map((medicine) => {
                        const taken = chosenIds.includes(medicine.id);
                        return (
                            <button
                                key={medicine.id}
                                type="button"
                                role="option"
                                aria-selected={false}
                                disabled={taken}
                                onClick={() => pick(medicine)}
                                className="block w-full rounded-md px-3 py-2 text-left text-xs hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-40"
                            >
                                <span className="font-semibold text-slate-800">{medicine.name}</span>
                                <span className="ml-2 text-slate-500">{[medicine.activeIngredient, medicine.strength, medicine.route, medicine.unit, medicine.registration && `SĐK ${medicine.registration}`].filter(Boolean).join(' · ')}</span>
                                {taken && <span className="ml-2 text-teal-700">(đã có trong đơn)</span>}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

function NoteField({ label, value, onChange, error, disabled }) {
    const id = useId();
    return (
        <div>
            <label htmlFor={id} className="block text-xs font-semibold text-slate-700">{label}</label>
            <textarea
                id={id}
                rows={2}
                maxLength={LIMITS.notes}
                value={value}
                disabled={disabled}
                onChange={(event) => onChange(event.target.value)}
                aria-invalid={error ? 'true' : undefined}
                className={`${inputClass} mt-1 resize-y py-2 ${error ? 'border-rose-400' : 'border-slate-200'}`}
            />
            <FieldError>{error}</FieldError>
        </div>
    );
}

function formatDateTime(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

/** "2026-11-10" → "10/11/2026" without a time-zone shift. */
function formatDate(value) {
    if (!value) return '';
    const [year, month, day] = String(value).split('-');
    return `${day}/${month}/${year}`;
}

/** BR-59-07: a child under 72 months needs the age in months, weight and accompanying person; missing ones are marked. */
function ChildData({ child }) {
    const missing = (label) => <span className="font-semibold text-amber-700">Chưa có — {MSG.CHILD_DATA_MISSING(label)}</span>;
    return (
        <section aria-label="Thông tin trẻ dưới 72 tháng tuổi" className="space-y-1 rounded-xl border border-sky-200 bg-sky-50 p-4 text-xs text-slate-700">
            <h2 className="text-sm font-bold text-sky-900">Trẻ dưới 72 tháng tuổi</h2>
            <p><span className="font-semibold">Tuổi:</span> {child.ageMonths} tháng</p>
            <p><span className="font-semibold">Cân nặng:</span> {child.weightKg != null ? `${Number(child.weightKg).toLocaleString('vi-VN')} kg` : missing('cân nặng')}</p>
            <p><span className="font-semibold">Người đưa trẻ đến khám:</span> {child.accompanyingPerson || missing('thông tin người đưa trẻ đến khám')}</p>
        </section>
    );
}

const EMPTY = { lines: [], notes: '', advice: '' };

/** The lines with the one at {@code from} moved to {@code to}; the saved order is the printed order. */
function moved(lines, from, to) {
    if (to < 0 || to >= lines.length) return lines;
    const next = [...lines];
    const [line] = next.splice(from, 1);
    next.splice(to, 0, line);
    return next;
}

/** UC-91 step 3: the latest send and its answer. */
function SubmissionResult({ data, onResend, onEdit, canResend, dirty }) {
    const submission = data?.lastSubmission;
    if (!submission) return null;
    if (submission.result === 'ACCEPTED') {
        return (
            <section role="status" aria-label="Kết quả gửi đơn thuốc" className="space-y-1 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
                <p className="font-semibold">{SUBMIT_MSG.ACCEPTED(data.prescriptionCode)}</p>
            </section>
        );
    }
    if (submission.result === 'REJECTED') {
        return (
            <section role="alert" aria-label="Kết quả gửi đơn thuốc" className="space-y-1 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                <p className="font-semibold">{SUBMIT_MSG.REJECTED}</p>
                <p className="text-xs">{SUBMIT_MSG.REJECTED_ATTEMPT(submission.attemptNo, formatDateTime(submission.respondedAt ?? submission.submittedAt))}</p>
                {/* UC-92 step 1: the errors of the last rejection stay visible while the Doctor corrects the prescription. */}
                <ul className="list-inside list-disc text-xs">
                    {submission.errors.map((error) => <li key={error}>{error}</li>)}
                </ul>
                <p className="text-xs text-rose-900">{SUBMIT_MSG.CORRECT_ELSEWHERE}</p>
                <div className="flex flex-wrap items-center gap-2 pt-1">
                    <button type="button" onClick={onEdit} className={BUTTON.smallRose}>
                        <Pencil aria-hidden="true" className="h-3.5 w-3.5" />Sửa đơn
                    </button>
                    <button type="button" onClick={onResend} disabled={!canResend} className={BUTTON.smallRose}>
                        <RotateCcw aria-hidden="true" className="h-3.5 w-3.5" />Gửi lại
                    </button>
                    {dirty && <span className="text-xs">{SUBMIT_MSG.SAVE_BEFORE_RESEND}</span>}
                </div>
            </section>
        );
    }
    return (
        <section role="alert" aria-label="Kết quả gửi đơn thuốc" className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <span>{SUBMIT_MSG.NO_ANSWER}</span>
            <button type="button" onClick={onResend} disabled={!canResend} className={BUTTON.smallAmber}>
                <RotateCcw aria-hidden="true" className="h-3.5 w-3.5" />Gửi lại
            </button>
        </section>
    );
}

/**
 * UC-59 Prescribe Medication: one ordinary prescription per visit; the record stays open after saving. UC-90: a saved
 * prescription is sent to the national prescription system (no digital signature, BR-47); UC-91 shows the answer.
 */
export function PrescriptionPanel({ visitId, api = defaultVisitApi, onSaved }) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [form, setForm] = useState(EMPTY);
    const [savedForm, setSavedForm] = useState(EMPTY);
    const [errors, setErrors] = useState({});
    const [pageError, setPageError] = useState('');
    const [saving, setSaving] = useState(false);
    const [checked, setChecked] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);
    // UC-90: the send dialog is open.
    const [sending, setSending] = useState(false);
    // UC-91: the submission history, opened from "Kết quả gửi".
    const [showHistory, setShowHistory] = useState(false);
    // UC-59 AF-03: the MSG109 confirmation, and the deletion in progress.
    const [confirmDelete, setConfirmDelete] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const titleId = useId();
    const prescriptionRef = useRef(null);
    const dirty = !samePrescription(form, savedForm);

    useEffect(() => {
        const controller = new AbortController();
        api.getPrescription(visitId, controller.signal)
            .then((response) => {
                const loaded = formFromResponse(response);
                setData(response);
                setForm(loaded);
                setSavedForm(loaded);
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') setLoadError(prescriptionError(error).message);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [api, visitId, reloadKey]);

    // MSG38: warn before leaving the page with unsaved changes.
    useEffect(() => {
        if (!dirty) return undefined;
        const warn = (event) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const longTermAllowed = Boolean(data?.longTermAllowed);
    const change = (next) => {
        setForm(next);
        if (checked) setErrors(validatePrescription(next, longTermAllowed));
    };
    const setLine = (index) => (line) => change({ ...form, lines: form.lines.map((item, i) => (i === index ? line : item)) });

    const submit = async () => {
        const found = validatePrescription(form, longTermAllowed);
        setChecked(true);
        setErrors(found);
        if (Object.keys(found).length) {
            setPageError(found.lines ?? MSG.CHECK_FIELDS);
            return;
        }
        setSaving(true);
        setPageError('');
        try {
            const saved = await api.savePrescription(visitId, toPayload(form, data?.version));
            const values = formFromResponse(saved);
            setData(saved);
            setForm(values);
            setSavedForm(values);
            setChecked(false);
            toast.success(MSG.SAVED); // MSG03
            onSaved?.(saved);
        } catch (error) {
            const result = prescriptionError(error);
            setErrors(result.fields);
            setPageError(result.message);
        } finally {
            setSaving(false);
        }
    };

    // UC-59 AF-03: remove a prescription that was never sent; the form starts again empty.
    const remove = async () => {
        setDeleting(true);
        setPageError('');
        try {
            await api.deletePrescription(data.prescriptionId);
            setConfirmDelete(false);
            setShowHistory(false);
            toast.success(MSG.DELETED);
            setReloadKey((key) => key + 1);
        } catch (error) {
            setConfirmDelete(false);
            setPageError(prescriptionError(error).message);
        } finally {
            setDeleting(false);
        }
    };

    if (loading) {
        return <div role="status" className="flex min-h-40 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white text-sm text-slate-500"><LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />Đang tải đơn thuốc…</div>;
    }
    if (loadError) {
        return <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{loadError}</p>;
    }

    // EF-07: without the link codes the form is shown but disabled.
    const linkCodeMessages = [
        data?.prescriberLinkCodeMissing && MSG.LINK_CODE_MISSING(MSG.PRESCRIBER),
        data?.facilityLinkCodeMissing && MSG.LINK_CODE_MISSING(MSG.FACILITY),
    ].filter(Boolean);
    const locked = Boolean(data?.sent) || !data?.conclusionSaved || linkCodeMessages.length > 0;
    const accepted = data?.status ? data.status === 'ACCEPTED' : Boolean(data?.sent);
    // UC-90 step 1: a saved, unchanged prescription that is pending, rejected or sent without an answer.
    // BR-59-17: the facility must have added the Doctor to the national system (UC-93).
    const canSend = Boolean(data?.prescriptionId) && !accepted && !dirty && Boolean(data?.conclusionSaved)
        && linkCodeMessages.length === 0 && !data?.prescriberNotAddedToFacility;
    // UC-59 AF-03 (P03): only a prescription that was never sent.
    const canDelete = Boolean(data?.prescriptionId) && data?.status === 'PENDING';
    // UC-92 step 1: "Sửa đơn" brings the Doctor to the first field of the prescription, which stays editable.
    const editPrescription = () => {
        const section = prescriptionRef.current;
        section?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
        section?.querySelector('input:not([disabled]), textarea:not([disabled])')?.focus();
    };
    const finishSending = (prescription) => {
        setSending(false);
        if (!prescription) {
            setReloadKey((key) => key + 1); // AF-02: reload to show the attempt without an answer.
            return;
        }
        const values = formFromResponse(prescription);
        setData(prescription);
        setForm(values);
        setSavedForm(values);
        if (prescription.lastSubmission?.result === 'ACCEPTED') {
            toast.success(SUBMIT_MSG.ACCEPTED(prescription.prescriptionCode));
        }
    };
    const closeSending = () => {
        setSending(false);
        setReloadKey((key) => key + 1); // A refused send may have copied the diagnoses, which changes the version.
    };
    return (
        <>
        <form
            noValidate
            aria-label="Kê đơn thuốc"
            onSubmit={(event) => { event.preventDefault(); void submit(); }}
            className="space-y-3"
        >
            <section className="space-y-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <h2 className="text-sm font-bold">Chẩn đoán (theo kết luận)</h2>
                <p className="text-xs text-slate-700">{data?.diagnosis || '—'}</p>
                {data?.diagnoses?.length > 0 && (
                    <ul className="flex flex-wrap gap-2">
                        {data.diagnoses.map((item, index) => (
                            <li key={item.code} className={`rounded-md border px-2 py-1 text-xs ${index === 0 ? 'border-teal-200 bg-teal-50 text-teal-800' : 'border-slate-200 bg-slate-50 text-slate-700'}`}>
                                <strong>{item.code}</strong> {item.name}{index === 0 && ' (bệnh chính)'}
                            </li>
                        ))}
                    </ul>
                )}
                <div className="flex flex-wrap items-center gap-2 pt-1 text-xs">
                    <span className="font-semibold text-slate-500">Dị ứng thuốc:</span>
                    {data?.allergies?.length
                        ? data.allergies.map((allergy) => (
                            <span key={allergy} className="inline-flex items-center gap-1 rounded-md border border-rose-300 bg-rose-50 px-2 py-0.5 text-xs font-bold text-rose-700">
                                <AlertTriangle aria-hidden="true" className="h-3 w-3" />{allergy}
                            </span>
                        ))
                        : <span className="text-xs text-slate-500">Chưa ghi nhận dị ứng thuốc</span>}
                </div>
                <p className="text-xs">
                    <span className="font-semibold text-slate-500">Ngày tái khám:</span>{' '}
                    {data?.followUpDate ? formatDate(data.followUpDate) : <span className="text-slate-500">Chưa hẹn tái khám</span>}
                </p>
            </section>

            {data?.child && <ChildData child={data.child} />}

            {!data?.conclusionSaved && <p role="status" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{MSG.CONCLUSION_REQUIRED}</p>}
            {linkCodeMessages.map((message) => <p key={message} role="status" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{message}</p>)}
            {data?.prescriptionId && !accepted && data?.prescriberNotAddedToFacility && linkCodeMessages.length === 0 && (
                <p role="status" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{SUBMIT_MSG.NOT_ADDED}</p>
            )}
            <SubmissionResult
                data={data}
                canResend={canSend}
                dirty={dirty}
                onResend={() => setSending(true)}
                onEdit={editPrescription}
            />
            {accepted && <p role="status" className="flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"><Lock aria-hidden="true" className="h-4 w-4" />{MSG.SENT}</p>}

            <section ref={prescriptionRef} aria-labelledby={`${titleId}`} className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="min-w-0">
                        <h2 id={titleId} className="flex items-center gap-2 text-base font-bold"><Pill aria-hidden="true" className="h-4 w-4 text-teal-600" />Đơn thuốc</h2>
                        {data?.prescriptionCode
                            ? (
                                <p className="mt-0.5 text-xs text-slate-600">
                                    Mã đơn thuốc <strong className="font-mono text-sm text-slate-900">{data.prescriptionCode}</strong>
                                    {data.prescribedAt && ` · kê lúc ${formatDateTime(data.prescribedAt)}`}
                                    {data.doctorName && ` · ${data.doctorName}`}
                                </p>
                            )
                            : <p className="mt-0.5 text-xs text-slate-500">Mã đơn thuốc được tạo khi lưu đơn lần đầu.</p>}
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        {!locked && (
                            <button
                                type="submit"
                                disabled={saving}
                                className={BUTTON.primary}
                            >
                                {saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}
                                {saving ? 'Đang lưu…' : 'Lưu đơn thuốc'}
                            </button>
                        )}
                        {canSend && (
                            <button
                                type="button"
                                onClick={() => setSending(true)}
                                className={BUTTON.outline}
                            >
                                <Send aria-hidden="true" className="h-4 w-4" />Gửi đơn
                            </button>
                        )}
                        {data?.lastSubmission && (
                            <button
                                type="button"
                                onClick={() => setShowHistory((open) => !open)}
                                aria-expanded={showHistory}
                                className={BUTTON.neutral}
                            >
                                <History aria-hidden="true" className="h-4 w-4" />Kết quả gửi
                            </button>
                        )}
                        {canDelete && (
                            <button
                                type="button"
                                onClick={() => setConfirmDelete(true)}
                                disabled={deleting || confirmDelete}
                                className={BUTTON.danger}
                            >
                                <Trash2 aria-hidden="true" className="h-4 w-4" />Xóa đơn
                            </button>
                        )}
                    </div>
                </div>
                {data?.prescriptionId && !accepted && dirty && <p className="text-xs text-slate-500">Lưu đơn thuốc trước khi gửi.</p>}

                {confirmDelete && (
                    <div role="alertdialog" aria-label="Xóa đơn thuốc" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm">
                        <p className="text-rose-900">{MSG.DELETE_CONFIRM(data.prescriptionCode)}</p>
                        <div className="mt-2 flex justify-end gap-2">
                            <button type="button" onClick={() => setConfirmDelete(false)} disabled={deleting} className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 font-semibold text-slate-600 disabled:opacity-50">Hủy</button>
                            <button type="button" onClick={() => { void remove(); }} disabled={deleting} className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-3 py-1.5 font-semibold text-white hover:bg-rose-700 disabled:opacity-50">
                                {deleting && <LoaderCircle aria-hidden="true" className="h-3.5 w-3.5 animate-spin" />}
                                {deleting ? 'Đang xóa…' : 'Xóa đơn'}
                            </button>
                        </div>
                    </div>
                )}

                {showHistory && data?.prescriptionId && (
                    <section aria-label="Kết quả gửi" className="space-y-2">
                        <h3 className="text-sm font-bold">Kết quả gửi</h3>
                        <PrescriptionSubmissionHistory key={data.version} prescriptionId={data.prescriptionId} api={api} />
                    </section>
                )}

                {pageError && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800">{pageError}</p>}

                {!locked && (
                    <MedicineSearch
                        searchMedicines={api.searchMedicines}
                        chosenIds={form.lines.map((line) => line.medicineId)}
                        onPick={(medicine) => change({ ...form, lines: [...form.lines, newLine(medicine)] })}
                    />
                )}

                {form.lines.length === 0
                    ? <p className="rounded-lg border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-500">Chưa có thuốc nào trong đơn.</p>
                    : (
                        <div className="space-y-1.5">
                        <p className="text-xs text-slate-600">Liều mỗi lần, số lần/ngày, số ngày, số lượng và cách dùng là bắt buộc; ghi chú không bắt buộc.</p>
                        <div className="overflow-x-auto border border-slate-200">
                            <table className="w-full min-w-[80rem] table-fixed border-collapse text-left text-sm text-slate-800">
                                <caption className="sr-only">Các thuốc trong đơn</caption>
                                <colgroup>
                                    <col className="w-10" />
                                    <col className="w-48" />
                                    <col className="w-40" />
                                    <col className="w-16" />
                                    <col className="w-24" />
                                    <col className="w-32" />
                                    <col className="w-20" />
                                    <col className="w-20" />
                                    <col className="w-20" />
                                    <col className="w-44" />
                                    <col className="w-28" />
                                    <col className="w-24" />
                                </colgroup>
                                <thead className="bg-slate-100 text-xs font-semibold text-slate-700">
                                    <tr>
                                        {['STT', 'Tên thuốc', 'Hoạt chất', 'ĐVT', 'Đường dùng', 'Liều mỗi lần', 'Lần/ngày', 'Số ngày', 'SL', 'Cách dùng', 'Ghi chú'].map((title) => (
                                            <th key={title} scope="col" className="border border-slate-200 px-2 py-2 text-center">{title}</th>
                                        ))}
                                        <th scope="col" className="border border-slate-200 px-2 py-2"><span className="sr-only">Thao tác</span></th>
                                    </tr>
                                </thead>
                                {form.lines.map((line, index) => (
                                    <MedicineLine
                                        key={line.medicineId}
                                        line={line}
                                        index={index}
                                        count={form.lines.length}
                                        errors={errors}
                                        disabled={locked}
                                        longTermAllowed={longTermAllowed}
                                        onChange={setLine(index)}
                                        onMove={(delta) => change({ ...form, lines: moved(form.lines, index, index + delta) })}
                                        onRemove={() => change({ ...form, lines: form.lines.filter((_, i) => i !== index) })}
                                    />
                                ))}
                            </table>
                        </div>
                        </div>
                    )}
                <FieldError>{errors.lines}</FieldError>

                <div className="grid gap-3 lg:grid-cols-2">
                    <NoteField label="Lưu ý" value={form.notes} onChange={(value) => change({ ...form, notes: value })} error={errors.notes} disabled={locked} />
                    <NoteField label="Lời dặn" value={form.advice} onChange={(value) => change({ ...form, advice: value })} error={errors.advice} disabled={locked} />
                </div>
            </section>
        </form>
        {sending && data && (
            <PrescriptionSubmitDialog
                prescription={data}
                api={api}
                onClose={closeSending}
                onFinished={finishSending}
            />
        )}
        </>
    );
}

export default PrescriptionPanel;
