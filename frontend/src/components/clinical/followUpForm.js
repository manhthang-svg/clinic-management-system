/** UC-61, UC-62 (docs/uc-61-follow-up-appointment.md): messages, client checks and error mapping. */

export const ADVICE_MAX = 2000;

export const FOLLOW_UP_MSG = {
    DATE_REQUIRED: 'Ngày tái khám là bắt buộc.', // MSG02
    CREATED: 'Tạo thành công lịch hẹn tái khám.', // MSG04
    UPDATED: 'Lịch hẹn tái khám cập nhật thành công.', // MSG03
    TOO_LONG: `Vượt quá độ dài tối đa ${ADVICE_MAX.toLocaleString('vi-VN')} ký tự.`, // MSG08
    NOT_AFTER_VISIT: 'Ngày tái khám phải sau ngày khám hiện tại.', // MSG33
    DISCARD: 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?', // MSG38
    TOO_FAR: 'Ngày tái khám không được quá 365 ngày sau ngày khám.', // MSG125 (ASSUMPTION)
    // MSG126 (ASSUMPTION): BR-59-08, a change after the prescription was sent does not reach it.
    PRESCRIPTION_SENT: (date) => `Đơn thuốc đã gửi với ngày tái khám ${date || 'không có'}. Lịch hẹn thay đổi sau khi gửi không làm thay đổi đơn đã gửi.`,
    CONCLUSION_REQUIRED: 'Vui lòng lưu kết luận trước khi hẹn tái khám.', // MSG127 (ASSUMPTION)
    NOT_SCHEDULED: 'Lịch hẹn tái khám đã được sử dụng hoặc đã hủy, không thể sửa hoặc in.', // MSG128 (ASSUMPTION)
    SAVE_BEFORE_PRINT: 'Lưu lịch hẹn trước khi in.', // UC-62 AF-02 (ASSUMPTION)
    CHECK_FIELDS: 'Vui lòng kiểm tra lại các trường được đánh dấu.',
    RECORD_CLOSED: 'Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.', // MSG68
    OUTSIDE_ROOM_OR_SHIFT: 'Bạn không được phân công phòng khám này hoặc đang ngoài ca trực.', // MSG48
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    CONCURRENT_MODIFICATION: 'Lịch hẹn vừa được cập nhật ở nơi khác. Vui lòng tải lại và thử lại.',
    SESSION_EXPIRED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    SYSTEM_ERROR: 'Không thể kết nối hoặc hệ thống đang gặp sự cố. Vui lòng tải lại và thử lại.', // MSG80
};

/** Lịch hẹn statuses (V7); only SCHEDULED is made and changed by the Doctor. */
export const FOLLOW_UP_STATUS = {
    SCHEDULED: 'Đã hẹn', ARRIVED: 'Đã đến khám', MISSED: 'Lỡ hẹn', CANCELLED: 'Đã hủy',
};

export const EMPTY_FOLLOW_UP = { followUpDate: '', advice: '' };

export function formFromResponse(response) {
    const appointment = response?.appointment;
    return appointment
        ? { followUpDate: appointment.followUpDate ?? '', advice: appointment.advice ?? '' }
        : EMPTY_FOLLOW_UP;
}

export function sameFollowUp(a, b) {
    return a.followUpDate === b.followUpDate && a.advice.trim() === b.advice.trim();
}

/** BR-61-02 for feedback only; the server decides. Dates are ISO yyyy-mm-dd, so text order is date order. */
export function validateFollowUp(form, data) {
    const errors = {};
    if (!form.followUpDate) errors.followUpDate = FOLLOW_UP_MSG.DATE_REQUIRED;
    else if (data?.minDate && form.followUpDate < data.minDate) errors.followUpDate = FOLLOW_UP_MSG.NOT_AFTER_VISIT;
    else if (data?.maxDate && form.followUpDate > data.maxDate) errors.followUpDate = FOLLOW_UP_MSG.TOO_FAR;
    if (form.advice.trim().length > ADVICE_MAX) errors.advice = FOLLOW_UP_MSG.TOO_LONG;
    return errors;
}

export function toPayload(form, version) {
    return { followUpDate: form.followUpDate, advice: form.advice.trim() || null, version: version ?? null };
}

const REASONS = {
    REQUIRED: FOLLOW_UP_MSG.DATE_REQUIRED,
    NOT_AFTER_VISIT: FOLLOW_UP_MSG.NOT_AFTER_VISIT,
    TOO_FAR: FOLLOW_UP_MSG.TOO_FAR,
    TOO_LONG: FOLLOW_UP_MSG.TOO_LONG,
};

/** The Vietnamese message of a failed UC-61 or UC-62 call, with the field it belongs to. */
export function followUpError(error) {
    const code = error?.errorResponse?.code;
    const details = error?.errorResponse?.details ?? {};
    switch (code) {
        case 'FOLLOW_UP_APPOINTMENT_INVALID': {
            const message = REASONS[details.reason] ?? FOLLOW_UP_MSG.CHECK_FIELDS;
            return { fields: { [details.field ?? 'followUpDate']: message }, message: FOLLOW_UP_MSG.CHECK_FIELDS };
        }
        case 'FOLLOW_UP_CONCLUSION_REQUIRED':
            return { fields: {}, message: FOLLOW_UP_MSG.CONCLUSION_REQUIRED };
        case 'FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED':
            return { fields: {}, message: FOLLOW_UP_MSG.NOT_SCHEDULED };
        case 'MEDICAL_RECORD_CLOSED':
            return { fields: {}, message: FOLLOW_UP_MSG.RECORD_CLOSED };
        case 'DOCTOR_OFF_DUTY':
        case 'ROOM_ASSIGNMENT_REQUIRED':
            return { fields: {}, message: FOLLOW_UP_MSG.OUTSIDE_ROOM_OR_SHIFT };
        case 'ACCESS_DENIED':
        case 'VISIT_NOT_IN_EXAMINATION':
            return { fields: {}, message: FOLLOW_UP_MSG.FORBIDDEN };
        case 'CONCURRENT_MODIFICATION':
            return { fields: {}, message: FOLLOW_UP_MSG.CONCURRENT_MODIFICATION };
        default:
            if (error?.status === 401) return { fields: {}, message: FOLLOW_UP_MSG.SESSION_EXPIRED };
            if (error?.status === 403) return { fields: {}, message: FOLLOW_UP_MSG.FORBIDDEN };
            return { fields: {}, message: FOLLOW_UP_MSG.SYSTEM_ERROR };
    }
}

/** "ngày 03 tháng 12 năm 2026" for an ISO date (Phụ lục V). */
export function dayMonthYear(value) {
    if (!value) return 'ngày …… tháng …… năm ……';
    const [year, month, day] = String(value).split('-');
    return `ngày ${day} tháng ${month} năm ${year}`;
}

/** "03/12/2026" for an ISO date. */
export function shortDate(value) {
    if (!value) return '';
    const [year, month, day] = String(value).split('-');
    return `${day}/${month}/${year}`;
}
