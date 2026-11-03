// UC-36 View Patient Information: labels and formatting. Pure functions, no React.

import { CLOSED_VISIT_STATUSES } from '../../constants/visitData';

export const GENDER_LABELS = { 1: 'Nam', 2: 'Nữ', 3: 'Chưa xác định' };

// PaymentStatus (BR-37, I1-D41).
export const PAYMENT_STATUS_LABELS = {
    PENDING: 'Chờ thanh toán',
    PAID: 'Đã thanh toán',
    FEE_EXEMPT: 'Miễn phí',
    CANCELLED: 'Đã hủy',
};

// Report 3 System Messages (Vietnamese).
export const MSG = {
    NOT_FOUND: 'Không tìm thấy hồ sơ bệnh nhân được yêu cầu.',
    NO_HISTORY: 'Chưa có lịch sử khám.',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
};

/** Maps an API failure to a Vietnamese message (MSG41, MSG13, MSG12, MSG99). */
export function overviewErrorMessage(error) {
    if (error?.status === 404) return MSG.NOT_FOUND;
    if (error?.status === 403) return MSG.FORBIDDEN;
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    return MSG.SYSTEM_ERROR;
}

/** Whole years from a YYYY-MM-DD birth date to today (YYYY-MM-DD). */
export function ageOn(birthDate, today) {
    if (!birthDate) return null;
    const [by, bm, bd] = birthDate.split('-').map(Number);
    const [ty, tm, td] = today.split('-').map(Number);
    return ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0);
}

export function formatDate(value) {
    return value ? new Date(`${value}T00:00:00`).toLocaleDateString('vi-VN') : '—';
}

export function formatDateTime(value) {
    return value
        ? new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short', hour12: false })
        : '—';
}

/** Address with ward and province when present. */
export function fullAddress(patient) {
    return [patient.diachi, patient.xaphuong, patient.tinhthanh].filter(Boolean).join(', ') || '—';
}

/** BR-17: Register Examination is disabled while a visit is active. */
export function canRegisterVisit(activeVisit) {
    return !activeVisit || CLOSED_VISIT_STATUSES.includes(activeVisit.status);
}
