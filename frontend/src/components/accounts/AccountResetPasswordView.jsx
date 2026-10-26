import { useState } from 'react';
import { CheckCircle2, Clipboard, KeyRound, Printer } from 'lucide-react';
import { AccountPageHeader, FormField, INPUT_CLASS } from './AccountFormParts';
import { ConfirmModal } from '../ui';
import { accountErrorMessage } from './accountErrors';

// Same policy as the backend (StaffAccount.checkPasswordPolicy): 8–64 characters, at most 72 UTF-8 bytes.
function passwordPolicyError(password) {
  const length = [...password].length;
  if (length < 8 || length > 64 || new TextEncoder().encode(password).length > 72) {
    return 'Mật khẩu phải dài 8–64 ký tự và không quá 72 byte.';
  }
  return null;
}

/** UC-11 supports both password modes exposed by ResetAccountPasswordRequest. */
export function AccountResetPasswordView({ account, onCancel, onGoToList, onReset }) {
  const [mode, setMode] = useState('GENERATE');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [temporaryPassword, setTemporaryPassword] = useState('');
  const [confirming, setConfirming] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    const policyError = mode === 'MANUAL' ? passwordPolicyError(password) : null;
    if (policyError) {
      setError(policyError);
      return;
    }
    if (mode === 'MANUAL' && password !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp.');
      return;
    }
    // UC-11 step 2: confirm (MSG06) with the target's full name and username before resetting.
    setConfirming(true);
  };

  const reset = async () => {
    setConfirming(false);
    setSubmitting(true);
    try {
      const response = await onReset({ mode, password: mode === 'MANUAL' ? password : null });
      setTemporaryPassword(response.temporaryPassword);
    } catch (requestError) {
      setError(accountErrorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <AccountPageHeader
        title="Đặt lại mật khẩu"
        description={`Tạo mật khẩu tạm cho ${account.username}`}
        onBack={onCancel}
        crumbs={[{ label: 'Quản trị hệ thống' }, { label: 'Quản lý tài khoản', onClick: onGoToList }, { label: 'Đặt lại mật khẩu' }]}
      />

      {temporaryPassword ? (
        <section className="bg-white rounded-2xl border border-emerald-200 p-6 max-w-2xl mx-auto text-center">
          <CheckCircle2 className="w-12 h-12 mx-auto text-emerald-600" />
          <h2 className="text-xl! font-extrabold! text-[#1b2533] mt-3">Đã đặt lại mật khẩu</h2>
          <p className="text-sm text-slate-500 mt-1">Mật khẩu chỉ hiển thị trong lần này.</p>
          <code className="block mt-5 p-4 rounded-xl bg-slate-900 text-white text-lg tracking-wider break-all">{temporaryPassword}</code>
          <div className="mt-5 flex justify-center gap-3">
            <button type="button" onClick={() => navigator.clipboard?.writeText(temporaryPassword)} className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border rounded-xl"><Clipboard className="w-4 h-4" />Sao chép</button>
            <button type="button" onClick={() => window.print()} className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border rounded-xl"><Printer className="w-4 h-4" />In</button>
            <button type="button" onClick={onCancel} className="px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] rounded-xl">Về chi tiết</button>
          </div>
        </section>
      ) : (
        <form onSubmit={submit} className="bg-white rounded-2xl border border-slate-200/70 p-6 max-w-2xl mx-auto space-y-5" noValidate>
          <div>
            <h2 className="text-lg! font-bold! text-[#1b2533]">Phương thức tạo mật khẩu</h2>
            <p className="text-sm text-slate-500">Hệ thống tự sinh là lựa chọn mặc định và an toàn hơn.</p>
          </div>
          <label className="flex items-center gap-3 p-4 border rounded-xl cursor-pointer">
            <input type="radio" name="password-mode" value="GENERATE" checked={mode === 'GENERATE'} onChange={(e) => setMode(e.target.value)} />
            <span><span className="block font-semibold text-slate-800">Hệ thống tự sinh</span><span className="block text-xs text-slate-500">Backend tạo mật khẩu tạm 12 ký tự.</span></span>
          </label>
          <label className="flex items-center gap-3 p-4 border rounded-xl cursor-pointer">
            <input type="radio" name="password-mode" value="MANUAL" checked={mode === 'MANUAL'} onChange={(e) => setMode(e.target.value)} />
            <span><span className="block font-semibold text-slate-800">Admin nhập thủ công</span><span className="block text-xs text-slate-500">Dùng khi cần bàn giao một mật khẩu đã thống nhất.</span></span>
          </label>
          {mode === 'MANUAL' && (
            <div className="grid sm:grid-cols-2 gap-4">
              <FormField id="reset-password" label="Mật khẩu tạm" required>
                <input id="reset-password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} className={INPUT_CLASS} autoComplete="new-password" />
              </FormField>
              <FormField id="reset-confirm" label="Xác nhận mật khẩu" required>
                <input id="reset-confirm" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} className={INPUT_CLASS} autoComplete="new-password" />
              </FormField>
            </div>
          )}
          {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
          <div className="flex justify-end gap-3">
            <button type="button" onClick={onCancel} className="px-5 py-2.5 text-sm font-medium border rounded-xl">Hủy</button>
            <button type="submit" disabled={submitting} className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] rounded-xl disabled:opacity-50"><KeyRound className="w-4 h-4" />{submitting ? 'Đang xử lý…' : 'Đặt lại mật khẩu'}</button>
          </div>
        </form>
      )}
      <ConfirmModal
        isOpen={confirming}
        title="Xác nhận đặt lại mật khẩu"
        description={`Bạn có chắc muốn đặt lại mật khẩu cho ${account.fullName} (${account.username})? Mật khẩu cũ sẽ hết hiệu lực và mọi phiên đăng nhập của tài khoản sẽ bị đăng xuất.`}
        confirmText="Đặt lại mật khẩu"
        variant="destructive"
        onConfirm={reset}
        onClose={() => setConfirming(false)}
      />
    </div>
  );
}

export default AccountResetPasswordView;
