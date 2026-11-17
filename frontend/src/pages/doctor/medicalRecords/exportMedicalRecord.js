/** UC-53 Export Medical Record: purposes, messages and error mapping (docs/uc-53-export-medical-record.md). */
import { ApiError } from '../../../apis/apiError';

// BR-43, BR-131: the four export purposes.
export const EXPORT_PURPOSES = [
    ['LIEN_THONG', 'Trao đổi dữ liệu'],
    ['LUU_TRU', 'Lưu trữ'],
    ['CUNG_CAP_NGUOI_BENH', 'Cung cấp cho người bệnh'],
    ['CO_QUAN_CO_THAM_QUYEN', 'Cơ quan có thẩm quyền'],
];

// BR-53-03: the recipient is required when the record leaves the clinic for the patient or an authority.
export const RECIPIENT_REQUIRED = new Set(['CUNG_CAP_NGUOI_BENH', 'CO_QUAN_CO_THAM_QUYEN']);
export const RECIPIENT_MAX_LENGTH = 200;

export const EXPORT_MSG = {
    purposeRequired: 'Mục đích xuất là bắt buộc.', // MSG02
    purposeInvalid: 'Mục đích xuất không đúng định dạng.', // MSG05
    recipientRequired: 'Người nhận là bắt buộc.', // MSG02
    recipientTooLong: `Vượt quá độ dài tối đa ${RECIPIENT_MAX_LENGTH} ký tự.`, // MSG08
    NOT_CLOSED: 'Chỉ xuất được hồ sơ bệnh án đã đóng.', // MSG131
    SUCCESS: 'Xuất tệp thành công.', // MSG34
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.', // MSG80
};

/** BR-53-02: only a closed record has an archive package to export. */
export function isExportable(recordStatus) {
    return recordStatus === 'LOCKED' || recordStatus === 'ARCHIVED';
}

/** Client checks of BR-53-03, for feedback only; the backend decides. */
export function exportFieldErrors(purpose, recipient) {
    const errors = {};
    const trimmed = (recipient ?? '').trim();
    if (!purpose) errors.purpose = EXPORT_MSG.purposeRequired;
    if (RECIPIENT_REQUIRED.has(purpose) && !trimmed) errors.recipient = EXPORT_MSG.recipientRequired;
    if (trimmed.length > RECIPIENT_MAX_LENGTH) errors.recipient = EXPORT_MSG.recipientTooLong;
    return errors;
}

/** Maps an export failure to { field, message } (field-level) or { message } (dialog-level). */
export function exportError(error) {
    if (!(error instanceof ApiError)) return { message: EXPORT_MSG.SYSTEM_ERROR };
    const code = error.errorResponse?.code;
    const details = error.errorResponse?.details ?? {};
    if (code === 'EXPORT_INVALID') {
        if (details.field === 'recipient') {
            return {
                field: 'recipient',
                message: details.reason === 'TOO_LONG' ? EXPORT_MSG.recipientTooLong : EXPORT_MSG.recipientRequired,
            };
        }
        return {
            field: 'purpose',
            message: details.reason === 'INVALID' ? EXPORT_MSG.purposeInvalid : EXPORT_MSG.purposeRequired,
        };
    }
    if (code === 'EXPORT_RECORD_NOT_CLOSED') return { message: EXPORT_MSG.NOT_CLOSED };
    if (error.status === 401) return { message: EXPORT_MSG.SESSION_EXPIRED };
    if (error.status === 403) return { message: EXPORT_MSG.FORBIDDEN };
    return { message: EXPORT_MSG.SYSTEM_ERROR };
}

/** Hands a downloaded file to the browser. */
export function saveFile({ blob, fileName }) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName || 'ho-so-benh-an.pdf';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}
