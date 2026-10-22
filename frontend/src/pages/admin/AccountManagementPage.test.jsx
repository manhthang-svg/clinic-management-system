import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { AccountManagementPage } from './AccountManagementPage';

// BrowserRouter on jsdom's history, so the tests can read window.location.
const renderPage = (ui) => render(<BrowserRouter>{ui}</BrowserRouter>);

const admin = { username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };

describe('account sub-pages', () => {
  // jsdom keeps the URL between tests; every test starts as if just signed in at "/".
  beforeEach(() => window.history.replaceState(null, '', '/'));

  it('creates an account and shows the one-time password', async () => {
    const user = userEvent.setup();
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(screen.getByRole('heading', { name: 'Tạo tài khoản' })).toBeTruthy();
    expect(screen.queryByRole('option', { name: 'Quản trị viên' })).toBeNull();

    await user.click(screen.getByRole('button', { name: /Tạo tài khoản/ }));
    expect(screen.getByRole('alert').textContent).toContain('Vui lòng sửa');
    expect(screen.getByText('Vui lòng nhập tên đăng nhập.')).toBeTruthy();

    await user.type(screen.getByLabelText(/Họ và tên/), 'Nguyễn Văn Mới');
    await user.type(screen.getByLabelText(/Tên đăng nhập/), 'bs.tranvanan');
    await user.type(screen.getByLabelText(/Số điện thoại/), '0901234567');
    await user.type(screen.getByLabelText(/^Email/), 'bs.tranvanan@clinic.example');
    expect(screen.getByText('Tên đăng nhập đã tồn tại.')).toBeTruthy();
    expect(screen.getByText('Email đã được dùng cho tài khoản khác.')).toBeTruthy();

    await user.clear(screen.getByLabelText(/Tên đăng nhập/));
    await user.type(screen.getByLabelText(/Tên đăng nhập/), 'bs.nguyenvanmoi');
    await user.clear(screen.getByLabelText(/^Email/));
    await user.type(screen.getByLabelText(/^Email/), 'moi@clinic.example');
    await user.click(screen.getByRole('button', { name: /Tạo tài khoản/ }));

    expect(screen.getByText('Đã tạo tài khoản')).toBeTruthy();
    expect(screen.getByText(/^[A-Za-z0-9@#$%]{12}$/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Xem chi tiết' }));
    expect(screen.getByRole('heading', { name: 'Chi tiết tài khoản' })).toBeTruthy();
    expect(screen.getAllByText('bs.nguyenvanmoi').length).toBeGreaterThan(0);
  });

  it('edits an account from the list', async () => {
    const user = userEvent.setup();
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    await user.click(screen.getByRole('button', { name: 'Cập nhật bs.tranvanan' }));
    expect(screen.getByRole('heading', { name: 'Cập nhật thông tin tài khoản' })).toBeTruthy();
    expect(screen.getByLabelText('Tên đăng nhập').disabled).toBe(true);
    const save = screen.getByRole('button', { name: /Lưu thay đổi/ });
    expect(save.disabled).toBe(true);

    await user.clear(screen.getByLabelText(/Địa chỉ/));
    await user.type(screen.getByLabelText(/Địa chỉ/), '1 Đường Mới, Hà Nội');
    await user.click(save);
    // Opened from the list, so saving returns to the list.
    expect(await screen.findByText('Danh sách tài khoản')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Xem chi tiết bs.tranvanan' }));
    expect(screen.getByText('1 Đường Mới, Hà Nội')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: /Chỉnh sửa tài khoản/ }));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    await user.selectOptions(screen.getByLabelText('Vai trò mới'), 'TECHNICIAN');
    await user.click(screen.getByRole('button', { name: 'Đổi' }));
    expect(within(screen.getByRole('main')).getAllByText('Kỹ thuật viên').length).toBeGreaterThan(0);
    // UC-9: the confirmation names both roles and the removed room; a new Technician is reminded about the room.
    expect(confirm.mock.calls[0][0]).toContain('Bác sĩ → Kỹ thuật viên');
    expect(confirm.mock.calls[0][0]).toContain('PK-NOI-TQ');
    expect(alert.mock.calls[0][0]).toContain('phân công phòng');
  });

  it('asks before unlocking and clears the login block on reset', async () => {
    const user = userEvent.setup();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    renderPage(<AccountManagementPage currentAdmin={admin} />);

    await user.click(screen.getByRole('button', { name: 'Mở khóa bs.phamminhchau' }));
    expect(confirm).toHaveBeenCalledWith('Mở khóa tài khoản bs.phamminhchau?');
    expect(screen.getByRole('button', { name: 'Mở khóa bs.phamminhchau' })).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Xem chi tiết ktv.tranthicuc' }));
    expect(screen.queryByText('Không bị chặn')).toBeNull();
    confirm.mockReturnValue(true);
    await user.click(screen.getByRole('button', { name: /Đặt lại mật khẩu/ }));
    expect(screen.getByText('Không bị chặn')).toBeTruthy();
  });

  it('lowercases the username while typing', async () => {
    const user = userEvent.setup();
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    await user.type(screen.getByLabelText(/Tên đăng nhập/), 'BS.Moi');
    expect(screen.getByLabelText(/Tên đăng nhập/).value).toBe('bs.moi');
  });

  it('opens own profile with only phone and email editable', async () => {
    const user = userEvent.setup();
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    await user.click(screen.getByRole('button', { name: 'Tài khoản của tôi' }));
    await user.click(screen.getByRole('menuitem', { name: 'Hồ sơ cá nhân' }));
    expect(screen.getByRole('heading', { name: 'Hồ sơ cá nhân' })).toBeTruthy();
    expect(screen.getByLabelText('Họ và tên').disabled).toBe(true);
    expect(screen.getByLabelText(/Số điện thoại/).disabled).toBe(false);
    await user.clear(screen.getByLabelText(/Số điện thoại/));
    await user.type(screen.getByLabelText(/Số điện thoại/), '0912345678');
    await user.click(screen.getByRole('button', { name: /Lưu thay đổi/ }));
    expect(screen.getByText('Đã cập nhật thông tin liên hệ.')).toBeTruthy();
  });

  it('gives every account screen its own URL', async () => {
    const user = userEvent.setup();
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    expect(window.location.pathname).toBe('/admin/accounts');

    await user.click(screen.getByRole('button', { name: 'Xem chi tiết bs.tranvanan' }));
    expect(window.location.pathname).toBe('/admin/accounts/02');
    await user.click(screen.getByRole('button', { name: /Chỉnh sửa tài khoản/ }));
    expect(window.location.pathname).toBe('/admin/accounts/02/edit');
    await user.click(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('button', { name: 'Quản lý tài khoản' }));
    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(window.location.pathname).toBe('/admin/accounts/new');
    await user.click(screen.getByRole('button', { name: 'Tài khoản của tôi' }));
    await user.click(screen.getByRole('menuitem', { name: 'Hồ sơ cá nhân' }));
    expect(window.location.pathname).toBe('/admin/profile');
  });

  it('opens a screen straight from its URL', () => {
    window.history.replaceState(null, '', '/admin/accounts/03/edit');
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Cập nhật thông tin tài khoản' })).toBeTruthy();
    expect(screen.getByLabelText(/Họ và tên/).value).toBe('Lê Thị Bình');
  });

  it('shows a message for an unknown account', () => {
    window.history.replaceState(null, '', '/admin/accounts/khong-ton-tai');
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    expect(screen.getByRole('heading', { name: 'Không tìm thấy tài khoản' })).toBeTruthy();
  });

  it('goes back to the previous page, and to the list from breadcrumb or sidebar', async () => {
    const user = userEvent.setup();
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    const pageTitle = (name) => screen.findByRole('heading', { level: 1, name });

    // list → edit → Hủy returns to the list
    await user.click(screen.getByRole('button', { name: 'Cập nhật bs.tranvanan' }));
    await user.click(screen.getByRole('button', { name: 'Hủy' }));
    expect(await pageTitle('Quản lý tài khoản')).toBeTruthy();

    // list → detail → edit → Quay lại returns to the detail
    await user.click(screen.getByRole('button', { name: 'Xem chi tiết bs.tranvanan' }));
    await user.click(screen.getByRole('button', { name: /Chỉnh sửa tài khoản/ }));
    await user.click(screen.getByRole('button', { name: /Quay lại/ }));
    expect(await pageTitle('Chi tiết tài khoản')).toBeTruthy();

    // breadcrumb "Quản lý tài khoản" always opens the list
    await user.click(screen.getByRole('button', { name: /Chỉnh sửa tài khoản/ }));
    await user.click(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('button', { name: 'Quản lý tài khoản' }));
    expect(await pageTitle('Quản lý tài khoản')).toBeTruthy();

    // sidebar "Quản lý tài khoản" opens the list too
    await user.click(screen.getByRole('button', { name: 'Xem chi tiết bs.tranvanan' }));
    await user.click(within(screen.getByRole('navigation', { name: 'Điều hướng chính' })).getByRole('button', { name: 'Quản lý tài khoản' }));
    expect(await pageTitle('Quản lý tài khoản')).toBeTruthy();
  });

  it('falls back to the list when a page opened from its URL goes back', async () => {
    window.history.replaceState(null, '', '/admin/accounts/02');
    const user = userEvent.setup();
    renderPage(<AccountManagementPage currentAdmin={admin} />);
    await user.click(screen.getByRole('button', { name: /Quay lại/ }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Quản lý tài khoản' })).toBeTruthy();
    expect(window.location.pathname).toBe('/admin/accounts');
  });
});
