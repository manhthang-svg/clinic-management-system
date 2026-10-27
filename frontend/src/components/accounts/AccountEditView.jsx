import { useState } from 'react';
import { accountErrorMessage, accountFieldErrors, withoutField } from './accountErrors';
import {
  CalendarClock,
  Lock,
  LockOpen,
  CalendarDays,
  TriangleAlert,
  LockKeyhole,
  FileText,
  ChevronRight,
  RotateCcw,
  Save,
} from 'lucide-react';
import { ROLE_MAP, STATUS_LABELS } from '../../constants/accountData';
import { AccountPersonalFields } from './AccountPersonalFields';
import { avatarTone, initialsOf, formatDate, formatTime } from './accountFormat';
import { accountToForm, validateAccountForm } from './accountForm';
import { INPUT_CLASS, FormField, AccountPageHeader } from './AccountFormParts';

// UC-8 edits exactly these seven fields; username, role and status have their own use cases.
const EDIT_FIELDS = [
  'fullName', 'title', 'practiceLicenseNo', 'dateOfBirth', 'gender', 'phone', 'email', 'address',
];
const DIRTY_FIELDS = EDIT_FIELDS;
function dateTime(iso) {
  return iso ? `${formatDate(iso)} ${formatTime(iso)}` : '—';
}

function ControlRow({ icon: Icon, tone, title, description, children }) {
  return (
    <div className="flex items-start gap-3 py-3.5">
      <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${tone}`}>
        <Icon className="w-4 h-4" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold text-slate-800">{title}</div>
        <div className="text-xs text-slate-500">{description}</div>
        {children}
      </div>
    </div>
  );
}

function InfoRow({ icon: Icon, tone, label, value, note }) {
  return (
    <div className="flex items-start gap-3 py-2.5">
      <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${tone}`}>
        <Icon className="w-4 h-4" />
      </div>
      <div className="text-sm">
        <div className="font-semibold text-slate-800">{label}</div>
        <div className="text-slate-600">{value}</div>
        {note && <div className="text-xs text-slate-400">{note}</div>}
      </div>
    </div>
  );
}

const SMALL_BUTTON = 'inline-flex items-center gap-1.5 px-3 py-1.5 mt-2 text-xs font-semibold rounded-lg border cursor-pointer transition-colors';

