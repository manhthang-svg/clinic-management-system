import { describe, expect, it } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { insuranceErrorKind, loadErrorMessage, MSG99, NETWORK_MESSAGE } from './visitErrors';

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
