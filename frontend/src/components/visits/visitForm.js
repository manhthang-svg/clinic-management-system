// UC-40 Register Examination: form state, client checks, and the POST /visits body.
// Client checks mirror CreateVisitRequest for feedback only; the backend decides.

export const MAX_REASON_LENGTH = 500;
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

/** UC-74 step 1: exactly 15 characters, benefit code (3rd character) from 1 to 5. */
export function isCardNumberFormatValid(cardNumber) {
  return cardNumber.length === CARD_NUMBER_LENGTH && /^[A-Z0-9]+$/.test(cardNumber) && /[1-5]/.test(cardNumber[2]);
}

/** UC-74 step 6: coverage rate from the benefit code. */
export function coverageRateOf(card) {
  if (typeof card?.coverageRate === 'number') return card.coverageRate;
  const code = String(card?.benefitCode ?? '');
  if (['1', '2', '5'].includes(code)) return 100;
  if (code === '3') return 95;
  if (code === '4') return 80;
  return null;
}

/** The card was verified, or recorded as unverified (AF-03), for the number now in the field. */
export function isInsuranceSettled(form, insurance) {
  return insurance.cardNumber === form.cardNumber && (insurance.status === 'VALID' || insurance.status === 'UNVERIFIED');
}

export function validateVisitForm(form, insurance) {
  const errors = {};

  if (form.isPriority && !form.priorityReason) errors.priorityReason = 'Vui lòng chọn lý do ưu tiên.';

  if (!form.visitType) errors.visitType = 'Vui lòng chọn loại lượt khám.';
  else if (form.visitType === 'BHYT') {
    if (!form.cardNumber) errors.cardNumber = 'Vui lòng nhập số thẻ BHYT.';
    else if (!isCardNumberFormatValid(form.cardNumber)) errors.cardNumber = 'Số thẻ BHYT không đúng định dạng.';
    else if (!isInsuranceSettled(form, insurance)) errors.cardNumber = 'Vui lòng xác minh thẻ BHYT.';
  }

  const reason = form.reason.trim();
  if (!reason) errors.reason = 'Vui lòng nhập lý do khám.';
  else if (reason.length > MAX_REASON_LENGTH) errors.reason = `Vượt quá độ dài tối đa ${MAX_REASON_LENGTH} ký tự.`;

  if (!form.roomId) errors.roomId = 'Vui lòng chọn phòng khám.';

  return errors;
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
    noigioithieuLoai: form.referralSource,
    isPriority: form.isPriority,
    priorityReason: form.isPriority ? form.priorityReason : null,
    // ASSUMPTION: not in CreateVisitRequest yet; needed for AF-03 (BR-45) and AF-04 (BR-34).
    insuranceVerified: insured ? insurance.status === 'VALID' : null,
    followUpAppointmentId: form.followUpAppointmentId,
  };
}