/** UC-8 Update Account Information, with the UC-10 lock control beside the form. */
export function AccountEditView({ account, accounts, isSelf, onSave, onCancel, onGoToList, onViewDetail, onToggleLock }) {
  const initialForm = accountToForm(account);
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [serverErrors, setServerErrors] = useState({});

  const errors = validateAccountForm(form, { accounts, exceptId: account.id, fields: EDIT_FIELDS });
  const dirty = DIRTY_FIELDS.some((f) => form[f] !== initialForm[f]);
  const locked = account.status === 'LOCKED';

  const onChange = (field, value) => {
    setServerErrors((prev) => withoutField(prev, field));
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const submit = async (e) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0 || !dirty) return;
    setSubmitError('');
    setServerErrors({});
    try {
      await onSave(form);
    } catch (error) {
      setServerErrors(accountFieldErrors(error));
      setSubmitError(accountErrorMessage(error));
    }
  };

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <AccountPageHeader
        title="Cập nhật thông tin tài khoản"
        description="Chỉnh sửa thông tin cá nhân và liên hệ của nhân viên"
        onBack={onCancel}
        crumbs={[
          { label: 'Quản lý tài khoản', onClick: onGoToList },
          { label: 'Cập nhật tài khoản' },
        ]}
      />

      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex flex-col lg:flex-row lg:items-center gap-5">
        <div className="flex items-center gap-4 flex-1 min-w-0">
          <div className={`w-16 h-16 rounded-full font-extrabold text-lg flex items-center justify-center shrink-0 ring-4 ring-[#e6f5f3] ${avatarTone(account.role)}`}>
            {initialsOf(account.fullName)}
          </div>
          <div className="min-w-0">
            <div className="text-xl font-extrabold text-[#1b2533] truncate">
              {account.title && <span className="text-slate-400 font-semibold mr-1.5">{account.title}</span>}
              {account.fullName}
            </div>
            <div className="text-sm text-slate-500">{account.username}</div>
          </div>
        </div>
        <div className="flex divide-x divide-slate-100 text-center">
          <div className="px-6">
            <span className={`inline-flex px-3 py-1 rounded-lg border text-xs font-semibold ${ROLE_MAP[account.role]?.color}`}>
              {ROLE_MAP[account.role]?.label}
            </span>
            <div className="text-xs text-slate-500 mt-1.5">Vai trò</div>
          </div>
          <div className="px-6">
            <span className={`inline-flex px-3 py-1 rounded-lg text-xs font-semibold ${locked ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}`}>
              {STATUS_LABELS[account.status]}
            </span>
            <div className="text-xs text-slate-500 mt-1.5">Trạng thái</div>
          </div>
          <div className="px-6 flex items-center gap-3 text-left">
            <CalendarClock className="w-6 h-6 text-[#127e7a]" />
            <div className="text-sm">
              <div className="text-xs text-slate-500">Cập nhật lần cuối</div>
              <div className="font-semibold text-slate-800">{dateTime(account.updatedAt || account.createdAt)}</div>
            </div>
          </div>
        </div>
      </section>

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_340px] gap-5 items-start">
        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5">
          <h2 className="text-lg! leading-7! font-bold! text-[#1b2533]">Thông tin tài khoản</h2>
          <p className="text-sm text-slate-500 mb-5">
            Cập nhật thông tin bên dưới. Các ô có dấu <span className="text-rose-500">*</span> là bắt buộc.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-5 gap-y-4 mb-4">
            <FormField id="account-username" label="Tên đăng nhập" hint="Không thể thay đổi">
              <input id="account-username" value={account.username} disabled className={INPUT_CLASS} />
            </FormField>
            <FormField id="account-status" label="Trạng thái" hint="Đổi bằng Khóa / Mở khóa ở bên phải">
              <input id="account-status" value={STATUS_LABELS[account.status]} disabled className={INPUT_CLASS} />
            </FormField>
          </div>

          <AccountPersonalFields form={form} errors={{ ...(touched ? errors : {}), ...serverErrors }} onChange={onChange} editable={EDIT_FIELDS} showUsername={false} />
        </section>

        <aside className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5">
          <h2 className="text-lg! leading-7! font-bold! text-[#1b2533]">Kiểm soát tài khoản</h2>
          <p className="text-sm text-slate-500">Quản lý quyền đăng nhập và bảo mật.</p>

          <div className="divide-y divide-slate-100 mt-2">
            {!isSelf && account.role !== 'ADMIN' && (
              <ControlRow
                icon={locked ? LockOpen : Lock}
                tone={locked ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}
                title={locked ? 'Tài khoản đang bị khóa' : 'Khóa tài khoản'}
                description={locked ? `Lý do: ${account.lockReason || '—'}` : 'Chặn đăng nhập và đăng xuất mọi phiên đang mở.'}
              >
                <button
                  type="button"
                  onClick={onToggleLock}
                  className={`${SMALL_BUTTON} ${locked ? 'text-emerald-700 border-emerald-200 hover:bg-emerald-50' : 'text-rose-600 border-rose-200 hover:bg-rose-50'}`}
                >
                  {locked ? 'Mở khóa' : 'Khóa'}
                </button>
              </ControlRow>
            )}

          </div>

          <div className="border-t border-slate-100 mt-1 pt-2">
            <InfoRow icon={CalendarDays} tone="bg-slate-50 text-slate-600" label="Đăng nhập gần nhất" value={account.lastLoginAt ? dateTime(account.lastLoginAt) : 'Chưa đăng nhập'} />
            <InfoRow
              icon={TriangleAlert}
              tone={account.failedLoginCount ? 'bg-rose-50 text-rose-600' : 'bg-slate-50 text-slate-600'}
              label="Đăng nhập sai liên tiếp"
              value={account.failedLoginCount || 0}
            />
            <InfoRow
              icon={LockKeyhole}
              tone="bg-slate-50 text-slate-600"
              label="Đổi mật khẩu lần cuối"
              value={account.passwordChangedAt ? dateTime(account.passwordChangedAt) : 'Chưa đổi'}
            />
          </div>

          <button
            type="button"
            onClick={onViewDetail}
            className="w-full mt-3 flex items-center gap-3 px-4 py-3 rounded-xl bg-[#e6f5f3] text-[#127e7a] text-sm font-semibold hover:bg-[#d8efec] cursor-pointer"
          >
            <FileText className="w-5 h-5" />
            <span className="flex-1 text-left">Xem chi tiết & lịch sử đăng nhập</span>
            <ChevronRight className="w-4 h-4" />
          </button>
        </aside>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 flex flex-wrap items-center justify-end gap-2.5 sticky bottom-4">
        {submitError && <p role="alert" className="text-xs text-rose-600 mr-auto">{submitError}</p>}
        {touched && Object.keys(errors).length > 0 && (
          <p role="alert" className="text-xs text-rose-600 mr-auto">Vui lòng sửa các ô được đánh dấu.</p>
        )}
        <button
          type="button"
          disabled={!dirty}
          onClick={() => {
            setForm(initialForm);
            setTouched(false);
          }}
          className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <RotateCcw className="w-4 h-4" />
          Khôi phục
        </button>
        <button type="button" onClick={onCancel} className="px-5 py-2.5 text-sm font-medium text-slate-700 bg-slate-100 rounded-xl hover:bg-slate-200 cursor-pointer">
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

export default AccountEditView;
