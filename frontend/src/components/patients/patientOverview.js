// UC-36: nhan hien thi va dinh dang cho trang ho so benh nhan

import { CLOSED_VISIT_STATUSES } from '../../constants/visitData';

export const GENDER_LABELS = { 1: 'Nam', 2: 'Nữ', 3: 'Chưa xác định' };

// nhan trang thai phieu thu
export const PAYMENT_STATUS_LABELS = {
    PENDING: 'Chờ thanh toán',
    PAID: 'Đã thanh toán',
    FEE_EXEMPT: 'Miễn phí',
    CANCELLED: 'Đã hủy',
};

// thong bao theo bang System Messages
export const MSG = {
    NOT_FOUND: 'Không tìm thấy hồ sơ bệnh nhân được yêu cầu.',
    NO_HISTORY: 'Chưa có lịch sử khám.',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
};

// doi loi API thanh thong bao tieng Viet
export function overviewErrorMessage(error) {
    if (error?.status === 404) return MSG.NOT_FOUND;
    if (error?.status === 403) return MSG.FORBIDDEN;
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    return MSG.SYSTEM_ERROR;
}

// tinh tuoi (so nam tron) den ngay hom nay
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

// dia chi day du kem xa phuong, tinh thanh
export function fullAddress(patient) {
    return [patient.diachi, patient.xaphuong, patient.tinhthanh].filter(Boolean).join(', ') || '—';
}

// dang co luot kham chua xong thi khong cho dang ky kham moi (BR-17)
export function canRegisterVisit(activeVisit) {
    return !activeVisit || CLOSED_VISIT_STATUSES.includes(activeVisit.status);
}
