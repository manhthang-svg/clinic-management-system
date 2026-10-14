import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '../shared/api/api-error'
import type { AuthApi } from '../features/auth/auth-api'
import { SessionManager } from '../features/auth/session-manager'
import { App } from './App'

function manager(api: Partial<AuthApi>) {
  return new SessionManager({
    api: {
      login: vi.fn<AuthApi['login']>(),
      refresh: vi.fn<AuthApi['refresh']>(),
      logout: vi.fn<AuthApi['logout']>().mockResolvedValue(undefined),
      ...api,
    },
    lock: (_name, callback) => callback(),
    setTimer: () => undefined,
    clearTimer: () => undefined,
  })
}

describe('App', () => {
  it('shows the login page when no session can be restored', async () => {
    const sessionManager = manager({
      refresh: vi.fn().mockRejectedValue(new ApiError('Unauthorized', { status: 401 })),
    })

    render(<App sessionManager={sessionManager} />)

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
  })

  it('restores an existing session and greets the user', async () => {
    const sessionManager = manager({
      refresh: vi.fn().mockResolvedValue({
        accessToken: 'access',
        tokenType: 'Bearer',
        expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
        passwordChangeRequired: true,
        account: { id: 'id', username: 'admin', fullName: 'Administrator', role: 'ADMIN' },
      }),
    })

    render(<App sessionManager={sessionManager} />)

    expect(await screen.findByText('Xin chào Administrator (Quản trị viên)')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('mật khẩu tạm')
  })

  it('returns to the login page after logout', async () => {
    const logout = vi.fn().mockResolvedValue(undefined)
    const sessionManager = manager({
      refresh: vi.fn().mockResolvedValue({
        accessToken: 'access',
        tokenType: 'Bearer',
        expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
        passwordChangeRequired: false,
        account: { id: 'id', username: 'dr.an', fullName: 'Nguyễn An', role: 'DOCTOR' },
      }),
      logout,
    })
    render(<App sessionManager={sessionManager} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Đăng xuất' }))

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
    expect(logout).toHaveBeenCalledTimes(1)
  })
})
