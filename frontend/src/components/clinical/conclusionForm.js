/** UC-56 Conclude Examination: form rules and messages (feedback only; the backend decides). */

// ASSUMPTION (BR-106 is not in docs/): at most 12 secondary diagnoses, as the backend. The texts have no length
// limit (the conclusions columns are TEXT).
export const LIMITS = { secondary: 12 };

export const EMPTY_CONCLUSION = {
    diagnosis: '', main: null, secondary: [], treatmentPlan: '', treatmentResult: '', advice: '', summary: '', note: '',
};

// MSG64, MSG65, MSG66 and MSG68 wording: ASSUMPTION where the catalogue has none.
export const MSG = {
    required: (field) => `Trường ${field} là bắt buộc.`, // MSG02
    SAVED: 'Kết luận khám cập nhật thành công.', // MSG03
    MAIN_REQUIRED: 'Kết luận phải có đúng một bệnh chính (ICD-10), không trùng với bệnh kèm theo.', // MSG64
    mainNotAllowed: (code) => `Mã ${code} không được dùng làm bệnh chính.`, // MSG65
    mainDiscouraged: (code) => `Mã ${code} không được khuyến khích dùng làm bệnh chính. Bạn có muốn tiếp tục không?`, // MSG66
    TOO_MANY_SECONDARY: `Chỉ chọn tối đa ${LIMITS.secondary} bệnh kèm theo.`,
    DIAGNOSES_LOCKED: 'Bệnh chính và bệnh kèm theo đã gửi kèm đơn thuốc nên không thể thay đổi.', // BR-47
    ICD10_INVALID: 'Mã ICD-10 không còn khả dụng. Vui lòng chọn mã khác.', // MSG40
    ICD10_NOT_DETAILED: 'Mã ICD-10 này có mã chi tiết hơn. Vui lòng chọn mã chi tiết.',
    ICD10_SEX_MISMATCH: 'Mã ICD-10 này không áp dụng cho giới tính của người bệnh.',
    RECORD_CLOSED: 'Hồ sơ bệnh án đã đóng hoặc bị khóa, không thể chỉnh sửa.', // MSG68
    NOT_CONCLUDABLE: 'Lượt khám chưa thể kết luận: bệnh nhân không còn đang khám hoặc chưa đủ kết quả cận lâm sàng.',
    OUTSIDE_ROOM_OR_SHIFT: 'Bạn không được phân công phòng khám này hoặc đang ngoài ca trực.', // MSG48
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    CONCURRENT_MODIFICATION: 'Kết luận vừa được cập nhật ở nơi khác. Vui lòng tải lại.',
    SYSTEM_ERROR: 'Không thể kết nối hoặc hệ thống đang gặp sự cố. Vui lòng tải lại và thử lại.', // MSG80
    CHECK_FIELDS: 'Vui lòng kiểm tra các trường được đánh dấu.',
    UNSAVED_CHANGES: 'Bạn có thay đổi chưa lưu. Nếu rời khỏi trang, các thay đổi này sẽ bị mất.', // MSG38
};

const LABELS = {
    diagnosis: 'Chẩn đoán', main: 'Bệnh chính (ICD-10)', treatmentPlan: 'Hướng điều trị',
    treatmentResult: 'Kết quả điều trị', advice: 'Lời dặn', summary: 'Tóm tắt bệnh án', note: 'Ghi chú',
};

const REQUIRED_TEXT = ['diagnosis', 'treatmentPlan', 'advice'];

/** The form as loaded from the API response. */
export function formFromResponse(data) {
    return {
        diagnosis: data?.diagnosis ?? '',
        main: data?.mainDiagnosis ?? null,
        secondary: data?.secondaryDiagnoses ?? [],
        treatmentPlan: data?.treatmentPlan ?? '',
        // CV 365 III.31.1: optional free text (V63).
        treatmentResult: data?.treatmentResult ?? '',
        advice: data?.advice ?? '',
        summary: data?.summary ?? '',
        note: data?.note ?? '',
    };
}

export function sameConclusion(left, right) {
    return JSON.stringify(left) === JSON.stringify(right);
}

