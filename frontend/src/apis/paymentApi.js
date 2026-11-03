import { apiClient } from '../services/session';

export const paymentApi = {
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

    history(params = {}, signal) {
        const query = new URLSearchParams(params);
        return apiClient.request(`/payments/history?${query.toString()}`, { signal });
    },

    getById(id, signal) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}`, { signal });
    },

    prepareReceiptPrint(id) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/commands/prepare-receipt-print`, {
            method: 'POST',
        });
    },

    confirm(id, data) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/confirm`, {
            method: 'POST',
            body: data,
        });
    },

    createPayosQr(id) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/payos-qr`, {
            method: 'POST',
        });
    },

    syncPayos(id) {
        return apiClient.request(`/payments/${encodeURIComponent(id)}/sync-payos`, {
            method: 'POST',
        });
    },

    confirmPayosResult(data) {
        return apiClient.request('/payments/confirm-payos-result', {
            method: 'POST',
            body: data,
        });
    },

    getByOrderCode(orderCode, signal) {
        return apiClient.request(`/payments/by-order-code/${encodeURIComponent(orderCode)}`, { signal });
    },
};

export default paymentApi;
