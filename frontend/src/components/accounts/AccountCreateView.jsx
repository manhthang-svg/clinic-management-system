import { useState } from 'react';
import { accountErrorMessage, accountFieldErrors, withoutField } from './accountErrors';
import {
  UserRound,
  Briefcase,
  LockKeyhole,
  ClipboardCheck,
  CheckCircle2,
  Circle,
  Info,
  Plus,
  X,
  Copy,
  KeyRound,
} from 'lucide-react';
import { ROLE_MAP, ROLE_PERMISSIONS } from '../../constants/accountData';
import { AccountPersonalFields } from './AccountPersonalFields';
import { EMPTY_ACCOUNT_FORM, validateAccountForm } from './accountForm';
import { INPUT_CLASS, FormField, FormSection, AccountPageHeader } from './AccountFormParts';

const CREATE_FIELDS = [
  'username', 'fullName', 'title', 'practiceLicenseNo', 'dateOfBirth', 'gender', 'phone', 'email', 'address',
];
// UC-7: ADMIN is never offered (AQ-17).
const ASSIGNABLE_ROLES = Object.entries(ROLE_MAP).filter(([role]) => role !== 'ADMIN');

function ChecklistItem({ done, title, detail }) {
  const Icon = done ? CheckCircle2 : Circle;
  return (
    <li className="flex items-start gap-3">
      <Icon className={`w-5 h-5 shrink-0 mt-0.5 ${done ? 'text-emerald-500' : 'text-slate-300'}`} />
      <div>
        <div className="text-sm font-semibold text-slate-800">{title}</div>
        <div className="text-xs text-slate-500">{detail}</div>
      </div>
    </li>
  );
}

/** One-time password result of UC-7, shown once and never again. */
function CreatedResult({ result, onViewDetail, onCreateAnother, onBackToList }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard?.writeText(result.temporaryPassword);
    setCopied(true);
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-8 max-w-xl mx-auto text-center">
      <div className="w-14 h-14 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto">
        <CheckCircle2 className="w-7 h-7" />
      </div>
      <h2 className="text-xl! font-extrabold! text-[#1b2533] mt-4">Đã tạo tài khoản</h2>
      <p className="text-sm text-slate-500 mt-1">
        {result.account.fullName} ({result.account.username}) · {ROLE_MAP[result.account.role]?.label}
      </p>

      <div className="mt-6 p-4 rounded-xl bg-amber-50 border border-amber-200 text-left">
        <div className="flex items-center gap-2 text-sm font-semibold text-amber-800">
          <KeyRound className="w-4 h-4" />
          Mật khẩu tạm thời
        </div>
        <div className="mt-2 flex items-center gap-2">
          <code className="flex-1 px-3 py-2 rounded-lg bg-white border border-amber-200 text-base font-mono tracking-wider text-slate-900">
            {result.temporaryPassword}
          </code>
          <button
            type="button"
            onClick={copy}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-amber-800 border border-amber-300 rounded-lg hover:bg-amber-100 cursor-pointer"
          >
            <Copy className="w-3.5 h-3.5" />
            {copied ? 'Đã sao chép' : 'Sao chép'}
          </button>
        </div>
        <p className="text-xs text-amber-800 mt-2">
          Mật khẩu chỉ hiển thị một lần. Hãy gửi cho nhân viên; họ phải đổi mật khẩu ở lần đăng nhập đầu tiên.
        </p>
      </div>

      {(result.account.role === 'DOCTOR' || result.account.role === 'TECHNICIAN') && (
        <p className="mt-4 flex items-start gap-2 text-left text-xs text-slate-600">
          <Info className="w-4 h-4 shrink-0 text-[#127e7a]" />
          Tài khoản chưa được phân công phòng. Phân công phòng làm việc ở chức năng Khoa / Phòng.
        </p>
      )}

      <div className="mt-6 flex flex-wrap justify-center gap-2.5">
        <button type="button" onClick={onViewDetail} className="px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer">
          Xem chi tiết
        </button>
        <button type="button" onClick={onCreateAnother} className="px-4 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer">
          Tạo tài khoản khác
        </button>
        <button type="button" onClick={onBackToList} className="px-4 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer">
          Về danh sách
        </button>
      </div>
    </section>
  );
}

