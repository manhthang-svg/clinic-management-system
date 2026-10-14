import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../shared/api/api-error'
import { ChangePasswordForm } from './ChangePasswordForm'

async function fill(current: string, next: string, confirmation: string) {
  await userEvent.type(screen.getByLabelText('Mật khẩu hiện tại'), current)
  await userEvent.type(screen.getByLabelText('Mật khẩu mới'), next)
  await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), confirmation)
  await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))
}

describe('ChangePasswordForm', () => {
  it('submits valid input and reports success', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const onChanged = vi.fn()
    render(<ChangePasswordForm onSubmit={onSubmit} onChanged={onChanged} />)

    await fill('temporary-pass', 'brand-new-password', 'brand-new-password')

    expect(onSubmit).toHaveBeenCalledWith('temporary-pass', 'brand-new-password')
    expect(onChanged).toHaveBeenCalledTimes(1)
  })

  it('checks the confirmation and the policy before calling the server', async () => {
    const onSubmit = vi.fn()
    render(<ChangePasswordForm onSubmit={onSubmit} onChanged={vi.fn()} />)

    await fill('temporary-pass', 'brand-new-password', 'different-value')
    expect(screen.getByRole('alert')).toHaveTextContent('Xác nhận mật khẩu không khớp.')
    expect(screen.getByLabelText('Nhập lại mật khẩu mới')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Mật khẩu mới')).not.toHaveAttribute('aria-invalid')

    await userEvent.clear(screen.getByLabelText('Mật khẩu mới'))
    await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))
    expect(screen.getByRole('alert')).toHaveTextContent('ít nhất 8 ký tự')
    expect(screen.getByLabelText('Mật khẩu mới')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Nhập lại mật khẩu mới')).not.toHaveAttribute('aria-invalid')

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('explains a wrong current password', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError('The current password is incorrect.', {
        status: 422,
        problem: {
          title: 'Current password incorrect',
          status: 422,
          code: 'CURRENT_PASSWORD_INCORRECT',
        },
      }),
    )
    const onChanged = vi.fn()
    render(<ChangePasswordForm onSubmit={onSubmit} onChanged={onChanged} />)

    await fill('wrong-password', 'brand-new-password', 'brand-new-password')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Mật khẩu hiện tại không đúng.')
    expect(screen.getByLabelText('Mật khẩu hiện tại')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Mật khẩu hiện tại')).toHaveAccessibleDescription(
      'Mật khẩu hiện tại không đúng.',
    )
    expect(onChanged).not.toHaveBeenCalled()
  })
})
