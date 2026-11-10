import { useMemo, useState } from 'react';
import {
  BadgeCheck,
  BriefcaseBusiness,
  CalendarDays,
  Check,
  ChevronRight,
  CircleAlert,
  Clock3,
  GraduationCap,
  Mail,
  Phone,
  ShieldCheck,
  UserCog,
} from 'lucide-react';
import { ROLE_MAP, ROLE_PERMISSIONS, STATUS_LABELS } from '../../constants/accountData';
import { AccountPageHeader, FormField, INPUT_CLASS } from './AccountFormParts';
import { ACADEMIC_TITLE_PATTERN, PRACTICE_LICENSE_PATTERN } from './accountForm';
import { avatarTone, formatDate, formatTime, initialsOf } from './accountFormat';

const ASSIGNABLE_ROLES = Object.entries(ROLE_MAP).filter(([role]) => role !== 'ADMIN');
const PROFESSIONAL_ROLES = ['DOCTOR', 'TECHNICIAN'];
const WITH_ICON = `${INPUT_CLASS} pl-10`;

/** UC-09 EF-03: client checks that mirror the backend (MSG02, MSG08, MSG05), for feedback only. */
function professionalErrors(title, license) {
  const errors = {};
  if (!title.trim()) errors.title = 'Học hàm / học vị là bắt buộc.';
  else if (title.trim().length > 50) errors.title = 'Vượt quá độ dài tối đa 50 ký tự.';
  else if (!ACADEMIC_TITLE_PATTERN.test(title.trim())) errors.title = 'Học hàm / học vị không đúng định dạng.';
  if (!license.trim()) errors.practiceLicenseNo = 'Số giấy phép hành nghề là bắt buộc.';
  else if (!PRACTICE_LICENSE_PATTERN.test(license.trim())) errors.practiceLicenseNo = 'Số giấy phép hành nghề không đúng định dạng.';
  return errors;
}

function AccountSummary({ account }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex flex-col lg:flex-row lg:items-center gap-5">
      <div className="flex items-center gap-4 flex-1 min-w-0">
        <div className={`w-16 h-16 rounded-full flex items-center justify-center text-lg font-extrabold ring-4 ring-[#e6f5f3] ${avatarTone(account.role)}`}>
          {initialsOf(account.fullName)}
        </div>
        <div className="min-w-0">
          <h2 className="text-xl font-extrabold text-[#1b2533] truncate">{account.fullName}</h2>
          <p className="text-sm text-slate-500">{account.username}</p>
          <span className={`inline-flex mt-2 px-2.5 py-1 rounded-lg border text-xs font-semibold ${ROLE_MAP[account.role]?.color}`}>
            {ROLE_MAP[account.role]?.label}
          </span>
        </div>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 lg:w-[58%]">
        <SummaryItem icon={Mail} label="Email" value={account.email || '—'} />
        <SummaryItem icon={Phone} label="Số điện thoại" value={account.phone || '—'} />
        <SummaryItem icon={ShieldCheck} label="Trạng thái" value={STATUS_LABELS[account.status]} positive={account.status === 'ACTIVE'} />
      </div>
    </section>
  );
}

function SummaryItem({ icon: Icon, label, value, positive }) {
  return (
    <div className="flex items-center gap-3 lg:border-l lg:border-slate-100 lg:pl-4">
      <div className="w-10 h-10 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0"><Icon className="w-5 h-5" /></div>
      <div className="min-w-0">
        <div className="text-xs text-slate-400">{label}</div>
        <div className={`text-sm font-semibold truncate ${positive ? 'text-emerald-600' : 'text-slate-700'}`}>{value}</div>
      </div>
    </div>
  );
}

