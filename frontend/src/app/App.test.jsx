import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../shared/api/api-error';
import { SessionManager } from '../features/auth/session-manager';
import { App } from './App';
function manager(api) {
    return new SessionManager({
        api: {
            login: vi.fn(),
            refresh: vi.fn(),
            logout: vi.fn().mockResolvedValue(undefined),
            ...api,
        },
        lock: (_name, callback) => callback(),
        setTimer: () => undefined,
        clearTimer: () => undefined,
    });
}
describe('App', () => {
    it('shows the login page when no session can be restored', async () => {
        const sessionManager = manager({
            refresh: vi.fn().mockRejectedValue(new ApiError('Unauthorized', { status: 401 })),
        });
        render(<App sessionManager={sessionManager}/>);
        expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    });
    it('restores an existing session and greets the user', async () => {
        const sessionManager = manager({
            refresh: vi.fn().mockResolvedValue({
                accessToken: 'access',
                tokenType: 'Bearer',
                expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
                passwordChangeRequired: true,
                account: { id: 'id', username: 'admin', fullName: 'Administrator', role: 'ADMIN' },
            }),
        });
        render(<App sessionManager={sessionManager}/>);
        expect(await screen.findByText('Xin chào Administrator (Quản trị viên)')).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent('mật khẩu tạm');
    });
    it('forces a password change first and drops the restriction afterwards', async () => {
        const refresh = vi
            .fn()
            .mockResolvedValueOnce(sessionResponse(true))
            .mockResolvedValueOnce(sessionResponse(false));
        const changePassword = vi.fn().mockResolvedValue(undefined);
        render(<App sessionManager={manager({ refresh })} accountApi={{ changePassword }}/>);
        await userEvent.type(await screen.findByLabelText('Mật khẩu hiện tại'), 'temporary-pass');
        await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'brand-new-password');
        await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'brand-new-password');
        await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));
        expect(await screen.findByText('Đã đổi mật khẩu.')).toBeInTheDocument();
        expect(changePassword).toHaveBeenCalledWith('temporary-pass', 'brand-new-password');
        expect(refresh).toHaveBeenCalledTimes(2);
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Mật khẩu hiện tại')).not.toBeInTheDocument();
    });
    it('returns to the login page after logout', async () => {
        const logout = vi.fn().mockResolvedValue(undefined);
        const sessionManager = manager({
            refresh: vi.fn().mockResolvedValue({
                accessToken: 'access',
                tokenType: 'Bearer',
                expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
                passwordChangeRequired: false,
                account: { id: 'id', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
            }),
            logout,
        });
        render(<App sessionManager={sessionManager}/>);
        await userEvent.click(await screen.findByRole('button', { name: 'Đăng xuất' }));
        expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
        expect(logout).toHaveBeenCalledTimes(1);
    });
});
function sessionResponse(passwordChangeRequired) {
    return {
        accessToken: 'access',
        tokenType: 'Bearer',
        expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
        passwordChangeRequired,
        account: { id: 'id', username: 'admin', fullName: 'Administrator', role: 'ADMIN' },
    };
}
