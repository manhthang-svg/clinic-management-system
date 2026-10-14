import { useSyncExternalStore } from 'react'

import { ApiClient } from '../../shared/api/api-client'
import { environment } from '../../shared/config/environment'
import { createAuthApi } from './auth-api'
import { SessionManager, type SessionState } from './session-manager'

// Auth endpoints use the refresh cookie, never the bearer token.
const authClient = new ApiClient({ baseUrl: environment.apiBaseUrl })

export const sessionManager = new SessionManager({ api: createAuthApi(authClient) })

/** API client for authenticated endpoints. */
export const apiClient = new ApiClient({
  baseUrl: environment.apiBaseUrl,
  accessTokenProvider: sessionManager.getAccessToken,
})

export function useSession(manager: SessionManager = sessionManager): SessionState {
  return useSyncExternalStore(manager.subscribe, manager.getState)
}
