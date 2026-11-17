import { useEffect, useId, useState } from 'react';
import { CalendarDays, Info, LoaderCircle, Save } from 'lucide-react';
import { toast } from 'sonner';

import defaultVisitApi from '../../apis/visitApi';
import { BUTTON } from './buttonStyles';
import {
    ADVICE_MAX, EMPTY_FOLLOW_UP, FOLLOW_UP_MSG, FOLLOW_UP_STATUS, followUpError, formFromResponse, sameFollowUp,
    shortDate, toPayload, validateFollowUp,
} from './followUpForm';
import { PrintFollowUpButton } from './PrintFollowUpButton';

const inputClass = 'w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-700 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-50';

/**
 * UC-61 Create Follow-up Appointment (docs/uc-61-follow-up-appointment.md): the visit's single appointment — date and
 * advice — made after the conclusion and changed while it is scheduled and the record is open. UC-62 prints it.
 */
export function FollowUpPanel({ visitId, api = defaultVisitApi }) {
    const dateId = useId();
    const adviceId = useId();
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [form, setForm] = useState(EMPTY_FOLLOW_UP);
    const [savedForm, setSavedForm] = useState(EMPTY_FOLLOW_UP);
    const [errors, setErrors] = useState({});
    const [pageError, setPageError] = useState('');
    const [checked, setChecked] = useState(false);
    const [saving, setSaving] = useState(false);
    const [confirmDiscard, setConfirmDiscard] = useState(false);
    const dirty = !sameFollowUp(form, savedForm);

    useEffect(() => {
        const controller = new AbortController();
        api.getFollowUp(visitId, controller.signal)
            .then((response) => {
                const loaded = formFromResponse(response);
                setData(response);
                setForm(loaded);
                setSavedForm(loaded);
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') setLoadError(followUpError(error).message);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [api, visitId]);

    // AF-02 (MSG38): warn before leaving the page with unsaved changes.
    useEffect(() => {
        if (!dirty) return undefined;
        const warn = (event) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const set = (key) => (event) => {
        const next = { ...form, [key]: event.target.value };
        setForm(next);
        if (checked) setErrors(validateFollowUp(next, data));
    };

    const submit = async () => {
        const found = validateFollowUp(form, data);
        setChecked(true);
        setErrors(found);
        if (Object.keys(found).length) {
            setPageError(FOLLOW_UP_MSG.CHECK_FIELDS);
            return;
        }
        setSaving(true);
        setPageError('');
        try {
            const created = !data?.appointment;
            const saved = await api.saveFollowUp(visitId, toPayload(form, data?.appointment?.version));
            const values = formFromResponse(saved);
            setData(saved);
            setForm(values);
            setSavedForm(values);
            setChecked(false);
            toast.success(created ? FOLLOW_UP_MSG.CREATED : FOLLOW_UP_MSG.UPDATED); // MSG04 / MSG03
        } catch (error) {
            const result = followUpError(error);
            setErrors(result.fields);
            setPageError(result.message);
        } finally {
            setSaving(false);
        }
    };

    const discard = () => {
        setForm(savedForm);
        setErrors({});
        setChecked(false);
        setPageError('');
        setConfirmDiscard(false);
    };

    if (loading) {
        return <div role="status" className="flex min-h-40 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white text-sm text-slate-500"><LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />Đang tải lịch hẹn tái khám…</div>;
    }
    if (loadError) {
        return <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{loadError}</p>;
    }

    const appointment = data?.appointment;
    const scheduled = !appointment || appointment.status === 'SCHEDULED';
    const editable = Boolean(data?.conclusionSaved) && scheduled;
    const adviceLength = form.advice.trim().length;
    return (
        <form
            noValidate
            aria-label="Hẹn tái khám"
            onSubmit={(event) => { event.preventDefault(); void submit(); }}
            className="space-y-3"
        >
            {!data?.conclusionSaved && <p role="status" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{FOLLOW_UP_MSG.CONCLUSION_REQUIRED}</p>}
            {!scheduled && <p role="status" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{FOLLOW_UP_MSG.NOT_SCHEDULED}</p>}
            {/* AF-03 (BR-59-08): a change after the prescription was sent does not reach the prescription. */}
            {data?.prescriptionAccepted && (
                <p role="status" className="flex items-start gap-2 rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-900">
                    <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                    {FOLLOW_UP_MSG.PRESCRIPTION_SENT(shortDate(data.prescriptionFollowUpDate))}
                </p>
            )}

            <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                        <h2 className="flex items-center gap-2 text-base font-bold"><CalendarDays aria-hidden="true" className="h-4 w-4 text-teal-600" />Hẹn tái khám</h2>
                        <p className="mt-0.5 text-xs text-slate-600">
                            Ngày khám {shortDate(data?.visitDate)}
                            {appointment && <> · Số giấy hẹn <strong className="font-mono text-slate-900">{appointment.slipNumber}</strong> · {FOLLOW_UP_STATUS[appointment.status] ?? appointment.status}</>}
                        </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        {editable && (
                            <button type="submit" disabled={saving} className={BUTTON.primary}>
                                {saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}
                                {saving ? 'Đang lưu…' : 'Lưu'}
                            </button>
                        )}
                        {editable && (
                            <button type="button" onClick={() => setConfirmDiscard(true)} disabled={!dirty || saving} className={BUTTON.neutral}>Hủy</button>
                        )}
                        <PrintFollowUpButton appointmentId={appointment?.id} disabled={dirty || !scheduled} api={api} />
                    </div>
                </div>

                {confirmDiscard && (
                    <div role="alertdialog" aria-label="Thay đổi chưa lưu" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm">
                        <p className="text-amber-900">{FOLLOW_UP_MSG.DISCARD}</p>
                        <div className="mt-2 flex justify-end gap-2">
                            <button type="button" onClick={() => setConfirmDiscard(false)} className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 font-semibold text-slate-600">Tiếp tục chỉnh sửa</button>
                            <button type="button" onClick={discard} className="rounded-lg bg-amber-600 px-3 py-1.5 font-semibold text-white hover:bg-amber-700">Loại bỏ</button>
                        </div>
                    </div>
                )}

                {data?.diagnoses?.length > 0 && (
                    <div className="text-xs">
                        <span className="font-semibold text-slate-500">Chẩn đoán (theo kết luận):</span>{' '}
                        {data.diagnoses.map((item) => `${item.code} ${item.name}`).join('; ')}
                    </div>
                )}

                <div className="grid gap-4 md:grid-cols-[16rem_1fr]">
                    <div>
                        <label htmlFor={dateId} className="mb-1 block text-xs font-semibold text-slate-600">Ngày tái khám <span className="text-rose-600">*</span></label>
                        <input
                            id={dateId}
                            type="date"
                            value={form.followUpDate}
                            min={data?.minDate}
                            max={data?.maxDate}
                            onChange={set('followUpDate')}
                            disabled={!editable}
                            aria-invalid={Boolean(errors.followUpDate)}
                            aria-describedby={errors.followUpDate ? `${dateId}-error` : undefined}
                            className={`${inputClass} ${errors.followUpDate ? 'border-rose-400' : 'border-slate-300'}`}
                        />
                        {errors.followUpDate && <span id={`${dateId}-error`} className="mt-1 block text-xs text-rose-600">{errors.followUpDate}</span>}
                    </div>
                    <div>
                        <label htmlFor={adviceId} className="mb-1 block text-xs font-semibold text-slate-600">Lời dặn tái khám</label>
                        <textarea
                            id={adviceId}
                            rows={3}
                            value={form.advice}
                            onChange={set('advice')}
                            disabled={!editable}
                            aria-invalid={Boolean(errors.advice)}
                            aria-describedby={`${adviceId}-count${errors.advice ? ` ${adviceId}-error` : ''}`}
                            className={`${inputClass} ${errors.advice ? 'border-rose-400' : 'border-slate-300'}`}
                        />
                        <span id={`${adviceId}-count`} className="mt-1 block text-right text-xs text-slate-500">{adviceLength.toLocaleString('vi-VN')}/{ADVICE_MAX.toLocaleString('vi-VN')}</span>
                        {errors.advice && <span id={`${adviceId}-error`} className="block text-xs text-rose-600">{errors.advice}</span>}
                    </div>
                </div>

                {pageError && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800">{pageError}</p>}
            </section>
        </form>
    );
}

export default FollowUpPanel;
