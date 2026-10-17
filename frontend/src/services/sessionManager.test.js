import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../apis/apiError';
import { SessionManager } from './sessionManager';

const NOW = Date.parse('2026-10-14T08:00:00Z');

function session(overrides = {}) {
    return {
        accessToken: 'access-1',
        tokenType: 'Bearer',
        expiresAt: '2026-10-14T08:15:00Z',
        passwordChangeRequired: false,
        account: { id: 'id-1', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
        ...overrides,
    };
}

function setup(api, lock) {
    const timers = [];
    const manager = new SessionManager({
        api: {
            login: vi.fn(),
            refresh: vi.fn(),
            logout: vi.fn().mockResolvedValue(undefined),
            ...api,
        },
        lock: lock ?? ((_name, callback) => callback()),
        now: () => NOW,
        setTimer: (callback, delayMs) => timers.push({ callback, delayMs }),
        clearTimer: () => undefined,
    });
    return { manager, timers };
}

describe('SessionManager', () => {
    it('starts restoring then adopts the active session', async () => {
        const { manager } = setup({ refresh: vi.fn().mockResolvedValue(session()) });
        expect(manager.getState()).toEqual({ status: 'restoring' });
        await manager.restore();
        expect(manager.getState()).toEqual({
            status: 'authenticated',
            account: { id: 'id-1', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
            passwordChangeRequired: false,
        });
        expect(manager.getAccessToken()).toBe('access-1');
    });
    it('switches to anonymous when the refresh cookie is gone', async () => {
        const { manager } = setup({
            refresh: vi.fn().mockRejectedValue(new ApiError('Unauthorized', { status: 401 })),
        });
        await manager.restore();
        expect(manager.getState()).toEqual({ status: 'anonymous' });
        expect(manager.getAccessToken()).toBeNull();
    });
    it('sets the authenticated state after a successful login', async () => {
        const { manager } = setup({ login: vi.fn().mockResolvedValue(session()) });
        await manager.login('dr.an', 'secret-password');
        expect(manager.getState().status).toBe('authenticated');
        expect(manager.getAccessToken()).toBe('access-1');
    });
    it('forgets the access token on logout and informs the listeners', async () => {
        const { manager } = setup({
            login: vi.fn().mockResolvedValue(session()),
            logout: vi.fn().mockResolvedValue(undefined),
        });
        await manager.login('dr.an', 'secret-password');
        const listener = vi.fn();
        manager.subscribe(listener);
        await manager.logout();
        expect(manager.getState()).toEqual({ status: 'anonymous' });
        expect(manager.getAccessToken()).toBeNull();
        expect(listener).toHaveBeenCalled();
    });
    it('refreshes the access token before it expires', async () => {
        const { manager, timers } = setup({
            login: vi.fn().mockResolvedValue(session()),
            refresh: vi
                .fn()
                .mockResolvedValue(session({ accessToken: 'access-2', expiresAt: '2026-10-14T08:30:00Z' })),
        });
        await manager.login('dr.an', 'secret-password');
        expect(timers).toHaveLength(1);
        expect(timers[0].delayMs).toBe(14 * 60 * 1000);
        await timers[0].callback();
        expect(manager.getAccessToken()).toBe('access-2');
    });
    it('shares concurrent refresh calls within the tab', async () => {
        let resolveRefresh;
        const refreshPromise = new Promise((resolve) => {
            resolveRefresh = resolve;
        });
        const refresh = vi.fn().mockReturnValue(refreshPromise);
        const { manager } = setup({ refresh });
        const first = manager.refresh();
        const second = manager.refresh();
        expect(refresh).toHaveBeenCalledTimes(1);
        resolveRefresh(session());
        await Promise.all([first, second]);
    });
    it('acquires the cross-tab lock around the refresh request', async () => {
        const lock = vi.fn((_name, callback) => callback());
        const { manager } = setup({ refresh: vi.fn().mockResolvedValue(session()) }, lock);
        await manager.refresh();
        expect(lock).toHaveBeenCalledWith('clinic-session-refresh', expect.any(Function));
    });
    it('lets other exceptions through while dropping 401s silently', async () => {
        const { manager } = setup({
            refresh: vi.fn().mockRejectedValue(new Error('Network down')),
        });
        await expect(manager.refresh()).rejects.toThrow('Network down');
    });
});
