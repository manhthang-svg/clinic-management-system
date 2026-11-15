// UC-40 Register Examination: form state, client checks, and the POST /visits body.
// Client checks mirror CreateVisitRequest for feedback only; the backend decides.

export const CARD_NUMBER_LENGTH = 15;
// BR-129: chẩn đoán của nơi giới thiệu, 2–1.000 ký tự.
export const MIN_REFERRAL_DIAGNOSIS_LENGTH = 2;
export const MAX_REFERRAL_DIAGNOSIS_LENGTH = 1000;
// BR-94: nơi giới thiệu "Y tế" (TT 32/2023/TT-BYT mẫu 15/BV1 mục I.13).
export const REFERRAL_HEALTH_FACILITY = 'CO_SO_Y_TE';

export const EMPTY_VISIT_FORM = {
  isPriority: false,
  priorityReason: '',
  visitType: '',
  cardNumber: '',
  reason: '',
  referralSource: 'TU_DEN',
  referralDiagnosis: '',
  roomId: '',
  // Set when the receptionist attaches a follow-up appointment (AF-04).
  followUpAppointmentId: null,
  followUpAppointmentVersion: null,
};

// No insurance check has run for the current card number.
export const NO_INSURANCE_CHECK = { cardNumber: '', status: null, card: null };

const CLINIC_TIME_ZONE = 'Asia/Ho_Chi_Minh';
const DAY_MS = 24 * 60 * 60 * 1000;

/** Today in the clinic time zone as YYYY-MM-DD. */
export function clinicToday() {
  return new Date().toLocaleDateString('en-CA', { timeZone: CLINIC_TIME_ZONE });
}

/** Whole days from `from` to `to`, both YYYY-MM-DD. */
export function daysBetween(from, to) {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS);
}

/** BR-50: a 10-digit social-insurance number or a 15-character legacy card number. */
export function isCardNumberFormatValid(cardNumber) {
  return /^(?:\d{10}|[A-Z]{2}\d{13})$/.test(cardNumber);
}

export const coverageRateOf = (card) => typeof card?.coverageRate === 'number' ? card.coverageRate : null;

/** The current code has a successful portal verification. */
export function isInsuranceSettled(form, insurance) {
  return insurance.cardNumber === form.cardNumber && insurance.status === 'VALID';
}

/** MSG02: a required field is empty (UC-40 EF-02). */
const msg02 = (fieldName) => `${fieldName} là bắt buộc.`;

export function validateVisitForm(form, insurance) {
  const errors = {};

  if (form.isPriority && !form.priorityReason) errors.priorityReason = msg02('Lý do ưu tiên');

  if (!form.visitType) errors.visitType = msg02('Loại lượt khám');
  else if (form.visitType === 'BHYT') {
    if (!form.cardNumber) errors.cardNumber = msg02('Số thẻ BHYT');
    else if (!isCardNumberFormatValid(form.cardNumber)) errors.cardNumber = 'Số thẻ BHYT không đúng định dạng.';
    else if (!isInsuranceSettled(form, insurance)) errors.cardNumber = 'Vui lòng xác minh thẻ BHYT.';
  }

  const reason = form.reason.trim();
  // No length limit: visits.lydovaovien is TEXT.
  if (!reason) errors.reason = msg02('Lý do khám');

  // BR-129: required when referred by a health facility; the field is hidden for "Tự đến" and follow-ups.
  if (referralSourceOf(form) === REFERRAL_HEALTH_FACILITY) {
    const diagnosis = form.referralDiagnosis.trim();
    if (!diagnosis) errors.referralDiagnosis = msg02('Chẩn đoán của nơi giới thiệu');
    else if (diagnosis.length < MIN_REFERRAL_DIAGNOSIS_LENGTH) {
      errors.referralDiagnosis = `Nhập tối thiểu ${MIN_REFERRAL_DIAGNOSIS_LENGTH} ký tự.`;
    } else if (diagnosis.length > MAX_REFERRAL_DIAGNOSIS_LENGTH) {
      errors.referralDiagnosis = `Vượt quá độ dài tối đa ${MAX_REFERRAL_DIAGNOSIS_LENGTH} ký tự.`;
    }
  }

  if (!form.roomId) errors.roomId = msg02('Phòng khám');

  return errors;
}

/** A follow-up visit comes on the clinic's own appointment, so its referral source is always "Tự đến". */
export function referralSourceOf(form) {
  return form.followUpAppointmentId ? 'TU_DEN' : form.referralSource;
}

/** Body of POST /visits (CreateVisitRequest). */
export function formToVisitRequest(form, patientId, insurance) {
  const insured = form.visitType === 'BHYT';
  return {
    patientId,
    roomId: form.roomId,
    doituongbnLoai: form.visitType,
    mabhyt: insured ? form.cardNumber : null,
    lydovaovien: form.reason.trim(),
    lydotiepnhan: form.followUpAppointmentId ? 'FOLLOW_UP' : 'EXAMINATION',
    noigioithieuLoai: referralSourceOf(form),
    chandoancuanoigioithieu: referralSourceOf(form) === REFERRAL_HEALTH_FACILITY
      ? form.referralDiagnosis.trim()
      : null,
    isPriority: form.isPriority,
    priorityReason: form.isPriority ? form.priorityReason : null,
    followUpAppointmentId: form.followUpAppointmentId,
    followUpAppointmentVersion: form.followUpAppointmentVersion,
  };
}
