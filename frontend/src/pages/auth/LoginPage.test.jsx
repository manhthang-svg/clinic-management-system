import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { LoginPage } from './LoginPage';

describe('LoginPage', () => {
    it('submits the trimmed username and the password', async () => {
        const onLogin = vi.fn().mockResolvedValue(undefined);
        render(<LoginPage onLogin={onLogin}/>);
        await userEvent.type(screen.getByLabelText('Tên đăng nhập'), '  dr.an ');
        await userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret-password');
        await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
        expect(onLogin).toHaveBeenCalledWith('dr.an', 'secret-password');
    });
    it('shows one generic message for rejected credentials and clears the password', async () => {
        const onLogin = vi
            .fn()
            .mockRejectedValue(new ApiError('Invalid username or password.', { status: 401 }));
        render(<LoginPage onLogin={onLogin}/>);
        await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'dr.an');
        await userEvent.type(screen.getByLabelText('Mật khẩu'), 'wrong-password');
        await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Tên đăng nhập hoặc mật khẩu không chính xác. Vui lòng kiểm tra lại.',
        );
        expect(screen.getByLabelText('Mật khẩu')).toHaveValue('');
    });
    it('shows MSG10 when the account is locked', async () => {
        const onLogin = vi.fn().mockRejectedValue(new ApiError('The account is locked.', {
            status: 401,
            errorResponse: { code: 'ACCOUNT_LOCKED', message: 'The account is locked.' },
        }));
        render(<LoginPage onLogin={onLogin}/>);
        await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'locked.user');
        await userEvent.type(screen.getByLabelText('Mật khẩu'), 'correct-password');
        await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.',
        );
        expect(screen.getByLabelText('Mật khẩu')).toHaveValue('');
    });
    it('shows MSG11 when password sign-in is temporarily blocked', async () => {
        const onLogin = vi.fn().mockRejectedValue(new ApiError('Password sign-in is temporarily blocked.', {
            status: 401,
            errorResponse: {
                code: 'ACCOUNT_TEMPORARILY_BLOCKED',
                message: 'Password sign-in is temporarily blocked.',
            },
        }));
        render(<LoginPage onLogin={onLogin}/>);
        await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'blocked.user');
        await userEvent.type(screen.getByLabelText('Mật khẩu'), 'wrong-password');
        await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.',
        );
        expect(screen.getByLabelText('Mật khẩu')).toHaveValue('');
    });
    it('maps unexpected errors to MSG80 without exposing raw details', async () => {
        const onLogin = vi.fn().mockRejectedValue(new Error('Network down: ECONNREFUSED'));
        render(<LoginPage onLogin={onLogin}/>);
        await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'dr.an');
        await userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret-password');
        await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
        );
        expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();
    });
    it('shows required messages after empty fields are touched and keeps submit disabled', async () => {
        const onLogin = vi.fn();
        render(<LoginPage onLogin={onLogin}/>);
        const submit = screen.getByRole('button', { name: 'Đăng nhập' });
        expect(submit).toBeDisabled();
        const username = screen.getByLabelText('Tên đăng nhập');
        const password = screen.getByLabelText('Mật khẩu');

        await userEvent.click(username);
        await userEvent.click(password);
        await userEvent.click(username);

        expect(screen.getByText('Tên đăng nhập là bắt buộc.')).toBeInTheDocument();
        expect(screen.getByText('Mật khẩu là bắt buộc.')).toBeInTheDocument();
        expect(username).toHaveAttribute('aria-invalid', 'true');
        expect(password).toHaveAttribute('aria-invalid', 'true');
        expect(onLogin).not.toHaveBeenCalled();
    });
    it('treats a whitespace-only password as empty', async () => {
        const onLogin = vi.fn();
        render(<LoginPage onLogin={onLogin}/>);
        await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'dr.an');
        const password = screen.getByLabelText('Mật khẩu');
        await userEvent.type(password, '   ');
        await userEvent.tab();

        expect(screen.getByText('Mật khẩu là bắt buộc.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeDisabled();
        expect(onLogin).not.toHaveBeenCalled();
    });
});
