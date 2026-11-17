import { apiClient } from '../services/session';

export const paymentApi = {
    // UC-69, UC-70: danh sach / tim kiem phieu thu
    list(params = {}, signal) {
        const query = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '' && value !== 'ALL') {
                query.set(key, value);
            }
        });
        const suffix = query.size ? `?${query.toString()}` : '';
        return apiClient.request(`/payments${suffix}`, { signal });
    },

    // UC-73: lich su thanh toan
    history(params = {}, signal) {
        const query = new URLSearchParams(params);
        return apiClient.request(`/payments/history?${query.toString()}`, { signal });
    },

    // chi tiet 1 phieu thu
    getById(id, signal) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}`, { signal });
    },

    // UC-72: lay noi dung in phieu thu (ghi log in)
    prepareReceiptPrint(id) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/commands/prepare-receipt-print`, {
            method: 'POST',
        });
    },

    // UC-71: xac nhan thu tien tai quay
    confirm(id, data) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/confirm`, {
            method: 'POST',
            body: data,
        });
    },

    // UC-71: tao ma QR PayOS
    createPayosQr(id) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/payos-qr`, {
            method: 'POST',
        });
    },

    // hoi PayOS da thanh toan chua
    syncPayos(id) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/sync-payos`, {
            method: 'POST',
        });
    },

    // xac nhan ket qua khi quay ve tu trang PayOS
    confirmPayosResult(data) {
        return apiClient.request('/payments/confirm-payos-result', {
            method: 'POST',
            body: data,
        });
    },

    // tim phieu theo ma don PayOS
    getByOrderCode(orderCode, signal) {
        return apiClient.request(`/payments/by-order-code/${encodeURIComponent(orderCode)}`, { signal });
    },
};

export default paymentApi;
