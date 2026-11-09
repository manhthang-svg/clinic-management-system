// Form state, client validation and requests of the medicine form (UC-82, UC-83). The rules mirror BR-79 for
// feedback only; the backend enforces them.
import { msg02, msg05, msg08 } from '../services/serviceMessages';

export const LIMITS = {
  code: 50,
  registrationNumber: 20,
  insuranceCode: 255,
  name: 255,
  activeIngredient: 500,
  strength: 255,
  unit: 50,
};

export const FIELD_LABELS = {
  code: 'Mã thuốc',
  registrationNumber: 'Số đăng ký',
  insuranceCode: 'Mã thuốc theo danh mục BHYT',
  name: 'Tên thuốc',
  activeIngredient: 'Hoạt chất',
  strength: 'Hàm lượng',
  route: 'Đường dùng',
  unit: 'Đơn vị tính',
  controlGroup: 'Nhóm kiểm soát',
};

const REQUIRED = ['code', 'registrationNumber', 'name', 'activeIngredient', 'strength', 'route', 'unit', 'controlGroup'];
const MIN_TWO = ['name', 'activeIngredient'];
const CODE_PATTERN = /^[A-Z0-9-]+$/;

export const EMPTY_MEDICINE_FORM = {
  code: '',
  registrationNumber: '',
  insuranceCode: '',
  name: '',
  activeIngredient: '',
  strength: '',
  route: '',
  unit: '',
  controlGroup: '',
};

export function medicineToForm(medicine) {
  return {
    code: medicine.code,
    registrationNumber: medicine.registrationNumber,
    insuranceCode: medicine.insuranceCode ?? '',
    name: medicine.name,
    activeIngredient: medicine.activeIngredient,
    strength: medicine.strength,
    route: medicine.route,
    unit: medicine.unit,
    controlGroup: medicine.controlGroup,
  };
}

/** Field errors of the form; editing skips the code, which cannot change (I1-D46). */
export function validateMedicineForm(form, { editing = false } = {}) {
  const errors = {};
  Object.keys(EMPTY_MEDICINE_FORM).forEach((field) => {
    if (editing && field === 'code') return;
    const value = String(form[field] ?? '').trim();
    if (value === '') {
      if (REQUIRED.includes(field)) errors[field] = msg02(FIELD_LABELS[field]); // MSG02
      return;
    }
    if (LIMITS[field] && value.length > LIMITS[field]) errors[field] = msg08(LIMITS[field]); // MSG08
    else if (MIN_TWO.includes(field) && value.length < 2) errors[field] = msg05(FIELD_LABELS[field]); // MSG05
    else if (field === 'code' && !CODE_PATTERN.test(value.toUpperCase())) errors[field] = msg05(FIELD_LABELS[field]);
  });
  return errors;
}

function trimmed(form) {
  return {
    registrationNumber: form.registrationNumber.trim(),
    insuranceCode: form.insuranceCode.trim() === '' ? null : form.insuranceCode.trim(),
    name: form.name.trim(),
    activeIngredient: form.activeIngredient.trim(),
    strength: form.strength.trim(),
    route: form.route,
    unit: form.unit.trim(),
    controlGroup: form.controlGroup,
  };
}

/** UC-82 request; the server also upper-cases the code. */
export function formToCreateRequest(form) {
  return { code: form.code.trim().toUpperCase(), ...trimmed(form) };
}

/** UC-83 request with the version the form was loaded with. */
export function formToUpdateRequest(form, version) {
  return { ...trimmed(form), version };
}
