export function createAccountApi(client) {
    return {
        changePassword: (currentPassword, newPassword) => client.request('/me/commands/change-password', {
            method: 'POST',
            body: { currentPassword, newPassword },
        }),
    };
}
