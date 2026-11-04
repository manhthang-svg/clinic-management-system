import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from './apis/apiError';
import { SessionManager } from './services/sessionManager';
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
        render(<MemoryRouter><App sessionManager={sessionManager}/></MemoryRouter>);
        expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    });
    it('restores an existing doctor session and opens the waiting list', async () => {
        const sessionManager = manager({
            refresh: vi.fn().mockResolvedValue({
                accessToken: 'access-1',
                tokenType: 'Bearer',
                expiresAt: '2026-10-14T08:15:00Z',
                passwordChangeRequired: false,
                account: { id: 'id-1', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
            }),
        });
        render(<MemoryRouter><App sessionManager={sessionManager}/></MemoryRouter>);
        expect(await screen.findByRole('heading', { name: 'Quản lý khám bệnh' })).toBeInTheDocument();
    });
    it('lets a non-admin view and update their own profile', async () => {
        const sessionManager = manager({
            refresh: vi.fn().mockResolvedValue({
                accessToken: 'access-1',
                tokenType: 'Bearer',
                expiresAt: '2026-10-14T08:15:00Z',
                passwordChangeRequired: false,
                account: { id: 'id-1', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
            }),
        });
        const profile = {
            username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR', status: 'ACTIVE',
            phoneNumber: '0901234567', phone: '0901234567', email: 'an@clinic.test',
        };
        const accountApi = {
            profile: vi.fn().mockResolvedValue(profile),
            updateProfile: vi.fn().mockResolvedValue({ ...profile, phone: '0912345678', phoneNumber: '0912345678' }),
        };
        render(<MemoryRouter><App sessionManager={sessionManager} accountApi={accountApi}/></MemoryRouter>);
        await userEvent.click(await screen.findByRole('button', { name: 'Hồ sơ cá nhân' }));
        expect(await screen.findByRole('heading', { name: 'Hồ sơ cá nhân' })).toBeInTheDocument();
        const phone = screen.getByLabelText(/Số điện thoại/);
        await userEvent.clear(phone);
        await userEvent.type(phone, '0912345678');
        await userEvent.click(screen.getByRole('button', { name: /Lưu thay đổi/ }));
        expect(accountApi.updateProfile).toHaveBeenCalledWith('0912345678', 'an@clinic.test');
    });
    it('forces a password change first and drops the restriction afterwards', async () => {
        const sessionManager = manager({
            refresh: vi
                .fn()
                .mockResolvedValueOnce({
                accessToken: 'temp-token',
                tokenType: 'Bearer',
                expiresAt: '2026-10-14T08:15:00Z',
                passwordChangeRequired: true,
                account: { id: 'id-1', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
            })
                .mockResolvedValueOnce({
                accessToken: 'fresh-token',
                tokenType: 'Bearer',
                expiresAt: '2026-10-14T08:15:00Z',
                passwordChangeRequired: false,
                account: { id: 'id-1', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
            }),
        });
        const accountApi = {
            changePassword: vi.fn().mockResolvedValue(undefined),
        };
        render(<MemoryRouter><App sessionManager={sessionManager} accountApi={accountApi}/></MemoryRouter>);
        expect(await screen.findByRole('alert')).toHaveTextContent('Bạn phải đổi mật khẩu trước khi tiếp tục.');
        expect(screen.getByRole('heading', { name: 'Đổi mật khẩu' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Đổi mật khẩu' })).toBeInTheDocument();
        await userEvent.type(screen.getByLabelText('Mật khẩu hiện tại'), 'temporary-pass');
        await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'brand-new-password');
        await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'brand-new-password');
        await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));
        expect(accountApi.changePassword).toHaveBeenCalledWith('temporary-pass', 'brand-new-password');
        expect(await screen.findByRole('status')).toHaveTextContent('Đổi mật khẩu thành công.');
        expect(screen.queryByRole('heading', { name: 'Đổi mật khẩu' })).not.toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
    it('keeps the admin layout while changing the password', async () => {
        const sessionManager = manager({
            refresh: vi.fn().mockResolvedValue({
                accessToken: 'temp-token',
                tokenType: 'Bearer',
                expiresAt: '2026-10-14T08:15:00Z',
                passwordChangeRequired: true,
                account: { id: 'admin-1', username: 'admin', fullName: 'Administrator', role: 'ADMIN' },
            }),
        });
        const accountApi = { changePassword: vi.fn() };

        render(<MemoryRouter><App sessionManager={sessionManager} accountApi={accountApi}/></MemoryRouter>);

        expect(await screen.findByRole('heading', { name: 'Đổi mật khẩu' })).toBeInTheDocument();
        expect(screen.getByText('METPH')).toBeInTheDocument();
        expect(screen.queryByText('Clinic Management System')).not.toBeInTheDocument();
    });
    it('opens service management from the admin services route', async () => {
        const sessionManager = manager({
            refresh: vi.fn().mockResolvedValue({
                accessToken: 'access-token',
                tokenType: 'Bearer',
                expiresAt: '2026-10-14T08:15:00Z',
                passwordChangeRequired: false,
                account: { id: 'admin-1', username: 'admin', fullName: 'Administrator', role: 'ADMIN' },
            }),
        });

        render(
          <MemoryRouter initialEntries={['/admin/services']}>
            <App sessionManager={sessionManager}/>
          </MemoryRouter>,
        );

        expect(await screen.findByRole('heading', { name: 'Quản lý dịch vụ và giá' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Dịch vụ' })).toHaveAttribute('aria-current', 'page');
        expect(screen.queryByRole('heading', { name: 'Quản lý tài khoản' })).not.toBeInTheDocument();
    });
    it('ends the session and returns to the login page on logout', async () => {
        const sessionManager = manager({
            refresh: vi.fn().mockResolvedValue({
                accessToken: 'access-1',
                tokenType: 'Bearer',
                expiresAt: '2026-10-14T08:15:00Z',
                passwordChangeRequired: false,
                account: { id: 'id-1', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
            }),
        });
        render(<MemoryRouter><App sessionManager={sessionManager}/></MemoryRouter>);
        const logout = await screen.findByRole('button', { name: 'Đăng xuất' });
        await userEvent.click(logout);
        expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    });
});
