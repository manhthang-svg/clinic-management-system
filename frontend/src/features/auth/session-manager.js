import { ApiError } from '../../shared/api/api-error';
const REFRESH_LOCK_NAME = 'clinic-session-refresh';
const REFRESH_MARGIN_MS = 60_000;
/**
 * Web Locks serialize refreshes across tabs: the tabs share one refresh cookie, and a parallel
 * refresh would be treated by the backend as token reuse and end the session (docs/security.md).
 */
export const browserCrossTabLock = (name, callback) => {
    if (typeof navigator !== 'undefined' && navigator.locks) {
        return navigator.locks.request(name, callback);
    }
    return callback();
};
/**
 * Holds the access token in memory only and keeps the session alive by refreshing shortly
 * before the access token expires.
 */
export class SessionManager {
    state = { status: 'restoring' };
    accessToken = null;
    refreshTimer = null;
    inFlightRefresh = null;
    listeners = new Set();
    api;
    lock;
    now;
    setTimer;
    clearTimer;
    constructor(options) {
        this.api = options.api;
        this.lock = options.lock ?? browserCrossTabLock;
        this.now = options.now ?? Date.now;
        this.setTimer =
            options.setTimer ?? ((callback, delayMs) => window.setTimeout(callback, delayMs));
        this.clearTimer =
            options.clearTimer ?? ((handle) => window.clearTimeout(handle));
    }
    subscribe = (listener) => {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    };
    getState = () => this.state;
    getAccessToken = () => this.accessToken;
    /** Throws {@link ApiError} when the credentials are rejected or the API is unreachable. */
    async login(username, password) {
        this.accept(await this.api.login(username, password));
    }
    /** Restores a session from the refresh cookie after a page load. */
    async restore() {
        if (!(await this.refresh())) {
            this.setState({ status: 'anonymous' });
        }
    }
    /**
     * Renews the access token. Concurrent calls in this tab share one request.
     *
     * @returns whether the session is still valid
     */
    refresh() {
        this.inFlightRefresh ??= this.lock(REFRESH_LOCK_NAME, () => this.api.refresh())
            .then((session) => {
            this.accept(session);
            return true;
        })
            .catch((error) => {
            if (error instanceof ApiError && error.status === 401) {
                this.clear();
                return false;
            }
            throw error;
        })
            .finally(() => {
            this.inFlightRefresh = null;
        });
        return this.inFlightRefresh;
    }
    /**
     * UC-2 Logout: ends this session on the server, then forgets it locally even if the server
     * could not be reached, so the user is never stuck in a session they asked to leave.
     */
    async logout() {
        try {
            await this.api.logout();
        }
        catch {
            // The refresh cookie expires on its own; local state is cleared below regardless.
        }
        finally {
            this.clear();
        }
    }
    /** Forgets the session locally. */
    clear() {
        this.cancelTimer();
        this.accessToken = null;
        this.setState({ status: 'anonymous' });
    }
    accept(session) {
        this.accessToken = session.accessToken;
        this.setState({
            status: 'authenticated',
            account: session.account,
            passwordChangeRequired: session.passwordChangeRequired,
        });
        this.scheduleRefresh(Date.parse(session.expiresAt));
    }
    scheduleRefresh(expiresAtMs) {
        this.cancelTimer();
        const delay = Math.max(expiresAtMs - this.now() - REFRESH_MARGIN_MS, 0);
        this.refreshTimer = this.setTimer(() => {
            this.refresh().catch(() => {
                // Network failure: keep the current token; the next API call will surface the problem.
            });
        }, delay);
    }
    cancelTimer() {
        if (this.refreshTimer !== null) {
            this.clearTimer(this.refreshTimer);
            this.refreshTimer = null;
        }
    }
    setState(state) {
        this.state = state;
        this.listeners.forEach((listener) => listener());
    }
}
