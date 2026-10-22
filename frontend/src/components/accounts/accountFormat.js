// Display helpers shared by the account management components.

const AVATAR_TONES = {
  ADMIN: 'bg-violet-100 text-violet-700',
  DOCTOR: 'bg-teal-100 text-teal-700',
  RECEPTIONIST: 'bg-blue-100 text-blue-700',
  TECHNICIAN: 'bg-amber-100 text-amber-700',
  CASHIER: 'bg-rose-100 text-rose-700',
};

export function avatarTone(role) {
  return AVATAR_TONES[role] || 'bg-slate-100 text-slate-600';
}

/** "Trần Văn An" → "TA" (first and last word). */
export function initialsOf(fullName = '') {
  const words = fullName.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  const first = words[0][0];
  const last = words.length > 1 ? words[words.length - 1][0] : '';
  return (first + last).toUpperCase();
}

/** Lowercase, strip Vietnamese diacritics (đ → d) so "nguyen" matches "Nguyễn" (UC-5). */
export function normalizeSearch(value = '') {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();
}

const DATE_FORMAT = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
const TIME_FORMAT = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false });

export function formatDate(iso) {
  return iso ? DATE_FORMAT.format(new Date(iso)) : '—';
}

export function formatTime(iso) {
  return iso ? TIME_FORMAT.format(new Date(iso)) : '';
}

/** "0901000002" → "0901 000 002". */
export function formatPhone(phone = '') {
  const digits = phone.replace(/\D/g, '');
  return digits.length === 10 ? `${digits.slice(0, 4)} ${digits.slice(4, 7)} ${digits.slice(7)}` : phone;
}

export function percentOf(part, total) {
  if (!total) return '0%';
  return `${((part / total) * 100).toFixed(1).replace('.0', '')}%`;
}
