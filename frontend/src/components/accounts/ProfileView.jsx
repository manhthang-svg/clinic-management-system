import { useState } from 'react';
import { Mail, Phone, AtSign, UserRound, Briefcase, ShieldCheck, KeyRound, Save, Info } from 'lucide-react';
import { ROLE_MAP, STATUS_LABELS } from '../../constants/accountData';
import { AccountPersonalFields } from './AccountPersonalFields';
import { avatarTone, initialsOf, formatDate, formatTime, formatPhone } from './accountFormat';
import { accountToForm, validateAccountForm } from './accountForm';
import { INPUT_CLASS, FormField, FormSection, AccountPageHeader } from './AccountFormParts';

// BR-04: staff change only their own phone number and email.
const PROFILE_FIELDS = ['phone', 'email'];

function ReadOnly({ id, label, value }) {
  return (
    <FormField id={id} label={label}>
      <input id={id} value={value} disabled className={INPUT_CLASS} />
    </FormField>
  );
}

/** UC-4 Update Profile: own account, phone and email editable, everything else read-only. */
export function ProfileView({ account, accounts, onSave, onCancel, onChangePassword }) {
  const initialForm = accountToForm(account);
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);
  const [saved, setSaved] = useState(false);
  const [submitError, setSubmitError] = useState('');

  const errors = validateAccountForm(form, { accounts, exceptId: account.id, fields: PROFILE_FIELDS });
  const dirty = PROFILE_FIELDS.some((f) => form[f] !== initialForm[f]);
  const role = ROLE_MAP[account.role];
  const hasRoom = account.role === 'DOCTOR' || account.role === 'TECHNICIAN';
  const room = account.roomName
    ? { name: account.roomName, department: account.departmentName }
    : null;

  const onChange = (field, value) => {
    setSaved(false);
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const submit = async (e) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0 || !dirty) return;
    setSubmitError('');
    try {
      await onSave({ phone: form.phone.trim(), email: form.email.trim() });
      setTouched(false);
      setSaved(true);
    } catch (error) {
      setSubmitError(error.message);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <AccountPageHeader
        title="Hồ sơ cá nhân"
        description="Xem thông tin tài khoản và cập nhật thông tin liên hệ của bạn"
        onBack={onCancel}
        crumbs={[{ label: 'Quản trị hệ thống' }, { label: 'Hồ sơ cá nhân' }]}
      />

      {saved && (
        <div role="status" className="p-3.5 rounded-xl bg-teal-50 border border-teal-200 text-teal-800 text-sm font-semibold">
          Đã cập nhật thông tin liên hệ.
        </div>
      )}
      {submitError && <div role="alert" className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm">{submitError}</div>}

      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex flex-col lg:flex-row lg:items-center gap-6">
        <div className="flex items-center gap-5">
          <div className={`w-24 h-24 rounded-full font-extrabold text-2xl flex items-center justify-center shrink-0 ring-4 ring-[#e6f5f3] ${avatarTone(account.role)}`}>
            {initialsOf(account.fullName)}
          </div>
          <div>
            <div className="text-2xl font-extrabold text-[#1b2533]">{account.fullName}</div>
            <span className={`inline-flex mt-1.5 px-2.5 py-1 rounded-lg border text-[11px] font-semibold ${role?.color}`}>{role?.label}</span>
            <div className="flex items-center gap-1.5 mt-1.5 text-sm text-emerald-600">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              {STATUS_LABELS[account.status]}
            </div>
          </div>
        </div>
        <ul className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2.5 text-sm text-slate-600 lg:border-l lg:border-slate-100 lg:pl-6">
          <li className="flex items-center gap-2.5"><Mail className="w-4 h-4 text-slate-400" />{account.email || '—'}</li>
          <li className="flex items-center gap-2.5"><Phone className="w-4 h-4 text-slate-400" />{account.phone ? formatPhone(account.phone) : '—'}</li>
          <li className="flex items-center gap-2.5"><AtSign className="w-4 h-4 text-slate-400" />{account.username}</li>
          {account.title && <li className="flex items-center gap-2.5"><Briefcase className="w-4 h-4 text-slate-400" />{account.title}</li>}
        </ul>
      </section>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-5 items-start">
        <FormSection icon={UserRound} title="Thông tin cá nhân" description="Bạn chỉ được sửa số điện thoại và email.">
          <AccountPersonalFields form={form} errors={touched ? errors : {}} onChange={onChange} editable={PROFILE_FIELDS} />
          <p className="flex items-start gap-2 mt-4 text-xs text-slate-500">
            <Info className="w-4 h-4 shrink-0 text-[#127e7a]" />
            Cần sửa họ tên, ngày sinh, giới tính hoặc địa chỉ? Hãy liên hệ quản trị viên.
          </p>
        </FormSection>

        <div className="space-y-5">
          <FormSection icon={Briefcase} title="Thông tin công tác" description="Do quản trị viên quản lý">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-5 gap-y-4">
              <ReadOnly id="profile-role" label="Vai trò" value={role?.label || account.role} />
              <ReadOnly id="profile-status" label="Trạng thái" value={STATUS_LABELS[account.status]} />
              {hasRoom && (
                <>
                  <ReadOnly id="profile-department" label="Khoa" value={room?.department || 'Chưa được phân công'} />
                  <ReadOnly id="profile-room" label="Phòng" value={room?.name || 'Chưa được phân công'} />
                </>
              )}
              <ReadOnly id="profile-created" label="Ngày tạo tài khoản" value={formatDate(account.createdAt)} />
              <ReadOnly
                id="profile-last-login"
                label="Đăng nhập gần nhất"
                value={account.lastLoginAt ? `${formatDate(account.lastLoginAt)} ${formatTime(account.lastLoginAt)}` : '—'}
              />
            </div>
          </FormSection>

          <FormSection icon={ShieldCheck} title="Bảo mật" description="Bảo vệ tài khoản của bạn">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
                <KeyRound className="w-4 h-4" />
              </div>
              <div className="flex-1">
                <div className="text-sm font-semibold text-slate-800">Mật khẩu</div>
                <div className="text-xs text-slate-500">
                  {account.passwordChangedAt ? `Đổi lần cuối ngày ${formatDate(account.passwordChangedAt)}` : 'Chưa đổi lần nào'}
                </div>
              </div>
              <button
                type="button"
                onClick={onChangePassword}
                className="px-3.5 py-2 text-xs font-semibold text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 cursor-pointer"
              >
                Đổi mật khẩu
              </button>
            </div>
          </FormSection>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2.5">
        {touched && Object.keys(errors).length > 0 && (
          <p role="alert" className="text-xs text-rose-600 mr-auto">Vui lòng sửa các ô được đánh dấu.</p>
        )}
        <button type="button" onClick={onCancel} className="px-5 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer">
          Hủy
        </button>
        <button
          type="submit"
          disabled={!dirty}
          className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Save className="w-4 h-4" />
          Lưu thay đổi
        </button>
      </div>
    </form>
  );
}

export default ProfileView;
