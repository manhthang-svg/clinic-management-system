import { describe, expect, it } from 'vitest';

import ApiError from '../../apis/apiError';
import {
    FIELD_MSG,
    INITIAL_PATIENT_FORM,
    MSG,
    buildPatientPayload,
    createErrorMessage,
    isDirty,
    serverFieldErrors,
    validatePatientForm,
} from './patientForm';

const TODAY = '2026-11-03';
const adult = (overrides) => ({
    ...INITIAL_PATIENT_FORM,
    hoten: 'Nguyễn Văn An',
    ngaysinh: '1990-01-01',
    diachi: 'Hà Nội',
    sodienthoai: '0901234567',
    ...overrides,
});

describe('validatePatientForm (UC-37 steps 4–5)', () => {
    it('accepts a valid adult', () => {
        expect(validatePatientForm(adult(), TODAY)).toEqual({});
    });

    it('requires the phone from 18 years old', () => {
        expect(validatePatientForm(adult({ ngaysinh: '2008-11-03', sodienthoai: '' }), TODAY))
            .toEqual({ sodienthoai: FIELD_MSG.REQUIRED });
    });

    it('requires guardian name and relationship under 18 but not the phone', () => {
        const errors = validatePatientForm(adult({ ngaysinh: '2008-11-04', sodienthoai: '' }), TODAY);
        expect(errors).toEqual({ hotennguoithan: FIELD_MSG.REQUIRED, nguoithanQuanhe: FIELD_MSG.REQUIRED });
    });

    it('checks formats and lengths', () => {
        const errors = validatePatientForm(adult({
            hoten: 'A'.repeat(101), cccdSo: '123', hochieuSo: 'C-1', sodienthoai: '090123456', ngaysinh: '1900-01-01',
        }), TODAY);
        expect(errors).toEqual({
            hoten: FIELD_MSG.TOO_LONG,
            cccdSo: FIELD_MSG.INVALID_FORMAT,
            hochieuSo: FIELD_MSG.INVALID_FORMAT,
            sodienthoai: FIELD_MSG.INVALID_FORMAT,
            ngaysinh: FIELD_MSG.INVALID_FORMAT,
        });
    });

    it('rejects a birth date in the future', () => {
        expect(validatePatientForm(adult({ ngaysinh: '2026-11-04' }), TODAY).ngaysinh).toBe(FIELD_MSG.INVALID_FORMAT);
    });
});

describe('patientForm helpers', () => {
    it('builds a trimmed payload with null optional fields', () => {
        const payload = buildPatientPayload(adult({ hoten: ' An ', cccdSo: '' }));
        expect(payload.hoten).toBe('An');
        expect(payload.cccdSo).toBeNull();
        expect(payload.gioitinh).toBe(1);
    });

    it('maps backend validation codes to field messages', () => {
        expect(serverFieldErrors({ sodienthoai: 'REQUIRED', cccdSo: 'INVALID_FORMAT', other: 'X' }))
            .toEqual({ sodienthoai: FIELD_MSG.REQUIRED, cccdSo: FIELD_MSG.INVALID_FORMAT });
    });

    it('knows when something was entered', () => {
        expect(isDirty(INITIAL_PATIENT_FORM, INITIAL_PATIENT_FORM)).toBe(false);
        expect(isDirty({ ...INITIAL_PATIENT_FORM, hoten: 'A' }, INITIAL_PATIENT_FORM)).toBe(true);
    });

    it('never shows the raw backend message', () => {
        expect(createErrorMessage(new ApiError('raw', { status: 500 }))).toBe(MSG.SYSTEM_ERROR);
        expect(createErrorMessage(new ApiError('raw', { status: 403 }))).toBe(MSG.FORBIDDEN);
    });
});
