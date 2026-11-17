// UC-35: loai tim kiem va quy tac tu khoa (giong backend, backend kiem tra lai)

export const SEARCH_TYPES = [
    { value: 'CITIZEN_ID', label: 'CCCD', placeholder: 'Đủ 12 chữ số trên thẻ', prefillField: 'cccdSo' },
    { value: 'PASSPORT', label: 'Hộ chiếu', placeholder: 'Đủ số hộ chiếu (tối đa 15 ký tự)', prefillField: 'hochieuSo' },
    { value: 'PATIENT_CODE', label: 'Mã bệnh nhân', placeholder: 'Một phần mã, ví dụ: 01 hoặc BN0000' },
    { value: 'PHONE', label: 'Số điện thoại', placeholder: 'Ít nhất 4 chữ số, ví dụ: 4 số cuối', prefillField: 'sodienthoai' },
    { value: 'NAME', label: 'Họ tên', placeholder: 'Ít nhất 2 ký tự, có dấu hoặc không dấu', prefillField: 'hoten' },
    { value: 'INSURANCE_CARD', label: 'Số thẻ BHYT', placeholder: 'Đủ 15 ký tự trên thẻ' },
];

export const DEFAULT_SEARCH_TYPE = 'CITIZEN_ID';

// thong bao theo bang System Messages
export const MSG = {
    NO_RESULT: 'Không tìm thấy kết quả.',
    REQUIRED: 'Vui lòng nhập từ khóa tìm kiếm.',
    INVALID_FORMAT: 'Định dạng từ khóa không hợp lệ.',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
};

// giay to (CCCD, ho chieu, BHYT) nhap du; ho ten, SDT, ma BN nhap 1 phan (giong backend)
const RULES = {
    CITIZEN_ID: (keyword) => /^\d{12}$/.test(keyword),
    PASSPORT: (keyword) => /^[A-Za-z0-9]{1,15}$/.test(keyword),
    INSURANCE_CARD: (keyword) => /^[A-Za-z0-9]{15}$/.test(keyword),
    PATIENT_CODE: (keyword) => /^[A-Za-z0-9-]{1,20}$/.test(keyword),
    PHONE: (keyword) => /^\d{4,10}$/.test(keyword),
    NAME: (keyword) => keyword.length >= 2 && keyword.length <= 100,
};

// kiem tra tu khoa theo loai tim, hop le thi tra ''
export function validateKeyword(type, keyword) {
    const value = keyword.trim();
    if (!value) return MSG.REQUIRED;
    return RULES[type](value) ? '' : MSG.INVALID_FORMAT;
}

// doi loi API thanh thong bao tieng Viet
export function searchErrorMessage(error) {
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    const keywordError = error?.errorResponse?.details?.keyword;
    if (keywordError === 'REQUIRED') return MSG.REQUIRED;
    if (keywordError === 'INVALID_FORMAT') return MSG.INVALID_FORMAT;
    return MSG.SYSTEM_ERROR;
}

// tim khong ra thi dien san tu khoa vao form tao ho so moi
export function createPrefill(type, keyword) {
    const field = SEARCH_TYPES.find((item) => item.value === type)?.prefillField;
    return field ? { [field]: keyword.trim() } : {};
}
