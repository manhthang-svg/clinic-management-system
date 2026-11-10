import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AccountDetailView } from './AccountDetailView';

function renderDetail(account, props = {}) {
  render(
    <AccountDetailView
      account={account}
      loginEvents={[]}
      accountNames={new Map()}
      workInProgress={{ visitsInProgress: 0, ordersInProgress: 0 }}
      isSelf={false}
      onBack={vi.fn()}
      onGoToList={vi.fn()}
      onEdit={vi.fn()}
      onAssignRole={vi.fn()}
      onToggleLock={vi.fn()}
      onResetPassword={vi.fn()}
      {...props}
    />,
  );
}

const doctor = {
  id: 'doctor-1',
  username: 'doctor.an',
  fullName: 'Nguyễn Văn An',
  role: 'DOCTOR',
  status: 'ACTIVE',
  allowedActions: { assignRoom: true },
};

describe('AccountDetailView room assignment action', () => {
  it('does not show the room assignment action on account details', () => {
    renderDetail(doctor);

    expect(screen.queryByRole('button', { name: 'Phân công phòng' })).not.toBeInTheDocument();
  });

  it('hides the action when the backend does not allow room assignment', () => {
    renderDetail({ ...doctor, allowedActions: { assignRole: true, assignRoom: false } });

    expect(screen.queryByRole('button', { name: 'Phân công phòng' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đổi vai trò' })).toHaveClass('col-span-2');
  });
});

describe('AccountDetailView locked account actions', () => {
  it('shows management actions but only allows unlocking', async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    const onAssignRole = vi.fn();
    const onResetPassword = vi.fn();
    renderDetail({
      ...doctor,
      status: 'LOCKED',
      allowedActions: {
        update: true,
        assignRole: true,
        assignRoom: true,
        resetPassword: true,
        unlock: true,
      },
    }, { onEdit, onAssignRole, onResetPassword });

    expect(screen.getByRole('button', { name: 'Mở khóa tài khoản' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Chỉnh sửa tài khoản' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Đổi vai trò' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Đặt lại mật khẩu' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Sửa' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Chỉnh sửa tài khoản' }));
    await user.click(screen.getByRole('button', { name: 'Đổi vai trò' }));
    await user.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }));
    expect(onEdit).not.toHaveBeenCalled();
    expect(onAssignRole).not.toHaveBeenCalled();
    expect(onResetPassword).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Phân công phòng' })).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Hãy mở khóa tài khoản trước');
  });
});
