// UC-35 Search Patient: search types and keyword rules. Mirrors the backend PatientSearchType for feedback
// only; the backend repeats every check.

export const SEARCH_TYPES = [
    { value: 'CITIZEN_ID', label: 'CCCD', placeholder: 'Nhập số CCCD (chỉ chữ số)', prefillField: 'cccdSo' },
    { value: 'PASSPORT', label: 'Hộ chiếu', placeholder: 'Nhập số hộ chiếu', prefillField: 'hochieuSo' },
    { value: 'PATIENT_CODE', label: 'Mã bệnh nhân', placeholder: 'Ví dụ: BN0000' },
    { value: 'PHONE', label: 'Số điện thoại', placeholder: 'Nhập số điện thoại (chỉ chữ số)', prefillField: 'sodienthoai' },
    { value: 'NAME', label: 'Họ tên', placeholder: 'Nhập họ tên, có dấu hoặc không dấu', prefillField: 'hoten' },
    { value: 'INSURANCE_CARD', label: 'Số thẻ BHYT', placeholder: 'Nhập số thẻ BHYT' },
];

export const DEFAULT_SEARCH_TYPE = 'CITIZEN_ID';

// Report 3 System Messages (Vietnamese).
export const MSG = {
    NO_RESULT: 'Không tìm thấy kết quả.',
    REQUIRED: 'Vui lòng nhập từ khóa tìm kiếm.',
    INVALID_FORMAT: 'Định dạng từ khóa không hợp lệ.',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
};

// Part of a value is enough (search as you type): only the characters and the maximum length are checked.
const RULES = {
    CITIZEN_ID: (keyword) => /^\d{1,12}$/.test(keyword),
    PASSPORT: (keyword) => /^[A-Za-z0-9]{1,15}$/.test(keyword),
    PATIENT_CODE: (keyword) => /^[A-Za-z0-9-]{1,20}$/.test(keyword),
    PHONE: (keyword) => /^\d{1,10}$/.test(keyword),
    NAME: (keyword) => keyword.length <= 100,
    INSURANCE_CARD: (keyword) => /^[A-Za-z0-9]{1,15}$/.test(keyword),
};

/** Returns a Vietnamese error message, or '' when the keyword is valid for the type. */
export function validateKeyword(type, keyword) {
    const value = keyword.trim();
    if (!value) return MSG.REQUIRED;
    return RULES[type](value) ? '' : MSG.INVALID_FORMAT;
}

/** Maps an API failure to a Vietnamese message; never shows the raw backend message. */
export function searchErrorMessage(error) {
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    const keywordError = error?.errorResponse?.details?.keyword;
    if (keywordError === 'REQUIRED') return MSG.REQUIRED;
    if (keywordError === 'INVALID_FORMAT') return MSG.INVALID_FORMAT;
    return MSG.SYSTEM_ERROR;
}

/** Form values of UC-37 pre-filled with the keyword of an unsuccessful search (UC-35 EF-02). */
export function createPrefill(type, keyword) {
    const field = SEARCH_TYPES.find((item) => item.value === type)?.prefillField;
    return field ? { [field]: keyword.trim() } : {};
}
