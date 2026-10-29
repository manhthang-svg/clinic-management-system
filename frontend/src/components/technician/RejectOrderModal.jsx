import { useState } from 'react';
import { X, Ban, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { REJECT_REASONS } from '../../constants/technicianQueueData';

export function RejectOrderModal({ isOpen, onClose, order, onConfirmReject }) {
  const [selectedReason, setSelectedReason] = useState(REJECT_REASONS[0]);
  const [customNote, setCustomNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen || !order) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    setIsSubmitting(true);

    const fullReason =
      selectedReason === 'Lý do khác'
        ? customNote.trim() || 'Lý do khác chưa ghi rõ'
        : `${selectedReason}${customNote.trim() ? ` (${customNote.trim()})` : ''}`;

    setTimeout(() => {
      onConfirmReject(order.id, fullReason);
      setIsSubmitting(false);
      onClose();
    }, 250);
  };

  return (
    <div className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-150">
      <div
        className="w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-200/90 overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-rose-100 flex items-center justify-between bg-rose-50/70">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-rose-100 text-rose-700 flex items-center justify-center font-bold">
              <Ban className="w-5 h-5 text-rose-600" />
            </div>
            <div>
              <h3 className="text-base font-bold text-rose-950">
                Từ chối / Hủy thực hiện y lệnh cận lâm sàng
              </h3>
              <p className="text-xs text-rose-700">
                BN: <strong>{order.patientName}</strong> ({order.patientId}) • Phiếu: {order.orderCode}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-lg hover:bg-rose-200/50 text-slate-400 hover:text-slate-700 flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 text-xs">
          <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/70 space-y-1">
            <span className="text-slate-400 text-[11px] block">Dịch vụ hủy:</span>
            <div className="font-bold text-slate-800 text-[13px]">{order.serviceName}</div>
            <div className="text-slate-500">
              Bác sĩ chỉ định: <strong className="text-slate-700">{order.doctorName}</strong> ({order.doctorRoom})
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="block text-slate-800 font-bold text-xs">
              Lý do chống chỉ định / Từ chối tiếp nhận:
            </label>
            <div className="space-y-1.5">
              {REJECT_REASONS.map((reason) => (
                <label
                  key={reason}
                  className={`flex items-center gap-2.5 p-2.5 rounded-xl border transition-all cursor-pointer ${
                    selectedReason === reason
                      ? 'bg-rose-50/60 border-rose-300 text-rose-950 font-semibold'
                      : 'bg-white border-slate-200/80 text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <input
                    type="radio"
                    name="rejectReason"
                    value={reason}
                    checked={selectedReason === reason}
                    onChange={() => setSelectedReason(reason)}
                    className="w-3.5 h-3.5 text-rose-600 focus:ring-rose-500"
                  />
                  <span className="text-xs">{reason}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="block text-slate-800 font-bold text-xs">
              Ghi chú phản hồi gửi lại Bác sĩ chỉ định:
            </label>
            <textarea
              rows={2}
              value={customNote}
              onChange={(e) => setCustomNote(e.target.value)}
              placeholder="Nhập chi tiết tình trạng bệnh nhân hoặc chỉ dẫn tái khám..."
              className="w-full p-3 bg-white border border-slate-200 rounded-xl text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-rose-500"
            />
          </div>

          <div className="p-3 bg-amber-50 rounded-xl border border-amber-200/80 flex items-start gap-2 text-amber-900 text-[11px]">
            <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
            <span>
              Y lệnh bị từ chối sẽ được thông báo ngay lập tức về phòng khám của bác sĩ để bác sĩ điều chỉnh phác đồ hoặc đổi kỹ thuật thay thế.
            </span>
          </div>

          {/* Action buttons */}
          <div className="pt-2 flex items-center justify-end gap-2.5">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-xs font-semibold text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 rounded-xl transition-all cursor-pointer"
            >
              Hủy thao tác
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2 text-xs font-bold text-white bg-rose-600 hover:bg-rose-700 rounded-xl shadow-sm hover:shadow transition-all cursor-pointer flex items-center gap-1.5"
            >
              <Ban className="w-3.5 h-3.5" />
              <span>{isSubmitting ? 'Đang gửi...' : 'Xác nhận từ chối'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default RejectOrderModal;
