import { useState } from 'react';
import {
  Check,
  Circle,
  Eye,
  EyeOff,
  KeyRound,
  LockKeyhole,
  ShieldCheck,
  TriangleAlert,
} from 'lucide-react';
import { ApiError } from '../../apis/apiError';
import {
  AccountPageHeader,
  FormField,
  INPUT_CLASS,
} from '../../components/accounts/AccountFormParts';

const MINIMUM_LENGTH = 8;
const MAXIMUM_LENGTH = 64;
const MAXIMUM_UTF8_BYTES = 72;
const ERROR_ID = 'change-password-error';

function passwordLength(password) {
  return [...password].length;
}

function passwordBytes(password) {
  return new TextEncoder().encode(password).length;
}

function policyError(password) {
  const length = passwordLength(password);
  if (length < MINIMUM_LENGTH
    || length > MAXIMUM_LENGTH
    || passwordBytes(password) > MAXIMUM_UTF8_BYTES) {
    return 'Mật khẩu phải có độ dài từ 8–64 ký tự.';
  }
  return null;
}

function clientError(currentPassword, newPassword, confirmation) {
  const policy = policyError(newPassword);
  if (policy) {
    return { message: policy, field: 'new' };
  }
  if (newPassword !== confirmation) {
    return { message: 'Mật khẩu xác nhận không khớp.', field: 'confirmation' };
  }
  if (newPassword === currentPassword) {
    return { message: 'Mật khẩu mới phải khác mật khẩu hiện tại.', field: 'new' };
  }
  return null;
}

