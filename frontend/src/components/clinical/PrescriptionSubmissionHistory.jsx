import { useEffect, useState } from 'react';
import { BadgeCheck, LoaderCircle, ShieldAlert } from 'lucide-react';

import { SIGNING_MSG, SUBMISSION_RESULTS, signatureReason, signingError } from './prescriptionSigning';

function formatDateTime(value) {
    if (!value) return '—';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value));
}

const RESULT_TONES = {
    ACCEPTED: 'text-emerald-800',
    REJECTED: 'text-rose-700',
    NO_RESPONSE: 'text-amber-800',
};

/** BR-59-16: the signature of one send and its status as verified again by the server. */
export function SignatureStatus({ submission }) {
    if (!submission.signedAt) return <span className="text-slate-500">—</span>;
    const valid = submission.signatureValid !== false;
    return (
        <span className="block space-y-0.5">
            <span className={`flex items-center gap-1 font-semibold ${valid ? 'text-emerald-800' : 'text-rose-700'}`}>
                {valid
                    ? <BadgeCheck aria-hidden="true" className="h-3.5 w-3.5" />
                    : <ShieldAlert aria-hidden="true" className="h-3.5 w-3.5" />}
                {valid ? SIGNING_MSG.SIGNATURE_VALID : SIGNING_MSG.SIGNATURE_NOT_VALID(signatureReason(submission.signatureFailureReason))}
            </span>
            <span className="block text-slate-600">
                {submission.signerName} · ký lúc {formatDateTime(submission.signedAt)}
            </span>
            <span className="block break-all text-slate-500">
                Chứng thư {submission.certificateSerial} ({submission.certificateIssuer})
            </span>
        </span>
    );
}

/** UC-91 View Prescription Submission Result: every send of the prescription, newest first. */
export function PrescriptionSubmissionHistory({ prescriptionId, api }) {
    const [submissions, setSubmissions] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        const controller = new AbortController();
        api.getPrescriptionSubmissions(prescriptionId, controller.signal)
            .then((result) => setSubmissions(result ?? []))
            .catch((err) => {
                if (err?.name !== 'AbortError') setError(signingError(err));
            });
        return () => controller.abort();
    }, [api, prescriptionId]);

    if (error) {
        return <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">{error}</p>;
    }
    if (submissions == null) {
        return <p role="status" className="flex items-center gap-2 text-sm text-slate-500"><LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />Đang tải kết quả gửi…</p>;
    }
    if (submissions.length === 0) {
        return <p className="text-sm text-slate-500">{SIGNING_MSG.HISTORY_EMPTY}</p>;
    }
    return (
        <div className="overflow-x-auto border border-slate-200">
            <table className="w-full min-w-[56rem] border-collapse text-left text-xs text-slate-800">
                <caption className="sr-only">Lịch sử gửi đơn thuốc</caption>
                <thead className="bg-slate-100 font-semibold text-slate-700">
                    <tr>
                        {['Lần gửi', 'Thời gian gửi', 'Kết quả', 'Thời gian phản hồi', 'Người gửi', 'Chữ ký số'].map((title) => (
                            <th key={title} scope="col" className="border border-slate-200 px-2 py-2">{title}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {submissions.map((submission) => (
                        <tr key={submission.attemptNo} className="align-top">
                            <td className="border border-slate-200 px-2 py-1.5 text-center tabular-nums">{submission.attemptNo}</td>
                            <td className="border border-slate-200 px-2 py-1.5 tabular-nums">{formatDateTime(submission.submittedAt)}</td>
                            <td className={`border border-slate-200 px-2 py-1.5 ${RESULT_TONES[submission.result] ?? ''}`}>
                                <span className="font-semibold">{SUBMISSION_RESULTS[submission.result] ?? submission.result}</span>
                                {submission.errors?.length > 0 && (
                                    <ul className="mt-1 list-inside list-disc">
                                        {submission.errors.map((item) => <li key={item}>{item}</li>)}
                                    </ul>
                                )}
                            </td>
                            <td className="border border-slate-200 px-2 py-1.5 tabular-nums">{formatDateTime(submission.respondedAt)}</td>
                            <td className="border border-slate-200 px-2 py-1.5">{submission.submittedBy ?? '—'}</td>
                            <td className="border border-slate-200 px-2 py-1.5"><SignatureStatus submission={submission} /></td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export default PrescriptionSubmissionHistory;
