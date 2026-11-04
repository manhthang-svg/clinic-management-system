import { describe, expect, it } from 'vitest';

import { formatReferenceRangeLine } from './serviceFormat';

const range = (overrides) => ({ sex: null, ageMinDays: null, ageMaxDays: null, displayText: '4 – 10', ...overrides });

describe('formatReferenceRangeLine', () => {
  it('shows only the value for a range shared by every sex and age', () => {
    expect(formatReferenceRangeLine(range())).toBe('4 – 10');
  });

  it('prefixes sex and age, reading day counts as years', () => {
    expect(formatReferenceRangeLine(range({ sex: 1, ageMinDays: 6570, displayText: '< 41' }))).toBe('Nam, từ 18 tuổi: < 41');
  });

  it('reads an upper bound one day before a whole age as "dưới"', () => {
    expect(formatReferenceRangeLine(range({ ageMinDays: 183, ageMaxDays: 6569, displayText: '25 – 35' })))
      .toBe('6 tháng – dưới 18 tuổi: 25 – 35');
    expect(formatReferenceRangeLine(range({ ageMinDays: 0, ageMaxDays: 6569 }))).toBe('dưới 18 tuổi: 4 – 10');
  });
});
