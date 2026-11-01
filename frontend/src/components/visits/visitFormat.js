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
