import { describe, expect, it } from 'vitest';
import { referenceRangeToRequest, validateReferenceRange } from './labReferenceRangeForm';

const validRange = {
  referenceType: 'RANGE', lowerBound: '4', upperBound: '10',
  lowerInclusive: true, upperInclusive: true, normalValues: '', referenceText: '',
  sex: '', ageMinDays: '', ageMaxDays: '', effectiveFrom: '2026-01-01',
  effectiveTo: '', source: 'Bộ Y tế', active: true, version: null,
};

describe('labReferenceRangeForm', () => {
  it('validates numeric boundaries and dates', () => {
    expect(validateReferenceRange(validRange)).toEqual({});
    expect(validateReferenceRange({ ...validRange, upperBound: '3' })).toMatchObject({
      upperBound: expect.any(String),
    });
    expect(validateReferenceRange({ ...validRange, effectiveTo: '2025-12-31' })).toMatchObject({
      effectiveTo: expect.any(String),
    });
  });

  it('builds a qualitative request and removes fields for other shapes', () => {
    expect(referenceRangeToRequest({
      ...validRange,
      referenceType: 'QUALITATIVE',
      normalValues: 'Âm tính, Không phát hiện',
      sex: '2',
    })).toMatchObject({
      referenceType: 'QUALITATIVE',
      lowerBound: null,
      upperBound: null,
      normalValues: ['Âm tính', 'Không phát hiện'],
      sex: 2,
    });
  });
});
