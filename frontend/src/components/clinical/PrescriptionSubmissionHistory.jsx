import { useEffect, useState } from 'react';
import { LoaderCircle } from 'lucide-react';

import { SUBMIT_MSG, SUBMISSION_RESULTS, submissionError } from './prescriptionSubmission';

function formatDateTime(value) {
    if (!value) return '—';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value));
}

const RESULT_TONES = {
    ACCEPTED: 'text-emerald-800',
    REJECTED: 'text-rose-700',
    NO_RESPONSE: 'text-amber-800',
};

/** UC-91 View Prescription Submission Result: every send of the prescription, newest first. */
export function PrescriptionSubmissionHistory({ prescriptionId, api }) {
    const [submissions, setSubmissions] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        const controller = new AbortController();
        api.getPrescriptionSubmissions(prescriptionId, controller.signal)
            .then((result) => setSubmissions(result ?? []))
            .catch((err) => {
                if (err?.name !== 'AbortError') setError(submissionError(err));
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
        return <p className="text-sm text-slate-500">{SUBMIT_MSG.HISTORY_EMPTY}</p>;
    }
    return (
        <div className="overflow-x-auto border border-slate-200">
            <table className="w-full min-w-[44rem] border-collapse text-left text-xs text-slate-800">
                <caption className="sr-only">Lịch sử gửi đơn thuốc</caption>
                <thead className="bg-slate-100 font-semibold text-slate-700">
                    <tr>
                        {['Lần gửi', 'Thời gian gửi', 'Kết quả', 'Thời gian phản hồi', 'Người gửi'].map((title) => (
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
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export default PrescriptionSubmissionHistory;
