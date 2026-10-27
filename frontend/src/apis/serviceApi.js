export function createServiceApi(client) {
    return {
        list: (params = {}, signal) => {
            const query = new URLSearchParams();
            Object.entries(params).forEach(([key, value]) => {
                if (value !== undefined && value !== null && value !== '') query.set(key, value);
            });
            const suffix = query.size ? `?${query}` : '';
            return client.request(`/services${suffix}`, { signal });
        },
        formOptions: (signal) => client.request('/services/form-options', { signal }),
        create: (body) => client.request('/services', { method: 'POST', body }),
    };
}

export default createServiceApi;
