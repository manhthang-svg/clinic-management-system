import { useEffect, useId, useRef, useState } from 'react';
import { LoaderCircle, Send } from 'lucide-react';

import { SUBMIT_MSG, submissionError } from './prescriptionSubmission';

function formatDate(value) {
    if (!value) return '';
    const [year, month, day] = String(value).split('-');
    return `${day}/${month}/${year}`;
}

/**
 * UC-90 Submit e-Prescription: the summary of what is sent, then the send. When no national token is kept for the
 * Doctor (BR-59-17, cách B), the Doctor types the national prescription password, which goes with this one request
 * and is cleared afterwards. {@code onFinished(prescription)} receives the prescription after an answer, or null when
 * it must be reloaded (no answer from the national system).
 */
export function PrescriptionSubmitDialog({ prescription, api, onClose, onFinished }) {
    const [signInRequired, setSignInRequired] = useState(Boolean(prescription.nationalSignInRequired));
    const [password, setPassword] = useState('');
    const [sending, setSending] = useState(false);
    const [message, setMessage] = useState('');
    const titleId = useId();
    const passwordId = useId();
    const passwordRef = useRef(null);

    useEffect(() => {
        if (signInRequired) passwordRef.current?.focus();
    }, [signInRequired]);

    const send = async () => {
        setSending(true);
        setMessage('');
        try {
            const sent = await api.submitPrescription(
                prescription.prescriptionId, prescription.version, signInRequired ? password : undefined);
            onFinished(sent);
        } catch (error) {
            const code = error?.errorResponse?.code;
            if (code === 'NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE') {
                onFinished(null); // AF-02: the attempt without an answer is shown after a reload.
                return;
            }
            if (code === 'NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED') setSignInRequired(true);
            setMessage(submissionError(error));
        } finally {
            setPassword('');
            setSending(false);
        }
    };

    const diagnoses = prescription.diagnoses ?? [];
    const incomplete = signInRequired && password.trim() === '';
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
            <div role="dialog" aria-modal="true" aria-labelledby={titleId} className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
                <h2 id={titleId} className="flex items-center gap-2 text-lg font-bold text-slate-900">
                    <Send aria-hidden="true" className="h-5 w-5 text-teal-700" />Gửi đơn thuốc
                </h2>

                <section aria-label="Nội dung gửi" className="mt-4 space-y-2 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
                    <p>Mã đơn thuốc <strong className="font-mono">{prescription.prescriptionCode}</strong></p>
                    <div>
                        <p className="font-semibold">Chẩn đoán gửi kèm</p>
                        <ul className="list-inside list-disc">
                            {diagnoses.map((item, index) => (
                                <li key={item.code}>{item.code} {item.name}{index === 0 && ' (bệnh chính)'}</li>
                            ))}
                        </ul>
                    </div>
                    <div>
                        <p className="font-semibold">Thuốc ({prescription.lines?.length ?? 0})</p>
                        <ul className="list-inside list-decimal">
                            {(prescription.lines ?? []).map((line) => (
                                <li key={line.medicineId}>{line.name} — {Number(line.quantity).toLocaleString('vi-VN')} {line.unit}; {line.dosePerTime} × {line.timesPerDay} lần/ngày × {line.days} ngày</li>
                            ))}
                        </ul>
                    </div>
                    <p>
                        <span className="font-semibold">Ngày tái khám:</span>{' '}
                        {prescription.followUpDate ? formatDate(prescription.followUpDate) : 'Chưa hẹn tái khám'}
                    </p>
                </section>

                <p className="mt-3 text-sm font-medium text-amber-800">{SUBMIT_MSG.REMINDER}</p>

                <form
                    noValidate
                    onSubmit={(event) => { event.preventDefault(); if (!incomplete && !sending) void send(); }}
                    className="mt-3 space-y-3"
                >
                    {signInRequired && (
                        <div>
                            <label htmlFor={passwordId} className="block text-sm font-semibold text-slate-700">Mật khẩu liên thông đơn thuốc</label>
                            <input
                                id={passwordId}
                                ref={passwordRef}
                                type="password"
                                autoComplete="off"
                                value={password}
                                onChange={(event) => setPassword(event.target.value)}
                                className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                            />
                            <p className="mt-1 text-xs text-slate-500">{SUBMIT_MSG.SIGN_IN_HINT}</p>
                        </div>
                    )}
                    {message && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800">{message}</p>}

                    <div className="flex justify-end gap-3 pt-3">
                        <button type="button" onClick={onClose} disabled={sending} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
                            Hủy
                        </button>
                        <button
                            type="submit"
                            disabled={sending || incomplete}
                            className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {sending ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Send aria-hidden="true" className="h-4 w-4" />}
                            {sending ? 'Đang gửi…' : 'Gửi đơn'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default PrescriptionSubmitDialog;
