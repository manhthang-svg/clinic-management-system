/** UC-58 Close Medical Record: messages, retention categories and error mapping (docs/uc-58-close-medical-record.md). */

// BR-58-06, TT 33/2025/TT-BYT Phụ lục dòng 40, 44.
export const RETENTION_CATEGORIES = [
    ['STANDARD', 'Thông thường (10 năm)'],
    ['WORK_ACCIDENT', 'Tai nạn lao động (20 năm)'],
    ['TRAFFIC_ACCIDENT', 'Tai nạn giao thông (20 năm)'],
    ['PSYCHIATRIC', 'Tâm thần (20 năm)'],
];

export const CLOSE_MSG = {
    pendingParaclinical: (count) => `Không thể kết luận: còn ${count} dịch vụ cận lâm sàng chưa có kết quả.`, // MSG25
    CONFIRM: 'Sau khi xác nhận, kết luận và lịch hẹn sẽ bị khóa và lượt khám hoàn thành. Bạn có muốn tiếp tục không?', // MSG27
    PRESCRIPTION_NOT_ACCEPTED: 'Vui lòng gửi đơn thuốc thành công trước khi xác nhận kết luận.', // MSG67
    RECORD_CLOSED: 'Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.', // MSG68
    CONCURRENT_MODIFICATION: 'Kết luận vừa được người khác cập nhật. Vui lòng tải lại và thử lại.', // MSG75
    CONCLUSION_REQUIRED: 'Vui lòng lưu kết luận trước khi xác nhận.', // MSG129
    closed: (visitCode) => `Đã xác nhận kết luận. Lượt khám ${visitCode} đã hoàn thành.`, // MSG130
    INVALID_CATEGORY: 'Loại hồ sơ lưu trữ không đúng định dạng.', // MSG05
    SAVE_FIRST: 'Lưu các thay đổi của kết luận trước khi xác nhận.',
    NOT_CONCLUDABLE: 'Lượt khám chưa thể kết luận: bệnh nhân không còn đang khám hoặc chưa đủ kết quả cận lâm sàng.',
    OUTSIDE_ROOM_OR_SHIFT: 'Bạn không có ca làm việc vào lúc này. Bạn chỉ xem được danh sách, không gọi được bệnh nhân mới.', // MSG48
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.', // MSG80
};

/** The blocking reasons of a close check, as messages; empty when the record can be closed. */
export function closureProblems(check) {
    if (!check) return [];
    const problems = [];
    if (!check.conclusionSaved) problems.push({ key: 'conclusion', message: CLOSE_MSG.CONCLUSION_REQUIRED });
    if (check.pendingParaclinicalCount > 0) {
        problems.push({ key: 'paraclinical', message: CLOSE_MSG.pendingParaclinical(check.pendingParaclinicalCount) });
    }
    if (check.prescriptionStatus && check.prescriptionStatus !== 'ACCEPTED') {
        problems.push({ key: 'prescription', message: CLOSE_MSG.PRESCRIPTION_NOT_ACCEPTED });
    }
    return problems;
}

/** A confirmation failure as a Vietnamese message; never the raw backend message. */
export function closeError(error) {
    const code = error?.errorResponse?.code;
    const details = error?.errorResponse?.details ?? {};
    switch (code) {
        case 'CLOSE_CONCLUSION_REQUIRED':
            return { key: 'conclusion', message: CLOSE_MSG.CONCLUSION_REQUIRED };
        case 'CLOSE_PARACLINICAL_RESULTS_PENDING':
            return { key: 'paraclinical', message: CLOSE_MSG.pendingParaclinical(details.count ?? '') };
        case 'PRESCRIPTION_NOT_ACCEPTED':
            return { key: 'prescription', message: CLOSE_MSG.PRESCRIPTION_NOT_ACCEPTED };
        case 'CLOSE_INVALID':
            return { key: 'category', message: CLOSE_MSG.INVALID_CATEGORY };
        case 'MEDICAL_RECORD_CLOSED':
            return { key: 'closed', message: CLOSE_MSG.RECORD_CLOSED };
        case 'CONCURRENT_MODIFICATION':
            return { key: 'stale', message: CLOSE_MSG.CONCURRENT_MODIFICATION };
        case 'VISIT_NOT_IN_EXAMINATION':
            return { key: 'visit', message: CLOSE_MSG.NOT_CONCLUDABLE };
        case 'DOCTOR_OFF_DUTY':
        case 'ROOM_ASSIGNMENT_REQUIRED':
            return { key: 'duty', message: CLOSE_MSG.OUTSIDE_ROOM_OR_SHIFT };
        default:
            if (error?.status === 401) return { key: 'session', message: CLOSE_MSG.SESSION_EXPIRED };
            if (error?.status === 403) return { key: 'forbidden', message: CLOSE_MSG.FORBIDDEN };
            return { key: 'system', message: CLOSE_MSG.SYSTEM_ERROR };
    }
}
