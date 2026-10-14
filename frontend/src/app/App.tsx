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
      <main>
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

  return (
    <main>
      <h1>Clinic Management System</h1>
      <p>
        Xin chào {session.account.fullName} ({ROLE_LABELS[session.account.role]})
      </p>
      <button type="button" onClick={() => void sessionManager.logout()}>
        Đăng xuất
      </button>
      {!showPasswordForm && (
        <button type="button" onClick={() => setChangingPassword(true)}>
          Đổi mật khẩu
        </button>
      )}
      {notice && <p role="status">{notice}</p>}
      {session.passwordChangeRequired && (
        <p role="alert">
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
  )
}
