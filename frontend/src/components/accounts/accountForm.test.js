import { describe, expect, it } from 'vitest';
import { EMPTY_ACCOUNT_FORM, formToAccountFields, latestAdultBirthDate, validateAccountForm } from './accountForm';

const doctor = { ...EMPTY_ACCOUNT_FORM, role: 'DOCTOR' };

describe('validateAccountForm messages (SRS message templates)', () => {
  it('uses MSG02 for empty required fields, including a Doctor\'s professional fields', () => {
    expect(validateAccountForm(doctor, {})).toMatchObject({
      username: 'Tên đăng nhập là bắt buộc.',
      fullName: 'Họ và tên là bắt buộc.',
      title: 'Học hàm / học vị là bắt buộc.',
      practiceLicenseNo: 'Số giấy phép hành nghề là bắt buộc.',
      phone: 'Số điện thoại là bắt buộc.',
    });
  });

  it('uses MSG05 for wrong formats and MSG08 for values that are too long', () => {
    const errors = validateAccountForm({
      ...doctor,
      username: 'bs-an',
      fullName: 'Doctor123',
      title: 'x'.repeat(51),
      phone: '0123',
      email: 'invalid-email',
      address: 'x'.repeat(256),
    }, {});

    expect(errors).toMatchObject({
      username: 'Tên đăng nhập không đúng định dạng.',
      fullName: 'Họ và tên không đúng định dạng.',
      title: 'Vượt quá độ dài tối đa 50 ký tự.',
      phone: 'Số điện thoại không đúng định dạng.',
      email: 'Email không đúng định dạng.',
      address: 'Vượt quá độ dài tối đa 255 ký tự.',
    });
  });

  it.each([
    ['A', 'Họ và tên không đúng định dạng.'],
    ['  A  ', 'Họ và tên không đúng định dạng.'],
    ['Doctor123', 'Họ và tên không đúng định dạng.'],
    ['a'.repeat(121), 'Vượt quá độ dài tối đa 120 ký tự.'],
  ])('rejects the full name %j under BR-59', (fullName, message) => {
    expect(validateAccountForm({ ...doctor, fullName }, {}).fullName).toBe(message);
  });

  it.each(['An', "Trần-Lê O'Neil", '  Nguyễn   Văn  An  ', 'a'.repeat(120)])(
    'accepts the full name %j under BR-59',
    (fullName) => {
      expect(validateAccountForm({ ...doctor, fullName }, {}).fullName).toBeUndefined();
    },
  );

  it('saves the full name trimmed with repeated spaces collapsed (BR-59)', () => {
    expect(formToAccountFields({ ...doctor, fullName: '  Nguyễn   Văn  An  ' }).fullName).toBe('Nguyễn Văn An');
  });

  it.each(['BS@1', 'BSCK2', 'ThS/BS'])('rejects the academic title %j under BR-62', (title) => {
    expect(validateAccountForm({ ...doctor, title }, {}).title).toBe('Học hàm / học vị không đúng định dạng.');
  });

  it.each(['ThS.BS', 'PGS.TS, BS', 'Cử nhân'])('accepts the academic title %j under BR-62', (title) => {
    expect(validateAccountForm({ ...doctor, title }, {}).title).toBeUndefined();
  });

  it('rejects staff younger than 18 and accepts the 18th birthday (BR-62)', () => {
    const today = '2026-11-05';
    expect(validateAccountForm({ ...doctor, dateOfBirth: '2008-11-06' }, { today }).dateOfBirth)
      .toBe('Ngày sinh không hợp lệ. Nhân viên phải đủ 18 tuổi.');
    expect(validateAccountForm({ ...doctor, dateOfBirth: '2008-11-05' }, { today }).dateOfBirth).toBeUndefined();
  });

  it('computes the 18-year limit like LocalDate.minusYears for 29 February', () => {
    expect(latestAdultBirthDate('2026-11-05')).toBe('2008-11-05');
    expect(latestAdultBirthDate('2024-02-29')).toBe('2006-02-28');
  });

  it.each(['A123', 'a'.repeat(51), '000123 HCM@CCHN'])(
    'rejects the practice licence %j under BR-64',
    (practiceLicenseNo) => {
      expect(validateAccountForm({ ...doctor, practiceLicenseNo }, {}).practiceLicenseNo)
        .toBe('Số giấy phép hành nghề không đúng định dạng.');
    },
  );

  it.each(['000123/HCM-CCHN', 'CCHN1', 'a'.repeat(50)])('accepts the practice licence %j under BR-64', (practiceLicenseNo) => {
    expect(validateAccountForm({ ...doctor, practiceLicenseNo }, {}).practiceLicenseNo).toBeUndefined();
  });

  it.each(['BS-0001', 'mã 01', 'a'.repeat(51)])('rejects the prescriber link code %j for a Doctor', (maLienthongBacsi) => {
    expect(validateAccountForm({ ...doctor, maLienthongBacsi }, {}).maLienthongBacsi).toBeTruthy();
  });

  it.each(['', 'BS0001', 'a'.repeat(50)])('accepts the prescriber link code %j for a Doctor', (maLienthongBacsi) => {
    expect(validateAccountForm({ ...doctor, maLienthongBacsi }, {}).maLienthongBacsi).toBeUndefined();
  });

  it.each(['07909000123', '0790900012345', '07909000123a'])('rejects the personal identification number %j for a Doctor (BR-59-16)', (citizenId) => {
    expect(validateAccountForm({ ...doctor, citizenId }, {}).citizenId).toBe('Số định danh cá nhân không đúng định dạng.');
  });

  it.each(['', '079090001234', ' 079090001234 '])('accepts the personal identification number %j for a Doctor', (citizenId) => {
    expect(validateAccountForm({ ...doctor, citizenId }, {}).citizenId).toBeUndefined();
  });

  it('uses MSG07 for a username or email already in use', () => {
    const accounts = [{ id: 'other', username: 'lt.hong', email: 'lt.hong@clinic.test' }];
    const errors = validateAccountForm(
      { ...EMPTY_ACCOUNT_FORM, role: 'RECEPTIONIST', username: 'LT.HONG', email: 'LT.HONG@clinic.test' },
      { accounts },
    );

    expect(errors.username).toBe('Tên đăng nhập đã tồn tại trong hệ thống.');
    expect(errors.email).toBe('Email đã tồn tại trong hệ thống.');
  });
});
