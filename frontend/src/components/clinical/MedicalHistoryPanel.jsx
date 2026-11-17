import { useEffect, useRef, useState } from 'react';
import { History, Pencil, Save } from 'lucide-react';
import { toast } from 'sonner';

import defaultVisitApi from '../../apis/visitApi';
import {
    HISTORY_MAX_LENGTH, MESSAGES,
    draftFrom, groupHistory, isSameDraft, readOnlyMessage, saveErrorMessage, toRequest, validateDraft,
} from './medicalHistory';

function formatDateTime(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

// UC-39: khung "Tien su benh" tren man hinh kham cua bac si
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

    // tai lai (sau loi hoac sau MSG75), bo cac thay doi chua luu
    const reload = () => {
        setLoading(true);
        setLoadError('');
        setSaveError('');
        setStale(false);
        setConfirmDiscard(false);
        setEditing(false);
        setReloadKey((key) => key + 1);
    };

    // bam Cap nhat: mo o nhap voi du lieu hien tai
    const startEditing = () => {
        setDraft(draftFrom(data));
        setErrors({});
        setSaveError('');
        setStale(false);
        setEditing(true);
    };

    // bam Huy: da sua thi hoi xac nhan
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

    const setField = (key) => (event) => {
        const value = event.target.value;
        setDraft((current) => ({ ...current, [key]: value }));
    };

    // bam Luu: kiem tra roi gui len; bi nguoi khac sua truoc thi hien nut Tai lai
    const save = async () => {
        const found = validateDraft(draft);
        setErrors(found);
        if (Object.keys(found).length) return;
        setSaving(true);
        setSaveError('');
        try {
            const result = await visitApi.updateMedicalHistory(visitId, toRequest(draft, data));
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
                    <h2 id="medical-history-title" className="flex items-center gap-2 text-sm font-bold"><History className="h-4 w-4 text-teal-600" />2. Tiền sử bệnh</h2>
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

            {loading && !data && <p className="mt-3 text-xs text-slate-500">Đang tải tiền sử bệnh…</p>}
            {loadError && (
                <div role="alert" className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-700">
                    <span>{loadError}</span>
                    <button type="button" onClick={reload} className="font-semibold underline">Thử lại</button>
                </div>
            )}
            {notice && !editing && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">{notice}</p>}

            {data && !editing && (
                <dl className="mt-3 space-y-2 text-xs">
                    {[['Bản thân', groupHistory(data).self], ['Gia đình', groupHistory(data).family]].map(([label, items]) => (
                        <div key={label} className="flex gap-2">
                            <dt className="w-20 shrink-0 font-semibold text-slate-600">+ {label}:</dt>
                            <dd className={items.length ? 'text-slate-700' : 'text-slate-400'}>{items.length ? items.join('; ') : 'Chưa ghi nhận'}</dd>
                        </div>
                    ))}
                </dl>
            )}

            {editing && (
                <form noValidate onSubmit={(event) => { event.preventDefault(); void save(); }} className="mt-3 space-y-3 text-xs">
                    {[['self', 'Bản thân', 'VD: Tăng huyết áp 5 năm'], ['family', 'Gia đình', 'VD: Bố bị đái tháo đường']].map(([key, label, placeholder]) => (
                        <div key={key}>
                            <label htmlFor={`history-${key}`} className="mb-1 block font-semibold text-slate-700">+ {label}</label>
                            <textarea id={`history-${key}`} value={draft[key]} onChange={setField(key)} rows={2} maxLength={HISTORY_MAX_LENGTH} placeholder={placeholder} aria-invalid={errors[key] ? 'true' : undefined} className="w-full rounded-lg border border-slate-200 px-2 py-1.5" />
                            {errors[key] && <p className="mt-1 text-rose-600">{errors[key]}</p>}
                        </div>
                    ))}
                    <p className="text-[11px] text-slate-400">Mỗi dòng là một mục tiền sử.</p>
                    {errors.history && <p className="text-rose-600">{errors.history}</p>}

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