/** UC-9 Assign Role. Doctor/Technician roles also require the academic title and practice license (I1-D47). */
export function AccountAssignRoleView({ account, onCancel, onGoToList, onAssign }) {
  const [role, setRole] = useState(account.role);
  const [title, setTitle] = useState(account.title || '');
  const [license, setLicense] = useState(account.practiceLicenseNo || '');
  const [touched, setTouched] = useState(false);
  const [serverErrors, setServerErrors] = useState({});
  const permissions = useMemo(
    () => ROLE_PERMISSIONS[role] || { modules: [], actions: [] },
    [role],
  );
  const currentPermissions = ROLE_PERMISSIONS[account.role] || { modules: [], actions: [] };
  const roleChanged = role !== account.role;
  const removesRoom = roleChanged && ['DOCTOR', 'TECHNICIAN'].includes(account.role) && account.roomName;
  const needsRoom = roleChanged && PROFESSIONAL_ROLES.includes(role);
  const errors = needsRoom ? professionalErrors(title, license) : {};
  const shownErrors = touched ? { ...serverErrors, ...errors } : {};
  const editTitle = (value) => {
    setTitle(value);
    setServerErrors(({ title: _dropped, ...rest }) => rest);
  };
  const editLicense = (value) => {
    setLicense(value);
    setServerErrors(({ practiceLicenseNo: _dropped, ...rest }) => rest);
  };
  const permissionRows = useMemo(() => {
    const all = [...new Set([...permissions.modules, ...permissions.actions])];
    return all.slice(0, 8);
  }, [permissions]);

  const submit = async (event) => {
    event.preventDefault();
    if (!roleChanged) return;
    setTouched(true);
    if (Object.keys(errors).length > 0) return;
    const result = await onAssign(role, needsRoom ? { academicTitle: title.trim(), practiceLicenseNo: license.trim() } : {});
    // UC-09 EF-03: the server's field errors (e.g. MSG07 for a license in use) are shown under the fields.
    setServerErrors(result?.fieldErrors || {});
  };

  return (
    <form onSubmit={submit} className="space-y-6" noValidate>
      <AccountPageHeader
        title="Phân quyền tài khoản"
        description="Thay đổi vai trò và kiểm tra quyền truy cập trước khi áp dụng"
        onBack={onCancel}
        crumbs={[{ label: 'Quản lý tài khoản', onClick: onGoToList }, { label: 'Phân quyền' }]}
      />
      <AccountSummary account={account} />

      <div className="grid grid-cols-1 xl:grid-cols-[300px_minmax(0,0.9fr)_minmax(360px,1.1fr)] gap-5 items-start">
        <aside className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 space-y-5">
          <h2 className="text-base! font-bold! text-[#1b2533]">Tổng quan tài khoản</h2>
          <div className="flex items-center gap-3">
            <div className={`w-12 h-12 rounded-full flex items-center justify-center font-bold ${avatarTone(account.role)}`}>{initialsOf(account.fullName)}</div>
            <div><div className="font-bold text-slate-800">{account.fullName}</div><div className="text-xs text-slate-500">{account.username}</div></div>
          </div>
          <div className="space-y-3 text-sm">
            <Info icon={BriefcaseBusiness} label="Vai trò hiện tại" value={ROLE_MAP[account.role]?.label} />
            <Info icon={CalendarDays} label="Ngày tạo" value={formatDate(account.createdAt)} />
            <Info icon={Clock3} label="Đăng nhập gần nhất" value={account.lastLoginAt ? `${formatDate(account.lastLoginAt)} ${formatTime(account.lastLoginAt)}` : 'Chưa đăng nhập'} />
          </div>
          <div className="pt-4 border-t border-slate-100">
            <div className="text-xs font-semibold text-slate-500 mb-3">Quyền hiện tại</div>
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-xl bg-emerald-50 p-3"><div className="text-xl font-extrabold text-emerald-700">{currentPermissions.actions.length}</div><div className="text-xs text-emerald-700">Quyền thao tác</div></div>
              <div className="rounded-xl bg-blue-50 p-3"><div className="text-xl font-extrabold text-blue-700">{currentPermissions.modules.length}</div><div className="text-xs text-blue-700">Phân hệ</div></div>
            </div>
          </div>
        </aside>

        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5">
          <h2 className="text-base! font-bold! text-[#1b2533]">Chọn vai trò mới</h2>
          <p className="text-xs text-slate-500 mt-1 mb-4">Mỗi tài khoản chỉ có một vai trò tại một thời điểm.</p>
          <div className="space-y-2">
            {ASSIGNABLE_ROLES.map(([value, meta]) => (
              <label key={value} className={`flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-colors ${role === value ? 'border-[#127e7a] bg-[#e9f7f5]' : 'border-slate-200 hover:bg-slate-50'}`}>
                <input type="radio" name="role" value={value} checked={role === value} onChange={() => setRole(value)} className="accent-[#127e7a]" />
                <div className={`w-9 h-9 rounded-xl flex items-center justify-center ${meta.color}`}><UserCog className="w-4 h-4" /></div>
                <div className="flex-1"><div className="text-sm font-bold text-slate-800">{meta.label}</div><div className="text-xs text-slate-500">{ROLE_PERMISSIONS[value]?.modules.join(' · ')}</div></div>
                {role === value && <Check className="w-4 h-4 text-[#127e7a]" />}
              </label>
            ))}
          </div>
          {needsRoom && (
            <div className="mt-5 pt-5 border-t border-slate-100 space-y-4">
              <div>
                <h3 className="text-sm! font-bold! text-[#1b2533]">Thông tin chuyên môn</h3>
                <p className="text-xs text-slate-500 mt-0.5">Bắt buộc đối với Bác sĩ và Kỹ thuật viên.</p>
              </div>
              <FormField id="role-title" label="Học hàm / học vị" required error={shownErrors.title} icon={GraduationCap}>
                <input
                  id="role-title"
                  type="text"
                  placeholder="vd: ThS.BS"
                  maxLength={50}
                  value={title}
                  onChange={(e) => editTitle(e.target.value)}
                  aria-invalid={shownErrors.title ? true : undefined}
                  aria-describedby={shownErrors.title ? 'role-title-error' : undefined}
                  className={WITH_ICON}
                />
              </FormField>
              <FormField id="role-license" label="Số giấy phép hành nghề" required error={shownErrors.practiceLicenseNo} icon={BadgeCheck}>
                <input
                  id="role-license"
                  type="text"
                  placeholder="vd: 000123/HCM-CCHN"
                  maxLength={50}
                  value={license}
                  onChange={(e) => editLicense(e.target.value)}
                  aria-invalid={shownErrors.practiceLicenseNo ? true : undefined}
                  aria-describedby={shownErrors.practiceLicenseNo ? 'role-license-error' : undefined}
                  className={WITH_ICON}
                />
              </FormField>
            </div>
          )}
        </section>

        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs overflow-hidden">
          <div className="p-5 border-b border-slate-100 flex items-center justify-between gap-3"><div><h2 className="text-base! font-bold! text-[#1b2533]">Quyền được cấp</h2><p className="text-xs text-slate-500 mt-1">Xem trước quyền của vai trò đã chọn</p></div><span className={`px-2.5 py-1 rounded-lg border text-xs font-semibold ${ROLE_MAP[role]?.color}`}>{ROLE_MAP[role]?.label}</span></div>
          <div className="divide-y divide-slate-100">
            {permissionRows.map((permission) => (
              <div key={permission} className="px-5 py-3 flex items-center gap-3"><div className="w-8 h-8 rounded-lg bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center"><Check className="w-4 h-4" /></div><span className="text-sm text-slate-700 flex-1">{permission}</span><span className="text-xs font-semibold text-emerald-600">Cho phép</span></div>
            ))}
          </div>
          <div className="m-5 p-3.5 rounded-xl bg-blue-50 text-blue-800 text-xs flex gap-2"><CircleAlert className="w-4 h-4 shrink-0" /><span>Thay đổi vai trò sẽ đăng xuất mọi phiên đang mở của tài khoản.</span></div>
        </section>
      </div>


      {(removesRoom || needsRoom) && <div className="rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-sm text-amber-800 flex gap-2"><CircleAlert className="w-5 h-5 shrink-0" /><span>{removesRoom ? `Phân công phòng ${account.roomName} hiện tại sẽ bị gỡ. ` : ''}{needsRoom ? 'Sau khi đổi vai trò, hãy phân công phòng từ màn hình chi tiết tài khoản.' : ''}</span></div>}
      <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 flex justify-end gap-2.5 sticky bottom-4">
        <button type="button" onClick={onCancel} className="px-5 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer">Hủy</button>
        <button type="submit" disabled={!roleChanged} className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"><UserCog className="w-4 h-4" />Áp dụng vai trò<ChevronRight className="w-4 h-4" /></button>
      </div>
    </form>
  );
}

function Info({ icon: Icon, label, value }) {
  return <div className="flex gap-3"><Icon className="w-4 h-4 text-slate-400 mt-0.5" /><div><div className="text-xs text-slate-400">{label}</div><div className="font-semibold text-slate-700">{value}</div></div></div>;
}

export default AccountAssignRoleView;
