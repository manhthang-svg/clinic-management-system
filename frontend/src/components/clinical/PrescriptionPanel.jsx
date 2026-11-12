import { useEffect, useId, useState } from 'react';
import { AlertTriangle, LoaderCircle, Lock, Pill, Save, Search, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import defaultVisitApi from '../../apis/visitApi';
import { ConfirmModal } from '../ui/confirm-modal';
import {
    LIMITS, MSG, formFromResponse, newLine, prescriptionError, samePrescription, suggestedQuantity, toPayload,
    validatePrescription,
} from './prescriptionForm';

const inputClass = 'w-full rounded-lg border bg-white px-3 text-xs text-slate-700 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-50';

function FieldError({ children }) {
    if (!children) return null;
    return <span className="mt-1 block text-[11px] text-rose-600">{children}</span>;
}

function LineInput({ label, required = false, value, onChange, error, type = 'text', suffix, disabled, placeholder, wide = false }) {
    const id = useId();
    return (
        <div className={`min-w-0 ${wide ? 'sm:col-span-2' : ''}`}>
            <label htmlFor={id} className="block text-[11px] font-semibold text-slate-600">
                {label}{required && <span className="ml-0.5 text-rose-500">*</span>}
            </label>
            <span className="mt-1 flex items-center gap-1.5">
                <input
                    id={id}
                    type={type}
                    inputMode={type === 'number' ? 'numeric' : undefined}
                    min={type === 'number' ? 1 : undefined}
                    value={value}
                    disabled={disabled}
                    placeholder={placeholder}
                    onChange={(event) => onChange(event.target.value)}
                    aria-invalid={error ? 'true' : undefined}
                    className={`${inputClass} h-9 ${error ? 'border-rose-400' : 'border-slate-200'}`}
                />
                {suffix && <span className="whitespace-nowrap text-[11px] text-slate-400">{suffix}</span>}
            </span>
            <FieldError>{error}</FieldError>
        </div>
    );
}

/** One medicine line: catalog fields copied on save, the dosage entered by the Doctor (BR-107). */
function MedicineLine({ line, index, errors, disabled, onChange, onRemove }) {
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
    return (
        <li className="rounded-xl border border-slate-200 bg-white p-3">
            <div className="flex items-start gap-3">
                <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-teal-50 text-[11px] font-bold text-teal-700">{index + 1}</span>
                <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-slate-800">{line.name}</p>
                    <p className="text-[11px] text-slate-500">
                        {[line.activeIngredient, line.strength, line.route, line.unit && `Đơn vị: ${line.unit}`].filter(Boolean).join(' · ')}
                        {line.code && <span className="ml-1 font-mono text-slate-400">({line.code})</span>}
                    </p>
                </div>
                {!disabled && (
                    <button type="button" onClick={onRemove} aria-label={`Xóa ${line.name}`} className="rounded-md p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600">
                        <Trash2 className="h-4 w-4" />
                    </button>
                )}
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <LineInput label="Liều mỗi lần" required value={line.dosePerTime} onChange={update('dosePerTime')} error={errors[key('dosePerTime')]} disabled={disabled} placeholder="VD: 1 viên" />
                <LineInput label="Số lần/ngày" required type="number" value={line.timesPerDay} onChange={update('timesPerDay')} error={errors[key('timesPerDay')]} disabled={disabled} suffix="lần" />
                <LineInput label="Số ngày" required type="number" value={line.days} onChange={update('days')} error={errors[key('days')]} disabled={disabled} suffix="ngày" />
                <LineInput label="Số lượng" required type="number" value={line.quantity} onChange={update('quantity')} error={errors[key('quantity')]} disabled={disabled} suffix={line.unit} />
                <LineInput label="Cách dùng" required wide value={line.usage} onChange={update('usage')} error={errors[key('usage')]} disabled={disabled} placeholder="VD: Uống sau ăn sáng, chiều" />
                <LineInput label="Ghi chú" wide value={line.note} onChange={update('note')} disabled={disabled} />
            </div>
            <FieldError>{errors[key('medicine')]}</FieldError>
        </li>
    );
}

/** BR-33: active ordinary medicines of the catalog, searched by name, active ingredient or code. */
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
                    placeholder="Nhập tên thuốc, hoạt chất hoặc mã thuốc để tìm..."
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
                                <span className="ml-2 text-slate-500">{[medicine.activeIngredient, medicine.strength, medicine.route, medicine.unit].filter(Boolean).join(' · ')}</span>
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

const EMPTY = { lines: [], notes: '', advice: '' };

/** UC-59 Prescribe Medication: one ordinary prescription per visit; the record stays open after saving. */
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
    const [allergyWarning, setAllergyWarning] = useState(null);
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
    }, [api, visitId]);

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

    const submit = async (confirmAllergy = false) => {
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
            const saved = await api.savePrescription(visitId, toPayload(form, data?.version, confirmAllergy));
            const values = formFromResponse(saved);
            setData(saved);
            setForm(values);
            setSavedForm(values);
            setChecked(false);
            toast.success(MSG.SAVED); // MSG03
            onSaved?.(saved);
        } catch (error) {
            const result = prescriptionError(error);
            if (result.allergies) {
                setAllergyWarning(result.allergies); // AF-01, MSG26
            } else {
                setErrors(result.fields);
                setPageError(result.message);
            }
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return <div role="status" className="flex min-h-40 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white text-sm text-slate-500"><LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />Đang tải đơn thuốc…</div>;
    }
    if (loadError) {
        return <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{loadError}</p>;
    }

    const locked = Boolean(data?.sent) || !data?.conclusionSaved;
    return (
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
                            <li key={item.code} className={`rounded-md border px-2 py-1 text-[11px] ${index === 0 ? 'border-teal-200 bg-teal-50 text-teal-800' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>
                                <strong>{item.code}</strong> {item.name}{index === 0 && ' (bệnh chính)'}
                            </li>
                        ))}
                    </ul>
                )}
                <div className="flex flex-wrap items-center gap-2 pt-1 text-xs">
                    <span className="font-semibold text-slate-500">Dị ứng thuốc:</span>
                    {data?.allergies?.length
                        ? data.allergies.map((allergy) => (
                            <span key={allergy} className="inline-flex items-center gap-1 rounded-md border border-rose-300 bg-rose-50 px-2 py-0.5 text-[11px] font-bold text-rose-700">
                                <AlertTriangle aria-hidden="true" className="h-3 w-3" />Dị ứng: {allergy}
                            </span>
                        ))
                        : <span className="text-[11px] text-slate-400">Chưa ghi nhận dị ứng thuốc</span>}
                </div>
            </section>

            {!data?.conclusionSaved && <p role="status" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{MSG.CONCLUSION_REQUIRED}</p>}
            {data?.sent && <p role="status" className="flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"><Lock aria-hidden="true" className="h-4 w-4" />{MSG.SENT}</p>}

            <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                    <h2 className="flex items-center gap-2 text-sm font-bold"><Pill aria-hidden="true" className="h-4 w-4 text-teal-600" />Đơn thuốc</h2>
                    {data?.prescriptionCode && (
                        <span className="text-[11px] text-slate-500">
                            Mã đơn <strong className="font-mono text-slate-700">{data.prescriptionCode}</strong>
                            {data.prescribedAt && ` · lưu lúc ${formatDateTime(data.prescribedAt)}`}
                            {data.doctorName && ` · ${data.doctorName}`}
                        </span>
                    )}
                </div>

                {pageError && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-medium text-rose-800">{pageError}</p>}

                {!locked && (
                    <MedicineSearch
                        searchMedicines={api.searchMedicines}
                        chosenIds={form.lines.map((line) => line.medicineId)}
                        onPick={(medicine) => change({ ...form, lines: [...form.lines, newLine(medicine)] })}
                    />
                )}
                {!longTermAllowed && <p className="text-[11px] text-slate-500">{MSG.LONG_TERM}</p>}

                {form.lines.length === 0
                    ? <p className="rounded-lg border border-dashed border-slate-300 px-4 py-6 text-center text-xs text-slate-500">Chưa có thuốc nào trong đơn.</p>
                    : (
                        <ol className="space-y-2">
                            {form.lines.map((line, index) => (
                                <MedicineLine
                                    key={line.medicineId}
                                    line={line}
                                    index={index}
                                    errors={errors}
                                    disabled={locked}
                                    onChange={setLine(index)}
                                    onRemove={() => change({ ...form, lines: form.lines.filter((_, i) => i !== index) })}
                                />
                            ))}
                        </ol>
                    )}
                <FieldError>{errors.lines}</FieldError>

                <div className="grid gap-3 lg:grid-cols-2">
                    <NoteField label="Lưu ý" value={form.notes} onChange={(value) => change({ ...form, notes: value })} error={errors.notes} disabled={locked} />
                    <NoteField label="Lời dặn" value={form.advice} onChange={(value) => change({ ...form, advice: value })} error={errors.advice} disabled={locked} />
                </div>
            </section>

            {!locked && (
                <div className="flex justify-end">
                    <button
                        type="submit"
                        disabled={saving}
                        className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-teal-700 px-6 py-2.5 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}
                        {saving ? 'Đang lưu…' : 'Lưu đơn thuốc'}
                    </button>
                </div>
            )}

            <ConfirmModal
                isOpen={Boolean(allergyWarning)}
                title="Cảnh báo dị ứng thuốc"
                description={allergyWarning ? MSG.allergy(allergyWarning) : ''}
                confirmText="Vẫn kê thuốc này"
                cancelText="Đổi thuốc"
                variant="destructive"
                onConfirm={() => { setAllergyWarning(null); void submit(true); }}
                onClose={() => setAllergyWarning(null)}
            />
        </form>
    );
}

export default PrescriptionPanel;
