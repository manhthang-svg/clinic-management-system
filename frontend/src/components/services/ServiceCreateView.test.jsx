import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ServiceCreateView } from './ServiceCreateView';
import { MSG38 } from './serviceForm';

function renderView() {
  const onCancel = vi.fn();
  render(<ServiceCreateView onCreate={vi.fn()} onCancel={onCancel} onGoToList={vi.fn()} />);
  return onCancel;
}

describe('ServiceCreateView cancel (UC-22 AF-04)', () => {
  it('leaves at once when nothing has been entered', async () => {
    const user = userEvent.setup();
    const onCancel = renderView();

    await user.click(screen.getByRole('button', { name: 'Hủy' }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(MSG38)).toBeNull();
  });

  it('asks with MSG38 once a field has been entered, and keeps editing on "Tiếp tục chỉnh sửa"', async () => {
    const user = userEvent.setup();
    const onCancel = renderView();
    await user.type(screen.getByLabelText(/Tên dịch vụ/), 'Glucose máu');

    await user.click(screen.getByRole('button', { name: 'Hủy' }));
    expect(screen.getByText(MSG38)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tiếp tục chỉnh sửa' }));

    expect(onCancel).not.toHaveBeenCalled();
    expect(screen.queryByText(MSG38)).toBeNull();
    expect(screen.getByLabelText(/Tên dịch vụ/)).toHaveValue('Glucose máu');
  });

  it('discards the entered data and leaves on "Loại bỏ"', async () => {
    const user = userEvent.setup();
    const onCancel = renderView();
    await user.type(screen.getByLabelText(/Tên dịch vụ/), 'Glucose máu');

    await user.click(screen.getByRole('button', { name: 'Hủy' }));
    await user.click(screen.getByRole('button', { name: 'Loại bỏ' }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('shows MSG02 under the empty required fields', async () => {
    const user = userEvent.setup();
    renderView();

    await user.click(screen.getByRole('button', { name: /Lưu/ }));

    expect(screen.getByText('Mã dịch vụ là bắt buộc.')).toBeInTheDocument();
    expect(screen.getByText('Tên dịch vụ là bắt buộc.')).toBeInTheDocument();
  });
});
