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
    sessionManager.restore().catch(() => sessionManager.clear());
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
          await sessionManager.login(username, password);
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

  // Giữ nguyên luồng đổi mật khẩu bắt buộc của ADMIN.
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

  // Bắt buộc đổi mật khẩu đối với các role khác.
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

  // Hồ sơ cá nhân.
  if (showProfile) {
    return (
      <main className="app-main">
        <button
          type="button"
          className="button button--ghost"
          onClick={() => setShowProfile(false)}
        >
          Quay lại
        </button>

        {!profile && !profileError && (
          <p role="status">Đang tải hồ sơ…</p>
        )}

        {profileError && (
          <p role="alert">
            Không thể tải hồ sơ: {profileError}
          </p>
        )}

        {profile && (
          <ProfileView
            account={profile}
            accounts={[]}
            onSave={async (contact) => {
              const updated = await account.updateProfile(
                contact.phone,
                contact.email,
              );
              setProfile(updated);
            }}
            onCancel={() => setShowProfile(false)}
            onChangePassword={() => setChangingPassword(true)}
          />
        )}
      </main>
    );
  }

  // Tất cả trang nghiệp vụ được phân tuyến tại AppRoutes.
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
