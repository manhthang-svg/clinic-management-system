import { ApiError } from '../../apis/apiError';
import { MSG12, MSG13, MSG80, msg02, msg05, msg08, msg39, msg40, msg75 } from './serviceMessages';

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
  // MSG02 when a bound is empty, MSG05 when it is not a number.
  const boundError = (text, label) => (text.trim() === '' ? msg02(label) : msg05(label));
  if (referenceType === 'RANGE' || referenceType === 'LOWER_LIMIT') {
    if (lower === null) errors.lowerBound = boundError(form.lowerBound, 'Giá trị nhỏ nhất');
  }
  if (referenceType === 'RANGE' || referenceType === 'UPPER_LIMIT') {
    if (upper === null) errors.upperBound = boundError(form.upperBound, 'Giá trị lớn nhất');
  }
  // ASSUMPTION: the message list has no text for bounds in the wrong order (BR-136).
  if (referenceType === 'RANGE' && lower !== null && upper !== null && lower >= upper) {
    errors.upperBound = 'Giá trị lớn nhất phải lớn hơn giá trị nhỏ nhất.';
  }
  if (referenceType === 'QUALITATIVE' && splitValues(form.normalValues).length === 0) {
    errors.normalValues = msg02('Giá trị bình thường');
  }
  if (referenceType === 'TEXT' && !form.referenceText.trim()) {
    errors.referenceText = msg02('Giá trị tham chiếu');
  }
  if (!form.source.trim()) errors.source = msg02('Nguồn tham chiếu');
  else if (form.source.trim().length > 255) errors.source = msg08(255);
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
  UNAUTHORIZED: MSG12,
  FORBIDDEN: MSG13,
  CONCURRENT_MODIFICATION: msg75('Khoảng tham chiếu'),
  LAB_REFERENCE_RANGE_NOT_FOUND: msg39('khoảng tham chiếu'),
  LAB_PARAMETER_INVALID: msg39('chỉ số xét nghiệm'),
  REQUEST_VALIDATION_FAILED: msg05('Giá trị tham chiếu'),
};

/** One message for a failed revision; reload tells the page to refresh its list. */
export function revisionError(error) {
  if (!(error instanceof ApiError)) return { message: MSG80, reload: false };
  const code = error.errorResponse?.code;
  if (code === 'LAB_REFERENCE_RANGE_INVALID') {
    // An ended range is no longer a usable choice (MSG40); other rejections are a wrong value (MSG05).
    return error.errorResponse?.details?.referenceRange === 'NOT_CURRENT'
      ? { message: msg40('Khoảng tham chiếu'), reload: true }
      : { message: msg05('Giá trị tham chiếu'), reload: false };
  }
  return {
    message: CODE_MESSAGE[code] || MSG80,
    reload: ['CONCURRENT_MODIFICATION', 'LAB_REFERENCE_RANGE_NOT_FOUND'].includes(code),
  };
}
