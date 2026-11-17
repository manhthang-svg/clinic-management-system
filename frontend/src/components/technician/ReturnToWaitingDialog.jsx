import { useState } from 'react';
import { RotateCcw, X } from 'lucide-react';

import { RETURN_REASONS, RETURN_REASON_MAX_LENGTH, validateReturnReason } from './performOrder';

// UC-66: hop thoai tra dich vu ve hang cho kem ly do
export function ReturnToWaitingDialog({ order, onClose, onConfirm }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!order) return null;

  const submit = async (event) => {
    event.preventDefault();
    const found = validateReturnReason(reason);
    setError(found);
    if (found) return;
    setSubmitting(true);
    try {
      await onConfirm(order, reason.trim());
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-60 flex items-center justify-center bg-slate-900/50 p-4">
      <div role="dialog" aria-modal="true" aria-labelledby="return-to-waiting-title" className="w-full max-w-md overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-amber-100 bg-amber-50/70 px-5 py-4">
          <div>
            <h3 id="return-to-waiting-title" className="flex items-center gap-2 text-sm font-bold text-amber-950">
              <RotateCcw className="h-4 w-4 text-amber-600" />Trả chỉ định về hàng chờ
            </h3>
            <p className="mt-0.5 text-xs text-amber-800">
              BN: <strong>{order.patientName}</strong> • {order.serviceName}
            </p>
          </div>
          <button type="button" aria-label="Đóng" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-amber-100 hover:text-slate-700">
            <X className="h-4 w-4" />
          </button>
        </div>
        <form noValidate onSubmit={submit} className="space-y-3 p-5 text-xs">
          <p className="text-slate-600">
            Bệnh nhân quay lại hàng chờ ở đúng số thứ tự cũ. Lý do được ghi lại trong nhật ký.
          </p>
          <div className="flex flex-wrap gap-1.5">
            {RETURN_REASONS.map((item) => (
              <button key={item} type="button" onClick={() => { setReason(item); setError(''); }} className="rounded-lg border border-slate-200 px-2 py-1 text-left text-slate-700 hover:border-amber-300 hover:bg-amber-50">
                {item}
              </button>
            ))}
          </div>
          <div>
            <label htmlFor="return-to-waiting-reason" className="mb-1 block font-semibold text-slate-700">Lý do *</label>
            <textarea
              id="return-to-waiting-reason"
              rows={3}
              value={reason}
              maxLength={RETURN_REASON_MAX_LENGTH}
              onChange={(event) => { setReason(event.target.value); setError(''); }}
              aria-invalid={error ? true : undefined}
              className="w-full rounded-lg border border-slate-200 px-3 py-2 focus:border-amber-400 focus:outline-none"
            />
            {error && <p role="alert" className="mt-1 text-rose-600">{error}</p>}
          </div>
          <div className="flex justify-end gap-2 pt-1">
            <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-4 py-2 font-semibold text-slate-600">Hủy</button>
            <button type="submit" disabled={submitting} className="rounded-lg bg-amber-600 px-4 py-2 font-semibold text-white hover:bg-amber-700 disabled:opacity-50">
              {submitting ? 'Đang trả về…' : 'Trả về hàng chờ'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default ReturnToWaitingDialog;
