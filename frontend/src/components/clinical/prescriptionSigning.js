/** UC-90 Submit e-Prescription: messages and error mapping of the digital signature (docs/uc-59-e-prescription.md). */
import { prescriptionError } from './prescriptionForm';

// MSG86, MSG107, MSG110-MSG111, MSG117-MSG118, MSG120-MSG124; MSG106 onward are ASSUMPTION wording.
export const SIGNING_MSG = {
    REMINDER: 'Sau khi gửi thành công, đơn thuốc và chẩn đoán không sửa được nữa.',
    ACCEPTED: (code) => `Đã gửi đơn thuốc ${code} thành công. Đơn thuốc đã được khóa.`, // MSG110
    REJECTED: 'Hệ thống đơn thuốc quốc gia từ chối đơn thuốc. Vui lòng sửa theo danh sách lỗi và gửi lại.', // MSG111
    NO_ANSWER: 'Không thể kết nối với Hệ thống đơn thuốc quốc gia. Vui lòng thử lại.', // MSG86
    NOT_REGISTERED: 'Tài khoản của bạn chưa được đăng ký chứng thư chữ ký số. Vui lòng liên hệ quản trị viên.', // MSG117
    CERTIFICATE_INVALID: (reason) => `Chứng thư chữ ký số không còn hiệu lực (${reason}). Không thể ký đơn thuốc.`, // MSG118
    WAITING: (time) => `Vui lòng xác nhận ký trên ứng dụng ký số của bạn. Yêu cầu hết hạn sau ${time}.`, // MSG120
    DECLINED: 'Bạn đã từ chối ký. Đơn thuốc chưa được gửi.', // MSG121
    EXPIRED: 'Yêu cầu ký đã hết hạn. Vui lòng ký lại.', // MSG122
    SERVICE_UNAVAILABLE: 'Không thể kết nối với dịch vụ chữ ký số. Vui lòng thử lại.', // MSG123
    SIGNATURE_INVALID: (reason) => `Chữ ký số không hợp lệ (${reason}). Đơn thuốc chưa được gửi.`, // MSG124
    CHILD_DATA: (item) => `Trẻ dưới 72 tháng tuổi cần có ${item} trước khi gửi đơn thuốc.`, // MSG107
    CANCELLED: 'Yêu cầu ký đã được hủy. Đơn thuốc chưa được gửi.',
    SIGNATURE_VALID: 'Chữ ký số hợp lệ',
    // BR-59-16: the stored signature verified again on display.
    SIGNATURE_NOT_VALID: (reason) => `Chữ ký số không hợp lệ (${reason})`,
    HISTORY_EMPTY: 'Đơn thuốc chưa được gửi lần nào.',
    // UC-92 (ASSUMPTION wording): the rejection, AF-01, and a resend that needs the corrections saved first.
    REJECTED_ATTEMPT: (attempt, time) => `Lần gửi ${attempt}, phản hồi lúc ${time}.`,
    CORRECT_ELSEWHERE: 'Nếu lỗi thuộc chẩn đoán kết luận, hãy sửa ở phần Kết luận; nếu lỗi thuộc mã liên thông bác sĩ, hãy liên hệ quản trị viên. Sau đó bấm "Gửi lại", không cần sửa đơn. Mã đơn thuốc giữ nguyên khi gửi lại.',
    SAVE_BEFORE_RESEND: 'Lưu đơn thuốc trước khi gửi lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.', // MSG80
};

const CERTIFICATE_REASONS = {
    CERTIFICATE_EXPIRED: 'chứng thư đã hết hạn',
    CERTIFICATE_NOT_YET_VALID: 'chứng thư chưa có hiệu lực',
    CERTIFICATE_REVOKED: 'chứng thư đã bị thu hồi',
    CA_NOT_TRUSTED: 'tổ chức cấp chứng thư không thuộc danh sách tin cậy',
    CA_CERTIFICATE_INVALID: 'chứng thư của tổ chức cấp không còn hiệu lực',
    UNKNOWN_TO_CA: 'tổ chức cấp không nhận ra chứng thư',
    CERTIFICATE_NOT_DOCTOR: 'chứng thư không mang số định danh cá nhân của bạn',
};

const SIGNATURE_REASONS = {
    ...CERTIFICATE_REASONS,
    SIGNATURE_MISMATCH: 'chữ ký không khớp với nội dung đơn thuốc',
    MALFORMED_SIGNATURE: 'dữ liệu chữ ký không đúng định dạng',
    SIGNER_CERTIFICATE_MISSING: 'thiếu chứng thư của người ký',
    SIGNING_CERTIFICATE_ATTRIBUTE_INVALID: 'chữ ký không gắn với chứng thư của người ký',
    CERTIFICATE_NOT_REGISTERED_FOR_SIGNER: 'chữ ký không được tạo bằng chứng thư đã đăng ký của bạn',
    TIMESTAMP_INVALID: 'dấu thời gian không hợp lệ',
    CHAIN_NOT_TRUSTED: 'chứng thư không do tổ chức tin cậy cấp',
    CERTIFICATE_NOT_VALID_AT_SIGNING_TIME: 'chứng thư không còn hiệu lực tại thời điểm ký',
    SIGNATURE_MISSING: 'không còn dữ liệu chữ ký',
    EVIDENCE_MISSING: 'thiếu bằng chứng kiểm tra chứng thư lúc ký',
};

/** UC-91 step 2: the result labels of a send. */
export const SUBMISSION_RESULTS = { ACCEPTED: 'Chấp nhận', REJECTED: 'Bị từ chối', NO_RESPONSE: 'Không phản hồi' };

/** BR-59-16: why a stored signature is not valid, in Vietnamese. */
export function signatureReason(reason) {
    return SIGNATURE_REASONS[reason] ?? 'không xác định';
}

const CHILD_ITEMS = { WEIGHT: 'cân nặng', ACCOMPANYING_PERSON: 'thông tin người đưa trẻ đến khám' };

/** The Vietnamese message of a failed UC-90 call; falls back to the UC-59 messages. */
export function signingError(error) {
    const code = error?.errorResponse?.code;
    const details = error?.errorResponse?.details ?? {};
    switch (code) {
        case 'SIGNING_CERTIFICATE_NOT_REGISTERED':
            return SIGNING_MSG.NOT_REGISTERED;
        case 'SIGNING_CERTIFICATE_INVALID':
            return SIGNING_MSG.CERTIFICATE_INVALID(CERTIFICATE_REASONS[details.reason] ?? 'không xác định');
        case 'SIGNATURE_INVALID':
            return SIGNING_MSG.SIGNATURE_INVALID(signatureReason(details.reason));
        case 'SIGNING_SERVICE_UNAVAILABLE':
            return SIGNING_MSG.SERVICE_UNAVAILABLE;
        case 'NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE':
            return SIGNING_MSG.NO_ANSWER;
        case 'PRESCRIPTION_CHILD_DATA_REQUIRED':
            return SIGNING_MSG.CHILD_DATA(CHILD_ITEMS[details.item] ?? 'đủ thông tin');
        case 'SIGNING_REQUEST_NOT_FOUND':
            return SIGNING_MSG.SYSTEM_ERROR;
        default:
            return prescriptionError(error).message;
    }
}

/** "4:05" for the time left until {@code expiresAt}. */
export function timeLeft(expiresAt, now) {
    const seconds = Math.max(0, Math.ceil((new Date(expiresAt).getTime() - now) / 1000));
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
