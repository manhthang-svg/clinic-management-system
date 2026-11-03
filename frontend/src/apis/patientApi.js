import { apiClient } from '../services/session';

export const patientApi = {
    // Lấy danh sách bệnh nhân, hỗ trợ tìm kiếm và phân trang
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

    // Lấy chi tiết bệnh nhân theo ID
    detail(id, signal) {
        return apiClient.request(`/patients/${encodeURIComponent(id)}`, {
            signal,
        });
    },

    // UC-36: thông tin hành chính, dị ứng, lịch sử khám (backend ghi audit lượt xem)
    overview(id, signal) {
        return apiClient.request(`/patients/${encodeURIComponent(id)}/overview`, { signal });
    },

    // UC-37 AF-02: hồ sơ có cùng họ tên và ngày sinh
    possibleDuplicates(hoten, ngaysinh, signal) {
        const query = new URLSearchParams({ hoten, ngaysinh });
        return apiClient.request(`/patients/possible-duplicates?${query}`, { signal });
    },

    // Tạo hồ sơ bệnh nhân
    create(data) {
        return apiClient.request('/patients', {
            method: 'POST',
            body: data,
        });
    },

    // Cập nhật thông tin bệnh nhân
    update(id, data) {
        return apiClient.request(`/patients/${encodeURIComponent(id)}`, {
            method: 'PUT',
            body: data,
        });
    },
};

export default patientApi;