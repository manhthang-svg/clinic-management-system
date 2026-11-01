import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { HospitalInformationPage } from './HospitalInformationPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const facility = {
  configured: true, id: 'f-1', maCskcb: '79001', tenCskcb: 'PK Đa khoa An Bình', diachi: '1 Lê Lợi',
  sodienthoai: '02812345678', soGiayPhepHoatDong: '01234/HCM-GPHĐ', hinhThucToChuc: 'PHONG_KHAM_DA_KHOA',
  maCskcbLocked: false, updatedAt: '2026-10-30T08:00:00Z', version: 3,
};
const unconfigured = {
  configured: false, id: null, maCskcb: null, tenCskcb: null, diachi: null, sodienthoai: null,
  soGiayPhepHoatDong: null, hinhThucToChuc: null, maCskcbLocked: false, updatedAt: null, version: null,
};

function renderPage(api) {
  return render(
    <MemoryRouter initialEntries={['/admin/hospital-information']}>
      <HospitalInformationPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} />
    </MemoryRouter>,
  );
}

// Typing six fields takes over 5 s when the whole suite runs in parallel.
describe('HospitalInformationPage (UC-77)', { timeout: 15000 }, () => {
  it('declares the facility the first time with a null version', async () => {
    const api = { get: vi.fn().mockResolvedValue(unconfigured), save: vi.fn().mockResolvedValue(facility) };
    const user = userEvent.setup();
    renderPage(api);

    await user.type(await screen.findByLabelText(/Mã cơ sở KBCB/), '79001');
    await user.type(screen.getByLabelText(/Số điện thoại/), '028 1234 5678');
    await user.type(screen.getByLabelText(/Tên cơ sở/), 'PK Đa khoa An Bình');
    await user.type(screen.getByLabelText(/Địa chỉ/), '1 Lê Lợi');
    await user.type(screen.getByLabelText(/Số giấy phép hoạt động/), '01234/HCM-GPHĐ');
    await user.selectOptions(screen.getByLabelText(/Hình thức tổ chức/), 'PHONG_KHAM_DA_KHOA');
    await user.click(screen.getByRole('button', { name: 'Khai báo cơ sở' }));

    await waitFor(() => expect(api.save).toHaveBeenCalledWith({
      maCskcb: '79001', tenCskcb: 'PK Đa khoa An Bình', diachi: '1 Lê Lợi', sodienthoai: '02812345678',
      soGiayPhepHoatDong: '01234/HCM-GPHĐ', hinhThucToChuc: 'PHONG_KHAM_DA_KHOA', version: null,
    }));
  });

  it('disables the code field once it is locked and still saves other fields', async () => {
    const api = {
      get: vi.fn().mockResolvedValue({ ...facility, maCskcbLocked: true }),
      save: vi.fn().mockResolvedValue({ ...facility, maCskcbLocked: true, sodienthoai: '0901234567', version: 4 }),
    };
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByLabelText(/Mã cơ sở KBCB/)).toBeDisabled();
    const phone = screen.getByLabelText(/Số điện thoại/);
    await user.clear(phone);
    await user.type(phone, '0901234567');
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    await waitFor(() => expect(api.save).toHaveBeenCalledWith(expect.objectContaining({
      maCskcb: '79001', sodienthoai: '0901234567', version: 3,
    })));
  });

  it('asks to check the licence before saving an address change', async () => {
    const api = { get: vi.fn().mockResolvedValue(facility), save: vi.fn().mockResolvedValue(facility) };
    const user = userEvent.setup();
    renderPage(api);

    const address = await screen.findByLabelText(/Địa chỉ/);
    await user.clear(address);
    await user.type(address, '2 Lê Lợi');
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    const dialog = screen.getByRole('dialog');
    expect(api.save).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Vẫn lưu' }));
    await waitFor(() => expect(api.save).toHaveBeenCalledWith(expect.objectContaining({ diachi: '2 Lê Lợi' })));
  });

  it('reloads and explains when the code became locked meanwhile', async () => {
    const api = {
      get: vi.fn()
        .mockResolvedValueOnce(facility)
        .mockResolvedValueOnce({ ...facility, maCskcbLocked: true }),
      save: vi.fn().mockRejectedValue(new ApiError('locked', {
        status: 409, errorResponse: { code: 'FACILITY_CODE_LOCKED', message: 'locked' },
      })),
    };
    const user = userEvent.setup();
    renderPage(api);

    const code = await screen.findByLabelText(/Mã cơ sở KBCB/);
    await user.clear(code);
    await user.type(code, '79002');
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể đổi mã cơ sở KBCB vì đã có lượt khám.');
    expect(screen.getByLabelText(/Mã cơ sở KBCB/)).toBeDisabled();
    expect(screen.getByLabelText(/Mã cơ sở KBCB/)).toHaveValue('79001');
  });

  it('keeps save disabled until something changes', async () => {
    const api = { get: vi.fn().mockResolvedValue(facility), save: vi.fn() };
    renderPage(api);

    expect(await screen.findByRole('button', { name: 'Lưu thay đổi' })).toBeDisabled();
  });
});
