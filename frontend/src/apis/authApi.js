export function createAuthApi(client) {
    return {
        login: (username, password) => client.request('/auth/login', {
            method: 'POST',
            body: { username, password },
        }),
        refresh: () => client.request('/auth/refresh', { method: 'POST' }),
        logout: () => client.request('/auth/logout', { method: 'POST' }),
    };
}

export default createAuthApi;
