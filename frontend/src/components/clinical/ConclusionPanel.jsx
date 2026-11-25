import { useEffect, useId, useState } from 'react';
import { FileCheck2, LoaderCircle, Lock, Save, X } from 'lucide-react';
import { toast } from 'sonner';

import defaultVisitApi from '../../apis/visitApi';
import { ConfirmModal } from '../ui/confirm-modal';
import { ConfirmConclusionSection } from './ConfirmConclusionSection';
import {
    EMPTY_CONCLUSION, MSG, conclusionError, formFromResponse, sameConclusion, toPayload,
    validateConclusion,
} from './conclusionForm';

const inputClass = 'w-full rounded-lg border bg-white px-3 py-2 text-xs font-normal leading-5 text-slate-700 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-50';

function FieldError({ id, children }) {
    if (!children) return null;
    return <span id={id} className="mt-1 block text-[11px] font-normal text-rose-600">{children}</span>;
}

/** A free-text field with no length limit; the corner shows how many characters were typed. */
function TextField({ label, required = false, value, onChange, rows = 2, error, placeholder }) {
    const id = useId();
    const errorId = `${id}-error`;
    return (
        <div className="min-w-0">
            <label htmlFor={id} className="block text-xs font-semibold text-slate-700">
                {label}{required && <span className="ml-0.5 text-rose-500">*</span>}
            </label>
            <span className="relative mt-1 block">
                <textarea
                    id={id}
                    rows={rows}
                    value={value}
                    placeholder={placeholder}
                    onChange={(event) => onChange(event.target.value)}
                    aria-invalid={error ? 'true' : undefined}
                    aria-describedby={error ? errorId : undefined}
                    className={`${inputClass} resize-y pr-12 ${error ? 'border-rose-400' : 'border-slate-200'}`}
                />
                <span aria-hidden="true" className="absolute bottom-2 right-2 text-[10px] text-slate-400">{value.length}</span>
            </span>
            <FieldError id={errorId}>{error}</FieldError>
        </div>
    );
}

/** BR-31, BR-46: ICD-10 codes searched by code or name among active codes; one (main) or several (secondary). */
function IcdPicker({ label, required = false, multiple = false, value, onChange, disabled, error, searchIcd, exclude = [] }) {
    const id = useId();
    const errorId = `${id}-error`;
    const [query, setQuery] = useState('');
    const [options, setOptions] = useState([]);
    const selected = multiple ? value : (value ? [value] : []);

    useEffect(() => {
        const keyword = query.trim();
        if (keyword.length < 2) return undefined;
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            searchIcd(keyword, controller.signal)
                .then((result) => setOptions(result ?? []))
                .catch((err) => { if (err?.name !== 'AbortError') setOptions([]); });
        }, 300);
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [query, searchIcd]);

    const add = (option) => {
        if (!option || selected.some((item) => item.code === option.code)) return;
        onChange(multiple ? [...value, option] : option);
        setQuery('');
        setOptions([]);
    };
    const remove = (code) => onChange(multiple ? value.filter((item) => item.code !== code) : null);
    const showOptions = !disabled && query.trim().length >= 2 && options.length > 0;
    const canAdd = multiple || selected.length === 0;

    return (
        <div className="min-w-0">
            <label htmlFor={id} className="block text-xs font-semibold text-slate-700">
                {label}{required && <span className="ml-0.5 text-rose-500">*</span>}
                {disabled && <Lock aria-hidden="true" className="ml-1 inline h-3 w-3 text-slate-400" />}
            </label>
            {canAdd && !disabled && (
                <input
                    id={id}
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && options.length) {
                            event.preventDefault();
                            add(options.find((option) => !exclude.includes(option.code)));
                        }
                    }}
                    placeholder="Nhập mã hoặc tên bệnh để tìm..."
                    aria-invalid={error ? 'true' : undefined}
                    aria-describedby={error ? errorId : undefined}
                    className={`${inputClass} mt-1 h-10 ${error ? 'border-rose-400' : 'border-slate-200'}`}
                />
            )}
            {showOptions && (
                <div role="listbox" aria-label={`Kết quả tìm ${label}`} className="mt-1 max-h-52 overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-lg">
                    {options.map((option) => {
                        const taken = selected.some((item) => item.code === option.code) || exclude.includes(option.code);
                        return (
                            <button
                                key={option.code}
                                type="button"
                                role="option"
                                aria-selected={false}
                                disabled={taken}
                                onClick={() => add(option)}
                                className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-xs hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-40"
                            >
                                <strong className="w-20 shrink-0 text-teal-700">{option.code}</strong>
                                <span>{option.name}</span>
                            </button>
                        );
                    })}
                </div>
            )}
            <div className="mt-2 space-y-2">
                {selected.map((item) => (
                    <div key={item.code} className="flex items-center gap-3 rounded-lg border border-teal-100 bg-teal-50 px-3 py-2 text-xs">
                        <strong className="w-20 shrink-0 text-teal-800">{item.code}</strong>
                        <span className="min-w-0 flex-1 text-slate-700">{item.name}</span>
                        {!disabled && (
                            <button type="button" onClick={() => remove(item.code)} aria-label={`Xóa ${item.code}`} className="rounded p-1 text-slate-400 hover:bg-white hover:text-rose-600">
                                <X className="h-4 w-4" />
                            </button>
                        )}
                    </div>
                ))}
                {disabled && selected.length === 0 && <p className="text-xs text-slate-400">—</p>}
            </div>
            <FieldError id={errorId}>{error}</FieldError>
        </div>
    );
}

