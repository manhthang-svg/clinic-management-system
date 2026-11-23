import { useId } from 'react';
import { CheckCircle2, ListOrdered } from 'lucide-react';

import { BUTTON } from './buttonStyles';
import { PrintConclusionButton } from './PrintConclusionButton';
import { PrintFollowUpButton } from './PrintFollowUpButton';
import { PrintPrescriptionButton } from './PrintPrescriptionButton';

/**
 * UC-57, UC-60, UC-62: the slips of a closed record, printed by the Doctor of the visit (PO 23/11/2026). The
 * conclusion always; the prescription when the visit has one, which is accepted once the record is closed (BR-32); the
 * follow-up slip when the visit has an appointment, while it is still scheduled (MSG128).
 */
export function ClosedRecordPrintActions({ visitId, prescriptionId, appointmentId, appointmentScheduled = true, api }) {
    return (
        <div className="flex flex-wrap items-center gap-2">
            <PrintConclusionButton visitId={visitId} api={api} />
            {prescriptionId && <PrintPrescriptionButton prescriptionId={prescriptionId} accepted api={api} />}
            {appointmentId && <PrintFollowUpButton appointmentId={appointmentId} disabled={!appointmentScheduled} api={api} />}
        </div>
    );
}

/** UC-58 then UC-57, UC-60, UC-62: right after closing, the Doctor prints the slips before going back to the list. */
export function ClosedRecordPrintDialog({ closure, api, onDone }) {
    const titleId = useId();
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
            <div role="dialog" aria-modal="true" aria-labelledby={titleId} className="w-full max-w-xl space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl">
                <div>
                    <h2 id={titleId} className="flex items-center gap-2 text-lg font-bold text-slate-900">
                        <CheckCircle2 aria-hidden="true" className="h-5 w-5 text-emerald-600" />Đã đóng hồ sơ bệnh án
                    </h2>
                    <p className="mt-1 text-sm text-slate-600">
                        Lượt khám <strong className="font-mono text-slate-900">{closure.visitCode}</strong>. In các phiếu giao cho người bệnh.
                    </p>
                </div>
                <ClosedRecordPrintActions
                    visitId={closure.visitId}
                    prescriptionId={closure.prescriptionId}
                    appointmentId={closure.followUpAppointmentId}
                    api={api}
                />
                <div className="flex justify-end border-t border-slate-100 pt-4">
                    <button type="button" onClick={onDone} className={BUTTON.primary}>
                        <ListOrdered aria-hidden="true" className="h-4 w-4" />Về danh sách chờ
                    </button>
                </div>
            </div>
        </div>
    );
}
