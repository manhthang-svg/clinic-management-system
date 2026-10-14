import { useEffect } from 'react'

import { LoginPage } from '../features/auth/LoginPage'
import { sessionManager as defaultSessionManager, useSession } from '../features/auth/session'
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
}

export function App({ sessionManager = defaultSessionManager }: AppProps) {
  const session = useSession(sessionManager)

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

  return (
    <main>
      <h1>Clinic Management System</h1>
      <p>
        Xin chào {session.account.fullName} ({ROLE_LABELS[session.account.role]})
      </p>
      <button type="button" onClick={() => void sessionManager.logout()}>
        Đăng xuất
      </button>
      {session.passwordChangeRequired && (
        <p role="alert">
          Bạn đang dùng mật khẩu tạm. Hãy đổi mật khẩu trước khi sử dụng các chức năng khác.
        </p>
      )}
    </main>
  )
}
