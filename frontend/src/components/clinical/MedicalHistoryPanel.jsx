import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, History, Pencil, Plus, Save, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import defaultVisitApi from '../../apis/visitApi';
import {
    ALLERGY_SEVERITY_LABELS, ENTRY_MAX_LENGTH, HISTORY_CATEGORY_LABELS, MESSAGES,
    draftFrom, isSameDraft, readOnlyMessage, saveErrorMessage, toRequest, validateDraft,
} from './medicalHistory';

function formatDateTime(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

/** Red labels of the patient's drug allergies, shown on the examination screen (UC-39 step 6). */
export function AllergyLabels({ data }) {
    if (!data) return null;
    if (data.noKnownDrugAllergy) {
        return <span className="rounded-md border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">Không có tiền sử dị ứng thuốc</span>;
    }
    if (!data.allergies?.length) {
        return <span className="text-[11px] text-slate-400">Chưa ghi nhận dị ứng thuốc</span>;
    }
    return (
        <span className="flex flex-wrap gap-1.5" aria-label="Dị ứng thuốc">
            {data.allergies.map((item, index) => (
                <span key={`${item.drug}-${index}`} className="inline-flex items-center gap-1 rounded-md border border-rose-300 bg-rose-50 px-2 py-0.5 text-[11px] font-bold text-rose-700">
                    <AlertTriangle className="h-3 w-3" />
                    Dị ứng: {item.drug}{item.severity ? ` (${ALLERGY_SEVERITY_LABELS[item.severity]})` : ''}
                </span>
            ))}
        </span>
    );
}

/** UC-39 Update Medical History and Allergies: panel on the Doctor's examination screen. */
export default function MedicalHistoryPanel({ visitId, visitApi = defaultVisitApi, onLoaded }) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [editing, setEditing] = useState(false);
    const [draft, setDraft] = useState(() => draftFrom(null));
    const [errors, setErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const [confirmDiscard, setConfirmDiscard] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);
    const [stale, setStale] = useState(false);
    const onLoadedRef = useRef(onLoaded);
    useEffect(() => { onLoadedRef.current = onLoaded; }, [onLoaded]);

    useEffect(() => {
        if (!visitId) return undefined;
        const controller = new AbortController();
        visitApi.getMedicalHistory(visitId, controller.signal)
            .then((result) => {
                setData(result);
                setLoadError('');
                onLoadedRef.current?.(result);
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') {
                    setLoadError(error?.status === 403 ? MESSAGES.readOnly : MESSAGES.loadError);
                }
            })
            .finally(() => setLoading(false));
        return () => controller.abort();
    }, [visitApi, visitId, reloadKey]);

    /** Retry after a load failure, or fetch the other Doctor's changes after MSG75; unsaved edits are dropped. */
    const reload = () => {
        setLoading(true);
        setLoadError('');
        setSaveError('');
        setStale(false);
        setConfirmDiscard(false);
        setEditing(false);
        setReloadKey((key) => key + 1);
    };

    const startEditing = () => {
        setDraft(draftFrom(data));
        setErrors({});
        setSaveError('');
        setStale(false);
        setEditing(true);
    };

    const cancel = () => {
        if (!isSameDraft(draft, draftFrom(data))) {
            setConfirmDiscard(true);
            return;
        }
        setEditing(false);
    };

    const discard = () => {
        setConfirmDiscard(false);
        setEditing(false);
    };

    const updateItem = (list, index, key) => (event) => {
        const value = event.target.value;
        setDraft((current) => ({
            ...current,
            [list]: current[list].map((item, i) => (i === index ? { ...item, [key]: value } : item)),
        }));
    };

    const removeItem = (list, index) => setDraft((current) => ({
        ...current,
        [list]: current[list].filter((_, i) => i !== index),
    }));

    const save = async () => {
        const found = validateDraft(draft);
        setErrors(found);
        if (Object.keys(found).length) return;
        setSaving(true);
        setSaveError('');
        try {
            const result = await visitApi.updateMedicalHistory(visitId, toRequest(draft, data?.updatedAt));
            setData(result);
            onLoadedRef.current?.(result);
            setEditing(false);
            toast.success(MESSAGES.saved);
        } catch (error) {
            setSaveError(saveErrorMessage(error));
            setStale(error?.errorResponse?.code === 'CONCURRENT_MODIFICATION');
        } finally {
            setSaving(false);
        }
    };

    const notice = readOnlyMessage(data?.readOnlyReason);

    return (
        <section aria-labelledby="medical-history-title" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                    <h2 id="medical-history-title" className="flex items-center gap-2 text-sm font-bold"><History className="h-4 w-4 text-teal-600" />Tiền sử & dị ứng thuốc</h2>
                    {data?.updatedAt && (
                        <p className="mt-0.5 text-[11px] text-slate-400">Cập nhật lần cuối: {formatDateTime(data.updatedAt)}{data.updatedByName ? ` · ${data.updatedByName}` : ''}</p>
                    )}
                </div>
                {!editing && data?.editable && (
                    <button type="button" onClick={startEditing} className="inline-flex items-center gap-1.5 rounded-lg border border-teal-600 px-3 py-1.5 text-xs font-semibold text-teal-700 hover:bg-teal-50">
                        <Pencil className="h-3.5 w-3.5" />Cập nhật
                    </button>
                )}
            </div>

            {loading && !data && <p className="mt-3 text-xs text-slate-500">Đang tải tiền sử và dị ứng…</p>}
            {loadError && (
                <div role="alert" className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">
                    <span>{loadError}</span>
                    <button type="button" onClick={reload} className="font-semibold underline">Thử lại</button>
                </div>
            )}
            {notice && !editing && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">{notice}</p>}

            {data && !editing && (
                <div className="mt-3 space-y-3 text-xs">
                    <div><p className="mb-1 font-semibold text-slate-600">Dị ứng thuốc</p><AllergyLabels data={data} /></div>
                    {data.allergies?.some((item) => item.reaction) && (
                        <ul className="space-y-1 text-slate-600">
                            {data.allergies.filter((item) => item.reaction).map((item, index) => (
                                <li key={`${item.drug}-r-${index}`}><strong>{item.drug}:</strong> {item.reaction}</li>
                            ))}
                        </ul>
                    )}
                    <div>
                        <p className="mb-1 font-semibold text-slate-600">Tiền sử</p>
                        {data.history?.length ? (
                            <ul className="space-y-1">
                                {data.history.map((item, index) => (
                                    <li key={`${item.category}-${index}`} className="text-slate-700"><span className="text-slate-400">{HISTORY_CATEGORY_LABELS[item.category]}:</span> {item.content}</li>
                                ))}
                            </ul>
                        ) : <p className="text-slate-400">Chưa ghi nhận tiền sử</p>}
                    </div>
                </div>
            )}

            {editing && (
                <form noValidate onSubmit={(event) => { event.preventDefault(); void save(); }} className="mt-3 space-y-4 text-xs">
                    <fieldset className="space-y-2">
                        <legend className="mb-1 font-semibold text-slate-700">Tiền sử</legend>
                        {draft.history.map((item, index) => (
                            <div key={index} className="grid grid-cols-[150px_minmax(0,1fr)_auto] items-start gap-2">
                                <label className="sr-only" htmlFor={`history-category-${index}`}>Nhóm tiền sử {index + 1}</label>
                                <select id={`history-category-${index}`} value={item.category} onChange={updateItem('history', index, 'category')} className="h-9 rounded-lg border border-slate-200 bg-white px-2">
                                    {Object.entries(HISTORY_CATEGORY_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                                </select>
                                <div>
                                    <label className="sr-only" htmlFor={`history-content-${index}`}>Nội dung tiền sử {index + 1}</label>
                                    <input id={`history-content-${index}`} value={item.content} maxLength={ENTRY_MAX_LENGTH} onChange={updateItem('history', index, 'content')} placeholder="VD: Tăng huyết áp 5 năm" className="h-9 w-full rounded-lg border border-slate-200 px-2" />
                                    {errors[`history.${index}`] && <p className="mt-1 text-rose-600">{errors[`history.${index}`]}</p>}
                                </div>
                                <button type="button" aria-label={`Xóa tiền sử ${index + 1}`} onClick={() => removeItem('history', index)} className="h-9 rounded-lg px-2 text-slate-400 hover:text-rose-600"><Trash2 className="h-4 w-4" /></button>
                            </div>
                        ))}
                        {errors.history && <p className="text-rose-600">{errors.history}</p>}
                        <button type="button" onClick={() => setDraft((current) => ({ ...current, history: [...current.history, { category: 'CHRONIC_DISEASE', content: '' }] }))} className="inline-flex items-center gap-1 font-semibold text-teal-700"><Plus className="h-3.5 w-3.5" />Thêm tiền sử</button>
                    </fieldset>

                    <fieldset className="space-y-2">
                        <legend className="mb-1 font-semibold text-slate-700">Dị ứng thuốc</legend>
                        <label className="flex items-center gap-2">
                            <input type="checkbox" checked={draft.noKnownDrugAllergy} onChange={(event) => setDraft((current) => ({ ...current, noKnownDrugAllergy: event.target.checked }))} className="h-4 w-4 accent-teal-600" />
                            Không có tiền sử dị ứng thuốc
                        </label>
                        {!draft.noKnownDrugAllergy && draft.allergies.map((item, index) => (
                            <div key={index} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_120px_auto] items-start gap-2">
                                <div>
                                    <label className="sr-only" htmlFor={`allergy-drug-${index}`}>Thuốc hoặc nhóm thuốc {index + 1}</label>
                                    <input id={`allergy-drug-${index}`} value={item.drug} maxLength={ENTRY_MAX_LENGTH} onChange={updateItem('allergies', index, 'drug')} placeholder="Thuốc / nhóm thuốc *" className="h-9 w-full rounded-lg border border-slate-200 px-2" />
                                    {errors[`allergies.${index}`] && <p className="mt-1 text-rose-600">{errors[`allergies.${index}`]}</p>}
                                </div>
                                <label className="sr-only" htmlFor={`allergy-reaction-${index}`}>Biểu hiện {index + 1}</label>
                                <input id={`allergy-reaction-${index}`} value={item.reaction} maxLength={ENTRY_MAX_LENGTH} onChange={updateItem('allergies', index, 'reaction')} placeholder="Biểu hiện" className="h-9 w-full rounded-lg border border-slate-200 px-2" />
                                <label className="sr-only" htmlFor={`allergy-severity-${index}`}>Mức độ {index + 1}</label>
                                <select id={`allergy-severity-${index}`} value={item.severity} onChange={updateItem('allergies', index, 'severity')} className="h-9 rounded-lg border border-slate-200 bg-white px-2">
                                    <option value="">Mức độ</option>
                                    {Object.entries(ALLERGY_SEVERITY_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                                </select>
                                <button type="button" aria-label={`Xóa dị ứng ${index + 1}`} onClick={() => removeItem('allergies', index)} className="h-9 rounded-lg px-2 text-slate-400 hover:text-rose-600"><Trash2 className="h-4 w-4" /></button>
                            </div>
                        ))}
                        {!draft.noKnownDrugAllergy && (
                            <button type="button" onClick={() => setDraft((current) => ({ ...current, allergies: [...current.allergies, { drug: '', reaction: '', severity: '' }] }))} className="inline-flex items-center gap-1 font-semibold text-teal-700"><Plus className="h-3.5 w-3.5" />Thêm dị ứng</button>
                        )}
                    </fieldset>

                    {saveError && (
                        <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-rose-50 px-3 py-2 text-rose-700">
                            <span>{saveError}</span>
                            {stale && <button type="button" onClick={reload} className="font-semibold underline">Tải lại</button>}
                        </div>
                    )}

                    {confirmDiscard ? (
                        <div role="alertdialog" aria-label="Loại bỏ thay đổi" className="rounded-lg border border-amber-200 bg-amber-50 p-3">
                            <p className="text-amber-900">{MESSAGES.discard}</p>
                            <div className="mt-2 flex justify-end gap-2">
                                <button type="button" onClick={() => setConfirmDiscard(false)} className="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold text-slate-600">Tiếp tục chỉnh sửa</button>
                                <button type="button" onClick={discard} className="rounded-lg bg-rose-600 px-3 py-1.5 font-semibold text-white">Loại bỏ</button>
                            </div>
                        </div>
                    ) : (
                        <div className="flex justify-end gap-2">
                            <button type="button" onClick={cancel} className="rounded-lg border border-slate-300 px-4 py-2 font-semibold text-slate-600">Hủy</button>
                            <button type="submit" disabled={saving} className="inline-flex items-center gap-1.5 rounded-lg bg-teal-700 px-4 py-2 font-semibold text-white hover:bg-teal-800 disabled:opacity-50"><Save className="h-3.5 w-3.5" />{saving ? 'Đang lưu…' : 'Lưu'}</button>
                        </div>
                    )}
                </form>
            )}
        </section>
    );
}
