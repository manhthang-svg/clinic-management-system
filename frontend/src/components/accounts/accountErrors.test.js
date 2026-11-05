import { describe, expect, it } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { accountErrorMessage, accountFieldErrors, withoutField } from './accountErrors';
import { EMPTY_ACCOUNT_FORM, validateAccountForm } from './accountForm';

function apiError(code, details, status = 409) {
  return new ApiError('raw backend message', { status, errorResponse: { code, message: 'raw backend message', details } });
}

describe('accountErrors', () => {
  it('maps duplicate fields to MSG07 under the matching form field', () => {
    const error = apiError('CONCURRENT_MODIFICATION', { phoneNumber: 'ALREADY_EXISTS', email: 'ALREADY_EXISTS' });

    expect(accountFieldErrors(error)).toEqual({
      phone: 'Số điện thoại đã tồn tại trong hệ thống.',
      email: 'Email đã tồn tại trong hệ thống.',
    });
    expect(accountErrorMessage(error)).not.toContain('raw backend message');
  });

  it('uses Vietnamese messages for error codes and never the raw backend text', () => {
    expect(accountErrorMessage(apiError('UNAUTHORIZED', undefined, 401)))
      .toBe('Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.');
    expect(accountErrorMessage(apiError('ADMIN_ACCOUNT_PROTECTED', undefined, 422)))
      .toBe('Không thể thực hiện thao tác này đối với tài khoản Admin.');
    expect(accountErrorMessage(apiError('SELF_ADMINISTRATION_FORBIDDEN', undefined, 422)))
      .toBe('Bạn không thể thực hiện thao tác này trên tài khoản của chính mình.');
    expect(accountErrorMessage(apiError('CONCURRENT_MODIFICATION', undefined, 409)))
      .toBe('Tài khoản vừa được người khác cập nhật. Vui lòng tải lại và thử lại.');
    expect(accountErrorMessage(apiError('SOMETHING_NEW', undefined, 500)))
      .toBe('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
  });

  it('fills MSG45 and MSG47 from the action context and maps state conflicts to MSG75', () => {
    expect(accountErrorMessage(apiError('SELF_ADMINISTRATION_FORBIDDEN', undefined, 422), { action: 'khóa' }))
      .toBe('Bạn không thể khóa tài khoản của chính mình.');
    expect(accountErrorMessage(apiError('WORK_IN_PROGRESS', { visits: '1', orders: '0' }), { fullName: 'Nguyễn An' }))
      .toBe('Nguyễn An đang có lượt khám hoặc dịch vụ đang thực hiện. Vui lòng hoàn tất các công việc này trước.');
    expect(accountErrorMessage(apiError('ACCOUNT_STATE_CONFLICT')))
      .toBe('Tài khoản vừa được người khác cập nhật. Vui lòng tải lại và thử lại.');
  });

  it('maps a too-short action reason to the BR-97 length message', () => {
    expect(accountErrorMessage(apiError('REQUEST_VALIDATION_FAILED', { reason: 'TOO_SHORT' }, 400)))
      .toBe('Lý do phải có từ 5 đến 500 ký tự.');
  });

  it('removes only the edited field from server errors', () => {
    expect(withoutField({ phone: 'a', email: 'b' }, 'phone')).toEqual({ email: 'b' });
  });
});

describe('validateAccountForm', () => {
  const valid = {
    ...EMPTY_ACCOUNT_FORM,
    username: 'bs.an',
    fullName: 'Nguyễn An',
    title: 'BS',
    practiceLicenseNo: '000123/HCM-CCHN',
    phone: '0901234567',
  };

  it('accepts an empty email because email is optional', () => {
    expect(validateAccountForm(valid, {})).toEqual({});
  });

  it('applies the backend username rule (4–30 characters, a–z 0–9 . _)', () => {
    expect(validateAccountForm({ ...valid, username: 'abc' }, {}).username).toBeTruthy();
    expect(validateAccountForm({ ...valid, username: 'bs-an' }, {}).username).toBeTruthy();
    expect(validateAccountForm({ ...valid, username: 'a'.repeat(31) }, {}).username).toBeTruthy();
  });

  it('rejects a phone number without a Vietnamese mobile prefix', () => {
    expect(validateAccountForm({ ...valid, phone: '0101234567' }, {}).phone).toBeTruthy();
  });

  it('requires a practice license for doctors and technicians only', () => {
    expect(validateAccountForm({ ...valid, practiceLicenseNo: '' }, {}).practiceLicenseNo).toBeTruthy();
    expect(validateAccountForm({ ...valid, role: 'TECHNICIAN', practiceLicenseNo: '' }, {}).practiceLicenseNo).toBeTruthy();
    expect(validateAccountForm({ ...valid, role: 'CASHIER', title: '', practiceLicenseNo: '' }, {})).toEqual({});
  });
});
