/** UC-71 Confirm Payment: messages shown after the Cashier confirms a receipt. */

const STATUS_LABEL = {
    PAID: 'thanh toán',
    FEE_EXEMPT: 'miễn phí',
    CANCELLED: 'hủy',
};

export const MSG = {
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
    NOT_FOUND: 'Không tìm thấy phiếu thu.',
};

/** MSG64 when the receipt is no longer Pending (EF-02), MSG12 / MSG99 otherwise (EF-03). */
export function confirmErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'PAYMENT_NOT_PENDING') {
        const details = error.errorResponse.details || {};
        const status = STATUS_LABEL[details.status] || 'xử lý';
        return `Phiếu thu ${details.receiptNumber} đã được ${status}.`;
    }
    if (code === 'PAYMENT_NOT_FOUND') return MSG.NOT_FOUND;
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    return MSG.SYSTEM_ERROR;
}

export const PRINT_MSG = {
    NOT_PAID: 'Chỉ in được phiếu thu sau khi đã xác nhận thanh toán.',
};

/** UC-72: MSG62 when the receipt is not Paid, MSG12 / MSG99 otherwise. */
export function printErrorMessage(error) {
    if (error?.errorResponse?.code === 'PAYMENT_NOT_PAID') return PRINT_MSG.NOT_PAID;
    return confirmErrorMessage(error);
}

/**
 * MSG30 plus, for a paraclinical fee, where each service is performed (BR-28) and which services have no
 * Active room yet (AF-02).
 */
export function confirmedNotice(payment) {
    const lines = [`Đã xác nhận thanh toán phiếu ${payment?.receiptNumber || payment?.paymentCode || ''}.`.trim()];
    if (payment?.type !== 'PARACLINICAL_FEE') {
        return { message: lines[0], rooms: [], unrouted: [] };
    }
    const items = payment.items || [];
    const rooms = items
        .filter((item) => item.roomName)
        .map((item) => ({
            service: item.serviceName || item.tenchidinh,
            room: item.roomName,
            location: item.roomLocation,
            queueNumber: item.queueNumber,
        }));
    const unrouted = items
        .filter((item) => !item.roomName)
        .map((item) => item.serviceName || item.tenchidinh);
    return { message: lines[0], rooms, unrouted };
}

/** One text line for places that only show a plain notice. */
export function confirmedNoticeText(payment) {
    const { message, rooms, unrouted } = confirmedNotice(payment);
    const parts = [message];
    rooms.forEach(({ service, room, location, queueNumber }) => {
        const where = location ? `${room} (${location})` : room;
        parts.push(`${service}: ${where}${queueNumber ? `, STT ${queueNumber}` : ''}.`);
    });
    if (unrouted.length) {
        parts.push(`Chưa có phòng đang hoạt động cho: ${unrouted.join(', ')}. Chỉ định vẫn ở trạng thái chờ.`);
    }
    return parts.join(' ');
}
