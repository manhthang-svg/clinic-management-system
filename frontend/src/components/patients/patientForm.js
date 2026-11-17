// UC-37, UC-38: gia tri form, kiem tra truong va thong bao (backend kiem tra lai)

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

// thong bao theo bang System Messages
export const FIELD_MSG = {
    REQUIRED: 'Trường này là bắt buộc.',
    INVALID_FORMAT: 'Định dạng không hợp lệ.',
    TOO_LONG: 'Vượt quá độ dài tối đa cho phép.',
};
export const MSG = {
    CREATED: 'Tạo hồ sơ bệnh nhân thành công.',
    UPDATED: 'Cập nhật hồ sơ bệnh nhân thành công.',
    // TBD: chua co MSG cho ho so bi nguoi khac sua
    STALE: 'Hồ sơ vừa được người khác cập nhật. Vui lòng tải lại trang để xem dữ liệu mới.',
    NOT_FOUND: 'Không tìm thấy hồ sơ bệnh nhân được yêu cầu.',
    FIX_FIELDS: 'Vui lòng kiểm tra lại các trường được đánh dấu.',
    DISCARD: 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
};

// MSG57: canh bao ho so co the trung
export const msg57 = (count) =>
    `Đã tồn tại ${count} bệnh nhân có cùng họ tên và ngày sinh. Vui lòng chọn một hồ sơ hoặc tạo hồ sơ mới.`;

// MSG19: CCCD / ho chieu da thuoc ho so khac
export const msg19 = (field, code, name) =>
    `Bệnh nhân có số ${field === 'hochieuSo' ? 'hộ chiếu' : 'CCCD'} này đã tồn tại (${code} – ${name}).`;

// tinh tuoi tu ngay sinh tren form, sai ngay thi tra null
export function formAge(form, today) {
    return /^\d{4}-\d{2}-\d{2}$/.test(form.ngaysinh) ? ageOn(form.ngaysinh, today) : null;
}

// kiem tra form, tra ve { truong: loi } (rong la hop le)
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

// tao body gui len: bo khoang trang, truong rong thanh null
export function buildPatientPayload(form) {
    const payload = {};
    Object.keys(INITIAL_PATIENT_FORM).forEach((field) => {
        const value = (form[field] ?? '').trim();
        payload[field] = value || null;
    });
    payload.gioitinh = Number(form.gioitinh);
    return payload;
}

// UC-38: doi ho so da luu thanh gia tri form
export function toFormValues(patient) {
    return Object.fromEntries(Object.keys(INITIAL_PATIENT_FORM).map((field) => {
        const value = patient?.[field];
        return [field, value === null || value === undefined ? '' : String(value)];
    }));
}

// doi ma loi backend thanh loi tung truong
export function serverFieldErrors(details) {
    return Object.fromEntries(Object.entries(details ?? {})
        .filter(([field]) => field in INITIAL_PATIENT_FORM)
        .map(([field, code]) => [field, FIELD_MSG[code] ?? FIELD_MSG.INVALID_FORMAT]));
}

// form da bi sua so voi ban dau chua
export function isDirty(form, initial) {
    return Object.keys(INITIAL_PATIENT_FORM).some((field) => (form[field] ?? '') !== (initial[field] ?? ''));
}

// thong bao loi chung (khong thuoc truong nao)
export function createErrorMessage(error) {
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    if (error?.errorResponse?.code === 'REQUEST_VALIDATION_FAILED') return MSG.FIX_FIELDS;
    return MSG.SYSTEM_ERROR;
}