/** UC-7 Create Account. */
export function AccountCreateView({ accounts, onCreate, onCancel, onGoToList, onViewDetail }) {
  const [form, setForm] = useState(EMPTY_ACCOUNT_FORM);
  const [touched, setTouched] = useState(false);
  const [result, setResult] = useState(null);
  const [submitError, setSubmitError] = useState('');
  const [serverErrors, setServerErrors] = useState({});

  const errors = validateAccountForm(form, { accounts, fields: CREATE_FIELDS });
  const shownErrors = { ...(touched ? errors : {}), ...serverErrors };
  const has = (field) => Boolean(errors[field]);
  const permissions = ROLE_PERMISSIONS[form.role];

  // Usernames are case-insensitive and stored in lowercase (AQ-05).
  const onChange = (field, value) => {
    setServerErrors((prev) => withoutField(prev, field));
    setForm((prev) => ({ ...prev, [field]: field === 'username' ? value.toLowerCase() : value }));
  };

  const submit = async (e) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0) return;
    setSubmitError('');
    setServerErrors({});
    try {
      setResult(await onCreate(form));
    } catch (error) {
      setServerErrors(accountFieldErrors(error));
      setSubmitError(accountErrorMessage(error));
    }
  };

  const header = (
    <AccountPageHeader
      title="Tạo tài khoản"
      description="Thêm tài khoản mới cho nhân viên phòng khám"
      onBack={onCancel}
      crumbs={[{ label: 'Quản trị hệ thống' }, { label: 'Quản lý tài khoản', onClick: onGoToList }, { label: 'Tạo tài khoản' }]}
    />
  );

  if (result) {
    return (
      <div className="space-y-6">
        {header}
        <CreatedResult
          result={result}
          onViewDetail={() => onViewDetail(result.account.id)}
          onCreateAnother={() => {
            setForm(EMPTY_ACCOUNT_FORM);
            setTouched(false);
            setResult(null);
          }}
          onBackToList={onGoToList}
        />
      </div>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      {header}

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_300px] gap-5 items-start">
        <div className="space-y-5">
          <FormSection icon={UserRound} title="Thông tin cá nhân" description="Thông tin cơ bản của nhân viên">
            <AccountPersonalFields
              form={form}
              errors={shownErrors}
              onChange={onChange}
              editable={CREATE_FIELDS}
              gridClass="md:grid-cols-2 2xl:grid-cols-3"
            />
          </FormSection>

          <FormSection icon={Briefcase} title="Vai trò & phân quyền" description="Mỗi tài khoản có đúng một vai trò">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <FormField id="account-role" label="Vai trò" required>
                <select
                  id="account-role"
                  value={form.role}
                  onChange={(e) => onChange('role', e.target.value)}
                  className={`${INPUT_CLASS} cursor-pointer`}
                >
                  {ASSIGNABLE_ROLES.map(([value, role]) => (
                    <option key={value} value={value}>{role.label}</option>
                  ))}
                </select>
              </FormField>
              <div className="space-y-1.5">
                <div className="text-sm font-semibold text-slate-700">Phân hệ được truy cập</div>
                <div className="flex flex-wrap gap-1.5">
                  {permissions.modules.map((m) => (
                    <span key={m} className="px-2.5 py-1 rounded-full bg-[#e6f5f3] text-[#127e7a] text-xs font-medium">{m}</span>
                  ))}
                </div>
                {(form.role === 'DOCTOR' || form.role === 'TECHNICIAN') && (
                  <p className="text-xs text-slate-500">Phòng làm việc được phân công sau khi tạo tài khoản.</p>
                )}
              </div>
            </div>
          </FormSection>

          <FormSection icon={LockKeyhole} title="Thiết lập đăng nhập" description="Hệ thống tự thiết lập khi tạo tài khoản">
            <ul className="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm">
              <li className="p-3.5 rounded-xl bg-slate-50 border border-slate-100">
                <div className="text-xs text-slate-500">Trạng thái ban đầu</div>
                <span className="inline-flex mt-1 px-2.5 py-0.5 rounded-lg bg-emerald-50 text-emerald-600 text-xs font-semibold">Hoạt động</span>
              </li>
              <li className="p-3.5 rounded-xl bg-slate-50 border border-slate-100">
                <div className="text-xs text-slate-500">Mật khẩu</div>
                <div className="font-medium text-slate-800 mt-1">Hệ thống tạo mật khẩu tạm 12 ký tự</div>
              </li>
              <li className="p-3.5 rounded-xl bg-slate-50 border border-slate-100">
                <div className="text-xs text-slate-500">Lần đăng nhập đầu</div>
                <div className="font-medium text-slate-800 mt-1">Bắt buộc đổi mật khẩu</div>
              </li>
            </ul>
          </FormSection>
        </div>

        <aside className="space-y-5 xl:sticky xl:top-4">
          <FormSection icon={ClipboardCheck} title="Kiểm tra trước khi tạo" description="Hoàn thành các mục dưới đây">
            <ul className="space-y-4">
              <ChecklistItem
                done={!has('fullName') && !has('username') && !has('phone') && !has('title') && !has('practiceLicenseNo')}
                title="Đã nhập thông tin bắt buộc"
                detail="Họ tên, tên đăng nhập, số điện thoại; học hàm / học vị và giấy phép hành nghề với Bác sĩ, Kỹ thuật viên"
              />
              <ChecklistItem
                done={form.username.trim() !== '' && !has('username')}
                title="Tên đăng nhập hợp lệ"
                detail="Đúng định dạng và chưa được sử dụng"
              />
              <ChecklistItem
                done={!has('email')}
                title="Email hợp lệ (không bắt buộc)"
                detail="Nếu nhập, không trùng với tài khoản khác, kể cả tài khoản đã khóa"
              />
              <ChecklistItem done={form.phone.trim() !== '' && !has('phone')} title="Số điện thoại hợp lệ" detail="10 chữ số, bắt đầu bằng 0" />
              <li className="flex items-start gap-3">
                <Info className="w-5 h-5 shrink-0 mt-0.5 text-[#3b7be0]" />
                <div>
                  <div className="text-sm font-semibold text-slate-800">Ghi nhật ký</div>
                  <div className="text-xs text-slate-500">Việc tạo tài khoản được ghi vào nhật ký hệ thống</div>
                </div>
              </li>
            </ul>
          </FormSection>

          <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-2.5">
            {submitError && <p role="alert" className="text-xs text-rose-600 text-center">{submitError}</p>}
            <button
              type="submit"
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              Tạo tài khoản
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer"
            >
              <X className="w-4 h-4" />
              Hủy
            </button>
            {touched && Object.keys(errors).length > 0 && (
              <p role="alert" className="text-xs text-rose-600 text-center">Vui lòng sửa các ô được đánh dấu.</p>
            )}
          </div>
        </aside>
      </div>
    </form>
  );
}

export default AccountCreateView;
