import { useEffect, useMemo, useState } from 'react'

import { createAccountApi, type AccountApi } from '../features/account/account-api'
import { ChangePasswordForm } from '../features/account/ChangePasswordForm'
import { LoginPage } from '../features/auth/LoginPage'
import {
  apiClient,
  sessionManager as defaultSessionManager,
  useSession,
} from '../features/auth/session'
import type { SessionManager } from '../features/auth/session-manager'

const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  RECEPTIONIST: 'Lễ tân',
  DOCTOR: 'Bác sĩ',
  TECHNICIAN: 'Kỹ thuật viên',
  CASHIER: 'Thu ngân',
} as const

/** The first letter of the full name, shown in the account avatar. */
function initial(fullName: string): string {
  return fullName.trim().charAt(0).toUpperCase()
}

interface AppProps {
  readonly sessionManager?: SessionManager
  readonly accountApi?: AccountApi
}

export function App({ sessionManager = defaultSessionManager, accountApi }: AppProps) {
  const session = useSession(sessionManager)
  const account = useMemo(() => accountApi ?? createAccountApi(apiClient), [accountApi])
  const [changingPassword, setChangingPassword] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    sessionManager.restore().catch(() => sessionManager.clear())
  }, [sessionManager])

  if (session.status === 'restoring') {
    return (
      <main className="app-loading">
        <p role="status">Đang tải…</p>
      </main>
    )
  }

  if (session.status === 'anonymous') {
    return <LoginPage onLogin={(username, password) => sessionManager.login(username, password)} />
  }

  async function handlePasswordChanged() {
    setChangingPassword(false)
    setNotice('Đã đổi mật khẩu.')
    // A new access token drops the password-change restriction (docs/security.md).
    await sessionManager.refresh()
  }

  const showPasswordForm = session.passwordChangeRequired || changingPassword

  const roleLabel = ROLE_LABELS[session.account.role]

  return (
    <div className="app-shell">
      <header className="app-sidebar">
        <div className="brand">
          <span className="brand__mark" aria-hidden="true" />
          <p className="app-name">Clinic Management System</p>
        </div>
        <div className="app-sidebar__spacer" />
        <div className="account-card">
          <div className="account-card__identity">
            <span className="avatar" aria-hidden="true">
              {initial(session.account.fullName)}
            </span>
            <div>
              <p className="account-card__name">{session.account.fullName}</p>
              <p className="account-card__role">{roleLabel}</p>
            </div>
          </div>
          <div className="account-card__actions">
            {!showPasswordForm && (
              <button
                type="button"
                className="button button--ghost"
                onClick={() => setChangingPassword(true)}
              >
                Đổi mật khẩu
              </button>
            )}
            <button
              type="button"
              className="button button--secondary"
              onClick={() => void sessionManager.logout()}
            >
              Đăng xuất
            </button>
          </div>
        </div>
      </header>
      <main className="app-main">
        <div className="page-header">
          <h1>
            Xin chào {session.account.fullName} ({roleLabel})
          </h1>
        </div>
        {notice && (
          <p role="status" className="banner banner--success">
            {notice}
          </p>
        )}
        {session.passwordChangeRequired && (
          <p role="alert" className="banner banner--warning">
            Bạn đang dùng mật khẩu tạm. Hãy đổi mật khẩu trước khi sử dụng các chức năng khác.
          </p>
        )}
        {showPasswordForm && (
          <ChangePasswordForm
            onSubmit={(currentPassword, newPassword) =>
              account.changePassword(currentPassword, newPassword)
            }
            onChanged={() => void handlePasswordChanged()}
          />
        )}
      </main>
    </div>
  )
}
