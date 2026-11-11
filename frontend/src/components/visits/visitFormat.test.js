import { describe, expect, it } from 'vitest';

import { ageText } from './visitFormat';

const at = new Date(2026, 10, 12); // 12/11/2026

describe('ageText', () => {
  it('counts months only under one year', () => {
    expect(ageText('2026-02-10', at)).toBe('9 tháng');
    expect(ageText('2026-10-09', at)).toBe('1 tháng');
    expect(ageText('2026-11-01', at)).toBe('0 tháng');
  });

  it('counts years and months from one year', () => {
    expect(ageText('2024-03-01', at)).toBe('2 tuổi 8 tháng');
    expect(ageText('2010-04-21', at)).toBe('16 tuổi 6 tháng');
  });

  it('leaves out zero months', () => {
    expect(ageText('2024-11-12', at)).toBe('2 tuổi');
  });

  it('does not count a month before its day comes', () => {
    expect(ageText('2024-03-20', at)).toBe('2 tuổi 7 tháng');
  });

  it('shows a dash without a valid date of birth', () => {
    expect(ageText(null, at)).toBe('—');
    expect(ageText('2027-01-01', at)).toBe('—');
  });
});
