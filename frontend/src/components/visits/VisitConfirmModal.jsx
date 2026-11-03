import { useEffect } from 'react';
import { ClipboardCheck, X } from 'lucide-react';
import { INTAKE_TYPE_LABELS, PRIORITY_REASON_LABELS, VISIT_TYPE_LABELS } from '../../constants/visitData';
import { FormAlert } from '../shared/FormFields';
import { coverageRateOf } from './visitForm';
import { formatDate, formatMoney, genderLabel, roomLabel } from './visitFormat';

function Row({ label, children }) {
  return (
    <div className="grid grid-cols-[140px_minmax(0,1fr)] gap-3 py-2">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-slate-800 font-medium break-words">{children}</dd>
    </div>
  );
}

function FeeItem({ label, value }) {
  return (
    <div className="p-3 rounded-xl bg-white border border-slate-100">
      <dt className="text-[11px] text-slate-500">{label}</dt>
      <dd className="text-sm font-bold text-slate-800 mt-0.5">{formatMoney(value)}</dd>
    </div>
  );
}

// An insurance visit shows only the two prices; its amount is totalled and paid at the end of the visit.
function FeeSummary({ fee, loading, insured }) {
  if (loading) return <p role="status" className="text-sm text-slate-400">Đang tính phí khám…</p>;
  if (!fee) {
    return <p role="alert" className="text-sm text-rose-600">Không tải được phí khám. Vui lòng quay lại và thử lại.</p>;
  }
  if (insured) {
    return (
      <dl className="grid grid-cols-2 gap-3">
        <FeeItem label="Giá viện phí" value={fee.clinicPrice} />
        <FeeItem label="Giá BHYT" value={fee.appliedPrice} />
      </dl>
    );
  }
  return (
    <dl className="grid grid-cols-3 gap-3">
      <FeeItem label="Giá viện phí" value={fee.clinicPrice} />
      <FeeItem label="BHYT chi trả" value={fee.insuranceAmount ?? 0} />
      <div className="p-3 rounded-xl bg-[#e6f5f3] border border-[#bfe0dc]">
        <dt className="text-[11px] text-[#127e7a]">Bệnh nhân trả</dt>
        <dd className="text-base font-extrabold text-[#127e7a] mt-0.5">{formatMoney(fee.patientAmount)}</dd>
      </div>
    </dl>
  );
}

/** UC-40 steps 10-11: the last check with the patient before the visit is created. */
export function VisitConfirmModal({
  patient,
  form,
  insurance,
  room,
  fee,
  feeLoading,
  submitting,
  error,
  onBack,
  onCancel,
  onConfirm,
}) {
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape' && !submitting) onBack();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onBack, submitting]);

  const insured = form.visitType === 'BHYT';
  const intakeType = form.followUpAppointmentId ? 'FOLLOW_UP' : 'EXAMINATION';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="visit-confirm-title"
        className="w-full max-w-2xl max-h-[calc(100vh-2rem)] overflow-y-auto rounded-2xl bg-white shadow-2xl border border-[#e3ece9]"
      >
        <div className="flex items-start gap-3 p-5 border-b border-slate-100">
          <div className="w-10 h-10 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
            <ClipboardCheck className="w-5 h-5" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-[#127e7a]">Bước 2/2</p>
            <h2 id="visit-confirm-title" className="text-base! leading-6! font-bold! text-[#1b2533]">Xác nhận đăng ký khám</h2>
            <p className="text-xs text-slate-500 mt-0.5">Kiểm tra lại thông tin cùng bệnh nhân trước khi đăng ký</p>
          </div>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onBack}
            disabled={submitting}
            className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 cursor-pointer disabled:cursor-not-allowed"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-5">
          <dl className="divide-y divide-slate-100 text-sm">
            <Row label="Bệnh nhân">
              {patient.hoten} <span className="font-mono text-slate-400">({patient.mabenhnhan})</span>
              <div className="text-xs text-slate-500 font-normal">{formatDate(patient.ngaysinh)} · {genderLabel(patient)}</div>
            </Row>
            <Row label="Ưu tiên">{form.isPriority ? PRIORITY_REASON_LABELS[form.priorityReason] : 'Không'}</Row>
            <Row label="Loại lượt khám">
              {VISIT_TYPE_LABELS[form.visitType]}
              {insured && (
                <div className="text-xs text-slate-500 font-normal">
                  Thẻ <span className="font-mono">{form.cardNumber}</span>
                  {insurance.status === 'VALID'
                    ? ` · Mức hưởng ${coverageRateOf(insurance.card) ?? '—'}%`
                    : ' · Chưa xác minh'}
                </div>
              )}
            </Row>
            <Row label="Loại tiếp nhận">{INTAKE_TYPE_LABELS[intakeType]}</Row>
            <Row label="Phòng khám">
              {roomLabel(room)}
              {room?.location && <div className="text-xs text-slate-500 font-normal">{room.location}</div>}
            </Row>
            <Row label="Lý do khám">{form.reason.trim()}</Row>
          </dl>

          <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-3">
            <h3 className="text-sm font-bold text-slate-800">Phí khám</h3>
            <FeeSummary fee={fee} loading={feeLoading} insured={insured} />
            <p className="text-xs text-slate-500">
              {insured
                ? 'Lượt khám BHYT vào danh sách chờ khám ngay. Phí khám được tính tổng cùng các chi phí khác và thanh toán khi kết thúc lượt khám.'
                : 'Bệnh nhân thanh toán phí khám tại quầy thu ngân trước khi vào khu chờ.'}
            </p>
          </div>

          {error && <FormAlert>{error}</FormAlert>}
        </div>

        <div className="flex flex-wrap justify-between gap-3 px-5 py-4 border-t border-slate-100 bg-slate-50/60 rounded-b-2xl">
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="px-4 py-2.5 text-sm font-medium text-rose-600 bg-white border border-rose-200 rounded-xl hover:bg-rose-50 cursor-pointer disabled:cursor-not-allowed"
          >
            Hủy
          </button>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={onBack}
              disabled={submitting}
              className="px-4 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:cursor-not-allowed"
            >
              Quay lại
            </button>
            <button
              type="button"
              onClick={onConfirm}
              disabled={submitting || feeLoading || !fee}
              className="px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {submitting ? 'Đang đăng ký…' : 'Xác nhận đăng ký'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
