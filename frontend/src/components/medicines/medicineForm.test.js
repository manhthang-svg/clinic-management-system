import { describe, expect, it } from 'vitest';
import {
  EMPTY_MEDICINE_FORM,
  formToCreateRequest,
  formToUpdateRequest,
  validateMedicineForm,
} from './medicineForm';

const valid = {
  code: 't-0001',
  registrationNumber: ' 840100205826 ',
  insuranceCode: '',
  name: 'EU-Para 500mg',
  activeIngredient: 'Paracetamol',
  strength: '500mg',
  route: 'Uống',
  unit: 'Viên',
  controlGroup: 'THUONG',
};

describe('medicine form (UC-82, UC-83)', () => {
  it('requires the BR-79 fields with MSG02 and leaves the insurance code optional', () => {
    const errors = validateMedicineForm(EMPTY_MEDICINE_FORM);

    expect(errors.code).toBe('Mã thuốc là bắt buộc.');
    expect(errors.route).toBe('Đường dùng là bắt buộc.');
    expect(errors.controlGroup).toBe('Nhóm kiểm soát là bắt buộc.');
    expect(errors.insuranceCode).toBeUndefined();
    expect(validateMedicineForm(valid)).toEqual({});
  });

  it('checks the code format, the minimum lengths and the maximum lengths', () => {
    expect(validateMedicineForm({ ...valid, code: 'T 01' }).code).toBe('Mã thuốc không đúng định dạng.');
    expect(validateMedicineForm({ ...valid, name: 'A' }).name).toBe('Tên thuốc không đúng định dạng.');
    expect(validateMedicineForm({ ...valid, registrationNumber: '1'.repeat(21) }).registrationNumber)
      .toBe('Vượt quá độ dài tối đa 20 ký tự.');
  });

  it('skips the code when editing, since it cannot change', () => {
    expect(validateMedicineForm({ ...valid, code: '' }, { editing: true })).toEqual({});
  });

  it('builds trimmed requests with an upper-case code and the loaded version', () => {
    expect(formToCreateRequest(valid)).toEqual({
      code: 'T-0001', registrationNumber: '840100205826', insuranceCode: null, name: 'EU-Para 500mg',
      activeIngredient: 'Paracetamol', strength: '500mg', route: 'Uống', unit: 'Viên', controlGroup: 'THUONG',
    });
    expect(formToUpdateRequest({ ...valid, insuranceCode: ' 40.17 ' }, 3)).toEqual(expect.objectContaining({
      insuranceCode: '40.17', version: 3,
    }));
    expect(formToUpdateRequest(valid, 3)).not.toHaveProperty('code');
  });
});
