import { ApiError } from '../apis/apiError';

/**
 * Tạo thông báo lỗi có gắn nhãn nguồn gốc để debug nhanh lỗi đến từ đâu:
 * - `[BE 4xx/5xx · CODE]`: backend đã nhận request và trả về lỗi HTTP.
 * - `[Mạng]`: không gọi được backend (server tắt, sai base URL, CORS, mất mạng...).
 * - `[FE]`: lỗi JavaScript phía frontend (không phải ApiError).
 *
 * Đồng thời log lỗi gốc ra console để xem stack trace / response chi tiết.
 *
 * @param {unknown} error Lỗi bắt được trong catch.
 * @param {string} action Mô tả thao tác, ví dụ "Không thể tải danh sách khoa/phòng".
 */
export function describeApiError(error, action) {
  console.error(`[${action}]`, error);

  if (error instanceof ApiError) {
    if (error.status === undefined) {
      return `${action}: không kết nối được máy chủ [Mạng]`;
    }
    const code = error.errorResponse?.code;
    const tag = code ? `BE ${error.status} · ${code}` : `BE ${error.status}`;
    return `${action}: ${error.message} [${tag}]`;
  }

  return `${action}: ${error?.message || 'Lỗi không xác định'} [FE]`;
}

export default describeApiError;
