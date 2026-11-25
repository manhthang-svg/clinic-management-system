import { describe, expect, it } from 'vitest';

import ApiError from '../../apis/apiError';
import {
    FIELD_MSG,
    INITIAL_PATIENT_FORM,
    MSG,
    buildPatientPayload,
    countryCode,
    countryOptions,
    createErrorMessage,
    formatFullName,
    ethnicityCode,
    ethnicityOptions,
    occupationCode,
    occupationOptions,
    withDerivedCodes,
    isDirty,
    serverFieldErrors,
    toFormValues,
    validatePatientForm,
} from './patientForm';

const TODAY = '2026-11-03';
const adult = (overrides) => ({
    ...INITIAL_PATIENT_FORM,
    hoten: 'Nguyễn Văn An',
    ngaysinh: '1990-01-01',
    diachi: 'Hà Nội',
    xaphuong: 'Phường Ba Đình',
    tinhthanh: 'Thành phố Hà Nội',
    nghenghiep: 'Công nhân',
    nghenghiepMa: '04',
    sodienthoai: '0901234567',
    ...overrides,
});

describe('validatePatientForm (UC-37 steps 4–5)', () => {
    it('flags invalid dates, gender and overlong administrative fields', () => {
        expect(validatePatientForm(adult({ ngaysinh: '2026-02-30', gioitinh: '4', diachi: 'a'.repeat(1025), nguoithanQuanhe: 'a'.repeat(51) }), TODAY)).toEqual({
            ngaysinh: FIELD_MSG.INVALID_FORMAT,
            gioitinh: FIELD_MSG.INVALID_FORMAT,
            diachi: FIELD_MSG.TOO_LONG,
            nguoithanQuanhe: FIELD_MSG.TOO_LONG,
        });
        expect(validatePatientForm(adult({ ngaysinh: '1906-11-02' }), TODAY).ngaysinh).toBe(FIELD_MSG.INVALID_FORMAT);
        expect(validatePatientForm(adult({ ngaysinh: '1906-11-03' }), TODAY).ngaysinh).toBeUndefined();
    });

    it('accepts a valid adult', () => {
        expect(validatePatientForm(adult(), TODAY)).toEqual({});
    });

    it('requires the province, the ward and the occupation', () => {
        expect(validatePatientForm(adult({ tinhthanh: ' ', xaphuong: '', nghenghiep: '', nghenghiepMa: '' }), TODAY))
            .toEqual({ tinhthanh: FIELD_MSG.REQUIRED, xaphuong: FIELD_MSG.REQUIRED, nghenghiep: FIELD_MSG.REQUIRED });
    });

    it('does not require a Vietnamese province or ward for a foreigner', () => {
        const foreigner = adult({
            nghenghiep: 'Ngoại kiều', nghenghiepMa: '12', ngoaikieu: 'Nhật', ngoaikieuMa: '16', tinhthanh: '', xaphuong: '',
        });
        expect(validatePatientForm(foreigner, TODAY)).toEqual({});
        expect(validatePatientForm({ ...foreigner, diachi: '' }, TODAY)).toEqual({ diachi: FIELD_MSG.REQUIRED });
    });

    it('requires the phone from 18 years old', () => {
        expect(validatePatientForm(adult({ ngaysinh: '2008-11-03', sodienthoai: '' }), TODAY))
            .toEqual({ sodienthoai: FIELD_MSG.REQUIRED });
    });

    it('requires guardian name, relationship and relative phone under 18 but not the patient phone', () => {
        const errors = validatePatientForm(adult({ ngaysinh: '2008-11-04', sodienthoai: '' }), TODAY);
        expect(errors).toEqual({
            hotennguoithan: FIELD_MSG.REQUIRED,
            nguoithanQuanhe: FIELD_MSG.REQUIRED,
            sodienthoainguoithan: FIELD_MSG.REQUIRED,
        });
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

    it('rejects names with numbers or special characters', () => {
        expect(validatePatientForm(adult({ hoten: 'Nguyễn Văn A 123' }), TODAY).hoten)
            .toBe(FIELD_MSG.INVALID_FORMAT);
        expect(validatePatientForm(adult({ hoten: 'Trần Thị B @#!' }), TODAY).hoten)
            .toBe(FIELD_MSG.INVALID_FORMAT);
        expect(validatePatientForm(adult({ hoten: '---' }), TODAY).hoten)
            .toBe(FIELD_MSG.INVALID_FORMAT);
    });

    it('accepts valid Vietnamese names with accents, apostrophes and hyphens', () => {
        expect(validatePatientForm(adult({ hoten: 'Nguyễn Văn An' }), TODAY).hoten).toBeUndefined();
        expect(validatePatientForm(adult({ hoten: "H'Hen Niê" }), TODAY).hoten).toBeUndefined();
        expect(validatePatientForm(adult({ hoten: 'A-Trô' }), TODAY).hoten).toBeUndefined();
    });

    it('rejects guardian names with numbers or special characters', () => {
        expect(validatePatientForm(adult({ hotennguoithan: 'Người thân 123' }), TODAY).hotennguoithan)
            .toBe(FIELD_MSG.INVALID_FORMAT);
        expect(validatePatientForm(adult({ hotennguoithan: 'Nguyễn Văn B' }), TODAY).hotennguoithan)
            .toBeUndefined();
    });

    it('rejects a birth date in the future', () => {
        expect(validatePatientForm(adult({ ngaysinh: '2026-11-04' }), TODAY).ngaysinh).toBe(FIELD_MSG.INVALID_FORMAT);
    });

    it('accepts an empty or valid email and rejects a wrong or too long one', () => {
        expect(validatePatientForm(adult({ email: '' }), TODAY).email).toBeUndefined();
        expect(validatePatientForm(adult({ email: ' an@example.com ' }), TODAY).email).toBeUndefined();
        expect(validatePatientForm(adult({ email: 'an@example' }), TODAY).email).toBe(FIELD_MSG.INVALID_FORMAT);
        expect(validatePatientForm(adult({ email: 'an example@x.vn' }), TODAY).email).toBe(FIELD_MSG.INVALID_FORMAT);
        expect(validatePatientForm(adult({ email: `${'a'.repeat(92)}@mail.com` }), TODAY).email).toBe(FIELD_MSG.TOO_LONG);
    });
});

describe('patientForm helpers', () => {
    it('builds a trimmed payload with null optional fields', () => {
        const payload = buildPatientPayload(adult({ hoten: ' An ', cccdSo: '' }));
        expect(payload.hoten).toBe('An');
        expect(payload.cccdSo).toBeNull();
        expect(payload.gioitinh).toBe(1);
        expect(payload.email).toBeNull();
        expect(buildPatientPayload(adult({ email: ' an@example.com ' })).email).toBe('an@example.com');
    });

    it('maps backend validation codes to field messages', () => {
        expect(serverFieldErrors({ sodienthoai: 'REQUIRED', cccdSo: 'INVALID_FORMAT', other: 'X' }))
            .toEqual({ sodienthoai: FIELD_MSG.REQUIRED, cccdSo: FIELD_MSG.INVALID_FORMAT });
    });

    it('turns a stored profile into form values (UC-38 step 2)', () => {
        const values = toFormValues({ hoten: 'An', gioitinh: 2, cccdSo: null, extra: 'x' });
        expect(values.hoten).toBe('An');
        expect(values.gioitinh).toBe('2');
        expect(values.cccdSo).toBe('');
        expect(values).not.toHaveProperty('extra');
    });

    it('knows when something was entered', () => {
        expect(isDirty(INITIAL_PATIENT_FORM, INITIAL_PATIENT_FORM)).toBe(false);
        expect(isDirty({ ...INITIAL_PATIENT_FORM, hoten: 'A' }, INITIAL_PATIENT_FORM)).toBe(true);
    });

    it('never shows the raw backend message', () => {
        expect(createErrorMessage(new ApiError('raw', { status: 500 }))).toBe(MSG.SYSTEM_ERROR);
        expect(createErrorMessage(new ApiError('raw', { status: 403 }))).toBe(MSG.FORBIDDEN);
    });

    it('formats full names to Title Case and cleans whitespace', () => {
        expect(formatFullName('nguyễn văn an')).toBe('Nguyễn Văn An');
        expect(formatFullName('NGUYỄN VĂN AN')).toBe('Nguyễn Văn An');
        expect(formatFullName('  nguyễn   văn   an  ')).toBe('Nguyễn Văn An');
        expect(formatFullName("h'hen niê")).toBe("H'Hen Niê");
        expect(formatFullName('a-trô')).toBe('A-Trô');
        expect(formatFullName('')).toBe('');
    });
});

describe('dan toc: chi chon ten, ma tu dien', () => {
    it('maps each ethnicity name to its 2-digit code', () => {
        expect(ethnicityCode('Kinh')).toBe('01');
        expect(ethnicityCode('Khơ-me')).toBe('05');
        expect(ethnicityCode('Khmer')).toBe('05');
        expect(ethnicityCode('HMông')).toBe('08');
        expect(ethnicityCode('Không rõ')).toBe('56');
        expect(ethnicityCode('Mường')).toBe('06');
        expect(ethnicityCode('Người nước ngoài')).toBe('55');
        expect(ethnicityCode('Khác')).toBeNull();
    });

    it('keeps an old saved name that is not in the list as an option', () => {
        expect(ethnicityOptions('Kinh')).toHaveLength(56);
        expect(ethnicityOptions('Khmer')[0]).toEqual({ value: 'Khmer', label: 'Khmer' });
        expect(ethnicityOptions('Khác')[0]).toEqual({ value: 'Khác', label: 'Khác' });
    });

    it('reports a missing code on the ethnicity field', () => {
        expect(validatePatientForm(adult({ dantocMa: '' }), TODAY)).toEqual({ dantoc: FIELD_MSG.REQUIRED });
        expect(serverFieldErrors({ dantocMa: 'REQUIRED' })).toEqual({ dantoc: FIELD_MSG.REQUIRED });
    });
});

describe('nghe nghiep: chi chon ten, ma tu dien', () => {
    it('maps each occupation to its code', () => {
        expect(occupationCode('Công nhân')).toBe('04');
        expect(occupationCode('Hưu và trên 60 tuổi')).toBe('03');
        expect(occupationCode('Khác')).toBe('99');
        expect(occupationCode('Kỹ sư')).toBeNull();
    });

    it('starts with an empty choice and keeps an old free-text occupation', () => {
        expect(occupationOptions('')[0]).toEqual({ value: '', label: 'Chưa chọn' });
        expect(occupationOptions('')).toHaveLength(14);
        expect(occupationOptions('Kỹ sư')[1]).toEqual({ value: 'Kỹ sư', label: 'Kỹ sư' });
    });

    it('fills and clears the code when the occupation changes', () => {
        const form = { nghenghiep: '', nghenghiepMa: '', dantoc: 'Kinh', dantocMa: '01' };
        const chosen = withDerivedCodes(form, 'nghenghiep', 'Y tế');
        expect(chosen).toMatchObject({ nghenghiep: 'Y tế', nghenghiepMa: '09' });
        expect(withDerivedCodes(chosen, 'nghenghiep', '')).toMatchObject({ nghenghiep: '', nghenghiepMa: '' });
        expect(withDerivedCodes(form, 'dantoc', 'Tày')).toMatchObject({ dantoc: 'Tày', dantocMa: '02' });
        expect(withDerivedCodes(form, 'hoten', 'A')).toMatchObject({ hoten: 'A', nghenghiepMa: '' });
    });

    it('reports a backend error on the occupation code under the occupation field', () => {
        expect(serverFieldErrors({ nghenghiepMa: 'TOO_LONG' })).toEqual({ nghenghiep: FIELD_MSG.TOO_LONG });
    });
});

describe('ngoai kieu: chi chon nuoc khi nghe nghiep la Ngoai kieu, ma tu dien', () => {
    const foreigner = (overrides) => adult({ nghenghiep: 'Ngoại kiều', nghenghiepMa: '12', ...overrides });

    it('maps each country to its code', () => {
        expect(countryCode('Anh')).toBe('01');
        expect(countryCode('Nhật')).toBe('16');
        expect(countryCode('Trung Quốc')).toBe('21');
        expect(countryCode('Khác')).toBe('99');
        expect(countryCode('Úc')).toBeNull();
    });

    it('starts with an empty choice and keeps an old free-text country', () => {
        expect(countryOptions('')[0]).toEqual({ value: '', label: 'Chưa chọn' });
        expect(countryOptions('')).toHaveLength(23);
        expect(countryOptions('VN')[1]).toEqual({ value: 'VN', label: 'VN' });
    });

    it('fills the code from the country and clears both when the occupation is no longer foreigner', () => {
        const chosen = withDerivedCodes(withDerivedCodes(adult(), 'nghenghiep', 'Ngoại kiều'), 'ngoaikieu', 'Pháp');
        expect(chosen).toMatchObject({ nghenghiepMa: '12', ngoaikieu: 'Pháp', ngoaikieuMa: '17' });
        expect(withDerivedCodes(chosen, 'ngoaikieu', '')).toMatchObject({ ngoaikieu: '', ngoaikieuMa: '' });
        expect(withDerivedCodes(chosen, 'nghenghiep', 'Công nhân')).toMatchObject({ ngoaikieu: '', ngoaikieuMa: '' });
    });

    it('requires a listed country for a foreigner', () => {
        expect(validatePatientForm(foreigner(), TODAY)).toEqual({ ngoaikieu: FIELD_MSG.REQUIRED });
        expect(validatePatientForm(foreigner({ ngoaikieu: 'Úc' }), TODAY)).toEqual({ ngoaikieu: FIELD_MSG.INVALID_FORMAT });
        expect(validatePatientForm(foreigner({ ngoaikieu: 'Nhật', ngoaikieuMa: '16' }), TODAY)).toEqual({});
    });

    it('rejects a country for someone who is not a foreigner', () => {
        expect(validatePatientForm(adult({ ngoaikieu: 'Nhật' }), TODAY)).toEqual({ ngoaikieu: FIELD_MSG.INVALID_FORMAT });
        expect(validatePatientForm(adult(), TODAY)).toEqual({});
    });

    it('reports a backend error on the country code under the country field', () => {
        expect(serverFieldErrors({ ngoaikieuMa: 'TOO_LONG' })).toEqual({ ngoaikieu: FIELD_MSG.TOO_LONG });
    });
});
