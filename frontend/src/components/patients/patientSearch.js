// UC-35 Search Patient: search types and keyword rules. Mirrors the backend PatientSearchType for feedback
// only; the backend repeats every check.

export const SEARCH_TYPES = [
    { value: 'CITIZEN_ID', label: 'CCCD', placeholder: '12 chữ số', prefillField: 'cccdSo' },
    { value: 'PASSPORT', label: 'Hộ chiếu', placeholder: 'Tối đa 15 ký tự', prefillField: 'hochieuSo' },
    { value: 'PATIENT_CODE', label: 'Mã bệnh nhân', placeholder: 'Ví dụ: BN1234567' },
    { value: 'PHONE', label: 'Số điện thoại', placeholder: '10 chữ số', prefillField: 'sodienthoai' },
    { value: 'NAME', label: 'Họ tên', placeholder: 'Từ 2 đến 100 ký tự', prefillField: 'hoten' },
    { value: 'INSURANCE_CARD', label: 'Số thẻ BHYT', placeholder: '15 ký tự' },
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

const RULES = {
    CITIZEN_ID: (keyword) => /^\d{12}$/.test(keyword),
    PASSPORT: (keyword) => /^[A-Za-z0-9]{1,15}$/.test(keyword),
    PATIENT_CODE: () => true,
    PHONE: (keyword) => /^\d{10}$/.test(keyword),
    NAME: (keyword) => keyword.length >= 2 && keyword.length <= 100,
    INSURANCE_CARD: (keyword) => /^[A-Za-z0-9]{15}$/.test(keyword),
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
