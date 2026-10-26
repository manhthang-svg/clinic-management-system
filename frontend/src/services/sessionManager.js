import { ApiError } from '../apis/apiError';

const REFRESH_LOCK_NAME = 'clinic-session-refresh';
const REFRESH_MARGIN_MS = 60_000;

export const browserCrossTabLock = (name, callback) => {
    if (typeof navigator !== 'undefined' && navigator.locks) {
        return navigator.locks.request(name, callback);
    }
    return callback();
};

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
    async login(username, password) {
        const session = await this.api.login(username, password);
        this.accept(session);
        return session;
    }
    async restore() {
        if (!(await this.refresh())) {
            this.setState({ status: 'anonymous' });
        }
    }
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

export default SessionManager;
