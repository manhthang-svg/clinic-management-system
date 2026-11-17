import { apiClient } from '../services/session';

export const patientApi = {
    // UC-35: lay danh sach benh nhan (tim kiem + phan trang)
    list(params = {}, signal) {
        const query = new URLSearchParams();

        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                query.set(key, value);
            }
        });

        const suffix = query.size ? `?${query}` : '';

        return apiClient.request(`/patients${suffix}`, { signal });
    },

    // lay ho so hanh chinh 1 benh nhan
    detail(id, signal) {
        return apiClient.request(`/patients/${encodeURIComponent(id)}`, {
            signal,
        });
    },

    // UC-36: ho so + di ung + lich su kham (backend ghi audit)
    overview(id, signal) {
        return apiClient.request(`/patients/${encodeURIComponent(id)}/overview`, { signal });
    },

    // UC-37: ho so co the trung (cung ho ten + ngay sinh)
    possibleDuplicates(hoten, ngaysinh, signal) {
        const query = new URLSearchParams({ hoten, ngaysinh });
        return apiClient.request(`/patients/possible-duplicates?${query}`, { signal });
    },

    // UC-37: tao ho so benh nhan
    create(data) {
        return apiClient.request('/patients', {
            method: 'POST',
            body: data,
        });
    },

    // UC-38: cap nhat thong tin benh nhan
    update(id, data) {
        return apiClient.request(`/patients/${encodeURIComponent(id)}`, {
            method: 'PUT',
            body: data,
        });
    },
};

export default patientApi;