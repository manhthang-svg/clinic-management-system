import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { OwnProfilePage } from './OwnProfilePage';

describe('OwnProfilePage', () => {
  it('loads and updates the authenticated account profile', async () => {
    const profile = {
      username: 'cashier.one',
      role: 'CASHIER',
      status: 'ACTIVE',
      fullName: 'Thu ngân Một',
      phoneNumber: '0901234567',
      email: 'old@clinic.test',
    };
    const accountApi = {
      profile: vi.fn().mockResolvedValue(profile),
      updateProfile: vi.fn().mockResolvedValue({
        ...profile,
        phoneNumber: '0912345678',
      }),
    };

    render(
      <OwnProfilePage
        accountApi={accountApi}
        onBack={vi.fn()}
        onChangePassword={vi.fn()}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'Hồ sơ cá nhân' })).toBeInTheDocument();
    const phone = screen.getByLabelText(/Số điện thoại/);
    await userEvent.clear(phone);
    await userEvent.type(phone, '0912345678');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    expect(accountApi.updateProfile).toHaveBeenCalledWith('0912345678', 'old@clinic.test');
    expect(await screen.findByRole('status')).toHaveTextContent('Đã cập nhật thông tin liên hệ.');
  });
});
