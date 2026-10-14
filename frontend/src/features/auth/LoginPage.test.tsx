import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../shared/api/api-error'
import { LoginPage } from './LoginPage'

describe('LoginPage', () => {
  it('submits the trimmed username and the password', async () => {
    const onLogin = vi.fn().mockResolvedValue(undefined)
    render(<LoginPage onLogin={onLogin} />)

    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), '  dr.an ')
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'secret-password')
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(onLogin).toHaveBeenCalledWith('dr.an', 'secret-password')
  })

  it('shows one generic message for rejected credentials and clears the password', async () => {
    const onLogin = vi
      .fn()
      .mockRejectedValue(new ApiError('Invalid username or password.', { status: 401 }))
    render(<LoginPage onLogin={onLogin} />)

    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'dr.an')
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Tên đăng nhập hoặc mật khẩu không đúng.',
    )
    expect(screen.getByLabelText('Mật khẩu')).toHaveValue('')
  })

  it('keeps the submit button disabled until both fields are filled', async () => {
    render(<LoginPage onLogin={vi.fn()} />)
    const submit = screen.getByRole('button', { name: 'Đăng nhập' })

    expect(submit).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'dr.an')
    expect(submit).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'x')
    expect(submit).toBeEnabled()
  })
})
