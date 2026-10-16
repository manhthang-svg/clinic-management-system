import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/api-error';
import { SessionManager } from './session-manager';
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
    it('keeps the access token in memory after login', async () => {
        const { manager } = setup({ login: vi.fn().mockResolvedValue(session()) });
        await manager.login('dr.an', 'secret-password');
        expect(manager.getAccessToken()).toBe('access-1');
        expect(manager.getState()).toEqual({
            status: 'authenticated',
            account: session().account,
            passwordChangeRequired: false,
        });
    });
    it('schedules a refresh one minute before the access token expires', async () => {
        const refresh = vi.fn().mockResolvedValue(session({ accessToken: 'access-2' }));
        const { manager, timers } = setup({
            login: vi.fn().mockResolvedValue(session()),
            refresh,
        });
        await manager.login('dr.an', 'secret-password');
        expect(timers[0]?.delayMs).toBe(14 * 60_000);
        timers[0]?.callback();
        await vi.waitFor(() => expect(manager.getAccessToken()).toBe('access-2'));
    });
    it('shares one refresh request between concurrent callers', async () => {
        let resolveRefresh = () => undefined;
        const refresh = vi.fn(() => new Promise((resolve) => (resolveRefresh = resolve)));
        const { manager } = setup({ refresh });
        const first = manager.refresh();
        const second = manager.refresh();
        resolveRefresh(session());
        await expect(Promise.all([first, second])).resolves.toEqual([true, true]);
        expect(refresh).toHaveBeenCalledTimes(1);
    });
    it('runs refresh inside the cross-tab lock', async () => {
        const lockNames = [];
        const lock = (name, callback) => {
            lockNames.push(name);
            return callback();
        };
        const { manager } = setup({ refresh: vi.fn().mockResolvedValue(session()) }, lock);
        await manager.refresh();
        expect(lockNames).toEqual(['clinic-session-refresh']);
    });
    it('becomes anonymous when the backend rejects the session', async () => {
        const { manager } = setup({
            refresh: vi.fn().mockRejectedValue(new ApiError('Unauthorized', { status: 401 })),
        });
        await manager.restore();
        expect(manager.getState()).toEqual({ status: 'anonymous' });
        expect(manager.getAccessToken()).toBeNull();
    });
    it('ends the session on the server and locally on logout', async () => {
        const logout = vi.fn().mockResolvedValue(undefined);
        const { manager } = setup({ login: vi.fn().mockResolvedValue(session()), logout });
        await manager.login('dr.an', 'secret-password');
        await manager.logout();
        expect(logout).toHaveBeenCalledTimes(1);
        expect(manager.getState()).toEqual({ status: 'anonymous' });
        expect(manager.getAccessToken()).toBeNull();
    });
    it('still forgets the session locally when the server cannot be reached', async () => {
        const { manager } = setup({
            login: vi.fn().mockResolvedValue(session()),
            logout: vi.fn().mockRejectedValue(new ApiError('Unable to reach the API.')),
        });
        await manager.login('dr.an', 'secret-password');
        await manager.logout();
        expect(manager.getState()).toEqual({ status: 'anonymous' });
    });
    it('reports that a password change is required', async () => {
        const { manager } = setup({
            login: vi.fn().mockResolvedValue(session({ passwordChangeRequired: true })),
        });
        await manager.login('new.admin', 'temporary-pass');
        expect(manager.getState()).toMatchObject({ passwordChangeRequired: true });
    });
});
