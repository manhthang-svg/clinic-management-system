import { useEffect, useState } from 'react';

import { ProfileView } from '../../components/accounts';
import { ownProfileFromApi } from '../../utils/ownProfile';

/** UC-04 page shared by every role that uses the routed dashboard layout. */
export function OwnProfilePage({ accountApi, onBack, onChangePassword }) {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();

    accountApi.profile(controller.signal)
      .then((value) => setProfile(ownProfileFromApi(value)))
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') {
          setError(requestError.message || 'Không thể tải hồ sơ.');
        }
      });

    return () => controller.abort();
  }, [accountApi]);

  if (error) {
    return <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">Không thể tải hồ sơ: {error}</p>;
  }

  if (!profile) {
    return <p role="status" className="text-sm text-slate-500">Đang tải hồ sơ…</p>;
  }

  return (
    <ProfileView
      account={profile}
      accounts={[]}
      onSave={async ({ phone, email }) => {
        const updated = await accountApi.updateProfile(phone, email);
        setProfile(ownProfileFromApi(updated));
      }}
      onCancel={onBack}
      onChangePassword={onChangePassword}
    />
  );
}

export default OwnProfilePage;

