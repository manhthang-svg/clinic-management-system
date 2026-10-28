import { useState } from 'react';
import { CalendarClock, CalendarCheck2, Info, Link2Off } from 'lucide-react';
import { INPUT_CLASS, Field } from '../shared/FormFields';
import { daysBetween } from './visitForm';
import { formatDate } from './visitFormat';

const MAX_CANCEL_REASON_LENGTH = 255;

const OUTLINE_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed';
const PRIMARY_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-2 px-3.5 py-2 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed';

/**
 * UC-40 AF-04 / AF-05 / AF-06: the patient's scheduled follow-up appointment.
 * Shows no clinical content (diagnosis, advice).
 */
export function VisitAppointmentCard({ appointment, today, attached, onAttach, onDetach, onCancel }) {
  // null | 'other' | 'cancelling'
  const [decision, setDecision] = useState(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelError, setCancelError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  const daysLate = daysBetween(appointment.appointmentDate, today);

  // AF-05: an appointment on a later day is only a note.
  if (daysLate < 0) {
    return (
      <p className="flex items-start gap-2 p-3.5 rounded-xl bg-[#e8f0fe] border border-[#cfdffb] text-sm text-[#3b6fd8]">
        <Info className="w-4 h-4 shrink-0 mt-0.5" />
        Có lịch hẹn tái khám ngày {formatDate(appointment.appointmentDate)}.
      </p>
    );
  }

  const submitCancel = async () => {
    const reason = cancelReason.trim();
    if (!reason) {
      setCancelError('Vui lòng nhập lý do.');
      return;
    }
    setCancelling(true);
    setCancelError(null);
    try {
      await onCancel(reason);
    } catch {
      setCancelError('Không hủy được lịch hẹn. Vui lòng thử lại.');
      setCancelling(false);
    }
  };

  return (
    <section className="rounded-2xl border border-[#e2d9fb] bg-[#f8f6fe] p-5 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#f1edfd] text-[#7c5cdb] flex items-center justify-center shrink-0">
            <CalendarClock className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">Lịch hẹn tái khám</h2>
            <p className="text-xs text-slate-500 mt-0.5">Hỏi bệnh nhân lý do đến khám hôm nay</p>
          </div>
        </div>
        {attached && (
          <span className="inline-flex items-center gap-1.5 self-start px-2.5 py-1 rounded-lg text-xs font-semibold bg-[#7c5cdb] text-white">
            <CalendarCheck2 className="w-3.5 h-3.5" />
            Đã gắn với lượt khám này
          </span>
        )}
      </div>

      <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
        <div>
          <dt className="text-xs text-slate-500">Ngày hẹn</dt>
          <dd className="font-semibold text-slate-800 mt-0.5">
            {formatDate(appointment.appointmentDate)}
            {daysLate > 0 && (
              <span className="ml-2 inline-flex px-2 py-0.5 rounded-md text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                Đến muộn {daysLate} ngày
              </span>
            )}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Bác sĩ hẹn</dt>
          <dd className="font-semibold text-slate-800 mt-0.5">{appointment.doctorName || '—'}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Phòng gợi ý</dt>
          <dd className="font-semibold text-slate-800 mt-0.5">{appointment.suggestedRoomName || '—'}</dd>
        </div>
      </dl>

      {attached ? (
        <div className="flex flex-wrap items-center gap-2.5">
          <p className="text-xs text-slate-600 flex-1 min-w-[200px]">
            Phòng gợi ý đã được chọn sẵn; vẫn có thể đổi phòng bên dưới.
          </p>
          <button type="button" onClick={onDetach} className={OUTLINE_BUTTON_CLASS}>
            <Link2Off className="w-4 h-4" />
            Bỏ gắn
          </button>
        </div>
      ) : decision === 'other' ? (
        <div className="flex flex-wrap items-center gap-2.5">
          <p className="text-xs text-slate-600 flex-1 min-w-[200px]">
            Khám vấn đề khác: loại tiếp nhận giữ "Khám bệnh", lịch hẹn vẫn ở trạng thái Đã hẹn.
          </p>
          <button type="button" onClick={() => setDecision(null)} className={OUTLINE_BUTTON_CLASS}>
            Chọn lại
          </button>
        </div>
      ) : decision === 'cancelling' ? (
        <div className="space-y-3">
          <Field id="cancel-appointment-reason" label="Lý do không tái khám" required error={cancelError}>
            <textarea
              id="cancel-appointment-reason"
              rows={2}
              value={cancelReason}
              maxLength={MAX_CANCEL_REASON_LENGTH}
              onChange={(e) => {
                setCancelReason(e.target.value);
                setCancelError(null);
              }}
              aria-invalid={Boolean(cancelError)}
              aria-describedby={cancelError ? 'cancel-appointment-reason-error' : undefined}
              className={`${INPUT_CLASS} resize-none`}
            />
          </Field>
          <div className="flex flex-wrap justify-end gap-2.5">
            <button type="button" onClick={() => setDecision(null)} disabled={cancelling} className={OUTLINE_BUTTON_CLASS}>
              Quay lại
            </button>
            <button
              type="button"
              onClick={submitCancel}
              disabled={cancelling}
              className="inline-flex items-center justify-center gap-2 px-3.5 py-2 text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {cancelling ? 'Đang hủy…' : 'Hủy lịch hẹn'}
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2.5">
          <button type="button" onClick={onAttach} className={PRIMARY_BUTTON_CLASS}>
            <CalendarCheck2 className="w-4 h-4" />
            Đến tái khám
          </button>
          <button type="button" onClick={() => setDecision('other')} className={OUTLINE_BUTTON_CLASS}>
            Khám vấn đề khác
          </button>
          <button
            type="button"
            onClick={() => setDecision('cancelling')}
            className="inline-flex items-center justify-center gap-2 px-3.5 py-2 text-xs font-semibold text-rose-600 bg-white border border-rose-200 rounded-xl hover:bg-rose-50 cursor-pointer"
          >
            Bệnh nhân không tái khám nữa
          </button>
        </div>
      )}
    </section>
  );
}
