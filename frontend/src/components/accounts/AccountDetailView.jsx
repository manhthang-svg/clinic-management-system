import { useState } from 'react';
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
  Building2,
} from 'lucide-react';
import {
  ROLE_MAP,
  STATUS_LABELS,
  GENDER_LABELS,
  ROLE_PERMISSIONS,
  LOGIN_EVENT_LABELS,
  CHANGE_ACTION_LABELS,
  CHANGE_FIELD_LABELS,
} from '../../constants/accountData';
import { avatarTone, initialsOf, formatDate, formatTime, formatPhone } from './accountFormat';
import { AccountPageHeader } from './AccountFormParts';

const VISIBLE_PERMISSIONS = 8;
const HISTORY_PAGE_SIZE = 5;

const EVENT_STYLES = {
  LOGIN_SUCCEEDED: { icon: LogIn, tone: 'bg-emerald-50 text-emerald-600' },
  LOGOUT: { icon: LogOut, tone: 'bg-blue-50 text-blue-600' },
  LOGIN_FAILED: { icon: CircleAlert, tone: 'bg-rose-50 text-rose-600' },
  LOGIN_THROTTLED: { icon: CircleAlert, tone: 'bg-amber-50 text-amber-600' },
};

function parseValues(json) {
  if (!json) return {};
  try {
    const value = JSON.parse(json);
    return value && typeof value === 'object' ? value : {};
  } catch {
    return {};
  }
}

/** Displays a recorded field value the way the rest of the screen shows it. */
function displayValue(field, value) {
  if (value === null || value === undefined || value === '') return '—';
  if (field === 'role') return ROLE_MAP[value]?.label || value;
  if (field === 'gender') return GENDER_LABELS[value] || value;
  if (field === 'status') return STATUS_LABELS[value] || value;
  if (field === 'dateOfBirth') return formatDate(value);
  if (field === 'phoneNumber') return formatPhone(String(value));
  return String(value);
}

/** Username for an account id; the seeded Admin and bootstrap actions have no actor. */
function actorName(accountNames, accountId) {
  if (!accountId) return 'Hệ thống';
  return accountNames?.get(accountId) || `tài khoản ${accountId.slice(0, 8)}…`;
}

function ChangeHistoryList({ entries, accountNames }) {
  return (
    <ul className="divide-y divide-slate-100 text-sm">
      {entries.map((entry) => {
        const oldValues = parseValues(entry.oldValue);
        const newValues = parseValues(entry.newValue);
        const fields = entry.changedFields?.length ? entry.changedFields : Object.keys(newValues);
        return (
          <li key={entry.id} className="py-3">
            <div className="flex items-center justify-between gap-3">
              <span className="font-semibold text-slate-800">{CHANGE_ACTION_LABELS[entry.action] || entry.action}</span>
              <span className="text-xs text-slate-500">{formatDate(entry.occurredAt)} {formatTime(entry.occurredAt)}</span>
            </div>
            <p className="mt-0.5 text-xs text-slate-500">Người thực hiện: {actorName(accountNames, entry.actorAccountId)}</p>
            {fields.length > 0 && entry.action !== 'CREATE' && (
              <dl className="mt-1.5 space-y-1 text-xs">
                {fields.map((field) => (
                  <div key={field} className="grid grid-cols-[120px_minmax(0,1fr)] gap-2">
                    <dt className="text-slate-500">{CHANGE_FIELD_LABELS[field] || field}</dt>
                    <dd className="text-slate-700 break-words">
                      <span className="text-slate-400 line-through">{displayValue(field, oldValues[field])}</span>
                      {' → '}
                      <span className="font-medium">{displayValue(field, newValues[field])}</span>
                    </dd>
                  </div>
                ))}
              </dl>
            )}
          </li>
        );
      })}
    </ul>
  );
}

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

