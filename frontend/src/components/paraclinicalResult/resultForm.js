// UC-67 form rules: lab interpretation, validation and file checks. Pure functions, no React.
import { QUALITATIVE_OPTIONS, RESULT_TEMPLATES } from './resultMockData';
import { composeDescription, emptySections, normalSections, sectionsFor } from './resultSections';

// SRS UC-67 step 2: images or PDF, at most 10 files, each at most 20 MB.
export const MAX_FILES = 10;
export const MAX_FILE_BYTES = 20 * 1024 * 1024;
export const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'application/pdf'];

// SRS messages (MSG02, MSG05, MSG06, MSG13, MSG29, MSG61), in Vietnamese.
export const MSG = {
  required: (field) => `Vui lòng nhập ${field}.`, // MSG02
  format: (field) => `${field} không đúng định dạng.`, // MSG05
  confirmSubmit: 'Bạn có chắc muốn gửi kết quả? Sau khi gửi, kết quả bị khóa và không thể sửa.', // MSG06
  notPerformer: 'Bạn không có quyền thực hiện thao tác này. Chỉ người thực hiện dịch vụ mới được nhập kết quả.', // MSG13
  submitted: 'Kết quả đã được gửi nên không thể sửa nữa.', // MSG61
  saved: 'Đã lưu kết quả và gửi cho bác sĩ chỉ định.', // MSG29
  draftSaved: 'Đã lưu nháp kết quả.', // MSG03
  fixFields: 'Vui lòng sửa các ô được đánh dấu.',
  // UC-68 EF-02. ASSUMPTION: the SRS catalog text of MSG52 belongs to another use case, so this wording is ours.
  printFailed: 'Không thể in phiếu kết quả. Vui lòng kiểm tra máy in và thử lại.',
  clinicMissing: 'Chưa cấu hình thông tin phòng khám (UC-77) nên chưa in được phiếu kết quả.',
  printerOnly: 'Chỉ người thực hiện dịch vụ mới in phiếu kết quả.',
};

export const KIND_LABEL = { LAB: 'Xét nghiệm', IMAGING: 'Chẩn đoán hình ảnh', FUNCTIONAL: 'Thăm dò chức năng' };

