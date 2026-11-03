import { describe, expect, it } from 'vitest';

import ApiError from '../../apis/apiError';
import { MSG, ageOn, canRegisterVisit, fullAddress, overviewErrorMessage } from './patientOverview';

describe('patientOverview (UC-36)', () => {
    it('computes the age in whole years', () => {
        expect(ageOn('1990-11-03', '2026-11-03')).toBe(36);
        expect(ageOn('1990-11-04', '2026-11-03')).toBe(35);
        expect(ageOn(null, '2026-11-03')).toBeNull();
    });

    it('allows registering only without an active visit (BR-17)', () => {
        expect(canRegisterVisit(null)).toBe(true);
        expect(canRegisterVisit({ status: 'WAITING' })).toBe(false);
        expect(canRegisterVisit({ status: 'COMPLETED' })).toBe(true);
    });

    it('joins the address parts that are present', () => {
        expect(fullAddress({ diachi: '12 Lê Lợi', xaphuong: null, tinhthanh: 'Hà Nội' })).toBe('12 Lê Lợi, Hà Nội');
        expect(fullAddress({})).toBe('—');
    });

    it('maps API failures to Vietnamese messages', () => {
        expect(overviewErrorMessage(new ApiError('raw', { status: 404 }))).toBe(MSG.NOT_FOUND);
        expect(overviewErrorMessage(new ApiError('raw', { status: 403 }))).toBe(MSG.FORBIDDEN);
        expect(overviewErrorMessage(new ApiError('raw', { status: 500 }))).toBe(MSG.SYSTEM_ERROR);
    });
});