function HistoryPager({ page, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-3 flex items-center justify-between gap-3 text-xs text-slate-500">
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        className="px-3 py-1.5 border border-slate-200 rounded-lg hover:bg-slate-50 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
      >
        Mới hơn
      </button>
      <span>Trang {page + 1}/{totalPages}</span>
      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={page + 1 >= totalPages}
        className="px-3 py-1.5 border border-slate-200 rounded-lg hover:bg-slate-50 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
      >
        Cũ hơn
      </button>
    </div>
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

/** UC-6 View Account Detail, with entry points to UC-8 … UC-11 and room assignment (UC-15). */
export function AccountDetailView({ account, loginEvents, changeHistory = [], historyPage, onHistoryPageChange, accountNames, workInProgress, isSelf, onBack, onGoToList, onEdit, onAssignRole, onAssignRoom, onToggleLock, onResetPassword }) {
  const [loginHistoryState, setLoginHistoryState] = useState({ accountId: account.id, page: 0 });
  const locked = account.status === 'LOCKED';
  const permissions = ROLE_PERMISSIONS[account.role] || { modules: [], actions: [] };
  const hasRoom = account.role === 'DOCTOR' || account.role === 'TECHNICIAN';
  const room = account.roomName
    ? { name: account.roomName, department: account.departmentName }
    : null;
  const actions = account.allowedActions || {};
  const canAssignRoom = Boolean(actions.assignRoom && hasRoom && !isSelf);
  const loginHistoryTotalPages = Math.max(1, Math.ceil(loginEvents.length / HISTORY_PAGE_SIZE));
  const loginHistoryPage = loginHistoryState.accountId === account.id
    ? Math.min(loginHistoryState.page, loginHistoryTotalPages - 1)
    : 0;
  const visibleLoginEvents = loginEvents.slice(
    loginHistoryPage * HISTORY_PAGE_SIZE,
    (loginHistoryPage + 1) * HISTORY_PAGE_SIZE,
  );

  const personalRows = [
    ['Mã tài khoản', account.id],
    ['Họ và tên', account.fullName],
    ['Học hàm / học vị', account.title || '—'],
    ['Số giấy phép hành nghề', account.practiceLicenseNo || '—'],
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
      ['Người khóa', actorName(accountNames, account.lockedByAccountId)],
    );
  }

  return (
    <div className="space-y-6">
      <AccountPageHeader
        title="Chi tiết tài khoản"
        description="Xem thông tin, trạng thái và quyền truy cập của tài khoản nhân viên"
        onBack={onBack}
        crumbs={[{ label: 'Quản lý tài khoản', onClick: onGoToList }, { label: 'Chi tiết tài khoản' }]}
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
            note={account.createdByAccountId ? `bởi ${actorName(accountNames, account.createdByAccountId)}` : 'Khởi tạo cùng hệ thống'}
          />
        </div>

        <div className="grid grid-cols-2 gap-2.5 w-full xl:w-96 xl:border-l xl:border-slate-100 xl:pl-5">
          {actions.update !== false && <button
            type="button"
            onClick={onEdit}
            className="col-span-2 min-h-11 inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:border-slate-300 hover:bg-slate-50 transition-colors cursor-pointer"
          >
            <Pencil className="w-4 h-4" />
            Chỉnh sửa tài khoản
          </button>}
          {actions.assignRole && (
            <button
              type="button"
              onClick={onAssignRole}
              className={`${canAssignRoom ? '' : 'col-span-2'} min-h-11 inline-flex items-center justify-center gap-2 px-3 py-2.5 text-sm font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:border-slate-300 hover:bg-slate-50 transition-colors cursor-pointer`}
            >
              <UserCog className="w-4 h-4" />
              Đổi vai trò
            </button>
          )}
          {canAssignRoom && (
            <button
              type="button"
              onClick={onAssignRoom}
              className="min-h-11 inline-flex items-center justify-center gap-2 px-3 py-2.5 text-sm font-semibold whitespace-nowrap text-slate-700 bg-white border border-slate-200 rounded-xl hover:border-slate-300 hover:bg-slate-50 transition-colors cursor-pointer"
            >
              <Building2 className="w-4 h-4" />
              Phân công phòng
            </button>
          )}
          {actions.resetPassword && !isSelf && (
            <button
              type="button"
              onClick={onResetPassword}
              className="col-span-2 min-h-11 inline-flex items-center justify-center gap-2 px-3 py-2.5 text-sm font-semibold text-[#b56f08] bg-[#fff8e8] border border-[#f4d99f] rounded-xl hover:bg-[#fdf0d1] hover:border-[#edc978] transition-colors cursor-pointer"
            >
              <KeyRound className="w-4 h-4" />
              Đặt lại mật khẩu
            </button>
          )}
          {!isSelf && (actions.lock || actions.unlock) && (
            <button
              type="button"
              onClick={onToggleLock}
              className={`col-span-2 min-h-11 inline-flex items-center justify-center gap-2 px-3 py-2.5 text-sm font-semibold rounded-xl border transition-colors cursor-pointer ${
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

          <SectionCard icon={Briefcase} title="Công việc đang xử lý">
            <InfoList rows={[
              ['Lượt khám đang xử lý', workInProgress?.visitsInProgress ?? 0],
              ['Chỉ định CLS đang xử lý', workInProgress?.ordersInProgress ?? 0],
            ]} />
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
                {visibleLoginEvents.map((event) => {
                  const style = EVENT_STYLES[event.type] || EVENT_STYLES.LOGIN_FAILED;
                  const Icon = style.icon;
                  return (
                    <li key={event.id} className="flex items-center gap-3 py-2.5">
                      <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${style.tone}`}>
                        <Icon className="w-4 h-4" />
                      </div>
                      <div className="flex-1">
                        <div className="text-sm font-semibold text-slate-800">{LOGIN_EVENT_LABELS[event.type] || event.type}</div>
                        <div className="text-xs text-slate-500">IP: {event.ipAddress || '—'}</div>
                      </div>
                      <div className="text-right text-xs text-slate-500">
                        <div>{formatDate(event.occurredAt)}</div>
                        <div>{formatTime(event.occurredAt)}</div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
            <HistoryPager
              page={loginHistoryPage}
              totalPages={loginHistoryTotalPages}
              onPageChange={(nextPage) => setLoginHistoryState({ accountId: account.id, page: nextPage })}
            />
          </SectionCard>

          <SectionCard icon={Clock} title="Lịch sử thay đổi tài khoản">
            {changeHistory.length === 0 ? (
              <p className="text-sm text-slate-400">Chưa có thay đổi nào được ghi nhận.</p>
            ) : (
              <>
                <ChangeHistoryList entries={changeHistory} accountNames={accountNames} />
                {historyPage && (
                  <HistoryPager
                    page={historyPage.page}
                    totalPages={historyPage.totalPages}
                    onPageChange={onHistoryPageChange}
                  />
                )}
              </>
            )}
          </SectionCard>
        </div>
      </div>
    </div>
  );
}

export default AccountDetailView;
