import { apiClient } from '../services/session';

export const visitApi = {
    create(data) {
        return apiClient.request('/visits', {
            method: 'POST',
            body: data,
        });
    },

    getActiveExaminationRooms(signal) {
        return apiClient.request('/rooms/active-examination', { signal });
    },

    orderParaclinical(data) {
        return apiClient.request('/paraclinical-orders', {
            method: 'POST',
            body: data,
        });
    },

    getParaclinicalServices(signal) {
        return apiClient.request('/paraclinical-orders/services', { signal });
    },

    getQueue(params = {}, signal) {
        const query = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '' && value !== 'ALL') {
                query.set(key, value);
            }
        });
        const suffix = query.size ? `?${query.toString()}` : '';
        return apiClient.request(`/visits/queue${suffix}`, { signal });
    },

    startExamination(visitId) {
        return apiClient.request(`/visits/${encodeURIComponent(visitId)}/start-examination`, {
            method: 'POST',
        });
    },
};

export default visitApi;
