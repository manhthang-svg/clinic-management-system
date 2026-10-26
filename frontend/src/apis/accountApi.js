export function createAccountApi(client) {
    return {
        list: (params = {}, signal) => {
            const query = new URLSearchParams();
            Object.entries(params).forEach(([key, value]) => {
                if (value !== undefined && value !== null && value !== '') query.set(key, value);
            });
            const suffix = query.size ? `?${query}` : '';
            return client.request(`/accounts${suffix}`, { signal });
        },
        detail: (id, signal) => client.request(`/accounts/${id}`, { signal }),
        departments: (signal) => client.request('/accounts/departments', { signal }),
        create: (body) => client.request('/accounts', { method: 'POST', body }),
        update: (id, body) => client.request(`/accounts/${id}`, { method: 'PUT', body }),
        checkRoleChange: (id, body) => client.request(`/accounts/${id}/commands/check-role-change`, { method: 'POST', body }),
        changeRole: (id, body) => client.request(`/accounts/${id}/commands/change-role`, { method: 'POST', body }),
        lock: (id, body) => client.request(`/accounts/${id}/commands/lock`, { method: 'POST', body }),
        unlock: (id, body) => client.request(`/accounts/${id}/commands/unlock`, { method: 'POST', body }),
        resetPassword: (id, body) => client.request(`/accounts/${id}/commands/reset-password`, { method: 'POST', body }),
        activities: (id, limit = 10, signal) => client.request(`/accounts/${id}/activities?limit=${limit}`, { signal }),
        changeHistory: (id, page = 0, size = 10, signal) =>
            client.request(`/accounts/${id}/change-history?page=${page}&size=${size}`, { signal }),
        workInProgress: (id, signal) => client.request(`/accounts/${id}/work-in-progress`, { signal }),
        profile: (signal) => client.request('/me/profile', { signal }),
        updateProfile: (phoneNumber, email) => client.request('/me/profile', {
            method: 'PUT',
            body: { phoneNumber, email: email || null },
        }),
        changePassword: (currentPassword, newPassword) => client.request('/me/commands/change-password', {
            method: 'POST',
            body: { currentPassword, newPassword },
        }),
    };
}

export default createAccountApi;
