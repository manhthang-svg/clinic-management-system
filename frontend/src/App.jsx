import { useEffect, useMemo, useState } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router';
import { ROUTES } from './routes/routes';
import { createAccountApi } from './apis/accountApi';
import { ChangePasswordForm } from './pages/auth/ChangePasswordForm';
import { AccountManagementPage, DepartmentManagementPage, ServiceManagementPage } from './pages/admin';
import { LoginPage } from './pages/auth/LoginPage';
import { apiClient, sessionManager as defaultSessionManager } from './services/session';
import { useSession } from './hooks/useSession';
import { ROLE_LABELS } from './constants/accountData';
import { ProfileView } from './components/accounts';
import { AdminLayout } from './layouts';

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
    sessionManager.restore().catch(() => sessionManager.clear());
  }, [sessionManager]);

  useEffect(() => {
    if (session.status !== 'authenticated' || !showProfile) return undefined;
    const controller = new AbortController();
    account.profile(controller.signal)
      .then(setProfile)
      .catch((error) => {
        if (error.name !== 'AbortError') setProfileError(error.message);
      });
    return () => controller.abort();
  }, [account, session.status, showProfile]);

  if (session.status === "restoring") {
    return (
      <main className="app-loading">
        <p role="status">Đang tải…</p>
      </main>
    );
  }

  if (session.status === "anonymous") {
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
    setNotice("Đã đổi mật khẩu.");

    // Refresh access token after the password-change restriction is removed.
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
          navigate(ROUTES.ADMIN.PROFILE);
        }}
        onNavigate={(key) => {
          setChangingPassword(false);
          if (key === 'accounts') navigate(ROUTES.ADMIN.ACCOUNTS);
          else if (key === 'departments' || key === 'rooms') navigate(ROUTES.ADMIN.DEPARTMENTS);
          else if (ROUTES.ADMIN[key.toUpperCase()]) navigate(ROUTES.ADMIN[key.toUpperCase()]);
        }}
        activeKey="accounts"
      >
        <div className="px-6 pb-8 space-y-5">
          {session.passwordChangeRequired && (
            <p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              Bạn đang dùng mật khẩu tạm. Hãy đổi mật khẩu trước khi sử dụng các chức năng khác.
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

  if (session.account.role === 'ADMIN' && !showPasswordForm) {
    return (
      <Routes>
        <Route
          path={ROUTES.ADMIN.DEPARTMENTS}
          element={
            <DepartmentManagementPage
              currentAdmin={session.account}
              onLogout={() => void sessionManager.logout()}
              onChangePassword={() => setChangingPassword(true)}
            />
          }
        />
        <Route
          path="/admin/rooms"
          element={
            <DepartmentManagementPage
              currentAdmin={session.account}
              onLogout={() => void sessionManager.logout()}
              onChangePassword={() => setChangingPassword(true)}
            />
          }
        />
        <Route
          path={`${ROUTES.ADMIN.SERVICES}/*`}
          element={
            <ServiceManagementPage
              currentAdmin={session.account}
              onLogout={() => void sessionManager.logout()}
              onChangePassword={() => setChangingPassword(true)}
              onNavigate={(key) => {
                if (key === 'accounts') navigate(ROUTES.ADMIN.ACCOUNTS);
                else if (key === 'departments' || key === 'rooms') navigate(ROUTES.ADMIN.DEPARTMENTS);
                else if (ROUTES.ADMIN[key.toUpperCase()]) navigate(ROUTES.ADMIN[key.toUpperCase()]);
              }}
            />
          }
        />
        <Route
          path="/admin/*"
          element={
            <AccountManagementPage
              currentAdmin={session.account}
              notice={notice}
              accountApi={account}
              onLogout={() => void sessionManager.logout()}
              onChangePassword={() => setChangingPassword(true)}
            />
          }
        />
        <Route path="*" element={<Navigate to={ROUTES.ADMIN.ACCOUNTS} replace />} />
      </Routes>
    );
  }

  return (
    <>
      <header className="app-header">
        <p className="app-name">Clinic Management System</p>

        <div className="app-header__actions">
          {!showPasswordForm && (
            <>
              <button type="button" className="button button--ghost" onClick={() => {
                setProfileError('');
                setShowProfile((value) => !value);
              }}>
                {showProfile ? 'Trang chủ' : 'Hồ sơ cá nhân'}
              </button>
              <button type="button" className="button button--ghost" onClick={() => setChangingPassword(true)}>
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
        {!showProfile && <h1>
          Xin chào {session.account.fullName} ({ROLE_LABELS[session.account.role] || session.account.role})
        </h1>}

        {notice && (
          <p role="status" className="banner banner--success">
            {notice}
          </p>
        )}

        {session.passwordChangeRequired && (
          <p role="alert" className="banner banner--warning">
            Bạn đang dùng mật khẩu tạm. Hãy đổi mật khẩu trước khi sử dụng
            các chức năng khác.
          </p>
        )}

        {showPasswordForm && (
          <ChangePasswordForm
            onSubmit={(currentPassword, newPassword) =>
              account.changePassword(currentPassword, newPassword)
            }
            onChanged={() => void handlePasswordChanged()}
            onCancel={session.passwordChangeRequired ? undefined : () => setChangingPassword(false)}
          />
        )}

        {!showPasswordForm && showProfile && !profile && !profileError && <p role="status">Đang tải hồ sơ…</p>}
        {!showPasswordForm && showProfile && profileError && <p role="alert">Không thể tải hồ sơ: {profileError}</p>}
        {!showPasswordForm && showProfile && profile && (
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

export default App;
