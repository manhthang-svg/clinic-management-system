import { useState } from 'react';
import { RotateCcw, X, Lock, User, Clock, MapPin, AlertCircle, Loader2 } from 'lucide-react';

/**
 * Modal xác nhận Tiếp nhận lại bệnh nhân / chỉ định bị bỏ qua (UC-46)
 *
 * @param {Object} props
 * @param {boolean} props.isOpen
 * @param {Function} props.onClose
 * @param {Object} props.order Bệnh nhân / chỉ định bị bỏ qua
 * @param {Object} props.room Phòng máy hiện tại
 * @param {Function} props.onConfirmReaccept (order, note) => Promise<void>
 */
export function ReacceptSkippedModal({ isOpen, onClose, order, room, onConfirmReaccept }) {
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen || !order) return null;

  // Xác định bước xử lý tiếp theo (Read-only, cố định theo UC-46)
  const resolveNextStep = (status) => {
    switch (status) {
      case 'PENDING_RESULT':
        return 'Chờ nhập kết quả cận lâm sàng';
      case 'IN_PROGRESS':
        return 'Đang thực hiện cận lâm sàng';
      case 'WAITING':
      default:
        return 'Chờ thực hiện cận lâm sàng (quay lại hàng đợi gọi vào phòng)';
    }
  };

  const nextStepLabel = resolveNextStep(order.status);
  const queueNumStr = order.queueNumber != null
    ? (order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber)
    : '---';

  const isInsurance = order.insuranceType === 'BHYT';
  const roomName = room?.name || order.roomName || 'Phòng cận lâm sàng';
  const roomCode = room?.code || order.roomCode || '';

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (submitting) return;
    try {
      setSubmitting(true);
      await onConfirmReaccept(order, note.trim());
      setNote('');
      onClose();
    } catch {
      // Error handled in parent toast
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-150">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="reaccept-modal-title"
        className="w-full max-w-lg bg-white rounded-2xl border border-slate-200 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
      >
        {/* Header Modal */}
        <div className="flex items-center justify-between border-b border-teal-100 bg-teal-50/70 px-5 py-3.5">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-teal-100 text-teal-800 flex items-center justify-center shrink-0">
              <RotateCcw className="w-4 h-4" />
            </div>
            <div>
              <h3 id="reaccept-modal-title" className="text-sm font-bold text-teal-950">
                Tiếp nhận lại bệnh nhân
              </h3>
              <p className="text-[11px] text-teal-800 mt-0.5">
                Khôi phục lượt khám/chỉ định quay trở lại hàng đợi phục vụ
              </p>
            </div>
          </div>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            disabled={submitting}
            className="w-7 h-7 flex items-center justify-center rounded-lg text-slate-400 hover:text-slate-700 hover:bg-teal-100 transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Nội dung form */}
        <form onSubmit={handleSubmit} className="p-5 overflow-y-auto space-y-4 text-xs text-slate-700">
          {/* 1. Tóm tắt thông tin bệnh nhân */}
          <div className="bg-slate-50 border border-slate-200/80 rounded-xl p-3.5 space-y-2">
            <div className="flex items-start justify-between gap-2 border-b border-slate-200/60 pb-2">
              <div>
                <span className="text-[10.5px] uppercase font-bold text-slate-400">Bệnh nhân</span>
                <h4 className="text-sm font-extrabold text-slate-900 uppercase tracking-tight">
                  {order.patientName}
                </h4>
                <div className="text-[11px] text-slate-500 mt-0.5">
                  Mã BN: <strong className="font-mono text-slate-700">{order.patientId || '—'}</strong>
                  {order.age ? ` • ${order.age} tuổi` : ''}
                  {order.gender ? ` • ${order.gender}` : ''}
                </div>
              </div>

              {/* Badge STT */}
              <div className="text-right shrink-0">
                <span className="text-[10px] text-slate-400 block font-semibold uppercase">Số thứ tự</span>
                <span className="inline-block font-extrabold text-teal-900 bg-teal-100/80 px-2.5 py-0.5 rounded-lg border border-teal-300 text-sm">
                  #{queueNumStr}
                </span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2 pt-1 text-[11px]">
              <div>
                <span className="text-slate-400">Phòng thực hiện:</span>
                <p className="font-semibold text-slate-800">
                  {roomCode ? `${roomCode} - ` : ''}{roomName}
                </p>
              </div>
              <div>
                <span className="text-slate-400">Loại khám:</span>
                <p>
                  <span
                    className={`inline-block px-2 py-0.5 rounded text-[10.5px] font-bold ${
                      isInsurance
                        ? 'bg-teal-50 text-teal-700 border border-teal-200'
                        : 'bg-slate-100 text-slate-700 border border-slate-200'
                    }`}
                  >
                    {isInsurance ? 'BHYT' : (order.insuranceType || 'Dịch vụ / Viện phí')}
                  </span>
                </p>
              </div>
              <div>
                <span className="text-slate-400">Thời điểm chỉ định:</span>
                <p className="font-medium text-slate-700">{order.orderTime || '—'}</p>
              </div>
              <div>
                <span className="text-slate-400">Thời điểm bỏ qua:</span>
                <p className="font-bold text-rose-600">
                  {order.skippedAtTime || order.completedAtTime || '—'}
                </p>
              </div>
            </div>

            {/* Lý do bỏ qua */}
            <div className="pt-1.5 border-t border-slate-200/60 text-[11px]">
              <span className="text-slate-400">Lý do bỏ qua trước đó:</span>
              <p className="font-medium text-amber-800 bg-amber-50/80 px-2 py-1 rounded border border-amber-200/60 mt-0.5">
                {order.cancelReason || 'Bệnh nhân tạm thời vắng mặt khi được gọi vào phòng.'}
              </p>
            </div>
          </div>

          {/* 2. Bước xử lý tiếp theo (Read-only Cố định) */}
          <div className="space-y-1.5">
            <label className="flex items-center gap-1.5 font-bold text-slate-800 text-xs">
              <Lock className="w-3.5 h-3.5 text-slate-400" />
              <span>Bước xử lý tiếp theo (Tự động khôi phục - Cố định)</span>
            </label>
            <div className="bg-slate-100/90 border border-slate-200 rounded-xl px-3.5 py-2.5 text-xs font-semibold text-slate-800 flex items-center justify-between">
              <span>{nextStepLabel}</span>
              <span className="text-[10.5px] font-bold text-teal-700 bg-teal-50 px-2 py-0.5 rounded border border-teal-200">
                Cố định
              </span>
            </div>
            <p className="text-[10.5px] text-slate-400 flex items-center gap-1 mt-1">
              <AlertCircle className="w-3 h-3 text-slate-400 shrink-0" />
              <span>
                Hệ thống tự động đưa bệnh nhân về đúng giai đoạn trước khi bị gián đoạn, người dùng không được tự ý đổi bước.
              </span>
            </p>
          </div>

          {/* 3. Ghi chú tiếp nhận lại (Tùy chọn) */}
          <div className="space-y-1.5">
            <label htmlFor="reaccept-note-input" className="block font-bold text-slate-800 text-xs">
              Ghi chú tiếp nhận lại <span className="text-slate-400 font-normal">(Tùy chọn)</span>
            </label>
            <textarea
              id="reaccept-note-input"
              rows={2}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Ví dụ: Bệnh nhân đã quay lại phòng sau khi đi lấy mẫu hoặc đóng phí..."
              className="w-full bg-white border border-slate-200 rounded-xl p-2.5 text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0d9488] focus:border-transparent transition-all"
            />
          </div>

          {/* Nút hành động */}
          <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="h-9 px-4 rounded-xl border border-slate-200 text-xs font-bold text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer disabled:opacity-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="h-9 px-4 rounded-xl bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold shadow-2xs inline-flex items-center gap-1.5 transition-all cursor-pointer active:scale-95 disabled:opacity-50"
            >
              {submitting ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Đang tiếp nhận...</span>
                </>
              ) : (
                <>
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Xác nhận tiếp nhận lại</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default ReacceptSkippedModal;

