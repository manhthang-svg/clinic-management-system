import { ApiError } from '../../apis/apiError';

// Message texts follow the SRS message list (MSG12, MSG17, MSG20, MSG22, MSG56, MSG80).
export const MSG12 = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
export const MSG17 = 'Thẻ BHYT không hợp lệ hoặc đã hết hạn. Lượt khám sẽ được đăng ký dạng Dịch vụ.';
export const MSG20 = 'Không thể kết nối với cổng BHYT. Vui lòng thử lại hoặc đăng ký lượt khám theo hình thức dịch vụ.';
export const MSG80 = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
// MSG102: no examination room can receive patients now (UC-40 step 8, UC-42 EF-01).
export const MSG102 = 'Không có phòng khám nào đang hoạt động có thể tiếp nhận bệnh nhân. Vui lòng báo quản trị viên.';
// ASSUMPTION: MSG91 is not in the SRS message list available in docs/.
export const MSG91 = 'Lịch hẹn tái khám vừa được gắn với lượt khám khác. Thông tin lịch hẹn đã được tải lại.';

export const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';

/**
 * MSG21: registration confirmed. A Dịch vụ visit that still has to pay has no queue number yet (I1-D48), so the
 * message says when it is issued instead.
 */
export function msg21(visit) {
  return visit.queueNumber != null
    ? `Đã đăng ký lượt khám ${visit.sovaovien}. Số thứ tự: ${visit.queueNumber}.`
    : `Đã đăng ký lượt khám ${visit.sovaovien}. Số thứ tự được cấp sau khi thanh toán phí khám.`;
}

/** MSG22: the patient already has an unfinished visit (UC-40 EF-01). */
export function msg22(visit) {
  return `Bệnh nhân này đã có lượt khám ${visit.sovaovien} đang hoạt động.`;
}

// MSG53: the selected room cannot receive patients (inactive, or no doctor on duty; BR-96).
export function msg56(roomName) {
  return `${roomName ?? 'Phòng khám'} đã ngừng hoạt động hoặc không có bác sĩ làm việc trong ngày nên không thể tiếp nhận bệnh nhân.`;
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
const INSURANCE_CODES = ['INSURANCE_CARD_INVALID'];
// BR-129.
export const REFERRAL_DIAGNOSIS_MESSAGE = 'Vui lòng nhập chẩn đoán của nơi giới thiệu (2–1.000 ký tự).';

/**
 * Classifies a failed POST /visits:
 * kind is 'activeVisit' (EF-01), 'room' (EF-03), 'appointment' (EF-04), 'session' or 'system' (EF-05).
 */
export function visitCreateError(error) {
  const code = codeOf(error);
  if (ACTIVE_VISIT_CODES.includes(code)) return { kind: 'activeVisit' };
  if (ROOM_CODES.includes(code)) return { kind: 'room' };
  if (APPOINTMENT_CODES.includes(code)) return { kind: 'appointment', message: MSG91 };
  if (INSURANCE_CODES.includes(code)) return { kind: 'insurance', message: MSG17 };
  if (code === 'REFERRAL_DIAGNOSIS_REQUIRED') return { kind: 'system', message: REFERRAL_DIAGNOSIS_MESSAGE };
  if (code === 'UNAUTHORIZED' || error?.status === 401) return { kind: 'session', message: MSG12 };
  if (isNetworkError(error)) return { kind: 'system', message: NETWORK_MESSAGE };
  return { kind: 'system', message: MSG80 };
}

// ASSUMPTION: card-not-valid codes of the proposed verification endpoint.
const INVALID_CARD_CODES = ['INSURANCE_CARD_NOT_FOUND', 'INSURANCE_CARD_INVALID', 'INSURANCE_CARD_EXPIRED'];

/** Classifies a non-timeout failure from POST /insurance/verifications. */
export function insuranceErrorKind(error) {
  if (codeOf(error) === 'INSURANCE_PATIENT_MISMATCH') return 'mismatch';
  return INVALID_CARD_CODES.includes(codeOf(error)) ? 'invalid' : 'system';
}

export function loadErrorMessage(error) {
  if (error?.status === 401) return MSG12;
  if (isNetworkError(error)) return NETWORK_MESSAGE;
  return MSG80;
}

// UC-41: reasons the examination ticket cannot be assembled (VisitServiceImpl.toTicketResponse).
const TICKET_CODE_MESSAGES = {
  CLINIC_FACILITY_NOT_CONFIGURED: 'Chưa cấu hình thông tin cơ sở khám bệnh. Vui lòng liên hệ quản trị viên.',
  PATIENT_NOT_FOUND: 'Không tìm thấy hồ sơ bệnh nhân của lượt khám.',
  ROOM_NOT_AVAILABLE: 'Không tìm thấy phòng khám của lượt khám.',
  NOT_FOUND: 'Không tìm thấy lượt khám hoặc phiếu thu phí khám.',
  FORBIDDEN: 'Bạn không có quyền xem phiếu khám.',
  PAYMENT_NOT_PAID: 'Chỉ in được phiếu số thứ tự sau khi đã thanh toán phí khám.',
};

/** Message for a failed ticket load; an unknown failure names its error code so it can be reported. */
export function ticketErrorMessage(error) {
  const code = codeOf(error);
  if (TICKET_CODE_MESSAGES[code]) return TICKET_CODE_MESSAGES[code];
  if (error?.status === 401) return MSG12;
  if (isNetworkError(error)) return NETWORK_MESSAGE;
  const reference = code ?? (error?.status ? `HTTP ${error.status}` : error?.name);
  return reference ? `${MSG80} (Mã lỗi: ${reference})` : MSG80;
}
