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
        detail: (id, signal) => client.request(`/services/${id}`, { signal }),
        create: (body) => client.request('/services', { method: 'POST', body }),
        update: (id, body) => client.request(`/services/${id}`, { method: 'PUT', body }),
        updatePrices: (id, body) => client.request(`/services/${id}/prices`, { method: 'PUT', body }),
        checkDeactivation: (id) => client.request(`/services/${id}/deactivation-check`),
        checkActivation: (id) => client.request(`/services/${id}/activation-check`),
        updateStatus: (id, body) => client.request(`/services/${id}/status`, { method: 'PATCH', body }),
        listReferenceRanges: (parameterId, signal) =>
            client.request(`/lab-parameters/${parameterId}/reference-ranges`, { signal }),
        createReferenceRange: (parameterId, body) =>
            client.request(`/lab-parameters/${parameterId}/reference-ranges`, { method: 'POST', body }),
        updateReferenceRange: (parameterId, rangeId, body) =>
            client.request(`/lab-parameters/${parameterId}/reference-ranges/${rangeId}`, { method: 'PUT', body }),
    };
}

export default createServiceApi;
