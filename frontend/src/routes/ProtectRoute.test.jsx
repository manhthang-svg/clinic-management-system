import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';

import ProtectRoute from './ProtectRoute';
import { MSG13 } from '../pages/Forbidden';

function CurrentPath() {
    return <p data-testid="path">{useLocation().pathname}</p>;
}

function renderAt(path, session) {
    render(
        <MemoryRouter initialEntries={[path]}>
            <Routes>
                <Route path="/" element={<p>Trang chủ</p>} />
                <Route path="/login" element={<p>Đăng nhập</p>} />
                <Route element={<ProtectRoute session={session} requiredRole="ADMIN" />}>
                    <Route path="/admin/services" element={<p>Quản lý dịch vụ</p>} />
                </Route>
            </Routes>
            <CurrentPath />
        </MemoryRouter>,
    );
}

const signedIn = (role) => ({ status: 'authenticated', account: { role } });

describe('ProtectRoute', () => {
    it('shows the page for the required role', () => {
        renderAt('/admin/services', signedIn('ADMIN'));
        expect(screen.getByText('Quản lý dịch vụ')).toBeInTheDocument();
    });

    it('stays on the URL and shows MSG13 for another role', () => {
        renderAt('/admin/services', signedIn('DOCTOR'));

        expect(screen.getByRole('alert')).toHaveTextContent(MSG13);
        expect(screen.queryByText('Quản lý dịch vụ')).not.toBeInTheDocument();
        expect(screen.getByTestId('path')).toHaveTextContent('/admin/services');
    });

    it('goes home only when the user asks', async () => {
        const user = userEvent.setup();
        renderAt('/admin/services', signedIn('DOCTOR'));

        await user.click(screen.getByRole('link', { name: 'Về trang chủ' }));

        expect(screen.getByText('Trang chủ')).toBeInTheDocument();
    });

    it('sends an anonymous user to the login screen', () => {
        renderAt('/admin/services', { status: 'anonymous', account: null });
        expect(screen.getByText('Đăng nhập')).toBeInTheDocument();
    });
});
