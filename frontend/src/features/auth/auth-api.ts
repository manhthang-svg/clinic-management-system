import type { ApiClient } from '../../shared/api/api-client'

export type StaffRole =
  | 'ADMIN'
  | 'RECEPTIONIST'
  | 'DOCTOR'
  | 'TECHNICIAN'
  | 'CASHIER'

export interface AccountSummary {
  readonly id: string
  readonly username: string
  readonly fullName: string
  readonly role: StaffRole
}

/** Body of login and refresh responses; the refresh token travels only as an HttpOnly cookie. */
export interface SessionResponse {
  readonly accessToken: string
  readonly tokenType: 'Bearer'
  readonly expiresAt: string
  readonly passwordChangeRequired: boolean
  readonly account: AccountSummary
}

export interface AuthApi {
  login(username: string, password: string): Promise<SessionResponse>
  refresh(): Promise<SessionResponse>
}

export function createAuthApi(client: ApiClient): AuthApi {
  return {
    login: (username, password) =>
      client.request<SessionResponse>('/auth/login', {
        method: 'POST',
        body: { username, password },
      }),
    refresh: () =>
      client.request<SessionResponse>('/auth/refresh', { method: 'POST' }),
  }
}
