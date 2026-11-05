import { describe, expect, it } from 'vitest';
import {
    MSG, ORGAN_FIELDS, examinationErrorMessage, fieldErrorsFromApi, notesKey, validateExamination, validateHistory,
} from './clinicalExaminationRules';

const blank = {
    reason: 'Đau đầu', progression: '', generalExam: '', preliminaryDiagnosis: '', icd10: '', treatment: '',
    pulse: '72', temperature: '', systolic: '', diastolic: '', respiratoryRate: '', weight: '', height: '', spo2: '',
    ...Object.fromEntries(ORGAN_FIELDS.flatMap(([key]) => [[key, ''], [notesKey(key), '']])),
};

describe('validateExamination', () => {
    it('accepts a draft with one vital sign and no diagnosis (AF-01)', () => {
        expect(validateExamination(blank, 'DRAFT')).toEqual({});
    });

    it('requires at least one vital sign (MSG96)', () => {
        expect(validateExamination({ ...blank, pulse: '' }, 'DRAFT')).toEqual({ vitals: MSG.VITALS_REQUIRED });
    });

    it('rejects out-of-range values and diastolic not below systolic (MSG97)', () => {
        expect(validateExamination({ ...blank, pulse: '300' }, 'DRAFT').pulse).toContain('Mạch phải trong khoảng');
        expect(validateExamination({ ...blank, systolic: '120', diastolic: '120' }, 'DRAFT').diastolic)
            .toBe(MSG.DIASTOLIC_NOT_BELOW_SYSTOLIC);
    });

    it('requires the reason for visit (MSG02)', () => {
        expect(validateExamination({ ...blank, reason: '  ' }, 'DRAFT').reason).toBe(MSG.required('Lý do khám'));
    });

    it('requires a finding when organ notes are entered', () => {
        expect(validateExamination({ ...blank, [notesKey('respiratory')]: 'Theo dõi' }, 'DRAFT').respiratory)
            .toBe(MSG.required('Kết quả khám hô hấp'));
    });

    it('requires a preliminary diagnosis before continuing (MSG98)', () => {
        expect(validateExamination(blank, 'CONTINUE')).toEqual({ preliminaryDiagnosis: MSG.DIAGNOSIS_REQUIRED });
        expect(validateExamination({ ...blank, preliminaryDiagnosis: 'Viêm họng' }, 'CONTINUE')).toEqual({});
    });
});

describe('validateHistory', () => {
    it('requires 2–2000 characters of content', () => {
        expect(validateHistory({ content: '', notes: '' }).content).toBe(MSG.required('Nội dung'));
        expect(validateHistory({ content: 'Penicillin', notes: '' })).toEqual({});
    });
});

describe('examinationErrorMessage', () => {
    it('never shows the raw backend message', () => {
        const error = { status: 500, message: 'Internal failure', errorResponse: { code: 'UNKNOWN', message: 'raw' } };
        expect(examinationErrorMessage(error)).toBe(MSG.SYSTEM_ERROR);
        expect(examinationErrorMessage(new Error('Unable to reach the API.'))).toBe(MSG.SYSTEM_ERROR);
    });

    it('maps permission, room/shift, session and closed-record failures', () => {
        expect(examinationErrorMessage({ status: 403, errorResponse: { code: 'FORBIDDEN' } })).toBe(MSG.FORBIDDEN);
        expect(examinationErrorMessage({ status: 403, errorResponse: { code: 'DOCTOR_OFF_DUTY' } }))
            .toBe(MSG.OUTSIDE_ROOM_OR_SHIFT);
        expect(examinationErrorMessage({ status: 401 })).toBe(MSG.SESSION_EXPIRED);
        expect(examinationErrorMessage({ status: 409, errorResponse: { code: 'MEDICAL_RECORD_CLOSED' } }))
            .toBe(MSG.RECORD_CLOSED);
    });
});

describe('fieldErrorsFromApi', () => {
    it('maps backend field paths to form fields, including organ findings by sent order', () => {
        const error = {
            errorResponse: {
                code: 'REQUEST_VALIDATION_FAILED',
                details: { reasonForVisit: 'size', 'organFindings[1].notes': 'size', 'vitalSigns.pulse': 'x' },
            },
        };
        expect(fieldErrorsFromApi(error, ['circulatory', 'ent'])).toEqual({
            reason: MSG.INVALID_DATA, [notesKey('ent')]: MSG.INVALID_DATA, vitals: MSG.CHECK_FIELDS,
        });
    });

    it('marks the diagnosis and ICD-10 fields for their business errors', () => {
        expect(fieldErrorsFromApi({ errorResponse: { code: 'ICD10_INVALID' } })).toEqual({ icd10: MSG.ICD10_INVALID });
        expect(fieldErrorsFromApi({ errorResponse: { code: 'PRELIMINARY_DIAGNOSIS_REQUIRED' } }))
            .toEqual({ preliminaryDiagnosis: MSG.DIAGNOSIS_REQUIRED });
    });
});
