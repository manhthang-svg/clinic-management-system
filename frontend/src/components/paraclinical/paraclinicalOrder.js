/** UC-49 Order Paraclinical Services: filtering, cost estimate and messages. */

export const TYPE_LABEL = {
    LAB: 'Xét nghiệm',
    IMAGING: 'Chẩn đoán hình ảnh',
    FUNCTIONAL: 'Thăm dò chức năng',
};

export const MSG = {
    NONE_SELECTED: 'Vui lòng chọn ít nhất một dịch vụ cận lâm sàng.',
    // EF-03: MSG75 ("Lượt khám") when the visit changed meanwhile, MSG68 when its medical record is closed.
    NOT_IN_EXAMINATION: 'Lượt khám vừa được cập nhật. Vui lòng tải lại và thử lại.',
    RECORD_CLOSED: 'Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.',
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
 * BR-27 applied coverage rate: 100% while the visit's total cost within insurance scope stays below the
 * co-payment exemption threshold (15% of the base salary), otherwise the card's rate.
 */
export function appliedCoverageRate(scopeTotal, { cardRate, threshold }) {
    return threshold != null && scopeTotal < threshold ? 100 : cardRate;
}

/**
 * Step 4 (BR-27): each line is charged at the clinic price. For a valid-card insurance visit (`coverage` =
 * { cardRate, scopeTotal, threshold }), a service with an insurance price is covered for
 * min(insurance price, clinic price) × the applied rate of the whole visit once these services are added, and the
 * patient pays the rest; other services, and every service of a self-paid visit (no coverage), are paid in full.
 * The backend computes the receipts; this is the estimate shown before confirming.
 */
export function estimate(services, selectedIds, coverage = null) {
    const selected = selectedIds
        .map((id) => services.find((service) => service.id === id))
        .filter(Boolean)
        .map((service) => {
            const clinicPrice = Number(service.clinicPrice || 0);
            const insurancePrice = service.insurancePrice == null ? null : Number(service.insurancePrice);
            const scope = insurancePrice == null ? 0 : Math.min(insurancePrice, clinicPrice);
            return { ...service, clinicPrice, insurancePrice, scope };
        });
    const newScope = selected.reduce((total, line) => total + line.scope, 0);
    const visitScope = Number(coverage?.scopeTotal || 0) + newScope;
    const appliedRate = coverage?.cardRate ? appliedCoverageRate(visitScope, coverage) : null;
    const lines = selected.map((line) => {
        const insuranceAmount = appliedRate && line.insurancePrice != null
            ? Math.round((line.scope * appliedRate) / 100)
            : 0;
        return { ...line, insuranceAmount, patientAmount: line.clinicPrice - insuranceAmount };
    });
    const sum = (key) => lines.reduce((total, line) => total + line[key], 0);
    return {
        lines,
        appliedRate,
        // The earlier receipts of the visit are repriced when the applied rate changes (BR-27).
        rateChanges: appliedRate != null && coverage?.cardRate
            && appliedRate !== appliedCoverageRate(Number(coverage.scopeTotal || 0), coverage),
        clinicTotal: sum('clinicPrice'),
        insuranceTotal: lines.reduce((total, line) => total + (line.insurancePrice ?? 0), 0),
        insuranceAmount: sum('insuranceAmount'),
        patientAmount: sum('patientAmount'),
        total: sum('clinicPrice'),
    };
}

/** MSG63 (toast) after the order is confirmed: "Đã tạo chỉ định cận lâm sàng {order_number}." (UC-49 step 10). */
export function createdMessage(order) {
    return `Đã tạo chỉ định cận lâm sàng ${order?.sophieu}.`;
}

export const PRINT_MSG = {
    NOT_FOUND: 'Không tìm thấy phiếu chỉ định.', // MSG39
    FACILITY_MISSING: 'Chưa có thông tin cơ sở khám bệnh, chữa bệnh. Vui lòng liên hệ quản trị viên.', // EF-04
};

/**
 * UC-50 EF-01, EF-02, EF-04, EF-05: MSG13, MSG39, the facility message, MSG12, MSG80; never the raw backend
 * message. An order with nothing to print has no print button (EF-03); if it was cancelled meanwhile, the backend
 * still refuses and this is MSG80.
 */
export function printErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'PARACLINICAL_ORDER_NOT_FOUND') return PRINT_MSG.NOT_FOUND;
    if (code === 'CLINIC_FACILITY_NOT_CONFIGURED') return PRINT_MSG.FACILITY_MISSING;
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    return MSG.SYSTEM_ERROR;
}

