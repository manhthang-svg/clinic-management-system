// Turns backend account errors (ErrorCode + details) into the Vietnamese messages of SRS V.2.
import { ApiError } from '../../apis/apiError';

/** Backend request field -> form field used by accountForm.js. */
const FORM_FIELD = {
  username: 'username',
  fullName: 'fullName',
  academicTitle: 'title',
  practiceLicenseNo: 'practiceLicenseNo',
  dateOfBirth: 'dateOfBirth',
  gender: 'gender',
  phoneNumber: 'phone',
  email: 'email',
  address: 'address',
  password: 'password',
  initialPassword: 'password',
};

const FIELD_LABEL = {
  username: 'Tên đăng nhập',
  fullName: 'Họ và tên',
  title: 'Học hàm / học vị',
  practiceLicenseNo: 'Số giấy phép hành nghề',
  dateOfBirth: 'Ngày sinh',
  gender: 'Giới tính',
  phone: 'Số điện thoại',
  email: 'Email',
  address: 'Địa chỉ',
  password: 'Mật khẩu',
};

const CODE_MESSAGE = {
  UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
  SELF_ADMINISTRATION_FORBIDDEN: 'Bạn không thể thực hiện thao tác này trên chính tài khoản của mình.', // MSG47
  ADMIN_ACCOUNT_PROTECTED: 'Không thể thực hiện thao tác này trên tài khoản Quản trị viên.', // MSG48
  ACCOUNT_NOT_FOUND: 'Không tìm thấy tài khoản được yêu cầu.', // MSG41
  NOT_FOUND: 'Không tìm thấy dữ liệu được yêu cầu.', // MSG41
  ACCOUNT_STATE_CONFLICT: 'Trạng thái tài khoản đã thay đổi. Vui lòng tải lại trang rồi thử lại.',
  CONCURRENT_MODIFICATION: 'Tài khoản vừa được thay đổi bởi thao tác khác. Vui lòng tải lại trang rồi thử lại.',
  ROLE_CHANGE_NOT_ALLOWED: 'Vai trò mới phải khác vai trò hiện tại.',
  WORK_IN_PROGRESS: 'Nhân viên còn lượt khám hoặc chỉ định đang xử lý. Vui lòng hoàn tất trước.', // MSG49
  PASSWORD_POLICY_VIOLATION: 'Mật khẩu phải dài 8–64 ký tự và không quá 72 byte.', // MSG43
  EMAIL_ALREADY_EXISTS: 'Email đã tồn tại trong hệ thống.', // MSG07
  PHONE_ALREADY_EXISTS: 'Số điện thoại đã tồn tại trong hệ thống.', // MSG07
  USERNAME_ALREADY_EXISTS: 'Tên đăng nhập đã tồn tại trong hệ thống.', // MSG07
  REQUEST_VALIDATION_FAILED: 'Một số thông tin chưa hợp lệ. Vui lòng kiểm tra lại.',
  MALFORMED_REQUEST: 'Yêu cầu không hợp lệ. Vui lòng tải lại trang rồi thử lại.',
};

const FALLBACK_MESSAGE = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.'; // MSG99
const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';

function fieldMessage(field, reason) {
  const label = FIELD_LABEL[field] || field;
  switch (reason) {
    case 'ALREADY_EXISTS':
      return `${label} đã tồn tại trong hệ thống.`; // MSG07
    case 'REQUIRED':
      return `Vui lòng nhập ${label.toLowerCase()}.`; // MSG02
    case 'TOO_SHORT':
    case 'TOO_LONG':
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
  const details = error instanceof ApiError ? error.errorResponse?.details : null;
  if (!details) return {};
  const errors = {};
  Object.entries(details).forEach(([backendField, reason]) => {
    const field = FORM_FIELD[backendField];
    if (field) errors[field] = fieldMessage(field, reason);
  });
  return errors;
}

/** One Vietnamese sentence for a toast or alert. */
export function accountErrorMessage(error) {
  if (!(error instanceof ApiError)) return FALLBACK_MESSAGE;
  const code = error.errorResponse?.code;
  if (!code && error.status === undefined) return NETWORK_MESSAGE;
  const fieldErrors = Object.values(accountFieldErrors(error));
  if (fieldErrors.length > 0) return fieldErrors.join(' ');
  if (code === 'CONCURRENT_MODIFICATION' && error.errorResponse?.details?.fields === 'UNIQUENESS_CONFLICT') {
    return 'Số điện thoại, email hoặc tên đăng nhập đã tồn tại trong hệ thống.';
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
