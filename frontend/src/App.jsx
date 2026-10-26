
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import { createAccountApi } from './apis/accountApi';
import { ChangePasswordForm } from './pages/auth/ChangePasswordForm';
import { LoginPage } from './pages/auth/LoginPage';
import { ProfileView } from './components/accounts';

import { apiClient, sessionManager as defaultSessionManager } from './services/session';
import { useSession } from './hooks/useSession';
import { ROLE_LABELS } from './constants/accountData';
import { AdminLayout } from './layouts';
import AppRoutes from './routes/AppRoutes';

export function App({
  sessionManager = defaultSessionManager,
  accountApi,
}) {
  const session = useSession(sessionManager);
  const navigate = useNavigate();

  const account = useMemo(
    () => accountApi ?? createAccountApi(apiClient),
    [accountApi],
  );

  const [changingPassword, setChangingPassword] = useState(false);
  const [showProfile, setShowProfile] = useState(false);
  const [profile, setProfile] = useState(null);
  const [profileError, setProfileError] = useState('');
  const [notice, setNotice] = useState(null);

  useEffect(() => {
    sessionManager.restore().catch((error) => {
      console.error('RESTORE FAILED:', error);
      sessionManager.clear();
    });
  }, [sessionManager]);

  useEffect(() => {
    if (session.status !== 'authenticated' || !showProfile) {
      return undefined;
    }

    const controller = new AbortController();

    account.profile(controller.signal)
      .then(setProfile)
      .catch((error) => {
        if (error.name !== 'AbortError') {
          setProfileError(error.message);
        }
      });

    return () => controller.abort();
  }, [account, session.status, showProfile]);

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
          const loggedInSession = await sessionManager.login(username, password);
          const role = loggedInSession.account?.role;

          if (role === 'ADMIN') {
            navigate('/admin/accounts', { replace: true });
          } else if (role === 'CASHIER') {
            navigate('/cashier', { replace: true });
          } else {
            navigate('/404', { replace: true });
          }
        }}
      />
    );
  }

  async function handlePasswordChanged() {
    setChangingPassword(false);
    setNotice('Đã đổi mật khẩu.');
    await sessionManager.refresh();
  }

  const showPasswordForm =
    session.passwordChangeRequired || changingPassword;

  if (session.account.role === 'ADMIN' && showPasswordForm) {
    const closePasswordForm = session.passwordChangeRequired
      ? undefined
      : () => setChangingPassword(false);

    return (
      <AdminLayout
        currentAdmin={session.account}
        onLogout={() => void sessionManager.logout()}
        onChangePassword={() => setChangingPassword(true)}
        onOpenProfile={() => {
          setChangingPassword(false);
          navigate('/admin/profile');
        }}
        activeKey="accounts"
      >
        <div className="px-6 pb-8 space-y-5">
          {session.passwordChangeRequired && (
            <p
              role="alert"
              className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
            >
              Bạn đang dùng mật khẩu tạm. Hãy đổi mật khẩu trước khi sử dụng
              các chức năng khác.
            </p>
          )}

          <ChangePasswordForm
            onSubmit={(currentPassword, newPassword) =>
              account.changePassword(currentPassword, newPassword)
            }
            onChanged={() => void handlePasswordChanged()}
            onCancel={closePasswordForm}
          />
        </div>
      </AdminLayout>
    );
  }

  if (showPasswordForm) {
    return (
      <main className="app-main">
        {session.passwordChangeRequired && (
          <p role="alert" className="banner banner--warning">
            Bạn đang dùng mật khẩu tạm. Hãy đổi mật khẩu trước khi sử dụng
            các chức năng khác.
          </p>
        )}

        <ChangePasswordForm
          onSubmit={(currentPassword, newPassword) =>
            account.changePassword(currentPassword, newPassword)
          }
          onChanged={() => void handlePasswordChanged()}
          onCancel={
            session.passwordChangeRequired
              ? undefined
              : () => setChangingPassword(false)
          }
        />
      </main>
    );
  }

  if (session.account.role !== 'ADMIN' && session.account.role !== 'CASHIER') {
    return (
      <>
        <header className="app-header">
          <p className="app-name">Clinic Management System</p>

          <div className="app-header__actions">
            {!showPasswordForm && (
              <>
                <button
                  type="button"
                  className="button button--ghost"
                  onClick={() => {
                    setProfileError('');
                    setShowProfile((value) => !value);
                  }}
                >
                  {showProfile ? 'Trang chủ' : 'Hồ sơ cá nhân'}
                </button>
                <button
                  type="button"
                  className="button button--ghost"
                  onClick={() => setChangingPassword(true)}
                >
                  Đổi mật khẩu
                </button>
              </>
            )}

            <button
              type="button"
              className="button button--secondary"
              onClick={() => void sessionManager.logout()}
            >
              Đăng xuất
            </button>
          </div>
        </header>

        <main className="app-main">
          {!showProfile && (
            <h1>
              Xin chào {session.account.fullName} ({ROLE_LABELS[session.account.role] || session.account.role})
            </h1>
          )}

          {notice && (
            <p role="status" className="banner banner--success">
              {notice}
            </p>
          )}

          {showProfile && !profile && !profileError && <p role="status">Đang tải hồ sơ…</p>}
          {showProfile && profileError && <p role="alert">Không thể tải hồ sơ: {profileError}</p>}
          {showProfile && profile && (
            <ProfileView
              account={profile}
              accounts={[]}
              onSave={async (contact) => {
                const updated = await account.updateProfile(contact.phone, contact.email);
                setProfile(updated);
              }}
              onCancel={() => setShowProfile(false)}
              onChangePassword={() => setChangingPassword(true)}
            />
          )}
        </main>
      </>
    );
  }

  return (
    <>
      {notice && (
        <p role="status" className="banner banner--success">
          {notice}
        </p>
      )}

      <AppRoutes
        session={session}
        account={account}
        notice={notice}
        onLogout={() => void sessionManager.logout()}
        onChangePassword={() => setChangingPassword(true)}
      />
    </>
  );
}

export default App;
