// UC-40 Register Examination: form state, client checks, and the POST /visits body.
// Client checks mirror CreateVisitRequest for feedback only; the backend decides.

export const CARD_NUMBER_LENGTH = 15;

export const EMPTY_VISIT_FORM = {
  isPriority: false,
  priorityReason: '',
  visitType: '',
  cardNumber: '',
  reason: '',
  referralSource: 'TU_DEN',
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

/** BR-124: the appointment's validity period (`validUntil`, from the backend) ended before `today`. */
export function isAppointmentExpired(appointment, today) {
  return Boolean(appointment?.validUntil) && daysBetween(appointment.validUntil, today) > 0;
}

/** BR-124 (UC-40 AF-05): the appointment can be linked today, from its date to the end of its validity period. */
export function isAppointmentUsable(appointment, today) {
  return Boolean(appointment) && daysBetween(appointment.appointmentDate, today) >= 0
    && !isAppointmentExpired(appointment, today);
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
    // BR-129: the Doctor enters the referring facility's diagnosis during the examination.
    chandoancuanoigioithieu: null,
    isPriority: form.isPriority,
    priorityReason: form.isPriority ? form.priorityReason : null,
    followUpAppointmentId: form.followUpAppointmentId,
    followUpAppointmentVersion: form.followUpAppointmentVersion,
  };
}
