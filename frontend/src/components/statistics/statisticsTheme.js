/** Colours and labels shared by the Admin statistics pages (UC-26 → UC-32). */

export const TEAL = '#127e7a';

export const COLORS = {
  completed: TEAL,
  inProgress: '#0ea5e9',
  cancelled: '#f59e0b',
  abandoned: '#e11d48',
  insurance: TEAL,
  patient: '#f59e0b',
  selfPay: '#6366f1',
  followUp: '#8b5cf6',
  priority: '#e11d48',
};

/** Visit outcome labels; "Đang xử lý" groups every unfinished visit status. */
export const OUTCOMES = [
  { key: 'completed', label: 'Hoàn thành' },
  { key: 'inProgress', label: 'Đang xử lý' },
  { key: 'cancelled', label: 'Đã hủy' },
  { key: 'abandoned', label: 'Bỏ khám' },
];
