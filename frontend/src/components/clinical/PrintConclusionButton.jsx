import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { LoaderCircle, Printer } from 'lucide-react';

import { BUTTON } from './buttonStyles';
import { ConclusionSlip } from './ConclusionSlip';
import { printError } from './prescriptionPrintFormat';

/**
 * UC-57: "In kết luận" opens the preview of the conclusion slip of a closed record (step 2); "In" gets the slip with
 * its print time and opens the browser print dialog (step 3; nothing is recorded, PO 26/11/2026). The printed copy
 * is rendered into `document.body` and shown only when printing, as for the follow-up slip.
 */
export function PrintConclusionButton({ visitId, api }) {
    const titleId = useId();
    const [open, setOpen] = useState(false);
    const [preview, setPreview] = useState(null);
    const [printing, setPrinting] = useState(false);
    const [error, setError] = useState('');
    const [job, setJob] = useState(null);
    const previewRequest = useRef(null);

    const openPreview = async () => {
        const controller = new AbortController();
        previewRequest.current = controller;
        setOpen(true);
        setPreview(null);
        setError('');
        try {
            const result = await api.getConclusionPrint(visitId, controller.signal);
            if (!controller.signal.aborted) setPreview(result);
        } catch (err) {
            if (!controller.signal.aborted) setError(printError(err));
        }
    };

    // AF-02: closing the preview prints nothing.
    const close = () => {
        previewRequest.current?.abort();
        setOpen(false);
        setPreview(null);
    };

    // Open the print dialog once the copy is in the DOM, then remove it.
    useEffect(() => {
        if (!job) return undefined;
        const done = () => setJob(null);
        window.addEventListener('afterprint', done, { once: true });
        window.print();
        return () => window.removeEventListener('afterprint', done);
    }, [job]);

    const print = async () => {
        setPrinting(true);
        setError('');
        try {
            const slip = await api.prepareConclusionPrint(visitId);
            setPreview(slip);
            setJob({ slip, id: Date.now() });
        } catch (err) {
            setError(printError(err));
        } finally {
            setPrinting(false);
        }
    };

    return (
        <>
            <button type="button" onClick={() => { void openPreview(); }} className={BUTTON.outline}>
                <Printer aria-hidden="true" className="h-4 w-4" />In kết luận
            </button>
            {open && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
                    <div role="dialog" aria-modal="true" aria-labelledby={titleId} className="flex max-h-[92vh] w-full max-w-3xl flex-col rounded-2xl border border-slate-200 bg-slate-50 p-5 shadow-2xl">
                        <h2 id={titleId} className="flex items-center gap-2 text-lg font-bold text-slate-900">
                            <Printer aria-hidden="true" className="h-5 w-5 text-teal-700" />In kết luận
                        </h2>
                        <div className="mt-3 min-h-0 flex-1 overflow-y-auto">
                            {!preview && !error && <p role="status" className="text-sm text-slate-600">Đang tải phiếu kết luận…</p>}
                            {preview && <ConclusionSlip slip={preview} />}
                        </div>
                        {error && <p role="alert" className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800">{error}</p>}
                        <div className="mt-4 flex justify-end gap-3">
                            <button type="button" onClick={close} className={BUTTON.neutral}>Đóng</button>
                            <button type="button" onClick={() => { void print(); }} disabled={!preview || printing} className={BUTTON.primary}>
                                {printing && <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />}
                                {printing ? 'Đang chuẩn bị phiếu…' : 'In'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {job && createPortal(
                <div className="conclusion-print hidden print:block">
                    <ConclusionSlip slip={job.slip} />
                </div>,
                document.body,
            )}
        </>
    );
}

export default PrintConclusionButton;
