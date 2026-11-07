/** UC-47 Perform Clinical Examination: client-side checks and messages (feedback only; the backend decides). */

export const ORGAN_FIELDS = [
    ['organExam', 'Bộ phận'],
];

export const notesKey = (key) => `${key}Notes`;

/** BR-98 ranges; label, unit, minimum, maximum. */
export const VITAL_RULES = {
    pulse: ['Mạch', 'lần/phút', 20, 250],
    temperature: ['Nhiệt độ', '°C', 30, 45],
    systolic: ['Huyết áp tâm thu', 'mmHg', 50, 300],
    diastolic: ['Huyết áp tâm trương', 'mmHg', 20, 200],
    respiratoryRate: ['Nhịp thở', 'lần/phút', 5, 80],
    weight: ['Cân nặng', 'kg', 0.5, 300],
    height: ['Chiều cao', 'cm', 30, 250],
    spo2: ['SpO₂', '%', 50, 100],
};

export const HISTORY_TYPE_LABELS = {
    PERSONAL: 'Tiền sử',
    DRUG_ALLERGY: 'Dị ứng thuốc',
};

// MSG68, MSG80, MSG96, MSG97 and MSG98 are not in the SRS message catalogue.
// ASSUMPTION: their wording below follows the UC-47 exception flows.
// The UC cites MSG48 for "outside the assigned room/shift", but the catalogue's MSG48 is an
// administrator message, so the wording here is also an ASSUMPTION.
export const MSG = {
    required: (field) => `Trường ${field} là bắt buộc.`, // MSG02
    tooLong: (max) => `Vượt quá độ dài tối đa ${max.toLocaleString('vi-VN')} ký tự.`, // MSG08
    lengthBetween: (field, min, max) => `${field} phải có từ ${min} đến ${max.toLocaleString('vi-VN')} ký tự.`,
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    OUTSIDE_ROOM_OR_SHIFT: 'Bạn không được phân công phòng khám này hoặc đang ngoài ca trực.', // MSG48 (UC-47)
    UNSAVED_CHANGES: 'Bạn có thay đổi chưa lưu. Nếu rời khỏi trang, các thay đổi này sẽ bị mất.', // MSG38
    RECORD_CLOSED: 'Hồ sơ bệnh án đã đóng hoặc bị khóa, không thể chỉnh sửa.', // MSG68
    SYSTEM_ERROR: 'Không thể kết nối hoặc hệ thống đang gặp sự cố. Vui lòng tải lại và thử lại.', // MSG80
    VITALS_REQUIRED: 'Cần nhập ít nhất một chỉ số sinh hiệu hợp lệ.', // MSG96
    vitalOutOfRange: ([label, unit, min, max]) =>
        `${label} phải trong khoảng ${min.toLocaleString('vi-VN')}–${max.toLocaleString('vi-VN')} ${unit}.`, // MSG97
    DIASTOLIC_NOT_BELOW_SYSTOLIC: 'Huyết áp tâm trương phải nhỏ hơn huyết áp tâm thu.', // MSG97
    DIAGNOSIS_REQUIRED: 'Chẩn đoán ban đầu phải có từ 2 đến 1.000 ký tự trước khi tiếp tục.', // MSG98
    ICD10_INVALID: 'Mã ICD-10 không còn khả dụng. Vui lòng chọn mã khác hoặc để trống.',
    ICD10_NOT_DETAILED: 'Mã ICD-10 này có mã chi tiết hơn. Vui lòng chọn mã chi tiết.', // TT 06/2026, cột 26
    ICD10_SEX_MISMATCH: 'Mã ICD-10 này không áp dụng cho giới tính của người bệnh.', // TT 06/2026, cột 28-29
    NOT_IN_EXAMINATION: 'Lượt khám không còn ở trạng thái đang khám.',
    VISIT_NOT_FOUND: 'Không tìm thấy lượt khám.',
    CONCURRENT_MODIFICATION: 'Hồ sơ vừa được cập nhật ở nơi khác. Vui lòng tải lại.',
    INVALID_DATA: 'Dữ liệu khám chưa hợp lệ. Vui lòng kiểm tra các trường được đánh dấu.',
    CHECK_FIELDS: 'Vui lòng kiểm tra các trường được đánh dấu.',
};

