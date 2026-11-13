import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { FileSignature, LoaderCircle, Smartphone } from 'lucide-react';

import { SIGNING_MSG, signingError, timeLeft } from './prescriptionSigning';

const POLL_MS = 3000; // UC-90 step 4: the client asks for the result every 3 s (ASSUMPTION).

function formatDate(value) {
    if (!value) return '';
    const [year, month, day] = String(value).split('-');
    return `${day}/${month}/${year}`;
}

/**
 * UC-90 Submit e-Prescription: preview, request the digital signature, wait for the Doctor's approval in the CA's app,
 * then let the server verify, store and send. {@code resume} is a pending request found on opening the tab (AF-06).
 * {@code onFinished(prescription)} receives the prescription after a signed send, or undefined when it must be
 * reloaded (no answer from the national system).
 */
export function PrescriptionSigningDialog({ prescription, api, resume = null, onClose, onFinished }) {
    const titleId = useId();
    const [phase, setPhase] = useState(resume ? 'waiting' : 'preview');
    const [request, setRequest] = useState(resume);
    // Copying the diagnoses on the first request creates a new version; a new request names it.
    const [version, setVersion] = useState(resume?.documentVersion ?? prescription.version);
    const [message, setMessage] = useState('');
    const [warning, setWarning] = useState('');
    const [now, setNow] = useState(() => Date.now());
    const inFlight = useRef(false);

    const sign = async () => {
        setPhase('requesting');
        setMessage('');
        try {
            const created = await api.requestPrescriptionSignature(prescription.prescriptionId, version);
            setRequest(created);
            setVersion(created.documentVersion);
            setPhase('waiting');
        } catch (error) {
            if (error?.errorResponse?.code === 'SIGNING_REQUEST_PENDING') {
                // EF-17: resume the request that is already pending.
                const current = await api.getCurrentPrescriptionSigning(prescription.prescriptionId).catch(() => null);
                if (current) {
                    setRequest(current);
                    setVersion(current.documentVersion);
                    setPhase('waiting');
                    return;
                }
            }
            setMessage(signingError(error));
            setPhase('preview');
        }
    };

    const complete = useCallback(async () => {
        if (!request || inFlight.current) return;
        inFlight.current = true;
        try {
            const result = await api.completePrescriptionSigning(prescription.prescriptionId, request.id);
            setWarning('');
            switch (result?.status) {
                case 'PENDING':
                    return;
                case 'SIGNED':
                    onFinished(result.prescription);
                    return;
                case 'DECLINED':
                    setMessage(SIGNING_MSG.DECLINED);
                    break;
                case 'EXPIRED':
                    setMessage(SIGNING_MSG.EXPIRED);
                    break;
                case 'CANCELLED':
                    setMessage(SIGNING_MSG.CANCELLED);
                    break;
                default:
                    setMessage(SIGNING_MSG.SYSTEM_ERROR);
            }
            setPhase('ended');
        } catch (error) {
            const code = error?.errorResponse?.code;
            if (code === 'SIGNING_SERVICE_UNAVAILABLE') {
                // EF-15: nothing changed; keep asking.
                setWarning(SIGNING_MSG.SERVICE_UNAVAILABLE);
                return;
            }
            if (code === 'NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE') {
                // AF-02: signed and kept, no answer; the panel reloads and offers "Gửi lại".
                onFinished(undefined);
                return;
            }
            setMessage(signingError(error));
            setPhase('ended');
        } finally {
            inFlight.current = false;
        }
    }, [api, onFinished, prescription.prescriptionId, request]);

    useEffect(() => {
        if (phase !== 'waiting') return undefined;
        // Ask at once: an approval may have arrived while the screen was closed (AF-06).
        const first = window.setTimeout(() => { void complete(); }, 0);
        const poll = window.setInterval(() => { void complete(); }, POLL_MS);
        const tick = window.setInterval(() => setNow(Date.now()), 1000);
        return () => {
            window.clearTimeout(first);
            window.clearInterval(poll);
            window.clearInterval(tick);
        };
    }, [phase, complete]);

    const cancel = async () => {
        if (phase === 'waiting' && request) {
            // AF-03: withdraw the pending request; nothing is sent.
            await api.cancelPrescriptionSigning(prescription.prescriptionId, request.id).catch(() => undefined);
        }
        onClose();
    };

    const diagnoses = prescription.diagnoses ?? [];
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
            <div role="dialog" aria-modal="true" aria-labelledby={titleId} className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
                <h2 id={titleId} className="flex items-center gap-2 text-lg font-bold text-slate-900">
                    <FileSignature aria-hidden="true" className="h-5 w-5 text-teal-700" />Ký số và gửi đơn thuốc
                </h2>

                <section aria-label="Xem trước đơn thuốc" className="relative mt-4 space-y-2 overflow-hidden rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
                    <span aria-hidden="true" className="pointer-events-none absolute inset-0 flex items-center justify-center text-4xl font-black tracking-widest text-slate-200/80 [transform:rotate(-20deg)]">BẢN NHÁP</span>
                    <p className="relative">Mã đơn thuốc <strong className="font-mono">{prescription.prescriptionCode}</strong></p>
                    <div className="relative">
                        <p className="font-semibold">Chẩn đoán gửi kèm</p>
                        <ul className="list-inside list-disc">
                            {diagnoses.map((item, index) => (
                                <li key={item.code}>{item.code} {item.name}{index === 0 && ' (bệnh chính)'}</li>
                            ))}
                        </ul>
                    </div>
                    <div className="relative">
                        <p className="font-semibold">Thuốc ({prescription.lines?.length ?? 0})</p>
                        <ul className="list-inside list-decimal">
                            {(prescription.lines ?? []).map((line) => (
                                <li key={line.medicineId}>{line.name} — {Number(line.quantity).toLocaleString('vi-VN')} {line.unit}; {line.dosePerTime} × {line.timesPerDay} lần/ngày × {line.days} ngày</li>
                            ))}
                        </ul>
                    </div>
                    <p className="relative">
                        <span className="font-semibold">Ngày tái khám:</span>{' '}
                        {prescription.followUpDate ? formatDate(prescription.followUpDate) : 'Chưa hẹn tái khám'}
                    </p>
                </section>

                <p className="mt-3 text-sm font-medium text-amber-800">{SIGNING_MSG.REMINDER}</p>

                {phase === 'waiting' && request && (
                    <p role="status" className="mt-3 flex items-start gap-2 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-900">
                        <Smartphone aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                        <span>{SIGNING_MSG.WAITING(timeLeft(request.expiresAt, now))}</span>
                    </p>
                )}
                {warning && <p role="status" className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">{warning}</p>}
                {message && <p role="alert" className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800">{message}</p>}

                <div className="mt-6 flex justify-end gap-3">
                    <button type="button" onClick={() => { void cancel(); }} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
                        {phase === 'ended' ? 'Đóng' : 'Hủy'}
                    </button>
                    {(phase === 'preview' || phase === 'requesting' || phase === 'ended') && (
                        <button
                            type="button"
                            onClick={() => { void sign(); }}
                            disabled={phase === 'requesting'}
                            className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {phase === 'requesting' && <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />}
                            {phase === 'requesting' ? 'Đang chuẩn bị ký…' : phase === 'ended' ? 'Ký lại' : 'Ký số'}
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}

export default PrescriptionSigningDialog;
