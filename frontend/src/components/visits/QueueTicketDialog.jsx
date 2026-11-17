import { useEffect, useState } from 'react';
import { Printer, X } from 'lucide-react';
import defaultVisitApi from '../../apis/visitApi';
import { VisitTicketA5 } from './VisitTicket';
import { ticketErrorMessage } from './visitErrors';

/**
 * I1-D48, UC-41, UC-71: once the examination fee of a Dịch vụ visit is paid, the Cashier prints the examination
 * ticket (the same A5 ticket as for a BHYT visit) that now carries the queue number. Before payment the patient only
 * had the payment guide from the reception desk.
 */
export function QueueTicketDialog({ visitId, onClose, visitApi = defaultVisitApi }) {
  const [ticket, setTicket] = useState(null);
  const [loading, setLoading] = useState(true);
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    // The dialog is mounted per visit, so loading starts true and needs no reset here.
    const controller = new AbortController();
    visitApi.getTicket(visitId, controller.signal)
      .then((data) => setTicket(data))
      .catch((loadError) => {
        if (!controller.signal.aborted) setError(ticketErrorMessage(loadError));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [visitApi, visitId]);

  const print = async () => {
    setPrinting(true);
    setError(null);
    try {
      // Loads the current ticket before the browser print dialog opens (UC-41 AF-02; the print is not logged).
      setTicket(await visitApi.prepareTicketPrint(visitId));
      window.setTimeout(() => window.print(), 0);
    } catch (printError) {
      setError(ticketErrorMessage(printError));
    } finally {
      setPrinting(false);
    }
  };

  const hasNumber = ticket?.queueNumber != null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
      {/* While the dialog is open, the browser prints the ticket on one A5 page. */}
      <style>{'@media print { @page { size: A4 portrait; margin: 0; } }'}</style>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="queue-ticket-title"
        className="relative w-full max-w-3xl max-h-[92vh] overflow-y-auto rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <h3 id="queue-ticket-title" className="font-bold text-slate-800">Phiếu khám</h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng hộp thoại"
            className="rounded-lg p-1 text-slate-500 hover:bg-slate-100"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="space-y-4 p-6">
          {loading && <p className="text-sm text-slate-500">Đang tải phiếu khám…</p>}
          {error && (
            <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</p>
          )}
          {ticket && !hasNumber && (
            <p className="text-sm text-slate-600">Lượt khám chưa có số thứ tự vì phí khám chưa được thanh toán.</p>
          )}
          {ticket && hasNumber && (
            <div className="flex justify-center rounded-2xl bg-slate-100 px-2 py-4">
              <VisitTicketA5 ticket={ticket} />
            </div>
          )}
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="inline-flex min-h-11 items-center rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              Đóng
            </button>
            <button
              type="button"
              onClick={() => void print()}
              disabled={!hasNumber || printing}
              className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-[#127e7a] px-5 py-2.5 text-sm font-semibold text-white hover:bg-[#0e6b67] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Printer className="h-4 w-4" />
              {printing ? 'Đang in…' : 'In phiếu khám'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
