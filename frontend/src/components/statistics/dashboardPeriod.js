import { ApiError } from '../../apis/apiError';

// khoang thoi gian toi da (giong backend)
export const MAX_PERIOD_DAYS = 366;

const DAY_MS = 24 * 60 * 60 * 1000;

// ngay theo gio may dang YYYY-MM-DD
export function toIsoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function parseIsoDate(value) {
  const [y, m, d] = value.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

// cac lua chon nhanh: hom nay, 7 ngay qua, thang nay, nam nay
export const PRESETS = [
  { id: 'today', label: 'Hôm nay', range: (today) => ({ from: toIsoDate(today), to: toIsoDate(today) }) },
  {
    id: 'last7',
    label: '7 ngày qua',
    range: (today) => {
      const start = new Date(today);
      start.setDate(start.getDate() - 6);
      return { from: toIsoDate(start), to: toIsoDate(today) };
    },
  },
  {
    id: 'month',
    label: 'Tháng này',
    range: (today) => ({
      from: toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1)),
      to: toIsoDate(today),
    }),
  },
  {
    id: 'year',
    label: 'Năm nay',
    range: (today) => ({ from: toIsoDate(new Date(today.getFullYear(), 0, 1)), to: toIsoDate(today) }),
  },
];

// kiem tra khoang ngay (giong backend), hop le thi tra ''
export function validatePeriod({ from, to }) {
  if (!from || !to) return 'Vui lòng chọn đủ ngày bắt đầu và ngày kết thúc.';
  const days = Math.round((parseIsoDate(to) - parseIsoDate(from)) / DAY_MS) + 1;
  if (days < 1) return 'Ngày bắt đầu không được sau ngày kết thúc.';
  if (days > MAX_PERIOD_DAYS) return `Khoảng thời gian không được vượt quá ${MAX_PERIOD_DAYS} ngày.`;
  return '';
}

// doi loi tai thong ke thanh thong bao
export function dashboardError(error) {
  if (!(error instanceof ApiError)) return 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
  if (error.errorResponse?.code === 'REQUEST_VALIDATION_FAILED') {
    return 'Khoảng thời gian không hợp lệ. Vui lòng chọn lại.';
  }
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Bạn không có quyền xem thống kê.';
  if (!error.errorResponse?.code && error.status === undefined) {
    return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  }
  return 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
}

const numberFormat = new Intl.NumberFormat('vi-VN');

export function formatNumber(value) {
  return numberFormat.format(Number(value ?? 0));
}

export function formatMoney(value) {
  return `${numberFormat.format(Number(value ?? 0))} ₫`;
}

// tien rut gon cho truc bieu do: 1,2 ty / 3,5 tr / 350 N
export function formatMoneyShort(value) {
  const n = Number(value ?? 0);
  const short = (amount) => amount.toLocaleString('vi-VN', { maximumFractionDigits: 1 });
  if (Math.abs(n) >= 1_000_000_000) return `${short(n / 1_000_000_000)} tỷ`;
  if (Math.abs(n) >= 1_000_000) return `${short(n / 1_000_000)} tr`;
  if (Math.abs(n) >= 1_000) return `${Math.round(n / 1_000).toLocaleString('vi-VN')} N`;
  return numberFormat.format(n);
}

// hien thang dang MM/yyyy
export function formatMonth(iso) {
  const [y, m] = iso.split('-');
  return `${m}/${y}`;
}

// hien ngay dang dd/MM (truc bieu do)
export function formatDayShort(iso) {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}

// hien ngay dang dd/MM/yyyy
export function formatDayLong(iso) {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}
