import { useSyncExternalStore } from 'react';
import { ApiClient } from '../apis/apiClient';
import { environment } from '../utils/env';
import { createAuthApi } from '../apis/authApi';
import { SessionManager } from './sessionManager';

// Auth endpoints use the refresh cookie, never the bearer token.
const authClient = new ApiClient({ baseUrl: environment.apiBaseUrl });
export const sessionManager = new SessionManager({ api: createAuthApi(authClient) });

/** API client for authenticated endpoints. */
export const apiClient = new ApiClient({
    baseUrl: environment.apiBaseUrl,
    accessTokenProvider: sessionManager.getAccessToken,
    onUnauthorized: () => sessionManager.clear(),
});

export function useSession(manager = sessionManager) {
    return useSyncExternalStore(manager.subscribe, manager.getState);
}
