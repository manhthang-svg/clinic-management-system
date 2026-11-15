// Turns backend account errors (ErrorCode + details) into the Vietnamese messages of SRS V.2.
import { ApiError } from "../../apis/apiError";

/** Backend request field -> form field used by accountForm.js. */
const FORM_FIELD = {
  username: "username",
  fullName: "fullName",
  academicTitle: "title",
  practiceLicenseNo: "practiceLicenseNo",
  maLienthongBacsi: "maLienthongBacsi",
  dateOfBirth: "dateOfBirth",
  gender: "gender",
  phoneNumber: "phone",
  email: "email",
  address: "address",
  password: "password",
  initialPassword: "password",
};

const FIELD_LABEL = {
  username: "Tên đăng nhập",
  fullName: "Họ và tên",
  title: "Học hàm / học vị",
  practiceLicenseNo: "Số giấy phép hành nghề",
  maLienthongBacsi: "Mã liên thông bác sĩ",
  dateOfBirth: "Ngày sinh",
  gender: "Giới tính",
  phone: "Số điện thoại",
  email: "Email",
  address: "Địa chỉ",
  password: "Mật khẩu",
};

const CODE_MESSAGE = {
  UNAUTHORIZED: "Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.", // MSG12
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.", // MSG13
  SELF_ADMINISTRATION_FORBIDDEN:
    "Bạn không thể thực hiện thao tác này trên tài khoản của chính mình.", // MSG45
  ADMIN_ACCOUNT_PROTECTED:
    "Không thể thực hiện thao tác này đối với tài khoản Admin.", // MSG46
  ACCOUNT_NOT_FOUND: "Không tìm thấy tài khoản.",
  NOT_FOUND: "Không tìm thấy dữ liệu.",
  ACCOUNT_STATE_CONFLICT:
    "Tài khoản vừa được người khác cập nhật. Vui lòng tải lại và thử lại.", // MSG75
  CONCURRENT_MODIFICATION:
    "Tài khoản vừa được người khác cập nhật. Vui lòng tải lại và thử lại.", // MSG75
  ROLE_CHANGE_NOT_ALLOWED: "Vai trò mới phải khác vai trò hiện tại.",
  WORK_IN_PROGRESS:
    "Nhân viên còn lượt khám hoặc chỉ định đang xử lý. Vui lòng hoàn tất trước.", // MSG49
  PASSWORD_POLICY_VIOLATION:
    "Mật khẩu phải dài 8–64 ký tự và không quá 72 byte.", // MSG43
  EMAIL_ALREADY_EXISTS: "Email đã tồn tại trong hệ thống.", // MSG07
  PHONE_ALREADY_EXISTS: "Số điện thoại đã tồn tại trong hệ thống.", // MSG07
  USERNAME_ALREADY_EXISTS: "Tên đăng nhập đã tồn tại trong hệ thống.", // MSG07
  REQUEST_VALIDATION_FAILED:
    "Một số thông tin chưa hợp lệ. Vui lòng kiểm tra lại.",
  MALFORMED_REQUEST:
    "Yêu cầu không hợp lệ. Vui lòng tải lại trang rồi thử lại.",
};

const FALLBACK_MESSAGE =
  "Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên."; // MSG80
const NETWORK_MESSAGE =
  "Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.";

function fieldMessage(field, reason) {
  const label = FIELD_LABEL[field] || field;
  // BR-62: the backend rejects a date of birth under 18 years with INVALID_FORMAT.
  if (field === "dateOfBirth" && reason !== "REQUIRED") return "Ngày sinh không hợp lệ. Nhân viên phải đủ 18 tuổi.";
  switch (reason) {
    case "ALREADY_EXISTS":
      return `${label} đã tồn tại trong hệ thống.`; // MSG07
    case "REQUIRED":
      return `${label} là bắt buộc.`; // MSG02
    case "TOO_LONG":
      if (field === "fullName") return "Vượt quá độ dài tối đa 120 ký tự."; // MSG08 (BR-59)
      if (field === "title") return "Vượt quá độ dài tối đa 50 ký tự."; // MSG08 (BR-62)
      if (field === "maLienthongBacsi") return "Vượt quá độ dài tối đa 50 ký tự."; // MSG08
      return CODE_MESSAGE.PASSWORD_POLICY_VIOLATION; // MSG43
    case "NOT_APPLICABLE":
      return `${label} chỉ áp dụng cho tài khoản Bác sĩ.`;
    case "TOO_SHORT":
      return CODE_MESSAGE.PASSWORD_POLICY_VIOLATION; // MSG43
    default:
      return `${label} không đúng định dạng.`; // MSG05
  }
}

/**
 * Field errors keyed by form field ({ phone: '...' }). Empty when the error is not tied to fields,
 * including the generic UNIQUENESS_CONFLICT the backend returns after a lost race.
 */
export function accountFieldErrors(error) {
  const details =
    error instanceof ApiError ? error.errorResponse?.details : null;
  if (!details) return {};
  const errors = {};
  Object.entries(details).forEach(([backendField, reason]) => {
    const field = FORM_FIELD[backendField];
    if (field) errors[field] = fieldMessage(field, reason);
  });
  return errors;
}

// BR-97 action reason length. ASSUMPTION: the message list has no text for a too-short reason.
export const REASON_LENGTH_MESSAGE = "Lý do phải có từ 5 đến 500 ký tự.";

/** MSG45 for the given action, e.g. "khóa". */
export function msg45(action) {
  return `Bạn không thể ${action} tài khoản của chính mình.`;
}

/** MSG47 for the staff member who still has work in progress. */
export function msg47(fullName) {
  return `${fullName} đang có lượt khám hoặc dịch vụ đang thực hiện. Vui lòng hoàn tất các công việc này trước.`;
}

/**
 * One Vietnamese sentence for a toast or alert. The optional context fills the MSG45 action and
 * the MSG47 full name; without it the generic sentence is used.
 */
export function accountErrorMessage(error, { action, fullName } = {}) {
  if (!(error instanceof ApiError)) return FALLBACK_MESSAGE;
  const code = error.errorResponse?.code;
  if (error.errorResponse?.details?.reason === "TOO_SHORT") return REASON_LENGTH_MESSAGE;
  if (code === "SELF_ADMINISTRATION_FORBIDDEN" && action) return msg45(action);
  if (code === "WORK_IN_PROGRESS" && fullName) return msg47(fullName);
  if (!code && error.status === undefined) return NETWORK_MESSAGE;
  const fieldErrors = Object.values(accountFieldErrors(error));
  if (fieldErrors.length > 0) return fieldErrors.join(" ");
  if (
    code === "CONCURRENT_MODIFICATION" &&
    error.errorResponse?.details?.fields === "UNIQUENESS_CONFLICT"
  ) {
    return "Số điện thoại, email hoặc tên đăng nhập đã tồn tại trong hệ thống.";
  }
  return CODE_MESSAGE[code] || FALLBACK_MESSAGE;
}

/** Drops a field's server error once the user edits that field. */
export function withoutField(errors, field) {
  if (!(field in errors)) return errors;
  const next = { ...errors };
  delete next[field];
  return next;
}
