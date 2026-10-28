import { ApiError } from '../../apis/apiError';

// Message texts follow the SRS message list (MSG12, MSG17, MSG20, MSG22, MSG56, MSG99).
export const MSG12 = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
export const MSG17 = 'Thẻ BHYT không hợp lệ hoặc đã hết hạn. Lượt khám sẽ được đăng ký dạng Dịch vụ.';
export const MSG20 = 'Không kết nối được cổng BHYT. Vui lòng xác minh lại hoặc ghi nhận thẻ chưa xác minh.';
export const MSG99 = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
// ASSUMPTION: MSG91 is not in the SRS message list available in docs/.
export const MSG91 = 'Lịch hẹn tái khám vừa được gắn với lượt khám khác. Thông tin lịch hẹn đã được tải lại.';

export const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';

export function msg22(visit, statusLabel) {
  return `Bệnh nhân đang có lượt khám chưa kết thúc (${visit.sovaovien} – ${visit.roomName ?? 'phòng khám'} – ${statusLabel}).`;
}

export function msg56(roomName) {
  return `${roomName ?? 'Phòng khám'} không hoạt động hoặc chưa có bác sĩ được phân công, không thể tiếp nhận bệnh nhân.`;
}

function codeOf(error) {
  return error instanceof ApiError ? error.errorResponse?.code : undefined;
}

function isNetworkError(error) {
  return error instanceof ApiError && !error.errorResponse && error.status === undefined;
}

// ASSUMPTION: error codes; the backend currently answers these cases without a stable code.
const ACTIVE_VISIT_CODES = ['ACTIVE_VISIT_EXISTS'];
const ROOM_CODES = ['ROOM_NOT_AVAILABLE', 'ROOM_NOT_ACTIVE', 'ROOM_HAS_NO_DOCTOR'];
const APPOINTMENT_CODES = ['FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED'];

/**
 * Classifies a failed POST /visits:
 * kind is 'activeVisit' (EF-01), 'room' (EF-03), 'appointment' (EF-04), 'session' or 'system' (EF-05).
 */
export function visitCreateError(error) {
  const code = codeOf(error);
  if (ACTIVE_VISIT_CODES.includes(code)) return { kind: 'activeVisit' };
  if (ROOM_CODES.includes(code)) return { kind: 'room' };
  if (APPOINTMENT_CODES.includes(code)) return { kind: 'appointment', message: MSG91 };
  if (code === 'UNAUTHORIZED' || error?.status === 401) return { kind: 'session', message: MSG12 };
  if (isNetworkError(error)) return { kind: 'system', message: NETWORK_MESSAGE };
  return { kind: 'system', message: MSG99 };
}

// ASSUMPTION: card-not-valid codes of the proposed verification endpoint.
const INVALID_CARD_CODES = ['INSURANCE_CARD_NOT_FOUND', 'INSURANCE_CARD_INVALID', 'INSURANCE_CARD_EXPIRED'];

/** 'invalid' (AF-02, MSG17) or 'unavailable' (AF-03, MSG20) for a failed verification. */
export function insuranceErrorKind(error) {
  return INVALID_CARD_CODES.includes(codeOf(error)) ? 'invalid' : 'unavailable';
}

export function loadErrorMessage(error) {
  if (error?.status === 401) return MSG12;
  if (isNetworkError(error)) return NETWORK_MESSAGE;
  return MSG99;
}
