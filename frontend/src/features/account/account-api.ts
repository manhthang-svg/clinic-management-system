import type { ApiClient } from '../../shared/api/api-client'

export interface AccountApi {
  changePassword(currentPassword: string, newPassword: string): Promise<void>
}

export function createAccountApi(client: ApiClient): AccountApi {
  return {
    changePassword: (currentPassword, newPassword) =>
      client.request<void>('/me/commands/change-password', {
        method: 'POST',
        body: { currentPassword, newPassword },
      }),
  }
}
