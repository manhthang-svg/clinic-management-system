import { describe, expect, it } from 'vitest';

import { isCurrentRange, revisionFormToRequest, validateRevisionForm } from './labRangeRevision';

const femaleGlucose = {
  id: 'r1',
  referenceType: 'RANGE',
  lowerBound: 5,
  upperBound: 6,
  lowerInclusive: true,
  upperInclusive: true,
  normalValues: [],
  referenceText: null,
  sex: 2,
  ageMinDays: 6570,
  ageMaxDays: null,
  effectiveFrom: '2026-01-01',
  effectiveTo: null,
  source: 'Quy trình xét nghiệm 2026',
  active: true,
  version: 4,
};

describe('isCurrentRange', () => {
  it('covers ranges in force today and excludes ended or switched-off ones', () => {
    expect(isCurrentRange(femaleGlucose, '2026-11-04')).toBe(true);
    expect(isCurrentRange({ ...femaleGlucose, effectiveTo: '2026-11-03' }, '2026-11-04')).toBe(false);
    expect(isCurrentRange({ ...femaleGlucose, effectiveFrom: '2026-11-05' }, '2026-11-04')).toBe(false);
    expect(isCurrentRange({ ...femaleGlucose, active: false }, '2026-11-04')).toBe(false);
  });
});

describe('revisionFormToRequest', () => {
  it('keeps who the range applies to and sends the new values from today', () => {
    const request = revisionFormToRequest(
      femaleGlucose,
      { lowerBound: '6', upperBound: '7,5', normalValues: '', referenceText: '', source: ' Quy trình 2027 ' },
      '2026-11-04',
    );

    expect(request).toMatchObject({
      referenceType: 'RANGE',
      lowerBound: 6,
      upperBound: 7.5,
      sex: 2,
      ageMinDays: 6570,
      ageMaxDays: null,
      effectiveFrom: '2026-11-04',
      effectiveTo: null,
      source: 'Quy trình 2027',
      active: true,
      version: 4,
    });
  });
});

describe('validateRevisionForm', () => {
  it('requires a lower bound below the upper bound for a numeric range', () => {
    const errors = validateRevisionForm(
      { lowerBound: '7', upperBound: '6', normalValues: '', referenceText: '', source: 'x' },
      'RANGE',
    );

    expect(errors.upperBound).toBe('Giá trị lớn nhất phải lớn hơn giá trị nhỏ nhất.');
  });

  it('requires a source and at least one normal value for a qualitative range', () => {
    const errors = validateRevisionForm(
      { lowerBound: '', upperBound: '', normalValues: ' , ', referenceText: '', source: '' },
      'QUALITATIVE',
    );

    expect(errors).toEqual({
      normalValues: 'Giá trị bình thường là bắt buộc.',
      source: 'Nguồn tham chiếu là bắt buộc.',
    });
  });
});
