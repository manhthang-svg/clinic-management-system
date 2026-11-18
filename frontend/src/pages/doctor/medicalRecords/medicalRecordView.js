/** UC-51, UC-52: messages, filter rules and labels (docs/uc-51-52-medical-record-view.md). */
import { ApiError } from '../../../apis/apiError';
import { VISIT_STATUS_LABELS } from '../../../constants/visitData';

// BR-51-04: keyword and ICD-10 text limits, mirrored from the backend for feedback only.
export const KEYWORD_MIN_LENGTH = 2;
export const TEXT_FILTER_MAX_LENGTH = 100;

export const RECORD_MSG = {
  NO_RESULT: 'Không tìm thấy kết quả.', // MSG01
  KEYWORD_TOO_SHORT: `Vui lòng nhập ít nhất ${KEYWORD_MIN_LENGTH} ký tự để tìm kiếm.`, // MSG132
  TOO_LONG: `Vượt quá độ dài tối đa ${TEXT_FILTER_MAX_LENGTH} ký tự.`, // MSG08
  DATE_RANGE: 'Ngày bắt đầu không được sau ngày kết thúc.', // MSG50
  PATIENT_NOT_FOUND: 'Không tìm thấy bệnh nhân.', // MSG39
  RECORD_NOT_FOUND: 'Không tìm thấy hồ sơ bệnh án.', // MSG39
  FILE_NOT_FOUND: 'Không tìm thấy tệp kết quả.', // MSG39
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
  SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
  SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.', // MSG80
};

// BR-51-04: the four status groups of the search; the backend owns which statuses each group holds.
export const VISIT_FILTER_OPTIONS = [
  ['ALL', 'Tất cả trạng thái'],
  ['ACTIVE', 'Đang hoạt động'],
  ['COMPLETED', 'Hoàn thành'],
  ['ABANDONED', 'Bỏ khám'],
  ['CANCELLED', 'Đã hủy'],
];

/** BR-51-04 client checks; returns { keyword?, icd10?, from? } with the message to show under each field. */
export function searchFieldErrors({ keyword, icd10, from, to }) {
  const errors = {};
  const search = (keyword ?? '').trim();
  if (search && search.length < KEYWORD_MIN_LENGTH) errors.keyword = RECORD_MSG.KEYWORD_TOO_SHORT;
  if (search.length > TEXT_FILTER_MAX_LENGTH) errors.keyword = RECORD_MSG.TOO_LONG;
  if ((icd10 ?? '').trim().length > TEXT_FILTER_MAX_LENGTH) errors.icd10 = RECORD_MSG.TOO_LONG;
  if (from && to && from > to) errors.from = RECORD_MSG.DATE_RANGE;
  return errors;
}

const FIELD_REASON_MESSAGES = {
  'keyword:TOO_SHORT': RECORD_MSG.KEYWORD_TOO_SHORT,
  'keyword:TOO_LONG': RECORD_MSG.TOO_LONG,
  'icd10:TOO_LONG': RECORD_MSG.TOO_LONG,
  'from:AFTER_TO': RECORD_MSG.DATE_RANGE,
};

/** Maps a failed UC-51/UC-52 request to a Vietnamese message; never shows the backend's own text. */
export function recordErrorMessage(error) {
  if (!(error instanceof ApiError)) return RECORD_MSG.SYSTEM_ERROR;
  const code = error.errorResponse?.code;
  if (code === 'PATIENT_NOT_FOUND') return RECORD_MSG.PATIENT_NOT_FOUND;
  if (code === 'MEDICAL_RECORD_NOT_FOUND') return RECORD_MSG.RECORD_NOT_FOUND;
  if (code === 'RESULT_IMAGE_NOT_FOUND') return RECORD_MSG.FILE_NOT_FOUND;
  if (code === 'REQUEST_VALIDATION_FAILED') {
    const details = error.errorResponse?.details ?? {};
    for (const [field, reason] of Object.entries(details)) {
      const message = FIELD_REASON_MESSAGES[`${field}:${reason}`];
      if (message) return message;
    }
    return RECORD_MSG.SYSTEM_ERROR;
  }
  if (error.status === 401) return RECORD_MSG.SESSION_EXPIRED;
  if (error.status === 403) return RECORD_MSG.FORBIDDEN;
  return RECORD_MSG.SYSTEM_ERROR;
}

/** BR-51-06: one label per status; a skipped visit keeps its status and reads "Đã bỏ qua". */
export function visitStatusLabel(status, skipped = false) {
  if (skipped) return 'Đã bỏ qua';
  return VISIT_STATUS_LABELS[status] ?? '—';
}

/** An ISO date (yyyy-MM-dd) as dd/MM/yyyy (DESIGN.md Content and Language); "—" when empty. */
export function formatDate(value) {
  if (!value) return '—';
  const [year, month, day] = String(value).slice(0, 10).split('-');
  return day && month && year ? `${day}/${month}/${year}` : '—';
}
