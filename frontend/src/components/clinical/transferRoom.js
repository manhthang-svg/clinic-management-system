// UC-48 Transfer Patient to Another Room: validation and Vietnamese messages.
import { MSG12, MSG80, MSG102, msg56 } from '../visits/visitErrors';

export const REASON_MIN = 5;
export const REASON_MAX = 500;
export const NOTES_MAX = 500;

export const TRANSFER_MESSAGES = {
    transferred: (roomName, queueNumber) => `Đã chuyển bệnh nhân đến ${roomName}. Số thứ tự mới: ${queueNumber}.`, // MSG24
    reasonRequired: 'Vui lòng nhập lý do.', // MSG31
    reasonLength: `Lý do phải có từ ${REASON_MIN} đến ${REASON_MAX} ký tự.`, // MSG95
    roomRequired: 'Vui lòng chọn phòng chuyển đến.',
    outsideShift: 'Bạn không có ca làm việc vào lúc này. Bạn chỉ xem được danh sách, không gọi được bệnh nhân.', // MSG48
    roomNotEligible: msg56, // MSG53
    recordOpened: 'Không thể chuyển bệnh nhân vì hồ sơ bệnh án của lượt khám này đã được mở.', // MSG56
    visitChanged: 'Lượt khám vừa được cập nhật. Vui lòng tải lại và thử lại.', // MSG75 ("Lượt khám")
    sessionExpired: MSG12,
    systemError: MSG80,
    unsavedChanges: 'Bạn có thay đổi chưa lưu. Nếu rời khỏi trang, các thay đổi này sẽ bị mất.', // MSG38
    noEligibleRoom: MSG102, // AF-03
    loadRoomsError: MSG80,
};

/** Field errors before sending; the backend checks the same rules again (BR-97). */
export function validateTransfer({ toRoomId, reason, notes }) {
    const errors = {};
    if (!toRoomId) errors.toRoomId = TRANSFER_MESSAGES.roomRequired;
    const trimmed = reason.trim();
    if (!trimmed) errors.reason = TRANSFER_MESSAGES.reasonRequired;
    else if (trimmed.length < REASON_MIN || trimmed.length > REASON_MAX) errors.reason = TRANSFER_MESSAGES.reasonLength;
    if (notes.length > NOTES_MAX) errors.notes = `Vượt quá độ dài tối đa ${NOTES_MAX} ký tự.`; // MSG08
    return errors;
}

/**
 * Where an API failure is shown: `field` errors stay under the field, the rest is a page-level message.
 * `ends` means the visit can no longer be transferred from this screen; `reloadVisit` asks the page to reload the
 * visit details (EF-04). Never shows the raw backend message.
 */
export function transferError(error, roomName) {
    const code = error?.errorResponse?.code;
    switch (code) {
        case 'TRANSFER_REASON_INVALID':
        case 'REQUEST_VALIDATION_FAILED':
            return { field: 'reason', message: TRANSFER_MESSAGES.reasonLength };
        case 'TRANSFER_ROOM_NOT_ELIGIBLE':
            return { field: 'toRoomId', message: TRANSFER_MESSAGES.roomNotEligible(roomName), reloadRooms: true };
        case 'MEDICAL_RECORD_ALREADY_OPENED':
            return { message: TRANSFER_MESSAGES.recordOpened, ends: true };
        case 'VISIT_NOT_TRANSFERABLE':
        case 'CONCURRENT_MODIFICATION':
        case 'VISIT_NOT_FOUND':
            return { message: TRANSFER_MESSAGES.visitChanged, ends: true, reloadVisit: true };
        case 'DOCTOR_OFF_DUTY':
        case 'ROOM_ASSIGNMENT_REQUIRED':
            return { message: TRANSFER_MESSAGES.outsideShift, ends: true };
        default:
            if (error?.status === 401) return { message: TRANSFER_MESSAGES.sessionExpired, ends: true };
            return { message: TRANSFER_MESSAGES.systemError };
    }
}
