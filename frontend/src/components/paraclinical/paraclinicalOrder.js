/** UC-49 Order Paraclinical Services: grouping, cost estimate and messages. */

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

/** The API returns a list; older responses were a page. */
export function toServiceList(response) {
    if (Array.isArray(response)) return response;
    return response?.content || [];
}

/** Step 2: active services matching the keyword, grouped by type in Lab → Imaging → Functional order. */
export function groupServices(services, keyword = '') {
    const term = keyword.trim().toLocaleLowerCase('vi');
    const matches = services.filter((service) => !term
        || service.name?.toLocaleLowerCase('vi').includes(term)
        || service.code?.toLocaleLowerCase('vi').includes(term));
    return Object.keys(TYPE_LABEL)
        .map((type) => ({ type, label: TYPE_LABEL[type], services: matches.filter((s) => s.type === type) }))
        .filter((group) => group.services.length > 0);
}

/** Step 4: estimated cost of the selected services (self-paid visit: the clinic price). */
export function estimate(services, selectedIds) {
    const lines = selectedIds
        .map((id) => services.find((service) => service.id === id))
        .filter(Boolean);
    const total = lines.reduce((sum, service) => sum + Number(service.clinicPrice || 0), 0);
    return { lines, total };
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
