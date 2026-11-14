import { PATIENT_GENDER_LABELS } from '../../constants/visitData';

/** YYYY-MM-DD (or an ISO instant) as dd/mm/yyyy. */
export function formatDate(value) {
  if (!value) return '—';
  if (/^\d{4}$/.test(value)) return value;
  if (/^\d{4}-\d{2}$/.test(value)) return `${value.slice(5, 7)}/${value.slice(0, 4)}`;
  const date = value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('vi-VN');
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('vi-VN');
}

export function formatMoney(value) {
  return typeof value === 'number' ? `${value.toLocaleString('vi-VN')} đ` : '—';
}

export function genderLabel(patient) {
  return PATIENT_GENDER_LABELS[patient?.gioitinh] ?? '—';
}

/** Citizen ID, else passport number. */
export function identityNumber(patient) {
  return patient?.cccdSo || patient?.hochieuSo || '—';
}

export function roomLabel(room) {
  if (!room) return '—';
  return [room.code, room.name].filter(Boolean).join(' – ');
}

/**
 * Age on the registration day in whole years and months: "9 tháng" under one year, "2 tuổi 8 tháng" (or
 * "2 tuổi") from one year.
 */
export function ageText(dateOfBirth, at = new Date()) {
  if (!dateOfBirth) return '—';
  const birth = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(birth.getTime()) || at < birth) return '—';
  let months = (at.getFullYear() - birth.getFullYear()) * 12 + at.getMonth() - birth.getMonth();
  if (at.getDate() < birth.getDate()) months -= 1;
  const years = Math.floor(months / 12);
  const rest = months % 12;
  if (years === 0) return `${rest} tháng`;
  return rest === 0 ? `${years} tuổi` : `${years} tuổi ${rest} tháng`;
}

/**
 * I1-D48, BR-16: a Dịch vụ visit that still has to pay has no queue number, so the reception desk prints a payment
 * guide instead of an examination ticket; the Cashier prints the examination ticket after payment (UC-71).
 */
export function needsPaymentGuide(ticket) {
  return Boolean(ticket) && ticket.visitType !== 'BHYT' && ticket.queueNumber == null;
}