const CODE_MESSAGES = {
    FORBIDDEN: MSG.FORBIDDEN,
    DOCTOR_OFF_DUTY: MSG.OUTSIDE_ROOM_OR_SHIFT,
    ROOM_ASSIGNMENT_REQUIRED: MSG.OUTSIDE_ROOM_OR_SHIFT,
    MEDICAL_RECORD_CLOSED: MSG.RECORD_CLOSED,
    VISIT_NOT_IN_EXAMINATION: MSG.NOT_IN_EXAMINATION,
    VISIT_NOT_FOUND: MSG.VISIT_NOT_FOUND,
    PATIENT_NOT_FOUND: MSG.VISIT_NOT_FOUND,
    NOT_FOUND: MSG.VISIT_NOT_FOUND,
    CONCURRENT_MODIFICATION: MSG.CONCURRENT_MODIFICATION,
    VITAL_SIGNS_REQUIRED: MSG.VITALS_REQUIRED,
    VITAL_SIGNS_INVALID: MSG.CHECK_FIELDS,
    PRELIMINARY_DIAGNOSIS_REQUIRED: MSG.DIAGNOSIS_REQUIRED,
    ICD10_INVALID: MSG.ICD10_INVALID,
    ICD10_NOT_DETAILED: MSG.ICD10_NOT_DETAILED,
    ICD10_SEX_MISMATCH: MSG.ICD10_SEX_MISMATCH,
    CLINICAL_EXAMINATION_INVALID: MSG.INVALID_DATA,
    REQUEST_VALIDATION_FAILED: MSG.INVALID_DATA,
};

/** Page-level Vietnamese message for an API failure; never the raw backend message. */
export function examinationErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (CODE_MESSAGES[code]) return CODE_MESSAGES[code];
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    return MSG.SYSTEM_ERROR;
}

const API_FIELDS = {
    reasonForVisit: 'reason', presentIllness: 'progression', generalFinding: 'generalExam',
    preliminaryDiagnosis: 'preliminaryDiagnosis', preliminaryDiagnosisIcd10Code: 'icd10',
    treatmentCarePerformed: 'treatment',
};

/** Field errors the backend reported, mapped to form keys; `sentOrgans` is the payload's organ-key order. */
export function fieldErrorsFromApi(error) {
    const code = error?.errorResponse?.code;
    if (code === 'PRELIMINARY_DIAGNOSIS_REQUIRED') return { preliminaryDiagnosis: MSG.DIAGNOSIS_REQUIRED };
    if (code === 'ICD10_INVALID' || code === 'ICD10_NOT_DETAILED' || code === 'ICD10_SEX_MISMATCH') {
        return { icd10: MSG[code] };
    }
    if (code === 'VITAL_SIGNS_REQUIRED') return { vitals: MSG.VITALS_REQUIRED };
    const details = error?.errorResponse?.details;
    if (code !== 'REQUEST_VALIDATION_FAILED' || !details || typeof details !== 'object') return {};
    const errors = {};
    Object.keys(details).forEach((path) => {
        if (path.startsWith('vitalSigns')) errors.vitals = MSG.CHECK_FIELDS;
        else if (path === 'organFinding') errors.organExam = MSG.INVALID_DATA;
        else if (API_FIELDS[path]) errors[API_FIELDS[path]] = MSG.INVALID_DATA;
    });
    return errors;
}

function checkLength(errors, key, value, label, min, max, required) {
    const text = value.trim();
    if (!text) {
        if (required) errors[key] = MSG.required(label);
        return;
    }
    if (text.length > max) errors[key] = MSG.tooLong(max);
    else if (text.length < min) errors[key] = MSG.lengthBetween(label, min, max);
}

/** Mirrors the backend rules for the chosen action (AF-01 draft vs. AF-04/AF-05 continue). */
export function validateExamination(form, mode) {
    const errors = {};
    checkLength(errors, 'reason', form.reason, 'Lý do khám', 2, 1000, true);
    checkLength(errors, 'generalExam', form.generalExam, 'Toàn thân', 0, 4000, false);

    const entered = Object.keys(VITAL_RULES).filter((key) => String(form[key] ?? '').trim() !== '');
    if (entered.length === 0) errors.vitals = MSG.VITALS_REQUIRED;
    entered.forEach((key) => {
        const value = Number(form[key]);
        const [, , min, max] = VITAL_RULES[key];
        if (!Number.isFinite(value) || value < min || value > max) errors[key] = MSG.vitalOutOfRange(VITAL_RULES[key]);
    });
    if (!errors.systolic && !errors.diastolic && entered.includes('systolic') && entered.includes('diastolic')
        && Number(form.diastolic) >= Number(form.systolic)) {
        errors.diastolic = MSG.DIASTOLIC_NOT_BELOW_SYSTOLIC;
    }

    ORGAN_FIELDS.forEach(([key, label]) => {
        const hasNotes = form[notesKey(key)].trim() !== '';
        checkLength(errors, key, form[key], `Kết quả khám ${label.toLocaleLowerCase('vi')}`, 2, 2000, hasNotes);
        checkLength(errors, notesKey(key), form[notesKey(key)], 'Ghi chú', 0, 500, false);
    });

    if (mode === 'CONTINUE') {
        if (!form.diagnoses?.length) errors.preliminaryDiagnosis = MSG.DIAGNOSIS_REQUIRED;
    }
    return errors;
}

/** UC-39 item edited during the examination. */
export function validateHistory(draft) {
    const errors = {};
    checkLength(errors, 'content', draft.content, 'Nội dung', 2, 2000, true);
    checkLength(errors, 'notes', draft.notes, 'Ghi chú', 0, 500, false);
    return errors;
}