function serverError(error) {
  if (error instanceof ApiError) {
    switch (error.errorResponse?.code) {
      case 'CURRENT_PASSWORD_INCORRECT':
        return { message: 'Mật khẩu hiện tại không chính xác.', field: 'current' };
      case 'PASSWORD_POLICY_VIOLATION':
        return {
          message: error.errorResponse.details?.newPassword === 'SAME_AS_CURRENT'
            ? 'Mật khẩu mới phải khác mật khẩu hiện tại.'
            : 'Mật khẩu phải có độ dài từ 8–64 ký tự.',
          field: 'new',
        };
    }
  }
  return { message: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.', field: null };
}

function PasswordInput({ id, label, value, onChange, autoComplete, invalidProps, requiredMessage }) {
  const [visible, setVisible] = useState(false);

  return (
    <FormField id={id} label={label} required icon={LockKeyhole}>
      <input
        id={id}
        className={`${INPUT_CLASS} pl-10! pr-11!`}
        type={visible ? 'text' : 'password'}
        aria-label={label}
        autoComplete={autoComplete}
        value={value}
        onChange={onChange}
        {...invalidProps}
      />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        className="absolute right-3 top-1/2 -translate-y-1/2 rounded-lg p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-[#127e7a] focus:outline-none focus:ring-2 focus:ring-[#127e7a]/20 cursor-pointer"
        aria-label={visible ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`}
        aria-pressed={visible}
      >
        {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
      {requiredMessage && (
        <p id={`${id}-required`} role="alert" className="mt-1.5 text-xs text-rose-600">
          {requiredMessage}
        </p>
      )}
    </FormField>
  );
}

function Requirement({ met, children }) {
  const Icon = met ? Check : Circle;
  return (
    <li className={`flex items-start gap-2 text-sm ${met ? 'text-[#127e7a]' : 'text-slate-500'}`}>
      <span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full ${met ? 'bg-[#e6f5f3]' : 'bg-slate-100'}`}>
        <Icon className={met ? 'h-3.5 w-3.5' : 'h-2.5 w-2.5'} />
      </span>
      <span>{children}</span>
    </li>
  );
}

export function ChangePasswordForm({ onSubmit, onChanged, onCancel, onOpenProfile }) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [touched, setTouched] = useState({ current: false, new: false, confirmation: false });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const length = passwordLength(newPassword);
  const bytes = passwordBytes(newPassword);
  const requirements = {
    validLength: length >= MINIMUM_LENGTH && length <= MAXIMUM_LENGTH,
    validBytes: newPassword !== '' && bytes <= MAXIMUM_UTF8_BYTES,
    changed: newPassword !== '' && currentPassword !== '' && newPassword !== currentPassword,
    confirmed: confirmation !== '' && newPassword === confirmation,
  };

  async function handleSubmit(event) {
    event.preventDefault();
    setTouched({ current: true, new: true, confirmation: true });
    if (currentPassword.trim() === '' || newPassword.trim() === '' || confirmation.trim() === '') {
      return;
    }
    const invalid = clientError(currentPassword, newPassword, confirmation);
    if (invalid) {
      setError(invalid);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(currentPassword, newPassword);
      onChanged();
    }
    catch (cause) {
      setError(serverError(cause));
    }
    finally {
      setSubmitting(false);
    }
  }

  function updateField(setter) {
    return (event) => {
      setter(event.target.value);
      setError(null);
    };
  }

  function invalidProps(field) {
    const required = touched[field] && {
      current: currentPassword,
      new: newPassword,
      confirmation,
    }[field].trim() === '';
    if (required) {
      return { 'aria-invalid': true, 'aria-describedby': `${field === 'current' ? 'current-password' : field === 'new' ? 'new-password' : 'confirm-password'}-required` };
    }
    return error?.field === field ? { 'aria-invalid': true, 'aria-describedby': ERROR_ID } : {};
  }

  const requiredMessages = {
    current: touched.current && currentPassword.trim() === '' ? 'Mật khẩu hiện tại là bắt buộc.' : null,
    new: touched.new && newPassword.trim() === '' ? 'Mật khẩu mới là bắt buộc.' : null,
    confirmation: touched.confirmation && confirmation.trim() === '' ? 'Mật khẩu xác nhận là bắt buộc.' : null,
  };

  return (
    <div className="space-y-6" aria-labelledby="change-password-title">
      <AccountPageHeader
        title="Đổi mật khẩu"
        description="Cập nhật mật khẩu để bảo vệ tài khoản của bạn."
        crumbs={[
          { label: 'Hồ sơ cá nhân', onClick: onOpenProfile },
          { label: 'Đổi mật khẩu' },
        ]}
        onBack={onCancel}
      />

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2 xl:items-start">
        <section className="overflow-hidden rounded-2xl border border-slate-200/70 bg-white shadow-2xs">
          <div className="flex items-start gap-3 border-b border-slate-100 px-5 py-5 sm:px-6">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#e6f5f3] text-[#127e7a]">
              <KeyRound className="h-5 w-5" />
            </div>
            <div>
              <h2 id="change-password-title" className="text-base! leading-6! font-bold! text-[#1b2533]">
                Thông tin mật khẩu
              </h2>
              <p className="mt-0.5 text-xs text-slate-500">Các trường có dấu * là bắt buộc.</p>
            </div>
          </div>

          <form className="space-y-5 p-5 sm:p-6" onSubmit={handleSubmit} noValidate>
            <PasswordInput
              id="current-password"
              label="Mật khẩu hiện tại"
              autoComplete="current-password"
              value={currentPassword}
              onChange={updateField(setCurrentPassword)}
              invalidProps={invalidProps('current')}
              requiredMessage={requiredMessages.current}
            />

            <div className="border-t border-slate-100 pt-5">
              <PasswordInput
                id="new-password"
                label="Mật khẩu mới"
                autoComplete="new-password"
                value={newPassword}
                onChange={updateField(setNewPassword)}
                invalidProps={invalidProps('new')}
                requiredMessage={requiredMessages.new}
              />
              <p className="mt-1.5 text-right text-xs text-slate-400" aria-live="polite">
                {length}/{MAXIMUM_LENGTH} ký tự · {bytes}/{MAXIMUM_UTF8_BYTES} byte
              </p>
            </div>

            <PasswordInput
              id="confirm-password"
              label="Nhập lại mật khẩu mới"
              autoComplete="new-password"
              value={confirmation}
              onChange={updateField(setConfirmation)}
              invalidProps={invalidProps('confirmation')}
              requiredMessage={requiredMessages.confirmation}
            />

            {error && (
              <div id={ERROR_ID} role="alert" className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error.message}</span>
              </div>
            )}

            <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
              {onCancel && (
                <button
                  type="button"
                  onClick={onCancel}
                  disabled={submitting}
                  className="rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 cursor-pointer"
                >
                  Hủy
                </button>
              )}
              <button
                type="submit"
                disabled={submitting}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-[#127e7a] px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#0f6f6b] focus:outline-none focus:ring-2 focus:ring-[#127e7a]/25 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 cursor-pointer"
              >
                <KeyRound className="h-4 w-4" />
                {submitting ? 'Đang lưu…' : 'Đổi mật khẩu'}
              </button>
            </div>
          </form>
        </section>

        <aside className="space-y-4">
          <section className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-2xs">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#e6f5f3] text-[#127e7a]">
                <ShieldCheck className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-sm! font-bold! text-[#1b2533]">Yêu cầu mật khẩu</h2>
                <p className="text-xs text-slate-400">Kiểm tra theo thời gian thực</p>
              </div>
            </div>
            <ul className="space-y-3">
              <Requirement met={requirements.validLength}>Từ 8 đến 64 ký tự</Requirement>
              <Requirement met={requirements.validBytes}>Không quá 72 byte UTF-8</Requirement>
              <Requirement met={requirements.changed}>Khác mật khẩu hiện tại</Requirement>
              <Requirement met={requirements.confirmed}>Hai mật khẩu mới trùng khớp</Requirement>
            </ul>
          </section>

          <div className="rounded-2xl border border-amber-200/70 bg-amber-50/80 p-4 text-sm text-amber-900">
            <div className="flex items-start gap-2.5">
              <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
              <p>Không chia sẻ mật khẩu hoặc sử dụng lại mật khẩu của tài khoản khác.</p>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}

export default ChangePasswordForm;
