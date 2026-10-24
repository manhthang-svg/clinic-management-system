import {
  CalendarDays,
  CalendarPlus,
  Pencil,
  UserCog,
  KeyRound,
  Lock,
  LockOpen,
  User,
  Briefcase,
  ShieldCheck,
  Key,
  Clock,
  LogIn,
  LogOut,
  CircleAlert,
} from 'lucide-react';
import {
  ROLE_MAP,
  STATUS_LABELS,
  GENDER_LABELS,
  ROLE_PERMISSIONS,
  LOGIN_EVENT_LABELS,
} from '../../constants/accountData';
import { avatarTone, initialsOf, formatDate, formatTime, formatPhone } from './accountFormat';
import { AccountPageHeader } from './AccountFormParts';

const VISIBLE_PERMISSIONS = 8;

const EVENT_STYLES = {
  LOGIN_SUCCEEDED: { icon: LogIn, tone: 'bg-emerald-50 text-emerald-600' },
  LOGOUT: { icon: LogOut, tone: 'bg-blue-50 text-blue-600' },
  LOGIN_FAILED: { icon: CircleAlert, tone: 'bg-rose-50 text-rose-600' },
  LOGIN_THROTTLED: { icon: CircleAlert, tone: 'bg-amber-50 text-amber-600' },
};

function ageOf(isoDate) {
  const dob = new Date(isoDate);
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  if (now < new Date(now.getFullYear(), dob.getMonth(), dob.getDate())) age -= 1;
  return age;
}

function daysAgo(iso) {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / (24 * 60 * 60 * 1000));
  return days <= 0 ? 'hôm nay' : `${days} ngày trước`;
}

