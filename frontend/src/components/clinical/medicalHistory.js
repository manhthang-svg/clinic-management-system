// UC-39: tien su benh chia 2 nhom Ban than / Gia dinh, moi dong la 1 muc

export const FAMILY_CATEGORY = 'FAMILY';
export const DEFAULT_SELF_CATEGORY = 'OTHER';

export const ENTRY_MAX_LENGTH = 255;
export const HISTORY_MAX_LENGTH = 2000;

export const MESSAGES = {
    saved: 'Tiền sử bệnh cập nhật thành công.', // MSG03
    readOnly: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    recordClosed: 'Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.', // MSG68
    changedByOthers: 'Tiền sử bệnh vừa được cập nhật. Vui lòng tải lại và thử lại.', // MSG75
    sessionExpired: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    systemError: 'Không thể lưu tiền sử bệnh do lỗi hệ thống. Vui lòng thử lại sau.', // MSG99
    loadError: 'Không thể tải tiền sử bệnh. Vui lòng thử lại.',
    invalid: 'Thông tin tiền sử bệnh chưa hợp lệ. Vui lòng kiểm tra lại.',
    discard: 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?', // MSG38
    lineTooLong: (max) => `Mỗi dòng không vượt quá ${max} ký tự.`, // MSG08
    tooLong: (max) => `Vượt quá độ dài tối đa ${max} ký tự.`, // MSG08
};

const isFamily = (item) => item.category === FAMILY_CATEGORY;

// tach tien su da luu thanh 2 nhom Ban than / Gia dinh
export function groupHistory(data) {
    const history = data?.history ?? [];
    return {
        self: history.filter((item) => !isFamily(item)).map((item) => item.content ?? ''),
        family: history.filter(isFamily).map((item) => item.content ?? ''),
    };
}

export function emptyDraft() {
    return { self: '', family: '' };
}

// tao ban nhap (2 o text) tu du lieu da luu
export function draftFrom(data) {
    if (!data) return emptyDraft();
    const { self, family } = groupHistory(data);
    return { self: self.join('\n'), family: family.join('\n') };
}

// tach text thanh tung dong, bo dong rong
function linesOf(text) {
    return text.split('\n').map((line) => line.trim()).filter(Boolean);
}

// kiem tra: moi dong toi da 255 ky tu, tong toi da 2000 ky tu
export function validateDraft(draft) {
    const errors = {};
    const self = linesOf(draft.self);
    const family = linesOf(draft.family);
    if (self.some((line) => line.length > ENTRY_MAX_LENGTH)) errors.self = MESSAGES.lineTooLong(ENTRY_MAX_LENGTH);
    if (family.some((line) => line.length > ENTRY_MAX_LENGTH)) errors.family = MESSAGES.lineTooLong(ENTRY_MAX_LENGTH);
    const total = [...self, ...family].reduce((sum, line) => sum + line.length, 0);
    if (total > HISTORY_MAX_LENGTH) errors.history = MESSAGES.tooLong(HISTORY_MAX_LENGTH);
    return errors;
}

// tao request luu: dong cu giu nhom cu, dong moi vao nhom Khac
// di ung khong sua o day nen gui lai nguyen nhu cu
export function toRequest(draft, data) {
    const stored = new Map((data?.history ?? []).filter((item) => !isFamily(item))
        .map((item) => [(item.content ?? '').trim(), item.category]));
    return {
        history: [
            ...linesOf(draft.self).map((content) => ({ category: stored.get(content) ?? DEFAULT_SELF_CATEGORY, content })),
            ...linesOf(draft.family).map((content) => ({ category: FAMILY_CATEGORY, content })),
        ],
        allergies: (data?.allergies ?? []).map((item) => ({
            drug: item.drug,
            reaction: item.reaction ?? null,
            severity: item.severity ?? null,
        })),
        noKnownDrugAllergy: Boolean(data?.noKnownDrugAllergy),
        lastUpdatedAt: data?.updatedAt ?? null,
    };
}

// ban nhap co bi sua khong
export function isSameDraft(left, right) {
    return JSON.stringify(left) === JSON.stringify(right);
}

// doi loi luu thanh thong bao tieng Viet
export function saveErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (error?.status === 401) return MESSAGES.sessionExpired;
    if (error?.status === 403) return MESSAGES.readOnly;
    if (code === 'MEDICAL_RECORD_CLOSED') return MESSAGES.recordClosed;
    if (code === 'CONCURRENT_MODIFICATION') return MESSAGES.changedByOthers;
    if (error?.status === 400) return MESSAGES.invalid;
    return MESSAGES.systemError;
}

// thong bao khi chi duoc xem (khong dang kham / benh an da dong)
export function readOnlyMessage(reason) {
    if (reason === 'MEDICAL_RECORD_CLOSED') return MESSAGES.recordClosed;
    if (reason) return MESSAGES.readOnly;
    return '';
}
