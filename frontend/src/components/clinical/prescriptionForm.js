/** UC-59 Prescribe Medication: form rules and messages (feedback only; the backend decides). */

// BR-107 / I1-D45 and the V55 column sizes, as the backend.
export const LIMITS = {
    quantity: 9999, timesPerDay: 24, days: 90, daysWithoutLongTerm: 30, dose: 50, usage: 255, notes: 2000,
};

// Message texts of docs/uc-59-e-prescription.md; MSG106-MSG119 are ASSUMPTION wording there.
export const MSG = {
    required: (field) => `Trường ${field} là bắt buộc.`, // MSG02
    tooLong: (max) => `Vượt quá độ dài tối đa ${max.toLocaleString('vi-VN')} ký tự.`, // MSG08
    SAVED: 'Đơn thuốc cập nhật thành công.', // MSG03
    NO_LINE: 'Vui lòng thêm ít nhất một thuốc vào đơn.', // MSG02
    QUANTITY: `Số lượng phải là số nguyên từ 1 đến ${LIMITS.quantity.toLocaleString('vi-VN')}.`, // MSG05
    TIMES: `Số lần mỗi ngày từ 1 đến ${LIMITS.timesPerDay}.`,
    DAYS: `Số ngày dùng từ 1 đến ${LIMITS.days}.`,
    LONG_TERM: `Số ngày dùng tối đa ${LIMITS.daysWithoutLongTerm} ngày. Chỉ kê đến ${LIMITS.days} ngày khi chẩn đoán thuộc danh mục bệnh được kê dài ngày.`, // MSG114
    // AF-05 shows "Bệnh được kê dài ngày: {disease}"; the response has no disease name yet, so the rule is stated.
    LONG_TERM_ALLOWED: `Chẩn đoán thuộc danh mục bệnh được kê dài ngày: được kê đến ${LIMITS.days} ngày.`,
    DUPLICATE: 'Thuốc này đã có trong đơn.',
    MEDICINE_INACTIVE: (name) => `${name ? `Thuốc ${name}` : 'Thuốc'} đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.`, // MSG40
    MEDICINE_NOT_ORDINARY: (name) => `${name || 'Thuốc đã chọn'} không còn thuộc nhóm thuốc thông thường nên không kê được. Vui lòng chọn thuốc khác.`, // MSG106
    CONCLUSION_REQUIRED: 'Lưu kết luận với chẩn đoán bệnh chính trước khi kê đơn.', // MSG113
    SENT: 'Đơn thuốc đã gửi thành công nên không sửa hoặc xóa được.', // MSG115
    LINK_CODE_MISSING: (item) => `Chưa có mã liên thông ${item}. Vui lòng liên hệ quản trị viên.`, // MSG116
    PRESCRIBER: 'bác sĩ',
    FACILITY: 'đơn thuốc của cơ sở khám chữa bệnh',
    CHILD_DATA_MISSING: (item) => `Trẻ dưới 72 tháng tuổi cần có ${item} trước khi gửi đơn thuốc.`, // MSG107
    RECORD_CLOSED: 'Hồ sơ bệnh án đã đóng hoặc bị khóa, không thể chỉnh sửa.', // MSG68
    NOT_ALLOWED: 'Lượt khám chưa thể kê đơn: bệnh nhân không còn đang khám hoặc chưa đủ kết quả cận lâm sàng.',
    OUTSIDE_ROOM_OR_SHIFT: 'Bạn không được phân công phòng khám này hoặc đang ngoài ca trực.', // MSG48
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    CONCURRENT_MODIFICATION: 'Đơn thuốc vừa được người khác cập nhật. Vui lòng tải lại và thử lại.', // MSG75
    SYSTEM_ERROR: 'Không thể kết nối hoặc hệ thống đang gặp sự cố. Vui lòng tải lại và thử lại.', // MSG80
    CHECK_FIELDS: 'Vui lòng kiểm tra các trường được đánh dấu.',
};

