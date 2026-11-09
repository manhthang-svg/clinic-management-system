/** Medicine catalog (UC-81 to UC-84). Paths are relative to the API base URL. */
export function createMedicineApi(client) {
    return {
        list: (params = {}, signal) => {
            const query = new URLSearchParams();
            Object.entries(params).forEach(([key, value]) => {
                if (value !== undefined && value !== null && value !== '') query.set(key, value);
            });
            const suffix = query.size ? `?${query}` : '';
            return client.request(`/medicines${suffix}`, { signal });
        },
        formOptions: (signal) => client.request('/medicines/form-options', { signal }),
        detail: (id, signal) => client.request(`/medicines/${id}`, { signal }),
        create: (body) => client.request('/medicines', { method: 'POST', body }),
        update: (id, body) => client.request(`/medicines/${id}`, { method: 'PUT', body }),
        updateStatus: (id, body) => client.request(`/medicines/${id}/status`, { method: 'PATCH', body }),
    };
}

export default createMedicineApi;