function formatDateTime(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

/**
 * UC-56 Conclude Examination: the conclusion form of the visit; the record stays open after saving. UC-58: below
 * it, "Xác nhận kết luận" closes the record.
 */
export function ConclusionPanel({ visitId, api = defaultVisitApi, onSaved, onClosed, onOpenPrescription }) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [form, setForm] = useState(EMPTY_CONCLUSION);
    const [savedForm, setSavedForm] = useState(EMPTY_CONCLUSION);
    const [errors, setErrors] = useState({});
    const [pageError, setPageError] = useState('');
    const [saving, setSaving] = useState(false);
    const [discouragedCode, setDiscouragedCode] = useState(null);
    const [checked, setChecked] = useState(false);
    const dirty = !sameConclusion(form, savedForm);

    useEffect(() => {
        const controller = new AbortController();
        api.getConclusion(visitId, controller.signal)
            .then((response) => {
                const loaded = formFromResponse(response);
                setData(response);
                setForm(loaded);
                setSavedForm(loaded);
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') setLoadError(conclusionError(error).message);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [api, visitId]);

    // AF-03 (MSG38): warn before leaving the page with unsaved changes.
    useEffect(() => {
        if (!dirty) return undefined;
        const warn = (event) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const set = (key) => (value) => {
        const next = { ...form, [key]: value };
        setForm(next);
        if (checked) setErrors(validateConclusion(next));
    };

    const submit = async (confirmDiscouragedMain = false) => {
        const found = validateConclusion(form);
        setChecked(true);
        setErrors(found);
        if (Object.keys(found).length) {
            setPageError(MSG.CHECK_FIELDS);
            return;
        }
        setSaving(true);
        setPageError('');
        try {
            const saved = await api.saveConclusion(visitId, toPayload(form, data?.version, confirmDiscouragedMain));
            const values = formFromResponse(saved);
            setData(saved);
            setForm(values);
            setSavedForm(values);
            setChecked(false);
            toast.success(MSG.SAVED); // MSG03
            onSaved?.(saved);
        } catch (error) {
            const result = conclusionError(error);
            if (result.discouragedCode) {
                setDiscouragedCode(result.discouragedCode); // AF-01, MSG66
            } else {
                setErrors(result.fields);
                setPageError(result.message);
            }
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return <div role="status" className="flex min-h-40 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white text-sm text-slate-500"><LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />Đang tải kết luận…</div>;
    }
    if (loadError) {
        return <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{loadError}</p>;
    }

    const locked = Boolean(data?.diagnosesLocked);
    return (
        <div className="space-y-3">
        <form
            noValidate
            aria-label="Kết luận khám"
            onSubmit={(event) => { event.preventDefault(); void submit(); }}
            className="space-y-3"
        >
            <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <h2 className="text-sm font-bold">Chẩn đoán sơ bộ (khám lâm sàng)</h2>
                <p className="mt-2 text-xs text-slate-700">{data?.preliminaryDiagnosis || '—'}</p>
                {data?.preliminaryDiagnoses?.length > 0 && (
                    <ul className="mt-2 flex flex-wrap gap-2">
                        {data.preliminaryDiagnoses.map((item) => (
                            <li key={item.code} className="rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] text-slate-600">
                                <strong className="text-slate-700">{item.code}</strong> {item.name}
                            </li>
                        ))}
                    </ul>
                )}
            </section>

            <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                    <h2 className="flex items-center gap-2 text-sm font-bold"><FileCheck2 aria-hidden="true" className="h-4 w-4 text-teal-600" />Kết luận khám</h2>
                    {data?.concludedAt && (
                        <span className="text-[11px] text-slate-500">Đã lưu lúc {formatDateTime(data.concludedAt)}{data.doctorName ? ` · ${data.doctorName}` : ''}</span>
                    )}
                </div>

                {pageError && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-medium text-rose-800">{pageError}</p>}
                {locked && <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">{MSG.DIAGNOSES_LOCKED}</p>}

                <TextField label="Chẩn đoán" required value={form.diagnosis} onChange={set('diagnosis')} error={errors.diagnosis} placeholder="Chẩn đoán xác định..." />

                <div className="grid gap-4 lg:grid-cols-2">
                    <IcdPicker
                        label="Bệnh chính (ICD-10)"
                        required
                        value={form.main}
                        onChange={set('main')}
                        disabled={locked}
                        error={errors.main}
                        searchIcd={api.searchIcd10}
                        exclude={form.secondary.map((item) => item.code)}
                    />
                    <IcdPicker
                        label="Bệnh kèm theo (ICD-10)"
                        multiple
                        value={form.secondary}
                        onChange={set('secondary')}
                        disabled={locked}
                        error={errors.secondary}
                        searchIcd={api.searchIcd10}
                        exclude={form.main ? [form.main.code] : []}
                    />
                </div>

                <TextField label="Hướng điều trị" required value={form.treatmentPlan} onChange={set('treatmentPlan')} rows={3} error={errors.treatmentPlan} />

                {/* CV 365 III.31.1: optional free text, e.g. nothing to assess yet at a first visit. */}
                <TextField
                    label="Kết quả điều trị"
                    value={form.treatmentResult}
                    onChange={set('treatmentResult')}
                    rows={2}
                    error={errors.treatmentResult}
                    placeholder="VD: Đỡ sốt, còn ho. Để trống nếu chưa đánh giá được."
                />

                <TextField label="Lời dặn" required value={form.advice} onChange={set('advice')} rows={3} error={errors.advice} />
                <TextField label="Tóm tắt bệnh án" value={form.summary} onChange={set('summary')} rows={3} error={errors.summary} />
                <TextField label="Ghi chú" value={form.note} onChange={set('note')} error={errors.note} />
            </section>

            <div className="flex justify-end">
                <button
                    type="submit"
                    disabled={saving}
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-teal-700 px-6 py-2.5 text-xs font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}
                    {saving ? 'Đang lưu…' : 'Lưu kết luận'}
                </button>
            </div>

            <ConfirmModal
                isOpen={Boolean(discouragedCode)}
                title="Bệnh chính không được khuyến khích"
                description={discouragedCode ? MSG.mainDiscouraged(discouragedCode) : ''}
                confirmText="Giữ mã này"
                cancelText="Chọn mã khác"
                onConfirm={() => { setDiscouragedCode(null); void submit(true); }}
                onClose={() => setDiscouragedCode(null)}
            />
        </form>
        <ConfirmConclusionSection
            visitId={visitId}
            saved={Boolean(data?.conclusionId)}
            dirty={dirty}
            api={api}
            onClosed={onClosed}
            onOpenPrescription={onOpenPrescription}
        />
        </div>
    );
}

export default ConclusionPanel;
