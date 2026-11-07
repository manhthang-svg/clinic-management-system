/** UC-49 Order Paraclinical Services: filtering, cost estimate and messages. */

export const TYPE_LABEL = {
    LAB: 'Xét nghiệm',
    IMAGING: 'Chẩn đoán hình ảnh',
    FUNCTIONAL: 'Thăm dò chức năng',
};

export const MSG = {
    NONE_SELECTED: 'Vui lòng chọn ít nhất một dịch vụ cận lâm sàng.',
    NOT_IN_EXAMINATION: 'Lượt khám không còn ở trạng thái đang khám. Vui lòng tải lại danh sách.',
    RECORD_CLOSED: 'Bệnh án đã đóng, không thể chỉ định thêm.',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
    LOAD_FAILED: 'Không tải được danh mục dịch vụ cận lâm sàng.',
};

export const formatMoney = (value) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`;

/** Amount without the currency sign, for table cells whose header already names the unit. */
export const formatAmount = (value) => Number(value || 0).toLocaleString('vi-VN');

/** The API returns a list; older responses were a page. */
export function toServiceList(response) {
    if (Array.isArray(response)) return response;
    return response?.content || [];
}

const TYPE_ORDER = Object.keys(TYPE_LABEL);

/** Service groups present in the catalogue, sorted by name, for the group filter. */
export function groupOptions(services) {
    const groups = new Map();
    services.forEach((service) => {
        if (service.serviceGroupId) groups.set(service.serviceGroupId, service.serviceGroupName || '—');
    });
    return [...groups].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name, 'vi'));
}

/** Step 2: active services matching the keyword, type and group, in Lab → Imaging → Functional order. */
export function filterServices(services, { keyword = '', type = '', groupId = '' } = {}) {
    const term = keyword.trim().toLocaleLowerCase('vi');
    return services
        .filter((service) => !term
            || service.name?.toLocaleLowerCase('vi').includes(term)
            || service.code?.toLocaleLowerCase('vi').includes(term))
        .filter((service) => !type || service.type === type)
        .filter((service) => !groupId || service.serviceGroupId === groupId)
        .sort((a, b) => TYPE_ORDER.indexOf(a.type) - TYPE_ORDER.indexOf(b.type));
}

/**
 * Step 4: the selected services with their catalogue prices. A self-paid visit pays the clinic price, so its
 * amount is the clinic total. An insured visit only shows both prices: the insurance and patient amounts are
 * settled at the final payment (ASSUMPTION, team decision 07/11/2026; the BHYT split is not implemented yet).
 */
export function estimate(services, selectedIds) {
    const lines = selectedIds
        .map((id) => services.find((service) => service.id === id))
        .filter(Boolean)
        .map((service) => ({
            ...service,
            clinicPrice: Number(service.clinicPrice || 0),
            insurancePrice: service.insurancePrice == null ? null : Number(service.insurancePrice),
        }));
    const clinicTotal = lines.reduce((sum, line) => sum + line.clinicPrice, 0);
    const insuranceTotal = lines.reduce((sum, line) => sum + (line.insurancePrice ?? 0), 0);
    return { lines, clinicTotal, insuranceTotal, total: clinicTotal };
}

/** MSG67 after the order is confirmed. */
export function createdMessage(order) {
    return `Đã tạo chỉ định cận lâm sàng. Phiếu thu ${order?.paymentCode} – bệnh nhân trả: ${formatMoney(order?.tongcong)}.`;
}

/** MSG42 / MSG12 / MSG99; never the raw backend message. */
export function orderErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'SERVICE_INACTIVE') {
        const name = error.errorResponse.details?.serviceName || 'dịch vụ đã chọn';
        return `Dịch vụ ${name} không còn hoạt động. Vui lòng chọn dịch vụ khác.`;
    }
    if (code === 'SERVICE_NOT_FOUND') return 'Dịch vụ đã chọn không còn hợp lệ. Vui lòng chọn dịch vụ khác.';
    if (code === 'VISIT_NOT_IN_EXAMINATION') return MSG.NOT_IN_EXAMINATION;
    if (code === 'MEDICAL_RECORD_CLOSED') return MSG.RECORD_CLOSED;
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    return MSG.SYSTEM_ERROR;
}
