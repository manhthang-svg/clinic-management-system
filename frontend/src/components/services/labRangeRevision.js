import { ApiError } from '../../apis/apiError';

const CLINIC_TIME_ZONE = 'Asia/Ho_Chi_Minh';

/** Today in the clinic's time zone as YYYY-MM-DD, the day a revised range takes effect. */
export function clinicToday() {
  return new Date().toLocaleDateString('en-CA', { timeZone: CLINIC_TIME_ZONE });
}

/** True when the range is active and its effective period covers `day` (YYYY-MM-DD). */
export function isCurrentRange(range, day) {
  return range.active && range.effectiveFrom <= day && (!range.effectiveTo || range.effectiveTo >= day);
}

const toText = (value) => (value === null || value === undefined ? '' : String(value).replace('.', ','));

/** The editable values of a range; who it applies to (sex, age) stays as it is. */
export function rangeToRevisionForm(range) {
  return {
    lowerBound: toText(range.lowerBound),
    upperBound: toText(range.upperBound),
    normalValues: (range.normalValues ?? []).join(', '),
    referenceText: range.referenceText ?? '',
    source: range.source ?? '',
  };
}

const parseNumber = (text) => {
  const value = text.trim().replace(',', '.');
  return value === '' || Number.isNaN(Number(value)) ? null : Number(value);
};

const splitValues = (text) => text.split(',').map((value) => value.trim()).filter(Boolean);

// Mirrors LabReferenceRange.validate in the backend, for feedback only.
export function validateRevisionForm(form, referenceType) {
  const errors = {};
  const lower = parseNumber(form.lowerBound);
  const upper = parseNumber(form.upperBound);
  if (referenceType === 'RANGE' || referenceType === 'LOWER_LIMIT') {
    if (lower === null) errors.lowerBound = 'Vui lòng nhập giá trị nhỏ nhất là một số.';
  }
  if (referenceType === 'RANGE' || referenceType === 'UPPER_LIMIT') {
    if (upper === null) errors.upperBound = 'Vui lòng nhập giá trị lớn nhất là một số.';
  }
  if (referenceType === 'RANGE' && lower !== null && upper !== null && lower >= upper) {
    errors.upperBound = 'Giá trị lớn nhất phải lớn hơn giá trị nhỏ nhất.';
  }
  if (referenceType === 'QUALITATIVE' && splitValues(form.normalValues).length === 0) {
    errors.normalValues = 'Vui lòng nhập ít nhất một giá trị bình thường.';
  }
  if (referenceType === 'TEXT' && !form.referenceText.trim()) {
    errors.referenceText = 'Vui lòng nhập giá trị tham chiếu.';
  }
  if (!form.source.trim()) errors.source = 'Vui lòng nhập nguồn tham chiếu.';
  else if (form.source.trim().length > 255) errors.source = 'Nguồn tham chiếu tối đa 255 ký tự.';
  return errors;
}

/**
 * Body of POST .../reference-ranges/{id}/revisions: the old range's type, sex and age with the new values.
 * The backend starts the revision today and ends the old range yesterday.
 */
export function revisionFormToRequest(range, form, today) {
  const type = range.referenceType;
  const usesLower = type === 'RANGE' || type === 'LOWER_LIMIT';
  const usesUpper = type === 'RANGE' || type === 'UPPER_LIMIT';
  return {
    referenceType: type,
    lowerBound: usesLower ? parseNumber(form.lowerBound) : null,
    upperBound: usesUpper ? parseNumber(form.upperBound) : null,
    lowerInclusive: range.lowerInclusive,
    upperInclusive: range.upperInclusive,
    normalValues: type === 'QUALITATIVE' ? splitValues(form.normalValues) : null,
    referenceText: type === 'TEXT' ? form.referenceText.trim() : null,
    sex: range.sex,
    ageMinDays: range.ageMinDays,
    ageMaxDays: range.ageMaxDays,
    effectiveFrom: today,
    effectiveTo: null,
    source: form.source.trim(),
    active: true,
    version: range.version,
  };
}

const CODE_MESSAGE = {
  UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  FORBIDDEN: 'Bạn không có quyền cập nhật chỉ số tham chiếu.',
  CONCURRENT_MODIFICATION: 'Khoảng tham chiếu vừa được cập nhật ở nơi khác. Danh sách đã được tải lại, vui lòng thử lại.',
  LAB_REFERENCE_RANGE_NOT_FOUND: 'Khoảng tham chiếu không còn tồn tại. Danh sách đã được tải lại.',
  LAB_PARAMETER_INVALID: 'Chỉ số xét nghiệm không còn tồn tại.',
  REQUEST_VALIDATION_FAILED: 'Một số thông tin chưa hợp lệ. Vui lòng kiểm tra lại.',
};

/** One message for a failed revision; reload tells the page to refresh its list. */
export function revisionError(error) {
  if (!(error instanceof ApiError)) return { message: 'Đã xảy ra lỗi. Vui lòng thử lại.', reload: false };
  const code = error.errorResponse?.code;
  if (!code && error.status === undefined) {
    return { message: 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.', reload: false };
  }
  if (code === 'LAB_REFERENCE_RANGE_INVALID') {
    return error.errorResponse?.details?.referenceRange === 'NOT_CURRENT'
      ? { message: 'Khoảng tham chiếu này đã hết hiệu lực. Danh sách đã được tải lại.', reload: true }
      : { message: 'Giá trị tham chiếu không hợp lệ. Vui lòng kiểm tra lại.', reload: false };
  }
  return {
    message: CODE_MESSAGE[code] || 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
    reload: ['CONCURRENT_MODIFICATION', 'LAB_REFERENCE_RANGE_NOT_FOUND'].includes(code),
  };
}
