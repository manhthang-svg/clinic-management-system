import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ServicePriceModal } from './ServiceUpdateModals';
import { MSG38, PRICE_MSG06 } from './serviceForm';

const service = { id: 's1', code: 'XN-01', name: 'Glucose máu', clinicPrice: 150000, insurancePrice: 50600, version: 3 };

function renderModal() {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const onClose = vi.fn();
  render(<ServicePriceModal service={service} onSubmit={onSubmit} onClose={onClose} />);
  return { onSubmit, onClose };
}

const priceDialog = () => screen.getByRole('dialog', { name: /Cập nhật giá dịch vụ/ });
const confirmDialog = () => screen.getByText(PRICE_MSG06).closest('[role="dialog"]');

async function enterClinicPrice(user, value) {
  const input = screen.getByLabelText(/Giá viện phí/);
  await user.clear(input);
  await user.type(input, value);
}

describe('ServicePriceModal (UC-24)', () => {
  it('confirms with MSG06 showing old and new prices before saving (steps 6-8)', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderModal();
    await enterClinicPrice(user, '160000');

    await user.click(within(priceDialog()).getByRole('button', { name: 'Lưu' }));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(PRICE_MSG06).toBe('Bạn có chắc chắn muốn cập nhật giá dịch vụ không?');
    expect(within(confirmDialog()).getByText('150.000 đ → 160.000 đ')).toBeInTheDocument();
    expect(within(confirmDialog()).getByText(/chỉ áp dụng cho các chỉ định và phiếu thu tạo từ bây giờ/)).toBeInTheDocument();

    await user.click(within(confirmDialog()).getByRole('button', { name: 'Xác nhận' }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ clinicPrice: '160000', insuranceCovered: true }));
  });

  it('keeps the prices and stays in the popup when the Admin does not confirm (AF-03)', async () => {
    const user = userEvent.setup();
    const { onSubmit, onClose } = renderModal();
    await enterClinicPrice(user, '160000');

    await user.click(within(priceDialog()).getByRole('button', { name: 'Lưu' }));
    await user.click(within(confirmDialog()).getByRole('button', { name: 'Hủy' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/Giá viện phí/)).toHaveValue('160.000');
  });

  it('marks the service as not covered with "Không thuộc BHYT"', async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderModal();

    await user.click(screen.getByRole('checkbox', { name: 'Không thuộc BHYT' }));
    expect(screen.getByLabelText(/Giá BHYT/)).toBeDisabled();
    await user.click(within(priceDialog()).getByRole('button', { name: 'Lưu' }));
    expect(within(confirmDialog()).getByText('50.600 đ → Không thuộc BHYT')).toBeInTheDocument();
    await user.click(within(confirmDialog()).getByRole('button', { name: 'Xác nhận' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ insuranceCovered: false }));
  });

  it('shows MSG32 for a price out of range', async () => {
    const user = userEvent.setup();
    renderModal();
    await enterClinicPrice(user, '0');

    await user.click(within(priceDialog()).getByRole('button', { name: 'Lưu' }));

    expect(screen.getByText('Giá phải là số nguyên từ 1 đến 100.000.000 VND.')).toBeInTheDocument();
  });

  it('asks with MSG38 before closing a changed popup (AF-01)', async () => {
    const user = userEvent.setup();
    const { onClose } = renderModal();
    await enterClinicPrice(user, '160000');

    await user.click(within(priceDialog()).getByRole('button', { name: 'Hủy' }));
    expect(screen.getByText(MSG38)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Loại bỏ' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
