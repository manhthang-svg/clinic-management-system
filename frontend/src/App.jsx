import { useEffect, useMemo, useState } from 'react';
import { createAccountApi } from './apis/accountApi';
import { ChangePasswordForm } from './pages/auth/ChangePasswordForm';
import { AccountManagementPage } from './pages/admin/AccountManagementPage';
import { LoginPage } from './pages/auth/LoginPage';
import { apiClient, sessionManager as defaultSessionManager } from './services/session';
import { useSession } from './hooks/useSession';
import { ROLE_LABELS } from './constants/accountData';

export function App({ sessionManager = defaultSessionManager, accountApi }) {
    const session = useSession(sessionManager);
    const account = useMemo(() => accountApi ?? createAccountApi(apiClient), [accountApi]);
    const [changingPassword, setChangingPassword] = useState(false);
    const [notice, setNotice] = useState(null);

    useEffect(() => {
        sessionManager.restore().catch(() => sessionManager.clear());
    }, [sessionManager]);

    if (session.status === 'restoring') {
        return (
          <main className="app-loading">
            <p role="status">Đang tải…</p>
          </main>
        );
    }

    if (session.status === 'anonymous') {
        return (
            <LoginPage
                onLogin={async (username, password) => {
                    await sessionManager.login(username, password);
                }}
            />
        );
    }

    async function handlePasswordChanged() {
        setChangingPassword(false);
        setNotice('Đã đổi mật khẩu.');
        // A new access token drops the password-change restriction (docs/security.md).
        await sessionManager.refresh();
    }

    const showPasswordForm = session.passwordChangeRequired || changingPassword;

    if (session.account.role === 'ADMIN' && !showPasswordForm) {
      return (
        <AccountManagementPage
          currentAdmin={session.account}
          notice={notice}
          onLogout={() => void sessionManager.logout()}
          onChangePassword={() => setChangingPassword(true)}
        />
      );
    }

    return (
      <>
        <header className="app-header">
          <p className="app-name">Clinic Management System</p>
          <div className="app-header__actions">
            {!showPasswordForm && (
              <button type="button" className="button button--ghost" onClick={() => setChangingPassword(true)}>
                Đổi mật khẩu
              </button>
            )}
            <button type="button" className="button button--secondary" onClick={() => void sessionManager.logout()}>
              Đăng xuất
            </button>
          </div>
        </header>
        <main className="app-main">
          <h1>
            Xin chào {session.account.fullName} ({ROLE_LABELS[session.account.role] || session.account.role})
          </h1>
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
              onSubmit={(currentPassword, newPassword) => account.changePassword(currentPassword, newPassword)}
              onChanged={() => void handlePasswordChanged()}
            />
          )}
        </main>
      </>
    );
}

export default App;
