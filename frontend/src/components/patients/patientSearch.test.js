import { describe, expect, it } from 'vitest';

import ApiError from '../../apis/apiError';
import { MSG, createPrefill, searchErrorMessage, validateKeyword } from './patientSearch';

describe('validateKeyword (UC-35 step 4)', () => {
    it.each([
        ['CITIZEN_ID', '012345678901'],
        ['PASSPORT', 'C1234567'],
        ['PATIENT_CODE', 'BN1234567'],
        ['PHONE', '0901234567'],
        ['NAME', 'An'],
        ['INSURANCE_CARD', 'DN4010123456789'],
    ])('accepts a valid %s', (type, keyword) => {
        expect(validateKeyword(type, ` ${keyword} `)).toBe('');
    });

    it.each([
        ['CITIZEN_ID', '12345'],
        ['PASSPORT', 'C-123'],
        ['PHONE', '090123456'],
        ['NAME', 'A'],
        ['NAME', 'A'.repeat(101)],
        ['INSURANCE_CARD', 'DN40101'],
    ])('rejects an invalid %s', (type, keyword) => {
        expect(validateKeyword(type, keyword)).toBe(MSG.INVALID_FORMAT);
    });

    it('requires a keyword', () => {
        expect(validateKeyword('NAME', '   ')).toBe(MSG.REQUIRED);
    });
});

describe('searchErrorMessage', () => {
    it('maps backend errors to Vietnamese messages', () => {
        const invalid = new ApiError('raw', {
            status: 400,
            errorResponse: { code: 'REQUEST_VALIDATION_FAILED', details: { keyword: 'INVALID_FORMAT' } },
        });
        expect(searchErrorMessage(invalid)).toBe(MSG.INVALID_FORMAT);
        expect(searchErrorMessage(new ApiError('raw', { status: 403 }))).toBe(MSG.FORBIDDEN);
        expect(searchErrorMessage(new ApiError('raw', { status: 500 }))).toBe(MSG.SYSTEM_ERROR);
    });
});

describe('createPrefill (UC-35 EF-02)', () => {
    it('puts the keyword into the matching form field', () => {
        expect(createPrefill('CITIZEN_ID', ' 012345678901 ')).toEqual({ cccdSo: '012345678901' });
        expect(createPrefill('NAME', 'Nguyễn An')).toEqual({ hoten: 'Nguyễn An' });
        expect(createPrefill('INSURANCE_CARD', 'DN4010123456789')).toEqual({});
    });
});
