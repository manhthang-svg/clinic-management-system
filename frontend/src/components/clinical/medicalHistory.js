// UC-39 Update Medical History: "Tiền sử bệnh" split into "Bản thân" and "Gia đình" (bệnh án mục 2).
// Each line of a group is one history entry; FAMILY entries form "Gia đình", all others "Bản thân".

export const FAMILY_CATEGORY = 'FAMILY';
export const DEFAULT_SELF_CATEGORY = 'OTHER';

export const ENTRY_MAX_LENGTH = 255;
export const HISTORY_MAX_LENGTH = 2000;

export const MESSAGES = {
    saved: 'Tiền sử bệnh cập nhật thành công.', // MSG03
    readOnly: 'Bạn không có quyền thực hiện thao tác này.', // MSG13
    recordClosed: 'Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.', // MSG68
    changedByOthers: 'Tiền sử bệnh vừa được người khác cập nhật. Vui lòng tải lại và thử lại.', // MSG75
    sessionExpired: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.', // MSG12
    systemError: 'Không thể lưu tiền sử bệnh do lỗi hệ thống. Vui lòng thử lại sau.', // MSG99
    loadError: 'Không thể tải tiền sử bệnh. Vui lòng thử lại.',
    invalid: 'Thông tin tiền sử bệnh chưa hợp lệ. Vui lòng kiểm tra lại.',
    discard: 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?', // MSG38
    lineTooLong: (max) => `Mỗi dòng không vượt quá ${max} ký tự.`, // MSG08
    tooLong: (max) => `Vượt quá độ dài tối đa ${max} ký tự.`, // MSG08
};

const isFamily = (item) => item.category === FAMILY_CATEGORY;

/** Contents of the "Bản thân" and "Gia đình" groups of the stored history. */
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

export function draftFrom(data) {
    if (!data) return emptyDraft();
    const { self, family } = groupHistory(data);
    return { self: self.join('\n'), family: family.join('\n') };
}

function linesOf(text) {
    return text.split('\n').map((line) => line.trim()).filter(Boolean);
}

/** UC-39 step 4: returns errors keyed "self", "family" or "history"; empty when valid. */
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

/**
 * Builds the UC-39 request. A "Bản thân" line keeps the category it was stored with; a new one is OTHER.
 * Drug allergies are no longer edited on this screen, so the stored ones are sent back unchanged.
 */
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