/** UC-56 step 6 on the client: MSG02, MSG64. */
export function validateConclusion(form) {
    const errors = {};
    REQUIRED_TEXT.forEach((key) => {
        if (!form[key].trim()) errors[key] = MSG.required(LABELS[key]);
    });
    if (!form.main) errors.main = MSG.MAIN_REQUIRED;
    else if (form.secondary.some((item) => item.code === form.main.code)) errors.secondary = MSG.MAIN_REQUIRED;
    if (form.secondary.length > LIMITS.secondary) errors.secondary = MSG.TOO_MANY_SECONDARY;
    return errors;
}

export function toPayload(form, version, confirmDiscouragedMain = false) {
    const text = (value) => value.trim() || null;
    return {
        diagnosis: form.diagnosis.trim(),
        mainIcd10Code: form.main?.code ?? null,
        secondaryIcd10Codes: form.secondary.map((item) => item.code),
        treatmentPlan: form.treatmentPlan.trim(),
        treatmentResult: text(form.treatmentResult),
        advice: form.advice.trim(),
        summary: text(form.summary),
        note: text(form.note),
        confirmDiscouragedMain,
        version: version ?? null,
    };
}

const FIELD_OF = {
    diagnosis: 'diagnosis', treatmentPlan: 'treatmentPlan', advice: 'advice', summary: 'summary', note: 'note',
    treatmentResult: 'treatmentResult', secondaryIcd10Codes: 'secondary',
};

/**
 * Where a save failure is shown: `fields` under the inputs, `message` at the top, `discouragedCode` asks MSG66
 * before saving again. Never the raw backend message.
 */
export function conclusionError(error) {
    const code = error?.errorResponse?.code;
    const details = error?.errorResponse?.details ?? {};
    const icdCode = details.icd10Code;
    switch (code) {
        case 'CONCLUSION_INVALID': {
            const field = FIELD_OF[details.field];
            const message = details.reason === 'TOO_MANY'
                ? MSG.TOO_MANY_SECONDARY
                : MSG.required(LABELS[field] ?? 'này');
            return { fields: field ? { [field]: message } : {}, message: MSG.CHECK_FIELDS };
        }
        case 'CONCLUSION_MAIN_DIAGNOSIS_REQUIRED':
            return { fields: { main: MSG.MAIN_REQUIRED }, message: MSG.MAIN_REQUIRED };
        case 'CONCLUSION_MAIN_DIAGNOSIS_NOT_ALLOWED':
            return { fields: { main: MSG.mainNotAllowed(icdCode) }, message: MSG.mainNotAllowed(icdCode) };
        case 'CONCLUSION_MAIN_DIAGNOSIS_DISCOURAGED':
            return { fields: {}, message: '', discouragedCode: icdCode };
        case 'CONCLUSION_DIAGNOSES_LOCKED':
            return { fields: {}, message: MSG.DIAGNOSES_LOCKED };
        case 'ICD10_INVALID':
        case 'ICD10_NOT_DETAILED':
        case 'ICD10_SEX_MISMATCH':
            return { fields: {}, message: `${icdCode ? `${icdCode}: ` : ''}${MSG[code]}` };
        case 'MEDICAL_RECORD_CLOSED':
            return { fields: {}, message: MSG.RECORD_CLOSED };
        case 'VISIT_NOT_IN_EXAMINATION':
            return { fields: {}, message: MSG.NOT_CONCLUDABLE };
        case 'DOCTOR_OFF_DUTY':
        case 'ROOM_ASSIGNMENT_REQUIRED':
            return { fields: {}, message: MSG.OUTSIDE_ROOM_OR_SHIFT };
        case 'CONCURRENT_MODIFICATION':
            return { fields: {}, message: MSG.CONCURRENT_MODIFICATION };
        default:
            if (error?.status === 401) return { fields: {}, message: MSG.SESSION_EXPIRED };
            if (error?.status === 403) return { fields: {}, message: MSG.FORBIDDEN };
            return { fields: {}, message: MSG.SYSTEM_ERROR };
    }
}