const LABELS = {
    dosePerTime: 'Liều mỗi lần', timesPerDay: 'Số lần/ngày', days: 'Số ngày', quantity: 'Số lượng', usage: 'Cách dùng',
    medicine: 'Thuốc',
};

/** A new line for a medicine picked from the catalog search. */
export function newLine(medicine) {
    return {
        medicineId: medicine.id, code: medicine.code, name: medicine.name, activeIngredient: medicine.activeIngredient,
        strength: medicine.strength, route: medicine.route, unit: medicine.unit,
        dosePerTime: '', timesPerDay: '', days: '', quantity: '', usage: '', note: '', quantityEdited: false,
    };
}

export function formFromResponse(data) {
    return {
        lines: (data?.lines ?? []).map((line) => ({
            ...line,
            timesPerDay: String(line.timesPerDay ?? ''),
            days: String(line.days ?? ''),
            quantity: line.quantity == null ? '' : String(Number(line.quantity)),
            note: line.note ?? '',
            quantityEdited: true,
        })),
        notes: data?.notes ?? '',
        advice: data?.advice ?? '',
    };
}

export function samePrescription(left, right) {
    const strip = (form) => ({ ...form, lines: form.lines.map(({ quantityEdited: _ignored, ...line }) => line) });
    return JSON.stringify(strip(left)) === JSON.stringify(strip(right));
}

/**
 * BR-59-04: the quantity to buy, dose × times a day × days, suggested only when the dose is a whole number of the
 * line's dispensing unit ("1 viên" for Viên, or a bare "2") and the result fits the limit. A dose in another unit
 * ("500 mg", "5 ml") or a split dose ("½ viên", "0,5 viên") gives null and the Doctor enters the quantity.
 */
export function suggestedQuantity(line) {
    const match = /^\s*(\d+)\s*(.*?)\s*$/u.exec(String(line.dosePerTime));
    if (!match) return null;
    const unit = String(line.unit ?? '').trim().toLocaleLowerCase('vi');
    const doseUnit = match[2].toLocaleLowerCase('vi');
    if (doseUnit !== '' && doseUnit !== unit) return null;
    const dose = Number(match[1]);
    const times = Number(line.timesPerDay);
    const days = Number(line.days);
    if (!(dose > 0) || !(times > 0) || !(days > 0)) return null;
    const quantity = dose * times * days;
    return quantity <= LIMITS.quantity ? quantity : null;
}

const wholeBetween = (value, min, max) => /^\d+$/.test(String(value).trim()) && Number(value) >= min && Number(value) <= max;

/** UC-59 step 4 on the client; errors keyed `lines.<index>.<field>`, `lines`, `notes`, `advice`. */
export function validatePrescription(form, longTermAllowed) {
    const errors = {};
    if (!form.lines.length) errors.lines = MSG.NO_LINE;
    form.lines.forEach((line, index) => {
        const key = (field) => `lines.${index}.${field}`;
        const dose = line.dosePerTime.trim();
        if (!dose) errors[key('dosePerTime')] = MSG.required(LABELS.dosePerTime);
        else if (dose.length > LIMITS.dose) errors[key('dosePerTime')] = MSG.tooLong(LIMITS.dose);
        if (!String(line.timesPerDay).trim()) errors[key('timesPerDay')] = MSG.required(LABELS.timesPerDay);
        else if (!wholeBetween(line.timesPerDay, 1, LIMITS.timesPerDay)) errors[key('timesPerDay')] = MSG.TIMES;
        if (!String(line.days).trim()) errors[key('days')] = MSG.required(LABELS.days);
        else if (!wholeBetween(line.days, 1, LIMITS.days)) errors[key('days')] = MSG.DAYS;
        else if (Number(line.days) > LIMITS.daysWithoutLongTerm && !longTermAllowed) errors[key('days')] = MSG.LONG_TERM;
        if (!String(line.quantity).trim()) errors[key('quantity')] = MSG.required(LABELS.quantity);
        else if (!wholeBetween(line.quantity, 1, LIMITS.quantity)) errors[key('quantity')] = MSG.QUANTITY;
        const usage = line.usage.trim();
        if (!usage) errors[key('usage')] = MSG.required(LABELS.usage);
        else if (usage.length > LIMITS.usage) errors[key('usage')] = MSG.tooLong(LIMITS.usage);
    });
    if (form.notes.trim().length > LIMITS.notes) errors.notes = MSG.tooLong(LIMITS.notes);
    if (form.advice.trim().length > LIMITS.notes) errors.advice = MSG.tooLong(LIMITS.notes);
    return errors;
}

