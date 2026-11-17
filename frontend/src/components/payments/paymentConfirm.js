// UC-71: thong bao sau khi thu ngan xac nhan thu tien

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
    AWAITS_CONCLUSION: 'Lượt khám BHYT chỉ thanh toán sau khi bác sĩ xác nhận kết luận.',
};

// nhan thay cho nut thu tien khi phieu BHYT con cho bac si ket luan
export const AWAITING_CONCLUSION_LABEL = 'Chờ kết luận (BHYT)';

// doi loi xac nhan thu thanh thong bao tieng Viet
export function confirmErrorMessage(error) {
    const code = error?.errorResponse?.code;
    if (code === 'PAYMENT_NOT_PENDING') {
        const details = error.errorResponse.details || {};
        const status = STATUS_LABEL[details.status] || 'xử lý';
        return `Phiếu thu ${details.receiptNumber} đã được ${status}.`;
    }
    if (code === 'PAYMENT_NOT_FOUND') return MSG.NOT_FOUND;
    if (code === 'PAYMENT_AWAITS_CONCLUSION') return MSG.AWAITS_CONCLUSION;
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    return MSG.SYSTEM_ERROR;
}

export const PRINT_MSG = {
    NOT_PAID: 'Chỉ in được phiếu thu sau khi đã xác nhận thanh toán.',
};

// UC-72: doi loi in phieu thu thanh thong bao
export function printErrorMessage(error) {
    if (error?.errorResponse?.code === 'PAYMENT_NOT_PAID') return PRINT_MSG.NOT_PAID;
    return confirmErrorMessage(error);
}

// thong bao thu xong: phi kham kem so thu tu; phi CLS kem phong lam tung dich vu
export function confirmedNotice(payment) {
    const lines = [`Đã xác nhận thanh toán phiếu ${payment?.receiptNumber || payment?.paymentCode || ''}.`.trim()];
    if (payment?.type !== 'PARACLINICAL_FEE') {
        if (payment?.type === 'EXAMINATION_FEE' && payment?.visitQueueNumber != null) {
            const room = payment.roomName ? ` – ${payment.roomName}` : '';
            lines.push(`Số thứ tự ${payment.visitQueueNumber}${room}.`);
        }
        return { message: lines.join(' '), rooms: [], unrouted: [] };
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

// ban 1 dong cua thong bao tren
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

// luot kham can in phieu so thu tu sau khi thu phi kham; khong co thi null
export function queueTicketVisitId(payment) {
    const type = payment?.type ?? payment?.paymentType;
    return type === 'EXAMINATION_FEE' && payment?.visitId ? payment.visitId : null;
}