function RoleBadge({ role }) {
  const meta = ROLE_MAP[role];
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-lg border text-[11px] font-semibold ${meta?.color || 'bg-slate-100 text-slate-600 border-slate-200'}`}>
      {meta?.label || role}
    </span>
  );
}

function StatusBadge({ status }) {
  const locked = status === 'LOCKED';
  return (
    <span className={`inline-flex items-center px-3 py-1 rounded-lg text-xs font-semibold ${locked ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}`}>
      {STATUS_LABELS[status]}
    </span>
  );
}

function SectionCard({ icon: Icon, title, action, children }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 className="flex items-center gap-2.5 text-base! leading-6! font-bold! text-[#1b2533]">
          <Icon className="w-5 h-5 text-[#127e7a]" />
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function EditButton({ onClick, label = 'Sửa' }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer"
    >
      <Pencil className="w-3.5 h-3.5" />
      {label}
    </button>
  );
}

function InfoList({ rows }) {
  return (
    <dl className="divide-y divide-slate-100 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="grid grid-cols-[150px_minmax(0,1fr)] gap-4 py-2.5">
          <dt className="text-slate-500">{label}</dt>
          <dd className="text-slate-800 break-words">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function HeaderStat({ icon: Icon, label, iso, empty, note }) {
  return (
    <div className="flex items-start gap-3 px-6">
      <Icon className="w-6 h-6 text-[#127e7a] mt-1 shrink-0" />
      <div className="text-sm">
        <div className="text-xs text-slate-500">{label}</div>
        {iso ? (
          <>
            <div className="font-semibold text-slate-800 mt-0.5">{formatDate(iso)}</div>
            <div className="text-xs text-slate-500">{formatTime(iso)}</div>
            {note && <div className="text-xs text-slate-500">{note}</div>}
          </>
        ) : (
          <div className="font-semibold text-slate-400 mt-0.5">{empty}</div>
        )}
      </div>
    </div>
  );
}

/** UC-6 View Account Detail, with entry points to UC-8 … UC-11 (AQ-07, AQ-17). */
export function AccountDetailView({ account, loginEvents, changeHistory = [], workInProgress, isSelf, onBack, onGoToList, onEdit, onAssignRole, onToggleLock, onResetPassword }) {
  const locked = account.status === 'LOCKED';
  const permissions = ROLE_PERMISSIONS[account.role] || { modules: [], actions: [] };
  const hasRoom = account.role === 'DOCTOR' || account.role === 'TECHNICIAN';
  const room = account.roomName
    ? { name: account.roomName, department: account.departmentName }
    : null;
  const actions = account.allowedActions || {};

  const personalRows = [
    ['Mã tài khoản', account.id],
    ['Họ và tên', account.fullName],
    ['Học hàm / học vị', account.title || '—'],
    ['Tên đăng nhập', account.username],
    ['Ngày sinh', account.dateOfBirth ? `${formatDate(account.dateOfBirth)} (${ageOf(account.dateOfBirth)} tuổi)` : '—'],
    ['Giới tính', GENDER_LABELS[account.gender] || '—'],
    ['Số điện thoại', account.phone ? formatPhone(account.phone) : '—'],
    ['Email', account.email || '—'],
    ['Địa chỉ', account.address || '—'],
  ];

  let assignmentRows;
  if (!hasRoom) {
    assignmentRows = [['Phòng làm việc', <span className="text-slate-400">Không áp dụng cho vai trò này</span>]];
  } else if (!room) {
    assignmentRows = [['Phòng làm việc', <span className="text-amber-600 font-medium">Chưa được phân công</span>]];
  } else {
    assignmentRows = [
      ['Khoa', room.department],
      ['Phòng', room.name],
    ];
  }
  const accessRows = [
    ['Vai trò', <RoleBadge role={account.role} />],
    ['Phân hệ truy cập', (
      <div className="flex flex-wrap gap-1.5">
        {permissions.modules.map((m) => (
          <span key={m} className="px-2.5 py-0.5 rounded-full bg-[#e6f5f3] text-[#127e7a] text-xs font-medium">{m}</span>
        ))}
      </div>
    )],
    ['Đổi mật khẩu lần cuối', account.passwordChangedAt ? `${formatDate(account.passwordChangedAt)} (${daysAgo(account.passwordChangedAt)})` : 'Chưa đổi'],
    ['Yêu cầu đổi mật khẩu', account.passwordChangeRequired ? <span className="text-amber-600 font-medium">Có</span> : 'Không'],
    ['Đăng nhập sai liên tiếp', account.failedLoginCount || 0],
    // AQ-04: password login is blocked for 15 minutes after 5 failures; the status stays ACTIVE.
    ['Chặn đăng nhập đến', account.loginBlockedUntil && new Date(account.loginBlockedUntil) > new Date()
      ? `${formatTime(account.loginBlockedUntil)} ${formatDate(account.loginBlockedUntil)}`
      : 'Không bị chặn'],
    ['Trạng thái khóa', (
      <span className={`inline-flex px-2.5 py-0.5 rounded-lg text-xs font-semibold ${locked ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}`}>
        {locked ? 'Đang khóa' : 'Không khóa'}
      </span>
    )],
  ];
  if (locked) {
    accessRows.push(
      ['Lý do khóa', account.lockReason || '—'],
      ['Thời gian khóa', account.lockedAt ? `${formatTime(account.lockedAt)} ${formatDate(account.lockedAt)}` : '—'],
      ['Người khóa', account.lockedBy || 'Quản trị viên'],
    );
  }

  return (
    <div className="space-y-6">
      <AccountPageHeader
        title="Chi tiết tài khoản"
        description="Xem thông tin, trạng thái và quyền truy cập của tài khoản nhân viên"
        onBack={onBack}
        crumbs={[{ label: 'Quản trị hệ thống' }, { label: 'Quản lý tài khoản', onClick: onGoToList }, { label: 'Chi tiết tài khoản' }]}
      />

      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex flex-col xl:flex-row xl:items-center gap-5">
        <div className="flex items-center gap-5 flex-1 min-w-0">
          <div className={`w-24 h-24 rounded-full font-extrabold text-2xl flex items-center justify-center shrink-0 ring-4 ring-[#e6f5f3] ${avatarTone(account.role)}`}>
            {initialsOf(account.fullName)}
          </div>
          <div className="min-w-0">
            {account.title && <div className="text-xs font-semibold text-slate-400">{account.title}</div>}
            <div className="text-2xl font-extrabold text-[#1b2533] truncate">{account.fullName}</div>
            <div className="text-sm text-slate-500 mt-0.5">
              Tên đăng nhập: <span className="text-slate-700 font-medium">{account.username}</span>
            </div>
            <div className="flex items-center gap-2 mt-2">
              <RoleBadge role={account.role} />
              <StatusBadge status={account.status} />
            </div>
          </div>
        </div>

        <div className="flex divide-x divide-slate-100 xl:border-l xl:border-slate-100">
          <HeaderStat icon={CalendarDays} label="Đăng nhập gần nhất" iso={account.lastLoginAt} empty="Chưa đăng nhập" />
          <HeaderStat
            icon={CalendarPlus}
            label="Ngày tạo tài khoản"
            iso={account.createdAt}
            note={account.createdByAccountId ? `bởi tài khoản ${account.createdByAccountId}` : 'Khởi tạo cùng hệ thống'}
          />
        </div>

        <div className="grid grid-cols-2 gap-2.5 xl:w-80 xl:border-l xl:border-slate-100 xl:pl-5">
          {actions.update !== false && <button
            type="button"
            onClick={onEdit}
            className="col-span-2 inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer"
          >
            <Pencil className="w-4 h-4" />
            Chỉnh sửa tài khoản
          </button>}
          {actions.assignRole && (
            <button
              type="button"
              onClick={onAssignRole}
              className="inline-flex items-center justify-center gap-2 px-3 py-2.5 text-sm font-medium text-slate-700 border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer"
            >
              <UserCog className="w-4 h-4" />
              Đổi vai trò
            </button>
          )}
          {actions.resetPassword && !isSelf && (
            <button
              type="button"
              onClick={onResetPassword}
              className="inline-flex items-center justify-center gap-2 px-3 py-2.5 text-sm font-medium text-[#c27c0e] bg-[#fdf1dc] border border-[#f8e2b8] rounded-xl hover:bg-[#fbe8c6] transition-colors cursor-pointer"
            >
              <KeyRound className="w-4 h-4" />
              Đặt lại mật khẩu
            </button>
          )}
          {!isSelf && (actions.lock || actions.unlock) && (
            <button
              type="button"
              onClick={onToggleLock}
              className={`col-span-2 inline-flex items-center justify-center gap-2 px-3 py-2.5 text-sm font-medium rounded-xl border transition-colors cursor-pointer ${
                locked
                  ? 'text-emerald-700 bg-emerald-50 border-emerald-200 hover:bg-emerald-100'
                  : 'text-rose-600 bg-rose-50 border-rose-200 hover:bg-rose-100'
              }`}
            >
              {locked ? <LockOpen className="w-4 h-4" /> : <Lock className="w-4 h-4" />}
              {locked ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}
            </button>
          )}
        </div>
      </section>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-5 items-start">
        <div className="space-y-5">
          <SectionCard icon={User} title="Thông tin cá nhân" action={actions.update !== false ? <EditButton onClick={onEdit} /> : null}>
            <InfoList rows={personalRows} />
          </SectionCard>

          <SectionCard icon={Briefcase} title="Thông tin công tác">
            <InfoList rows={assignmentRows} />
          </SectionCard>
        </div>

        <div className="space-y-5">
          <SectionCard
            icon={ShieldCheck}
            title="Bảo mật & truy cập"
          >
            <InfoList rows={accessRows} />
          </SectionCard>

          <SectionCard
            icon={Key}
            title="Tóm tắt quyền hạn"
            action={<span className="text-xs font-semibold text-slate-500">{permissions.actions.length} quyền</span>}
          >
            <div className="flex flex-wrap gap-2">
              {permissions.actions.slice(0, VISIBLE_PERMISSIONS).map((p) => (
                <span key={p} className="px-3 py-1 rounded-full bg-[#e6f5f3] text-[#127e7a] text-xs font-medium">{p}</span>
              ))}
              {permissions.actions.length > VISIBLE_PERMISSIONS && (
                <span className="px-3 py-1 rounded-full bg-slate-100 text-slate-600 text-xs font-medium">
                  +{permissions.actions.length - VISIBLE_PERMISSIONS} quyền khác
                </span>
              )}
            </div>
          </SectionCard>

          <SectionCard icon={Clock} title="Lịch sử đăng nhập">
            {loginEvents.length === 0 ? (
              <p className="text-sm text-slate-400">Tài khoản chưa đăng nhập lần nào.</p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {loginEvents.map((event) => {
                  const style = EVENT_STYLES[event.type] || EVENT_STYLES.LOGIN_FAILED;
                  const Icon = style.icon;
                  return (
                    <li key={event.id} className="flex items-center gap-3 py-2.5">
                      <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${style.tone}`}>
                        <Icon className="w-4 h-4" />
                      </div>
                      <div className="flex-1 text-sm font-semibold text-slate-800">{LOGIN_EVENT_LABELS[event.type]}</div>
                      <div className="text-right text-xs text-slate-500">
                        <div>{formatDate(event.occurredAt)}</div>
                        <div>{formatTime(event.occurredAt)}</div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </SectionCard>

          <SectionCard icon={Briefcase} title="Công việc đang xử lý">
            <InfoList rows={[
              ['Lượt khám đang xử lý', workInProgress?.visitsInProgress ?? 0],
              ['Chỉ định CLS đang xử lý', workInProgress?.ordersInProgress ?? 0],
            ]} />
          </SectionCard>

          <SectionCard icon={Clock} title="Lịch sử thay đổi tài khoản">
            {changeHistory.length === 0 ? (
              <p className="text-sm text-slate-400">Chưa có thay đổi nào được ghi nhận.</p>
            ) : (
              <ul className="divide-y divide-slate-100 text-sm">
                {changeHistory.map((entry) => (
                  <li key={entry.id} className="py-3">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-semibold text-slate-800">{entry.action}</span>
                      <span className="text-xs text-slate-500">{formatDate(entry.occurredAt)} {formatTime(entry.occurredAt)}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      Trường thay đổi: {entry.changedFields?.join(', ') || '—'}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>
        </div>
      </div>
    </div>
  );
}

export default AccountDetailView;
