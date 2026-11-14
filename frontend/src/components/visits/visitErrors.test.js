import { describe, expect, it } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { insuranceErrorKind, loadErrorMessage, msg21, MSG99, NETWORK_MESSAGE, ticketErrorMessage } from './visitErrors';

describe('insurance verification errors', () => {
  it('does not misclassify an API validation error as a portal timeout', () => {
    const error = new ApiError('Invalid request', {
      status: 400,
      errorResponse: { code: 'REQUEST_VALIDATION_FAILED', message: 'Invalid request' },
    });

    expect(insuranceErrorKind(error)).toBe('system');
    expect(loadErrorMessage(error)).toBe(MSG99);
  });

  it('keeps network failures distinct from portal timeouts', () => {
    const error = new ApiError('Unable to reach the API.');

    expect(insuranceErrorKind(error)).toBe('system');
    expect(loadErrorMessage(error)).toBe(NETWORK_MESSAGE);
  });

  it('recognizes stable invalid-card and mismatch codes', () => {
    const invalid = new ApiError('Invalid card', {
      status: 422,
      errorResponse: { code: 'INSURANCE_CARD_INVALID', message: 'Invalid card' },
    });
    const mismatch = new ApiError('Mismatch', {
      status: 422,
      errorResponse: { code: 'INSURANCE_PATIENT_MISMATCH', message: 'Mismatch' },
    });

    expect(insuranceErrorKind(invalid)).toBe('invalid');
    expect(insuranceErrorKind(mismatch)).toBe('mismatch');
  });
});

describe('ticket load errors (UC-41)', () => {
  it('explains a missing clinic facility', () => {
    const error = new ApiError('Not configured', {
      status: 409,
      errorResponse: { code: 'CLINIC_FACILITY_NOT_CONFIGURED', message: 'Not configured' },
    });

    expect(ticketErrorMessage(error)).toBe('Chưa cấu hình thông tin cơ sở khám bệnh. Vui lòng liên hệ quản trị viên.');
  });

  it('names the code or status of an unknown failure', () => {
    const coded = new ApiError('Boom', { status: 500, errorResponse: { code: 'INTERNAL_ERROR', message: 'Boom' } });
    const bare = new ApiError('Bad gateway', { status: 502 });

    expect(ticketErrorMessage(coded)).toBe(`${MSG99} (Mã lỗi: INTERNAL_ERROR)`);
    expect(ticketErrorMessage(bare)).toBe(`${MSG99} (Mã lỗi: HTTP 502)`);
  });
});

describe('MSG21 registration confirmed', () => {
  it('gives the queue number of a visit that has one', () => {
    expect(msg21({ sovaovien: 'DK0000000050', queueNumber: 7 }))
      .toBe('Đã đăng ký lượt khám DK0000000050. Số thứ tự: 7.');
  });

  it('says a self-paid visit gets its number after paying the fee', () => {
    expect(msg21({ sovaovien: 'DK0000000051', queueNumber: null }))
      .toBe('Đã đăng ký lượt khám DK0000000051. Số thứ tự được cấp sau khi thanh toán phí khám.');
  });
});