/** An order can be printed while it is not cancelled and still has a service to perform (BR-50-05). */
export function isPrintable(order) {
    if (!order || order.status === 'CANCELLED') return false;
    return (order.details ?? []).some((detail) => detail.status !== 'CANCELLED' && detail.status !== 'ABANDONED');
}

/** MSG70 after a refused service is cancelled; BR-41 when no service is left to perform. */
export function cancelledMessage(result) {
    const message = `Đã hủy dịch vụ ${result?.serviceName}. Khoản thu đã được tính lại.`;
    return result?.visitStatus === 'WAITING_CONCLUSION'
        ? `${message} Không còn dịch vụ cận lâm sàng nào phải làm, lượt khám chuyển sang chờ kết luận.`
        : message;
}

/** BR-25 rejections and MSG12 / MSG99; never the raw backend message. */
export function cancelErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'PARACLINICAL_SERVICE_NOT_CANCELLABLE') {
        return 'Dịch vụ đã được thanh toán hoặc kỹ thuật viên đã tiếp nhận, không thể hủy.';
    }
    if (code === 'REQUEST_VALIDATION_FAILED') return 'Lý do hủy từ 5 đến 500 ký tự.';
    return orderErrorMessage(error);
}

/**
 * I1-D47, BR-50-01: the room preselected for a service, the least busy Active room of its group. `null` when no
 * Active room performs it; the server then leaves the line without a room (UC-71 AF-02).
 */
export function defaultRoomId(service) {
    if (!service) return null;
    if (service.suggestedRoomId) return service.suggestedRoomId;
    return service.rooms?.[0]?.roomId ?? null;
}

/**
 * Room choice label: name, location and waiting count, so the Doctor can tell the patient where to go; a room with
 * nobody on duty in the current shift says so (it stays selectable).
 */
export function roomOptionLabel(room) {
    const location = room.location ? ` – ${room.location}` : '';
    const duty = room.onDuty === false ? ' · chưa có người trực' : '';
    return `${room.roomName}${location} (đang chờ: ${room.waitingCount ?? 0}${duty})`;
}

/** Request lines: the selected services; the backend assigns each room when the order is created. */
export function orderItems(selectedIds) {
    return selectedIds.map((serviceId) => ({ serviceId, roomId: null }));
}

/** MSG42 / MSG40 / MSG12 / MSG99; never the raw backend message. */
export function orderErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'PARACLINICAL_ROOM_NOT_ELIGIBLE') {
        const name = error.errorResponse.details?.roomName || 'Phòng thực hiện';
        return `${name} đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.`;
    }
    if (code === 'SERVICE_INACTIVE') {
        const name = error.errorResponse.details?.serviceName || 'dịch vụ đã chọn';
        // EF-02: MSG40; the panel also removes the service from the selection.
        return `Dịch vụ ${name} đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.`;
    }
    if (code === 'SERVICE_NOT_FOUND') return 'Dịch vụ đã chọn không còn hợp lệ. Vui lòng chọn dịch vụ khác.';
    if (code === 'VISIT_NOT_IN_EXAMINATION') return MSG.NOT_IN_EXAMINATION;
    if (code === 'MEDICAL_RECORD_CLOSED') return MSG.RECORD_CLOSED;
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    return MSG.SYSTEM_ERROR;
}
