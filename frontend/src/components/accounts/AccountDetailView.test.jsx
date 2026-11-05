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
      onAssignRoom={vi.fn()}
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
  it('shows the action for an eligible Doctor and invokes UC-15 navigation', async () => {
    const user = userEvent.setup();
    const onAssignRoom = vi.fn();
    renderDetail(doctor, { onAssignRoom });

    await user.click(screen.getByRole('button', { name: 'Phân công phòng' }));

    expect(onAssignRoom).toHaveBeenCalledOnce();
  });

  it('hides the action when the backend does not allow room assignment', () => {
    renderDetail({ ...doctor, allowedActions: { assignRole: true, assignRoom: false } });

    expect(screen.queryByRole('button', { name: 'Phân công phòng' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đổi vai trò' })).toHaveClass('col-span-2');
  });
});

describe('AccountDetailView locked account actions', () => {
  it('allows changing the role and unlocking while other actions remain unavailable', async () => {
    const user = userEvent.setup();
    const onAssignRole = vi.fn();
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
    }, { onAssignRole });

    expect(screen.getByRole('button', { name: 'Mở khóa tài khoản' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Đổi vai trò' }));
    expect(onAssignRole).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: 'Chỉnh sửa tài khoản' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Chỉnh sửa' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Phân công phòng' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đặt lại mật khẩu' })).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Tài khoản đang bị khóa');
  });
});
