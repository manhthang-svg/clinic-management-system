import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmModal } from './confirm-modal';

describe('ConfirmModal', () => {
  it('runs an async confirmation once and disables the buttons until it finishes', async () => {
    let finish;
    const onConfirm = vi.fn(() => new Promise((resolve) => { finish = resolve; }));
    const user = userEvent.setup();
    render(<ConfirmModal isOpen title="Khóa tài khoản" confirmText="Khóa" onConfirm={onConfirm} onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Khóa' }));
    await user.click(screen.getByRole('button', { name: 'Khóa' }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Khóa' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeDisabled();

    finish();
    expect(await screen.findByRole('button', { name: 'Khóa' })).toBeEnabled();
  });

  it('keeps a synchronous confirmation clickable', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(<ConfirmModal isOpen confirmText="Đồng ý" onConfirm={onConfirm} onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Đồng ý' }));
    await user.click(screen.getByRole('button', { name: 'Đồng ý' }));

    expect(onConfirm).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('button', { name: 'Đồng ý' })).toBeEnabled();
  });
});
