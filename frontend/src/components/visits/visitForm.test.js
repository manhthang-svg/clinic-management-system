import { describe, expect, it } from 'vitest';
import {
  EMPTY_VISIT_FORM,
  formToVisitRequest,
  isCardNumberFormatValid,
  validateVisitForm,
} from './visitForm';

describe('UC-40 visit form', () => {
  it('accepts the two BR-50 insurance-code formats', () => {
    expect(isCardNumberFormatValid('0000000104')).toBe(true);
    expect(isCardNumberFormatValid('HS4560000000104')).toBe(true);
    expect(isCardNumberFormatValid('CC10110145363')).toBe(false);
  });

  it('requires the fields needed to register a visit', () => {
    expect(validateVisitForm(EMPTY_VISIT_FORM, {})).toEqual({
      visitType: 'Vui lòng chọn loại lượt khám.',
      reason: 'Vui lòng nhập lý do khám.',
      roomId: 'Vui lòng chọn phòng khám.',
    });
  });

  it('requires the referral diagnosis when referred by a health facility (BR-129)', () => {
    const form = {
      ...EMPTY_VISIT_FORM,
      visitType: 'DV',
      reason: 'Ho kéo dài',
      roomId: '0192a000-0004-7000-8000-000000000001',
      referralSource: 'CO_SO_Y_TE',
    };

    expect(validateVisitForm(form, {})).toEqual({
      referralDiagnosis: 'Vui lòng nhập chẩn đoán của nơi giới thiệu.',
    });
    expect(validateVisitForm({ ...form, referralDiagnosis: 'V' }, {}).referralDiagnosis)
      .toBe('Nhập tối thiểu 2 ký tự.');
    expect(validateVisitForm({ ...form, referralDiagnosis: ' Viêm phổi ' }, {})).toEqual({});
    expect(formToVisitRequest({ ...form, referralDiagnosis: ' Viêm phổi ' }, 'p', {}))
      .toMatchObject({ noigioithieuLoai: 'CO_SO_Y_TE', chandoancuanoigioithieu: 'Viêm phổi' });
  });

  it('sends no referral diagnosis for a self-referred visit', () => {
    const form = { ...EMPTY_VISIT_FORM, referralSource: 'TU_DEN', referralDiagnosis: 'Viêm phổi' };
    expect(formToVisitRequest(form, 'p', {}).chandoancuanoigioithieu).toBeNull();
  });

  it('keeps the follow-up version and verified insurance state in the command', () => {
    const form = {
      ...EMPTY_VISIT_FORM,
      visitType: 'BHYT',
      cardNumber: 'HT3790000000003',
      reason: 'Tái khám tăng huyết áp',
      roomId: '0192a000-0004-7000-8000-000000000001',
      followUpAppointmentId: '0192a000-0005-7000-8000-000000000001',
      followUpAppointmentVersion: 3,
    };

    expect(formToVisitRequest(
      form,
      '0192a000-0001-7000-8000-000000000001',
      { cardNumber: form.cardNumber, status: 'VALID', card: {} },
    )).toMatchObject({
      doituongbnLoai: 'BHYT',
      lydotiepnhan: 'FOLLOW_UP',
      followUpAppointmentId: form.followUpAppointmentId,
      followUpAppointmentVersion: 3,
    });
  });

  it('requires a successful verification when the insurance portal is unavailable', () => {
    const form = {
      ...EMPTY_VISIT_FORM,
      visitType: 'BHYT',
      cardNumber: 'HT3790000000003',
      reason: 'Đau đầu',
      roomId: 'room-1',
    };

    expect(validateVisitForm(form, { cardNumber: form.cardNumber, status: 'UNAVAILABLE' }))
      .toHaveProperty('cardNumber');
    expect(validateVisitForm(form, { cardNumber: form.cardNumber, status: 'VALID' }))
      .not.toHaveProperty('cardNumber');
  });
});
