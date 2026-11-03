import { ApiError } from '../../apis/apiError';

const CODE_MESSAGE = {
  UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  FORBIDDEN: 'Bạn không có quyền xem danh mục dịch vụ.',
  REQUEST_VALIDATION_FAILED: 'Điều kiện tìm kiếm không hợp lệ. Vui lòng kiểm tra lại.',
};

const FALLBACK_MESSAGE = 'Không tải được danh sách dịch vụ. Vui lòng thử lại.';
const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';

export function serviceErrorMessage(error) {
  if (!(error instanceof ApiError)) return FALLBACK_MESSAGE;
  const code = error.errorResponse?.code;
  if (!code && error.status === undefined) return NETWORK_MESSAGE;
  return CODE_MESSAGE[code] || FALLBACK_MESSAGE;
}

const CREATE_CODE_MESSAGE = {
  UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  FORBIDDEN: 'Bạn không có quyền thêm dịch vụ.',
  SERVICE_CODE_ALREADY_EXISTS: 'Mã dịch vụ đã tồn tại trong hệ thống.',
  ACTIVE_EXAMINATION_SERVICE_EXISTS:
    'Đã có một dịch vụ khám bệnh đang hoạt động. Hãy ngừng hoạt động dịch vụ đó trước khi thêm dịch vụ khám bệnh mới.',
  SERVICE_ASSIGNMENT_INVALID: 'Khoa hoặc nhóm dịch vụ không hợp lệ. Vui lòng chọn lại.',
  REQUEST_VALIDATION_FAILED: 'Một số thông tin chưa hợp lệ. Vui lòng kiểm tra lại.',
  MALFORMED_REQUEST: 'Yêu cầu không hợp lệ. Vui lòng tải lại trang rồi thử lại.',
};

// SERVICE_ASSIGNMENT_INVALID details.assignment -> form field and message.
const ASSIGNMENT_ERRORS = {
  DEPARTMENT_NOT_FOUND: ['departmentId', 'Khoa thực hiện không tồn tại.'],
  DEPARTMENT_NOT_ACTIVE_PARACLINICAL: ['departmentId', 'Khoa phải là khoa cận lâm sàng đang hoạt động.'],
  SERVICE_GROUP_NOT_FOUND: ['serviceGroupId', 'Nhóm dịch vụ không tồn tại.'],
  SERVICE_GROUP_TYPE_MISMATCH: ['serviceGroupId', 'Nhóm dịch vụ không thuộc loại dịch vụ đã chọn.'],
  SERVICE_GROUP_DEPARTMENT_MISMATCH: ['serviceGroupId', 'Nhóm dịch vụ không thuộc khoa thực hiện đã chọn.'],
  DEPARTMENT_AND_GROUP_REQUIRED: ['serviceGroupId', 'Vui lòng chọn khoa và nhóm dịch vụ.'],
};

const FIELD_LABEL = {
  code: 'Mã dịch vụ',
  name: 'Tên dịch vụ',
  type: 'Loại dịch vụ',
  serviceGroupId: 'Nhóm dịch vụ',
  departmentId: 'Khoa thực hiện',
  clinicPrice: 'Giá viện phí',
  insurancePrice: 'Giá BHYT',
  description: 'Mô tả',
};

const UPDATE_CODE_MESSAGE = {
  UNAUTHORIZED: CREATE_CODE_MESSAGE.UNAUTHORIZED,
  FORBIDDEN: 'Bạn không có quyền cập nhật dịch vụ.',
  SERVICE_NOT_FOUND: 'Dịch vụ không còn tồn tại.',
  CONCURRENT_MODIFICATION:
    'Dịch vụ vừa được cập nhật ở nơi khác. Thông tin mới đã được tải lại, vui lòng kiểm tra rồi lưu lại.',
  REQUEST_VALIDATION_FAILED: CREATE_CODE_MESSAGE.REQUEST_VALIDATION_FAILED,
  MALFORMED_REQUEST: CREATE_CODE_MESSAGE.MALFORMED_REQUEST,
};

const STATUS_CODE_MESSAGE = {
  UNAUTHORIZED: CREATE_CODE_MESSAGE.UNAUTHORIZED,
  FORBIDDEN: 'Bạn không có quyền thay đổi trạng thái dịch vụ.',
  SERVICE_NOT_FOUND: 'Dịch vụ không còn tồn tại.',
  CONCURRENT_MODIFICATION: 'Dịch vụ vừa được cập nhật ở nơi khác. Thông tin mới đã được tải lại, vui lòng thử lại.',
  LAST_ACTIVE_EXAMINATION_SERVICE: 'Không thể ngừng hoạt động dịch vụ khám bệnh duy nhất đang hoạt động.',
  ACTIVE_EXAMINATION_SERVICE_EXISTS:
    'Đã có một dịch vụ khám bệnh đang hoạt động. Hãy ngừng hoạt động dịch vụ đó trước.',
  SERVICE_GROUP_HAS_NO_ACTIVE_ROOM: 'Nhóm dịch vụ chưa có phòng thực hiện nào đang hoạt động.',
};

/** One message for a failed UC-25 status check or change. */
export function serviceStatusErrorMessage(error) {
  if (!(error instanceof ApiError)) return 'Đã xảy ra lỗi. Vui lòng thử lại.';
  const code = error.errorResponse?.code;
  if (!code && error.status === undefined) return NETWORK_MESSAGE;
  return STATUS_CODE_MESSAGE[code] || 'Không thay đổi được trạng thái dịch vụ. Vui lòng thử lại.';
}

/** Field errors and one summary message for a failed UC-23 / UC-24 update. */
export function serviceUpdateErrors(error) {
  if (!(error instanceof ApiError)) return { fields: {}, message: 'Đã xảy ra lỗi. Vui lòng thử lại.' };
  const code = error.errorResponse?.code;
  if (!code && error.status === undefined) return { fields: {}, message: NETWORK_MESSAGE };

  const details = error.errorResponse?.details ?? {};
  const fields = {};
  if (code === 'REQUEST_VALIDATION_FAILED') {
    Object.keys(details).forEach((field) => {
      if (FIELD_LABEL[field]) fields[field] = `${FIELD_LABEL[field]} không hợp lệ.`;
    });
  }

  return {
    fields,
    message: UPDATE_CODE_MESSAGE[code] || 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
  };
}

/** Field errors and one summary message for a failed create-service request. */
export function serviceCreateErrors(error) {
  if (!(error instanceof ApiError)) return { fields: {}, message: 'Đã xảy ra lỗi. Vui lòng thử lại.' };
  const code = error.errorResponse?.code;
  if (!code && error.status === undefined) return { fields: {}, message: NETWORK_MESSAGE };

  const details = error.errorResponse?.details ?? {};
  const fields = {};
  if (code === 'SERVICE_CODE_ALREADY_EXISTS') {
    fields.code = CREATE_CODE_MESSAGE.SERVICE_CODE_ALREADY_EXISTS;
  } else if (code === 'SERVICE_ASSIGNMENT_INVALID' && ASSIGNMENT_ERRORS[details.assignment]) {
    const [field, message] = ASSIGNMENT_ERRORS[details.assignment];
    fields[field] = message;
  } else if (code === 'REQUEST_VALIDATION_FAILED') {
    Object.keys(details).forEach((field) => {
      if (FIELD_LABEL[field]) fields[field] = `${FIELD_LABEL[field]} không hợp lệ.`;
    });
  }

  return {
    fields,
    message: CREATE_CODE_MESSAGE[code] || 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
  };
}
