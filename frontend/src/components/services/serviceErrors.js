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
