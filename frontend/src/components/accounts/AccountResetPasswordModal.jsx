import { useState } from 'react';
import { CheckCircle2, Clipboard, Eye, EyeOff, KeyRound, Printer } from 'lucide-react';
import { FormField, INPUT_CLASS } from './AccountFormParts';
import { ServiceFormModal } from '../services/ServiceFormParts';
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

const SECONDARY_BUTTON =
  'inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:cursor-not-allowed';
const PRIMARY_BUTTON =
  'inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed';

function ModeOption({ value, mode, onChange, title, description }) {
  const checked = mode === value;
  return (
    <label
      className={`flex items-start gap-3 p-4 rounded-xl border cursor-pointer transition-colors ${
        checked ? 'border-[#127e7a] bg-[#e9f7f5]' : 'border-slate-200 hover:bg-slate-50'
      }`}
    >
      <input
        type="radio"
        name="password-mode"
        value={value}
        checked={checked}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1 accent-[#127e7a]"
      />
      <span>
        <span className="block text-sm font-semibold text-slate-800">{title}</span>
        <span className="block text-xs text-slate-500 mt-0.5">{description}</span>
      </span>
    </label>
  );
}

function PasswordInput({ id, label, value, onChange }) {
  const [visible, setVisible] = useState(false);
  return (
    <FormField id={id} label={label} required>
      <div className="relative">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={INPUT_CLASS + ' pr-10'}
          autoComplete="new-password"
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          className="absolute inset-y-0 right-2 flex items-center text-slate-400 hover:text-slate-600"
          tabIndex={-1}
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        >
          {visible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
        </button>
      </div>
    </FormField>
  );
}

/** UC-11 Reset Password, opened from the account detail; supports both modes of ResetAccountPasswordRequest. */
export function AccountResetPasswordModal({ account, onReset, onClose }) {
  const [mode, setMode] = useState('GENERATE');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [temporaryPassword, setTemporaryPassword] = useState('');
  const [confirming, setConfirming] = useState(false);
  const done = Boolean(temporaryPassword);

  const changeMode = (value) => {
    setMode(value);
    setError('');
  };

  const submit = (event) => {
    event.preventDefault();
    if (done) return;
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

  const footer = done ? (
    <>
      <button type="button" onClick={() => navigator.clipboard?.writeText(temporaryPassword)} className={SECONDARY_BUTTON}>
        <Clipboard className="w-4 h-4" />
        Sao chép
      </button>
      <button type="button" onClick={() => window.print()} className={SECONDARY_BUTTON}>
        <Printer className="w-4 h-4" />
        In
      </button>
      <button type="button" onClick={onClose} className={PRIMARY_BUTTON}>Đóng</button>
    </>
  ) : (
    <>
      <button type="button" onClick={onClose} disabled={submitting} className={SECONDARY_BUTTON}>Hủy</button>
      <button type="submit" disabled={submitting} className={PRIMARY_BUTTON}>
        <KeyRound className="w-4 h-4" />
        {submitting ? 'Đang xử lý…' : 'Đặt lại mật khẩu'}
      </button>
    </>
  );

  return (
    <>
      <ServiceFormModal
        id="reset-password-modal"
        icon={done ? CheckCircle2 : KeyRound}
        title={done ? 'Đã đặt lại mật khẩu' : 'Đặt lại mật khẩu'}
        description={`${account.fullName} (${account.username})`}
        submitting={submitting}
        alert={error}
        onSubmit={submit}
        onClose={() => !confirming && onClose()}
        footer={footer}
      >
        {done ? (
          <div className="text-center">
            <p className="text-sm text-slate-500">
              {mode === 'MANUAL'
                ? 'Mật khẩu chính thức đã được đặt. Mật khẩu chỉ hiển thị trong lần này.'
                : 'Mật khẩu tạm chỉ hiển thị trong lần này. Người dùng phải đổi mật khẩu khi đăng nhập.'}
            </p>
            <code className="block mt-4 p-4 rounded-xl bg-slate-900 text-white text-lg tracking-wider break-all">
              {temporaryPassword}
            </code>
          </div>
        ) : (
          <div className="space-y-3">
            <div>
              <h3 className="text-sm! font-bold! text-[#1b2533]">Phương thức tạo mật khẩu</h3>
              <p className="text-xs text-slate-500 mt-0.5">Hệ thống tự sinh là lựa chọn mặc định và an toàn hơn.</p>
            </div>
            <ModeOption
              value="GENERATE"
              mode={mode}
              onChange={changeMode}
              title="Hệ thống tự sinh"
              description="Backend tạo mật khẩu tạm 12 ký tự; người dùng phải đổi khi đăng nhập."
            />
            <ModeOption
              value="MANUAL"
              mode={mode}
              onChange={changeMode}
              title="Admin nhập thủ công"
              description="Mật khẩu chính thức đã thống nhất; người dùng không phải đổi khi đăng nhập."
            />
            {mode === 'MANUAL' && (
              <div className="grid sm:grid-cols-2 gap-4 pt-1">
                <PasswordInput id="reset-password" label="Mật khẩu chính thức" value={password} onChange={setPassword} />
                <PasswordInput id="reset-confirm" label="Xác nhận mật khẩu" value={confirmPassword} onChange={setConfirmPassword} />
              </div>
            )}
          </div>
        )}
      </ServiceFormModal>
      <ConfirmModal
        isOpen={confirming}
        title="Xác nhận đặt lại mật khẩu"
        description={`Bạn có chắc muốn đặt lại mật khẩu cho ${account.fullName} (${account.username})? Mật khẩu cũ sẽ hết hiệu lực và mọi phiên đăng nhập của tài khoản sẽ bị đăng xuất.`}
        confirmText="Đặt lại mật khẩu"
        variant="destructive"
        onConfirm={reset}
        onClose={() => setConfirming(false)}
      />
    </>
  );
}

export default AccountResetPasswordModal;