export function toPayload(form, version) {
    const text = (value) => value.trim() || null;
    return {
        lines: form.lines.map((line) => ({
            medicineId: line.medicineId,
            quantity: Number(line.quantity),
            dosePerTime: line.dosePerTime.trim(),
            timesPerDay: Number(line.timesPerDay),
            days: Number(line.days),
            usage: line.usage.trim(),
            note: text(line.note),
        })),
        notes: text(form.notes),
        advice: text(form.advice),
        version: version ?? null,
    };
}

const REASON_MESSAGES = {
    quantity: MSG.QUANTITY, timesPerDay: MSG.TIMES, days: MSG.DAYS,
};

/**
 * Where a save failure is shown: `fields` under the inputs, `message` at the top. Never the raw backend message.
 */
export function prescriptionError(error) {
    const code = error?.errorResponse?.code;
    const details = error?.errorResponse?.details ?? {};
    const lineKey = (field) => (details.line == null ? field : `lines.${details.line}.${field}`);
    switch (code) {
        case 'PRESCRIPTION_INVALID': {
            if (details.field === 'lines') return { fields: { lines: MSG.NO_LINE }, message: MSG.NO_LINE };
            let message = MSG.required(LABELS[details.field] ?? 'này');
            if (details.reason === 'RANGE') message = REASON_MESSAGES[details.field] ?? MSG.CHECK_FIELDS;
            if (details.reason === 'TOO_LONG') message = MSG.tooLong(details.field === 'usage' ? LIMITS.usage
                : details.field === 'dosePerTime' ? LIMITS.dose : LIMITS.notes);
            if (details.reason === 'DUPLICATE') message = MSG.DUPLICATE;
            return { fields: { [lineKey(details.field)]: message }, message: MSG.CHECK_FIELDS };
        }
        case 'PRESCRIPTION_LONG_TERM_NOT_ALLOWED':
            return { fields: { [lineKey('days')]: MSG.LONG_TERM }, message: MSG.LONG_TERM };
        case 'PRESCRIPTION_MEDICINE_UNAVAILABLE': {
            // EF-03: the line must be replaced; MSG106 when the medicine left the ordinary group, else MSG40.
            const message = details.reason === 'NOT_ORDINARY'
                ? MSG.MEDICINE_NOT_ORDINARY(details.medicineName)
                : MSG.MEDICINE_INACTIVE(details.medicineName);
            return { fields: { [lineKey('medicine')]: message }, message };
        }
        case 'PRESCRIPTION_CONCLUSION_REQUIRED':
            return { fields: {}, message: MSG.CONCLUSION_REQUIRED };
        case 'PRESCRIPTION_ALREADY_SENT':
            return { fields: {}, message: MSG.SENT };
        case 'PRESCRIBER_LINK_CODE_REQUIRED':
            return { fields: {}, message: MSG.LINK_CODE_MISSING(MSG.PRESCRIBER) };
        case 'FACILITY_LINK_CODE_REQUIRED':
            return { fields: {}, message: MSG.LINK_CODE_MISSING(MSG.FACILITY) };
        case 'MEDICAL_RECORD_CLOSED':
            return { fields: {}, message: MSG.RECORD_CLOSED };
        case 'VISIT_NOT_IN_EXAMINATION':
            return { fields: {}, message: MSG.NOT_ALLOWED };
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
