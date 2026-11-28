/** UC-90 Submit e-Prescription: messages and error mapping of the send (docs/uc-59-e-prescription.md). */
import { prescriptionError } from './prescriptionForm';

// MSG86, MSG107, MSG133-MSG134. The national sign-in, "not added", address and usage messages have no MSG number yet
// (TBD); their wording is ASSUMPTION.
export const SUBMIT_MSG = {
    REMINDER: 'Sau khi gửi thành công, đơn thuốc và chẩn đoán không sửa được nữa.',
    ACCEPTED: (code) => `Đã gửi đơn thuốc ${code} thành công. Đơn thuốc đã được khóa.`, // MSG133
    REJECTED: 'Hệ thống đơn thuốc quốc gia từ chối đơn thuốc. Vui lòng sửa theo danh sách lỗi và gửi lại.', // MSG134
    NO_ANSWER: 'Không thể kết nối với Hệ thống đơn thuốc quốc gia. Vui lòng thử lại.', // MSG86
    CHILD_DATA: (item) => `Trẻ dưới 72 tháng tuổi cần có ${item} trước khi gửi đơn thuốc.`, // MSG107
    NOT_ADDED: 'Bác sĩ chưa được thêm vào cơ sở trên Hệ thống đơn thuốc quốc gia nên chưa gửi được đơn thuốc. Vui lòng liên hệ quản trị viên.',
    SIGN_IN_HINT: 'Nhập mật khẩu liên thông đơn thuốc do Hệ thống đơn thuốc quốc gia cấp cho bạn. Mật khẩu chỉ dùng để đăng nhập, hệ thống không lưu lại.',
    SIGN_IN_REQUIRED: 'Phiên liên thông đơn thuốc đã hết hạn hoặc chưa có. Vui lòng nhập mật khẩu liên thông đơn thuốc.',
    SIGN_IN_FAILED: (reason) => `Hệ thống đơn thuốc quốc gia từ chối đăng nhập${reason ? `: ${reason}` : '.'}`,
    ADDRESS_TOO_LONG: 'Địa chỉ của người bệnh dài quá 500 ký tự nên không gửi được. Vui lòng nhờ lễ tân rút gọn địa chỉ.',
    ACCOMPANYING_TOO_LONG: 'Thông tin người đưa trẻ đến khám dài quá 500 ký tự nên không gửi được. Vui lòng nhờ lễ tân rút gọn.',
    USAGE_TOO_LONG: (line) => `Cách dùng của thuốc thứ ${line} (gồm đường dùng và liều dùng) dài quá 500 ký tự. Vui lòng rút gọn.`,
    HISTORY_EMPTY: 'Đơn thuốc chưa được gửi lần nào.',
    // UC-92 (ASSUMPTION wording): the rejection, AF-01, and a resend that needs the corrections saved first.
    REJECTED_ATTEMPT: (attempt, time) => `Lần gửi ${attempt}, phản hồi lúc ${time}.`,
    CORRECT_ELSEWHERE: 'Nếu lỗi thuộc chẩn đoán kết luận, hãy sửa ở phần Kết luận; nếu lỗi thuộc mã liên thông bác sĩ, hãy liên hệ quản trị viên. Sau đó bấm "Gửi lại", không cần sửa đơn. Mã đơn thuốc giữ nguyên khi gửi lại.',
    SAVE_BEFORE_RESEND: 'Lưu đơn thuốc trước khi gửi lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.', // MSG80
};

/** UC-91 step 2: the result labels of a send. */
export const SUBMISSION_RESULTS = { ACCEPTED: 'Chấp nhận', REJECTED: 'Bị từ chối', NO_RESPONSE: 'Không phản hồi' };

const CHILD_ITEMS = { WEIGHT: 'cân nặng', ACCOMPANYING_PERSON: 'thông tin người đưa trẻ đến khám' };

/** The Vietnamese message of a failed UC-90 call; falls back to the UC-59 messages. */
export function submissionError(error) {
    const code = error?.errorResponse?.code;
    const details = error?.errorResponse?.details ?? {};
    switch (code) {
        case 'NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE':
            return SUBMIT_MSG.NO_ANSWER;
        case 'NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED':
            return SUBMIT_MSG.SIGN_IN_REQUIRED;
        case 'NATIONAL_PRESCRIPTION_SIGN_IN_FAILED':
            // The national system's own error list (QĐ 808 VI.4), shown as the rejection errors are (UC-92).
            return SUBMIT_MSG.SIGN_IN_FAILED(details.errors);
        case 'PRESCRIBER_NOT_ADDED_TO_FACILITY':
            return SUBMIT_MSG.NOT_ADDED;
        case 'PRESCRIPTION_CHILD_DATA_REQUIRED':
            return SUBMIT_MSG.CHILD_DATA(CHILD_ITEMS[details.item] ?? 'đủ thông tin');
        case 'PRESCRIPTION_INVALID':
            if (details.field === 'address') return SUBMIT_MSG.ADDRESS_TOO_LONG;
            if (details.field === 'accompanyingPerson') return SUBMIT_MSG.ACCOMPANYING_TOO_LONG;
            if (details.field === 'usage' && details.reason === 'TOO_LONG') {
                return SUBMIT_MSG.USAGE_TOO_LONG(Number(details.line ?? 0) + 1);
            }
            return prescriptionError(error).message;
        default:
            return prescriptionError(error).message;
    }
}
