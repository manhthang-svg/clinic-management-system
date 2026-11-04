import { MSG50, msg02, msg05 } from './serviceMessages';

// MSG02 when a bound is empty, MSG05 when it is not a number.
const boundError = (value, label) => (value === null ? msg02(label) : msg05(label));

const today = () => new Date().toISOString().slice(0, 10);

export function emptyReferenceRangeForm() {
  return {
    referenceType: 'RANGE',
    lowerBound: '',
    upperBound: '',
    lowerInclusive: true,
    upperInclusive: true,
    normalValues: '',
    referenceText: '',
    sex: '',
    ageMinDays: '',
    ageMaxDays: '',
    effectiveFrom: today(),
    effectiveTo: '',
    source: '',
    active: true,
    version: null,
  };
}

export function referenceRangeToForm(range) {
  return {
    referenceType: range.referenceType,
    lowerBound: range.lowerBound ?? '',
    upperBound: range.upperBound ?? '',
    lowerInclusive: range.lowerInclusive,
    upperInclusive: range.upperInclusive,
    normalValues: range.normalValues?.join(', ') ?? '',
    referenceText: range.referenceText ?? '',
    sex: range.sex == null ? '' : String(range.sex),
    ageMinDays: range.ageMinDays ?? '',
    ageMaxDays: range.ageMaxDays ?? '',
    effectiveFrom: range.effectiveFrom,
    effectiveTo: range.effectiveTo ?? '',
    source: range.source,
    active: range.active,
    version: range.version,
  };
}

export function validateReferenceRange(form) {
  const errors = {};
  const lower = form.lowerBound === '' ? null : Number(form.lowerBound);
  const upper = form.upperBound === '' ? null : Number(form.upperBound);
  const minAge = form.ageMinDays === '' ? null : Number(form.ageMinDays);
  const maxAge = form.ageMaxDays === '' ? null : Number(form.ageMaxDays);

  if (!form.source.trim()) errors.source = msg02('Nguồn tham chiếu');
  if (!form.effectiveFrom) errors.effectiveFrom = msg02('Ngày bắt đầu hiệu lực');
  if (form.effectiveTo && form.effectiveFrom && form.effectiveTo < form.effectiveFrom) {
    errors.effectiveTo = MSG50;
  }
  if (minAge !== null && (!Number.isInteger(minAge) || minAge < 0)) {
    errors.ageMinDays = msg05('Tuổi tối thiểu');
  }
  if (maxAge !== null && (!Number.isInteger(maxAge) || maxAge < 0)) {
    errors.ageMaxDays = msg05('Tuổi tối đa');
  }
  if (minAge !== null && maxAge !== null && minAge > maxAge) {
    errors.ageMaxDays = 'Tuổi tối đa phải lớn hơn hoặc bằng tuổi tối thiểu.';
  }

  if (form.referenceType === 'RANGE') {
    if (lower === null || !Number.isFinite(lower)) errors.lowerBound = boundError(lower, 'Cận dưới');
    if (upper === null || !Number.isFinite(upper)) errors.upperBound = boundError(upper, 'Cận trên');
    if (lower !== null && upper !== null && lower >= upper) {
      errors.upperBound = 'Cận trên phải lớn hơn cận dưới.';
    }
  } else if (form.referenceType === 'UPPER_LIMIT') {
    if (upper === null || !Number.isFinite(upper)) errors.upperBound = boundError(upper, 'Giới hạn trên');
  } else if (form.referenceType === 'LOWER_LIMIT') {
    if (lower === null || !Number.isFinite(lower)) errors.lowerBound = boundError(lower, 'Giới hạn dưới');
  } else if (form.referenceType === 'QUALITATIVE') {
    if (!splitNormalValues(form.normalValues).length) errors.normalValues = msg02('Giá trị bình thường');
  } else if (form.referenceType === 'TEXT' && !form.referenceText.trim()) {
    errors.referenceText = msg02('Nội dung tham chiếu');
  }

  return errors;
}

export function referenceRangeToRequest(form) {
  const numeric = (value) => (value === '' ? null : Number(value));
  const type = form.referenceType;
  return {
    referenceType: type,
    lowerBound: type === 'RANGE' || type === 'LOWER_LIMIT' ? numeric(form.lowerBound) : null,
    upperBound: type === 'RANGE' || type === 'UPPER_LIMIT' ? numeric(form.upperBound) : null,
    lowerInclusive: Boolean(form.lowerInclusive),
    upperInclusive: Boolean(form.upperInclusive),
    normalValues: type === 'QUALITATIVE' ? splitNormalValues(form.normalValues) : null,
    referenceText: type === 'TEXT' ? form.referenceText.trim() : null,
    sex: form.sex === '' ? null : Number(form.sex),
    ageMinDays: numeric(form.ageMinDays),
    ageMaxDays: numeric(form.ageMaxDays),
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || null,
    source: form.source.trim(),
    active: Boolean(form.active),
    version: form.version,
  };
}

function splitNormalValues(value) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}
