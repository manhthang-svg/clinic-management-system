import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AccountManagementPage } from './AccountManagementPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const staff = {
  id: 'acc-1', username: 'bs.an', fullName: 'Nguyễn An', academicTitle: 'BS',
  phoneNumber: '0901234567', email: 'an@clinic.test', role: 'DOCTOR', status: 'ACTIVE',
  roomName: 'Phòng Nội', departmentName: 'Khoa Nội', passwordChangeRequired: false,
  failedLoginAttempts: 0, version: 2, createdAt: '2026-10-01T00:00:00Z',
  allowedActions: { update: true, assignRole: true, lock: true, unlock: false, resetPassword: true, assignRoom: true },
};

function page(content = [staff]) {
  return { content, page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 } };
}

function accountApi(overrides = {}) {
  return {
    list: vi.fn().mockResolvedValue(page()),
    departments: vi.fn().mockResolvedValue([{ id: 'dep-1', code: 'NOI', name: 'Khoa Nội', active: true }]),
    detail: vi.fn().mockResolvedValue(staff),
    activities: vi.fn().mockResolvedValue([]),
    changeHistory: vi.fn().mockResolvedValue(page([])),
    workInProgress: vi.fn().mockResolvedValue({ visitsInProgress: 0, ordersInProgress: 0 }),
    resetPassword: vi.fn().mockResolvedValue({ temporaryPassword: 'TempPass1@' }),
    ...overrides,
  };
}

function renderPage(path, api) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AccountManagementPage currentAdmin={admin} accountApi={api} onLogout={vi.fn()} onChangePassword={vi.fn()} />
    </MemoryRouter>,
  );
}

describe('AccountManagementPage backend integration surface', () => {
  it('loads department options and sends department plus sorting to the list API', async () => {
    const api = accountApi();
    const user = userEvent.setup();
    renderPage('/admin/accounts', api);

    await user.selectOptions(await screen.findByLabelText('Lọc theo khoa'), 'dep-1');
    await waitFor(() => expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({ departmentId: 'dep-1' }), expect.any(AbortSignal),
    ));

    await user.click(screen.getByRole('button', { name: 'Họ và tên' }));
    await waitFor(() => expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({ sort: 'fullName,asc' }), expect.any(AbortSignal),
    ));
  });

  it('loads detail, recent activity, change history and work in progress', async () => {
    const api = accountApi({
      changeHistory: vi.fn().mockResolvedValue({
        content: [{ id: 'history-1', action: 'UPDATE', occurredAt: '2026-10-20T08:00:00Z', changedFields: ['email'] }],
        page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
      }),
      workInProgress: vi.fn().mockResolvedValue({ visitsInProgress: 1, ordersInProgress: 2 }),
    });
    renderPage('/admin/accounts/acc-1', api);

    expect(await screen.findByRole('heading', { name: 'Chi tiết tài khoản' })).toBeInTheDocument();
    expect(await screen.findByText('Lịch sử thay đổi tài khoản')).toBeInTheDocument();
    expect(screen.getByText('Trường thay đổi: email')).toBeInTheDocument();
    expect(api.activities).toHaveBeenCalledWith('acc-1', 10, expect.any(AbortSignal));
    expect(api.changeHistory).toHaveBeenCalledWith('acc-1', 0, 10, expect.any(AbortSignal));
    expect(api.workInProgress).toHaveBeenCalledWith('acc-1', expect.any(AbortSignal));
  });

  it('uses backend-generated mode by default when resetting a password', async () => {
    const api = accountApi();
    const user = userEvent.setup();
    renderPage('/admin/accounts/acc-1/reset-password', api);

    await user.click(await screen.findByRole('button', { name: 'Đặt lại mật khẩu' }));
    await waitFor(() => expect(api.resetPassword).toHaveBeenCalledWith('acc-1', {
      mode: 'GENERATE', password: null, expectedVersion: 2,
    }));
    expect(await screen.findByText('TempPass1@')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'In' })).toBeInTheDocument();
  });
});