/** Badge of lab_result_items.interpretation. */
export const INTERPRETATION = {
  NORMAL: { label: 'Bình thường', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  HIGH: { label: 'Cao ↑', className: 'bg-rose-50 text-rose-700 border-rose-200' },
  LOW: { label: 'Thấp ↓', className: 'bg-sky-50 text-sky-700 border-sky-200' },
  ABNORMAL: { label: 'Bất thường', className: 'bg-rose-50 text-rose-700 border-rose-200' },
};

export function isLab(order) {
  return order.kind === 'LAB';
}

/** Accepts both "6,2" and "6.2". Returns null when the text is not a number. */
export function parseNumber(text) {
  const normalized = String(text ?? '').trim().replace(',', '.');
  if (!/^-?\d+(\.\d+)?$/.test(normalized)) return null;
  return Number(normalized);
}

export function formatReference(ref) {
  if (!ref) return '';
  switch (ref.type) {
    case 'RANGE': return `${ref.low} – ${ref.high}`;
    case 'UPPER': return `< ${ref.high}`;
    case 'LOWER': return `> ${ref.low}`;
    case 'QUALITATIVE': return ref.normal.map(qualitativeLabel).join(', ');
    default: return '';
  }
}

export function qualitativeLabel(value) {
  return QUALITATIVE_OPTIONS.find((option) => option.value === value)?.label ?? value;
}

/**
 * NORMAL, LOW, HIGH or ABNORMAL for an entered value; null while empty or not a number.
 * Mirrors lab_result_items.interpretation.
 */
export function interpret(parameter, value) {
  if (value === undefined || value === null || String(value).trim() === '') return null;
  const { ref } = parameter;
  if (parameter.valueType === 'QUALITATIVE') {
    if (!ref || ref.type !== 'QUALITATIVE') return null;
    return ref.normal.includes(value) ? 'NORMAL' : 'ABNORMAL';
  }
  const number = parseNumber(value);
  if (number === null || !ref) return null;
  if ((ref.type === 'RANGE' || ref.type === 'LOWER') && number < ref.low) return 'LOW';
  if ((ref.type === 'RANGE' || ref.type === 'UPPER') && number > ref.high) return 'HIGH';
  if (ref.type === 'UPPER' && number === ref.high) return 'HIGH';
  if (ref.type === 'LOWER' && number === ref.low) return 'LOW';
  return 'NORMAL';
}

export function emptyLabResult(order) {
  return {
    receivedAt: '',
    instrumentId: '',
    values: Object.fromEntries(order.parameters.map((p) => [p.code, ''])),
    conclusion: '',
    note: '',
  };
}

export function emptyDescriptiveResult() {
  return {
    technique: '', description: '', conclusion: '', recommendation: '', imageLink: '', readingDoctor: '', files: [],
    instrumentId: '',
  };
}

/**
 * The saved fields that hold a value. The API returns null for a field left blank in a draft (e.g. a draft saved
 * before the conclusion was typed); spreading that null over the empty form would break the inputs and `.trim()`.
 */
function withoutBlanks(saved) {
  return Object.fromEntries(Object.entries(saved ?? {}).filter(([, value]) => value !== null && value !== undefined));
}

/** The form state of an order: its saved draft or an empty form. */
export function resultToForm(order) {
  const saved = order.result;
  if (isLab(order)) {
    const empty = emptyLabResult(order);
    if (!saved) return empty;
    return { ...empty, ...withoutBlanks(saved), values: { ...empty.values, ...withoutBlanks(saved.values) } };
  }
  const form = { ...emptyDescriptiveResult(), ...withoutBlanks(saved), files: saved?.files ?? [] };
  if (sectionsFor(order)) form.sections = { ...emptySections(order), ...withoutBlanks(saved?.sections) };
  return form;
}

/** The form as saved and printed: per-organ fields are joined into the description. */
export function formForSave(order, form) {
  if (isLab(order) || !form.sections) return form;
  return { ...form, description: composeDescription(order, form.sections) };
}

/** Normal-result template of a service: per-organ fields or a free-text description. */
export function templateFor(order) {
  const template = RESULT_TEMPLATES[order.serviceCode] ?? {};
  if (sectionsFor(order)) return { sections: normalSections(order), conclusion: template.conclusion };
  return template.description ? template : null;
}

/** Field errors keyed by field name; lab values are keyed "value:<code>". */
export function validateResult(order, form) {
  const errors = {};
  if (isLab(order)) {
    order.parameters.forEach((parameter) => {
      const value = form.values[parameter.code];
      const key = `value:${parameter.code}`;
      if (!String(value ?? '').trim()) {
        errors[key] = 'Bắt buộc';
      } else if (parameter.valueType === 'NUMERIC' && parseNumber(value) === null) {
        errors[key] = 'Phải là số';
      }
    });
  } else if (form.sections) {
    sectionsFor(order).forEach((section) => {
      if (!String(form.sections[section.key] ?? '').trim()) errors[`section:${section.key}`] = 'Bắt buộc';
    });
  } else if (!form.description.trim()) {
    errors.description = MSG.required('mô tả kết quả');
  }
  if (!form.conclusion.trim()) errors.conclusion = MSG.required('kết luận');
  if (form.imageLink?.trim() && !/^https?:\/\/\S+$/i.test(form.imageLink.trim())) {
    errors.imageLink = MSG.format('Link xem ảnh');
  }
  return errors;
}

/** Splits picked files into accepted ones and Vietnamese error messages. */
export function checkFiles(picked, existingCount) {
  const accepted = [];
  const errors = [];
  Array.from(picked).forEach((file) => {
    if (!ACCEPTED_TYPES.includes(file.type)) {
      errors.push(`${file.name}: chỉ nhận ảnh JPG, PNG hoặc file PDF.`);
    } else if (file.size > MAX_FILE_BYTES) {
      errors.push(`${file.name}: dung lượng vượt quá 20 MB.`);
    } else if (existingCount + accepted.length >= MAX_FILES) {
      errors.push(`${file.name}: chỉ được đính kèm tối đa ${MAX_FILES} file.`);
    } else {
      accepted.push(file);
    }
  });
  return { accepted, errors };
}

export function formatFileSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** "Ngày 05 tháng 10 năm 2026", as printed on the forms. */
export function formatLongDate(iso) {
  if (!iso) return 'Ngày …… tháng …… năm ……';
  const date = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `Ngày ${pad(date.getDate())} tháng ${pad(date.getMonth() + 1)} năm ${date.getFullYear()}`;
}

export function formatTime(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

export function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

const ERROR_MESSAGE = {
  NOT_FOUND: 'Không tìm thấy chỉ định.',
  REQUEST_VALIDATION_FAILED: MSG.fixFields,
  RESULT_NOT_EDITABLE: 'Chỉ định chưa được xác nhận thực hiện nên chưa thể nhập kết quả.',
  CLINIC_FACILITY_NOT_CONFIGURED: MSG.clinicMissing,
  INSTRUMENT_UNAVAILABLE: 'Máy thực hiện đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.', // MSG40
  NOT_PERFORMER: MSG.notPerformer,
  RESULT_ALREADY_SUBMITTED: MSG.submitted,
};
const FALLBACK = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.'; // MSG99

/** Vietnamese message for a failed load, save or submit. */
export function resultErrorMessage(error) {
  return ERROR_MESSAGE[error?.code] ?? FALLBACK;
}
