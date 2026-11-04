import { act, fireEvent, render, screen } from '@testing-library/react';
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
    vi.useFakeTimers();
    const phone = screen.getByLabelText(/Số điện thoại/);
    fireEvent.change(phone, { target: { value: '0912345678' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    expect(accountApi.updateProfile).toHaveBeenCalledWith('0912345678', 'old@clinic.test');
    await act(async () => {});
    expect(screen.getByRole('status')).toHaveTextContent('Hồ sơ cập nhật thành công.');
    await act(() => vi.advanceTimersByTimeAsync(4000));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    vi.useRealTimers();
  });
});
