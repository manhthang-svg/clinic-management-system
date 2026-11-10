import { useEffect, useState } from 'react';
import { Printer, X } from 'lucide-react';
import defaultVisitApi from '../../apis/visitApi';
import { VisitTicketSheet } from './VisitTicket';
import { ticketErrorMessage } from './visitErrors';

/**
 * I1-D48, UC-41: once the examination fee is paid, the Cashier prints the ticket that now carries the queue number,
 * so the patient knows their number and room.
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
      // Records the print (and marks a reprint) before the browser print dialog opens.
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
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="queue-ticket-title"
        className="relative w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <h3 id="queue-ticket-title" className="font-bold text-slate-800">Phiếu số thứ tự</h3>
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
          {loading && <p className="text-sm text-slate-500">Đang tải phiếu số thứ tự…</p>}
          {error && (
            <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</p>
          )}
          {ticket && !hasNumber && (
            <p className="text-sm text-slate-600">Lượt khám chưa có số thứ tự vì phí khám chưa được thanh toán.</p>
          )}
          {ticket && <VisitTicketSheet ticket={ticket} />}
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
              {printing ? 'Đang in…' : 'In phiếu số thứ tự'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
