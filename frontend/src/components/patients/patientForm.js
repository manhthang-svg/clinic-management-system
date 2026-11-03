// UC-37 Create New Patient Profile: form values, field rules and messages. Pure functions, no React.
// Client checks mirror the backend for feedback only; the backend repeats every check.

import { ageOn } from './patientOverview';

export const ADULT_AGE = 18;
export const MAX_AGE_YEARS = 120;
export const MAX_NAME_LENGTH = 100;

export const INITIAL_PATIENT_FORM = {
    hoten: '',
    ngaysinh: '',
    gioitinh: '1',
    cccdSo: '',
    hochieuSo: '',
    nghenghiep: '',
    nghenghiepMa: '',
    dantoc: 'Kinh',
    dantocMa: '01',
    ngoaikieu: '',
    ngoaikieuMa: '',
    diachi: '',
    xaphuong: '',
    xaphuongMa: '',
    tinhthanh: '',
    tinhthanhMa: '',
    noilamviec: '',
    sodienthoai: '',
    yeutorh: '',
    nhommau: '',
    hotennguoithan: '',
    nguoithanQuanhe: '',
    diachinguoithan: '',
    sodienthoainguoithan: '',
};

// Report 3 System Messages (Vietnamese).
export const FIELD_MSG = {
    REQUIRED: 'Trường này là bắt buộc.',
    INVALID_FORMAT: 'Định dạng không hợp lệ.',
    TOO_LONG: 'Vượt quá độ dài tối đa cho phép.',
};
export const MSG = {
    CREATED: 'Tạo hồ sơ bệnh nhân thành công.',
    FIX_FIELDS: 'Vui lòng kiểm tra lại các trường được đánh dấu.',
    DISCARD: 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
};

/** MSG57: possible duplicates (same full name and date of birth). */
export const msg57 = (count) =>
    `Đã tồn tại ${count} bệnh nhân có cùng họ tên và ngày sinh. Vui lòng chọn một hồ sơ hoặc tạo hồ sơ mới.`;

/** MSG19: the citizen ID or passport belongs to another profile. */
export const msg19 = (field, code, name) =>
    `Bệnh nhân có số ${field === 'hochieuSo' ? 'hộ chiếu' : 'CCCD'} này đã tồn tại (${code} – ${name}).`;

/** Whole-year age of the form's date of birth, or null without a valid date. */
export function formAge(form, today) {
    return /^\d{4}-\d{2}-\d{2}$/.test(form.ngaysinh) ? ageOn(form.ngaysinh, today) : null;
}

/** UC-37 steps 4–5: returns { field: message } for every wrong field (empty when valid). */
export function validatePatientForm(form, today) {
    const errors = {};
    const text = (field) => (form[field] ?? '').trim();
    const require = (field) => { if (!text(field)) errors[field] = FIELD_MSG.REQUIRED; };

    require('hoten');
    if (text('hoten').length > MAX_NAME_LENGTH) errors.hoten = FIELD_MSG.TOO_LONG;
    require('diachi');
    require('dantoc');
    require('dantocMa');

    const age = formAge(form, today);
    if (!form.ngaysinh) errors.ngaysinh = FIELD_MSG.REQUIRED;
    else if (age === null || form.ngaysinh > today || age > MAX_AGE_YEARS) errors.ngaysinh = FIELD_MSG.INVALID_FORMAT;

    if (text('cccdSo') && !/^\d{12}$/.test(text('cccdSo'))) errors.cccdSo = FIELD_MSG.INVALID_FORMAT;
    if (text('hochieuSo') && !/^[A-Za-z0-9]{1,15}$/.test(text('hochieuSo'))) errors.hochieuSo = FIELD_MSG.INVALID_FORMAT;
    if (text('sodienthoai') && !/^\d{10}$/.test(text('sodienthoai'))) errors.sodienthoai = FIELD_MSG.INVALID_FORMAT;
    if (text('sodienthoainguoithan') && !/^\d{10}$/.test(text('sodienthoainguoithan'))) {
        errors.sodienthoainguoithan = FIELD_MSG.INVALID_FORMAT;
    }

    if (age !== null && age >= ADULT_AGE && !text('sodienthoai')) errors.sodienthoai = FIELD_MSG.REQUIRED;
    if (age !== null && age < ADULT_AGE) {
        require('hotennguoithan');
        require('nguoithanQuanhe');
    }
    return errors;
}

/** Request body of POST /patients: trimmed values, empty optional fields as null. */
export function buildPatientPayload(form) {
    const payload = {};
    Object.keys(INITIAL_PATIENT_FORM).forEach((field) => {
        const value = (form[field] ?? '').trim();
        payload[field] = value || null;
    });
    payload.gioitinh = Number(form.gioitinh);
    return payload;
}

/** Maps backend validation codes (details) to field messages. */
export function serverFieldErrors(details) {
    return Object.fromEntries(Object.entries(details ?? {})
        .filter(([field]) => field in INITIAL_PATIENT_FORM)
        .map(([field, code]) => [field, FIELD_MSG[code] ?? FIELD_MSG.INVALID_FORMAT]));
}

export function isDirty(form, initial) {
    return Object.keys(INITIAL_PATIENT_FORM).some((field) => (form[field] ?? '') !== (initial[field] ?? ''));
}

/** General message of a failure that is not tied to a field. */
export function createErrorMessage(error) {
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    if (error?.errorResponse?.code === 'REQUEST_VALIDATION_FAILED') return MSG.FIX_FIELDS;
    return MSG.SYSTEM_ERROR;
}
