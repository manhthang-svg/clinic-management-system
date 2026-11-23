// UC-67: quy tac form ket qua: danh gia chi so, kiem tra du lieu, kiem tra file
import { QUALITATIVE_OPTIONS, RESULT_TEMPLATES } from './resultMockData';
import { composeDescription, emptySections, normalSections, sectionsFor } from './resultSections';

// anh: toi da 10 anh, chi JPG / PNG, moi anh toi da 10 MB
export const MAX_FILES = 10;
export const MAX_FILE_BYTES = 10 * 1024 * 1024;
export const ACCEPTED_TYPES = ['image/jpeg', 'image/png'];

// thong bao theo bang System Messages
export const MSG = {
  required: (field) => `Vui lòng nhập ${field}.`, // MSG02
  imageRequired: 'Vui lòng tải lên ít nhất 1 ảnh kết quả.',
  format: (field) => `${field} không đúng định dạng.`, // MSG05
  confirmSubmit: 'Bạn có chắc muốn gửi kết quả? Sau khi gửi, kết quả bị khóa và không thể sửa.', // MSG06
  notPerformer: 'Bạn không có quyền thực hiện thao tác này. Chỉ người thực hiện dịch vụ mới được nhập kết quả.', // MSG13
  submitted: 'Kết quả đã được gửi nên không thể sửa nữa.', // MSG61
  saved: 'Đã lưu kết quả và gửi cho bác sĩ chỉ định.', // MSG29
  draftSaved: 'Đã lưu nháp kết quả.', // MSG03
  fixFields: 'Vui lòng sửa các ô được đánh dấu.',
  // UC-68: thong bao loi in (tu dat, MSG52 trong SRS la cua UC khac)
  printFailed: 'Không thể in phiếu kết quả. Vui lòng kiểm tra máy in và thử lại.',
  clinicMissing: 'Chưa cấu hình thông tin phòng khám (UC-77) nên chưa in được phiếu kết quả.',
  printerOnly: 'Chỉ người thực hiện dịch vụ mới in phiếu kết quả.',
};

export const KIND_LABEL = { LAB: 'Xét nghiệm', IMAGING: 'Chẩn đoán hình ảnh', FUNCTIONAL: 'Thăm dò chức năng' };

// nhan + mau cho danh gia chi so (binh thuong / thap / cao)
export const INTERPRETATION = {
  NORMAL: { label: 'Bình thường', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  HIGH: { label: 'Cao ↑', className: 'bg-rose-50 text-rose-700 border-rose-200' },
  LOW: { label: 'Thấp ↓', className: 'bg-sky-50 text-sky-700 border-sky-200' },
  ABNORMAL: { label: 'Bất thường', className: 'bg-rose-50 text-rose-700 border-rose-200' },
};

export function isLab(order) {
  return order.kind === 'LAB';
}

// doi chuoi thanh so, nhan ca "6,2" va "6.2"; khong phai so thi null
export function parseNumber(text) {
  const normalized = String(text ?? '').trim().replace(',', '.');
  if (!/^-?\d+(\.\d+)?$/.test(normalized)) return null;
  return Number(normalized);
}

// khoang tham chieu dang chu de hien thi
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

// danh gia gia tri vua nhap: NORMAL / LOW / HIGH / ABNORMAL (giong backend)
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

// form xet nghiem rong
export function emptyLabResult(order) {
  return {
    receivedAt: '',
    instrumentId: '',
    values: Object.fromEntries(order.parameters.map((p) => [p.code, ''])),
    conclusion: '',
    note: '',
  };
}

// form mo ta (hinh anh, tham do) rong
export function emptyDescriptiveResult() {
  return {
    technique: '', description: '', conclusion: '', recommendation: '', imageLink: '', readingDoctor: '', files: [],
    instrumentId: '',
  };
}

// bo cac truong null cua ban nhap (de khong de null len form rong)
function withoutBlanks(saved) {
  return Object.fromEntries(Object.entries(saved ?? {}).filter(([, value]) => value !== null && value !== undefined));
}

// form cua 1 phieu: ban nhap da luu hoac form rong
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

// form khi luu / in: gop cac muc tung co quan thanh mo ta
export function formForSave(order, form) {
  if (isLab(order) || !form.sections) return form;
  return { ...form, description: composeDescription(order, form.sections) };
}

// mau ket qua binh thuong cua dich vu
export function templateFor(order) {
  const template = RESULT_TEMPLATES[order.serviceCode] ?? {};
  if (sectionsFor(order)) return { sections: normalSections(order), conclusion: template.conclusion };
  return template.description ? template : null;
}

// kiem tra truoc khi gui, tra ve loi theo tung truong
// phieu CT/MRI: anh nam tren PACS, nhap link thay cho tai anh len
export const LINK_IMAGE_FORMS = ['CT', 'MRI'];

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
  if (!isLab(order) && !(form.files ?? []).length && !form.imageLink?.trim()) {
    // chan doan hinh anh / tham do chuc nang phai co anh; CT/MRI xem anh qua link PACS
    if (LINK_IMAGE_FORMS.includes(order.printForm)) errors.imageLink = MSG.required('link xem ảnh');
    else errors.files = MSG.imageRequired;
  }
  if (!form.conclusion.trim()) errors.conclusion = MSG.required('kết luận');
  if (form.imageLink?.trim() && !/^https?:\/\/\S+$/i.test(form.imageLink.trim())) {
    errors.imageLink = MSG.format('Link xem ảnh');
  }
  return errors;
}

// tach file chon thanh file hop le va thong bao loi
export function checkFiles(picked, existingCount) {
  const accepted = [];
  const errors = [];
  Array.from(picked).forEach((file) => {
    if (!ACCEPTED_TYPES.includes(file.type)) {
      errors.push(`${file.name}: chỉ nhận ảnh JPG hoặc PNG.`);
    } else if (file.size > MAX_FILE_BYTES) {
      errors.push(`${file.name}: dung lượng vượt quá 10 MB.`);
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

// ngay dang "Ngay 05 thang 10 nam 2026" de in phieu
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
  RESULT_IMAGE_INVALID: 'Ảnh không hợp lệ: chỉ nhận ảnh JPG hoặc PNG tối đa 10 MB.',
  RESULT_IMAGE_LIMIT_REACHED: 'Mỗi kết quả chỉ được đính kèm tối đa 10 ảnh.',
  RESULT_IMAGE_NOT_FOUND: 'Ảnh không còn tồn tại. Vui lòng tải lại trang.',
  FILE_STORAGE_NOT_CONFIGURED: 'Chưa cấu hình nơi lưu ảnh (Cloudinary). Vui lòng báo quản trị viên.',
  FILE_STORAGE_UNAVAILABLE: 'Không tải được ảnh lên kho lưu trữ. Vui lòng thử lại.',
};
const FALLBACK = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.'; // MSG99

// doi loi tai / luu / gui thanh thong bao tieng Viet
export function resultErrorMessage(error) {
  return ERROR_MESSAGE[error?.code] ?? FALLBACK;
}
