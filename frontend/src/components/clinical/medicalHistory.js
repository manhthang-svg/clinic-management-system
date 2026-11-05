// UC-39 Update Medical History and Allergies: labels, validation and error messages.

export const HISTORY_CATEGORY_LABELS = {
    CHRONIC_DISEASE: 'Bệnh mạn tính',
    SURGERY: 'Phẫu thuật',
    FAMILY: 'Tiền sử gia đình',
    OTHER: 'Khác',
};

export const ALLERGY_SEVERITY_LABELS = {
    MILD: 'Nhẹ',
    MODERATE: 'Trung bình',
    SEVERE: 'Nặng',
};

export const ENTRY_MAX_LENGTH = 255;
export const HISTORY_MAX_LENGTH = 2000;

export const MESSAGES = {
    saved: 'Tiền sử và dị ứng cập nhật thành công.', // MSG03
    readOnly: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    recordClosed: 'Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.', // MSG68
    changedByOthers: 'Tiền sử và dị ứng vừa được người khác cập nhật. Vui lòng tải lại và thử lại.', // MSG75
    sessionExpired: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    systemError: 'Không thể lưu tiền sử và dị ứng do lỗi hệ thống. Vui lòng thử lại sau.', // MSG99
    loadError: 'Không thể tải tiền sử và dị ứng. Vui lòng thử lại.',
    invalid: 'Thông tin tiền sử và dị ứng chưa hợp lệ. Vui lòng kiểm tra lại.',
    discard: 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?', // MSG38
    required: (field) => `${field} là bắt buộc.`, // MSG02
    tooLong: (max) => `Vượt quá độ dài tối đa ${max} ký tự.`, // MSG08
};

export function emptyDraft() {
    return { history: [], allergies: [], noKnownDrugAllergy: false };
}

export function draftFrom(data) {
    if (!data) return emptyDraft();
    return {
        history: (data.history ?? []).map((item) => ({ category: item.category, content: item.content ?? '' })),
        allergies: (data.allergies ?? []).map((item) => ({
            drug: item.drug ?? '',
            reaction: item.reaction ?? '',
            severity: item.severity ?? '',
        })),
        noKnownDrugAllergy: Boolean(data.noKnownDrugAllergy),
    };
}

/** UC-39 step 4: returns field errors keyed like "history.0" or "allergies.1"; empty when valid. */
export function validateDraft(draft) {
    const errors = {};
    let total = 0;
    draft.history.forEach((item, index) => {
        const content = item.content.trim();
        total += content.length;
        if (!content) errors[`history.${index}`] = MESSAGES.required('Nội dung tiền sử');
        else if (content.length > ENTRY_MAX_LENGTH) errors[`history.${index}`] = MESSAGES.tooLong(ENTRY_MAX_LENGTH);
    });
    if (total > HISTORY_MAX_LENGTH) errors.history = MESSAGES.tooLong(HISTORY_MAX_LENGTH);
    if (!draft.noKnownDrugAllergy) {
        draft.allergies.forEach((item, index) => {
            const drug = item.drug.trim();
            if (!drug) errors[`allergies.${index}`] = MESSAGES.required('Thuốc hoặc nhóm thuốc');
            else if (drug.length > ENTRY_MAX_LENGTH || item.reaction.trim().length > ENTRY_MAX_LENGTH) {
                errors[`allergies.${index}`] = MESSAGES.tooLong(ENTRY_MAX_LENGTH);
            }
        });
    }
    return errors;
}

export function toRequest(draft, lastUpdatedAt) {
    return {
        history: draft.history.map((item) => ({ category: item.category, content: item.content.trim() })),
        allergies: draft.noKnownDrugAllergy ? [] : draft.allergies.map((item) => ({
            drug: item.drug.trim(),
            reaction: item.reaction.trim() || null,
            severity: item.severity || null,
        })),
        noKnownDrugAllergy: draft.noKnownDrugAllergy,
        lastUpdatedAt: lastUpdatedAt ?? null,
    };
}

export function isSameDraft(left, right) {
    return JSON.stringify(left) === JSON.stringify(right);
}

/** Maps a save failure to the message of UC-39 EF-01 to EF-03. */
export function saveErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (error?.status === 401) return MESSAGES.sessionExpired;
    if (error?.status === 403) return MESSAGES.readOnly;
    if (code === 'MEDICAL_RECORD_CLOSED') return MESSAGES.recordClosed;
    if (code === 'CONCURRENT_MODIFICATION') return MESSAGES.changedByOthers;
    if (error?.status === 400) return MESSAGES.invalid;
    return MESSAGES.systemError;
}

export function readOnlyMessage(reason) {
    if (reason === 'MEDICAL_RECORD_CLOSED') return MESSAGES.recordClosed;
    if (reason) return MESSAGES.readOnly;
    return '';
}
