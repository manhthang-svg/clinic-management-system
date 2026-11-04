import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ApiError } from '../../apis/apiError';
import { ServiceInfoModal } from './ServiceUpdateModals';
import { MSG38 } from './serviceForm';

const service = { id: 's1', code: 'XN-01', name: 'Glucose máu', description: '', version: 2 };

function renderModal(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  const onClose = vi.fn();
  render(<ServiceInfoModal service={service} onSubmit={onSubmit} onClose={onClose} />);
  return { onClose, onSubmit };
}

describe('ServiceInfoModal (UC-23)', () => {
  it('closes at once on "Hủy" when nothing was changed', async () => {
    const user = userEvent.setup();
    const { onClose } = renderModal();

    await user.click(screen.getByRole('button', { name: 'Hủy' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(MSG38)).toBeNull();
  });

  it('asks with MSG38 after a change and keeps editing or discards (AF-01)', async () => {
    const user = userEvent.setup();
    const { onClose } = renderModal();
    await user.type(screen.getByLabelText(/Tên dịch vụ/), ' lúc đói');

    await user.click(screen.getByRole('button', { name: 'Hủy' }));
    expect(screen.getByText(MSG38)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tiếp tục chỉnh sửa' }));
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/Tên dịch vụ/)).toHaveValue('Glucose máu lúc đói');

    await user.click(screen.getByRole('button', { name: 'Đóng' }));
    await user.click(screen.getByRole('button', { name: 'Loại bỏ' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('shows a name used by another service under the name field (EF-02, MSG07)', async () => {
    const user = userEvent.setup();
    const duplicate = new ApiError('Duplicate', {
      status: 409,
      errorResponse: { code: 'SERVICE_NAME_ALREADY_EXISTS', message: 'Duplicate' },
    });
    renderModal(vi.fn().mockRejectedValue(duplicate));

    const name = screen.getByLabelText(/Tên dịch vụ/);
    await user.clear(name);
    await user.type(name, 'Định lượng Glucose');
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    expect(await screen.findAllByText('Tên dịch vụ đã tồn tại trong hệ thống.')).not.toHaveLength(0);
  });
});
