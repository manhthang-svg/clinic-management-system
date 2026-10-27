import { ChevronsLeft, ChevronsRight, ArrowUpDown } from 'lucide-react';
import { ROLE_MAP, STATUS_LABELS, PAGE_SIZE_OPTIONS } from '../../constants/accountData';
import { avatarTone, initialsOf, formatDate, formatTime, formatPhone } from './accountFormat';

/** Page numbers with gaps: 1 … 4 5 6 … 13. */
function pageItems(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const pages = new Set([1, total, current - 1, current, current + 1]);
  const sorted = [...pages].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b);
  const items = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) items.push(`gap-${p}`);
    items.push(p);
  });
  return items;
}

function StatusBadge({ account }) {
  const locked = account.status === 'LOCKED';
  return (
    <div className="flex flex-col items-start gap-1 whitespace-nowrap">
      <span
        className={`inline-flex px-2 py-0.5 rounded-md text-[11px] font-semibold border ${
          locked ? 'bg-[#fde9eb] text-[#d93b4a] border-[#f9cfd4]' : 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]'
        }`}
      >
        {STATUS_LABELS[account.status]}
      </span>
      {account.passwordChangeRequired && (
        <span className="inline-flex px-2 py-0.5 rounded-md text-[10px] font-semibold bg-[#fdf1d8] text-[#b7791f] border border-[#f6dfa9]">
          Chờ đổi mật khẩu
        </span>
      )}
    </div>
  );
}

function SortHeader({ field, sort, onSortChange, children, className = '' }) {
  const [currentField, direction] = sort.split(',');
  const active = currentField === field;
  return (
    <th className={`px-2 py-3 ${className}`}>
      <button
        type="button"
        onClick={() => onSortChange(`${field},${active && direction === 'asc' ? 'desc' : 'asc'}`)}
        className="inline-flex items-center gap-1 hover:text-[#127e7a] cursor-pointer"
      >
        {children}<ArrowUpDown className="w-3 h-3" aria-hidden="true" />
      </button>
    </th>
  );
}

/** UC-5 account list. Select a row to open UC-6 account details. */
export function AccountTable({
  accounts,
  loading = false,
  totalCount,
  page,
  pageSize,
  onPageChange,
  onPageSizeChange,
  onView,
  sort,
  onSortChange,
}) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const from = totalCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, totalCount);

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-[#f6f8f9] text-[11px] font-semibold text-[#1b2533]">
              <SortHeader field="username" sort={sort} onSortChange={onSortChange} className="pl-4">Tên đăng nhập</SortHeader>
              <SortHeader field="fullName" sort={sort} onSortChange={onSortChange}>Họ và tên</SortHeader>
              <th className="px-2 py-3">Khoa / Phòng</th>
              <SortHeader field="role" sort={sort} onSortChange={onSortChange}>Vai trò</SortHeader>
              <th className="px-2 py-3">Liên hệ</th>
              <SortHeader field="status" sort={sort} onSortChange={onSortChange}>Trạng thái</SortHeader>
              <SortHeader field="lastLoginAt" sort={sort} onSortChange={onSortChange}>Đăng nhập cuối</SortHeader>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-[11px]">
            {loading && accounts.length === 0 ? (
              Array.from({ length: 6 }, (_, index) => (
                <tr key={`account-skeleton-${index}`} aria-hidden="true">
                  <td colSpan="7" className="px-4 py-3.5">
                    <div className="h-7 animate-pulse rounded-lg bg-slate-100" />
                  </td>
                </tr>
              ))
            ) : accounts.length === 0 ? (
              <tr>
                <td colSpan="7" className="p-10 text-center text-slate-400">
                  Không tìm thấy tài khoản nào phù hợp với điều kiện lọc.
                </td>
              </tr>
            ) : (
              accounts.map((account) => {
                const role = ROLE_MAP[account.role];
                return (
                  <tr
                    key={account.id}
                    tabIndex={0}
                    aria-label={`Xem chi tiết ${account.username}`}
                    onClick={() => onView(account)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onView(account);
                      }
                    }}
                    className="cursor-pointer transition-colors hover:bg-slate-50/70 focus:bg-slate-50/70 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-[#127e7a]/20"
                  >
                    <td className="pl-4 pr-2 py-3 font-medium text-slate-700 whitespace-nowrap">{account.username}</td>
                    <td className="px-2 py-3">
                      <div className="flex items-center gap-2">
                        <div
                          className={`w-7 h-7 rounded-full font-bold text-[10px] flex items-center justify-center shrink-0 ${avatarTone(account.role)}`}
                          aria-hidden="true"
                        >
                          {initialsOf(account.fullName)}
                        </div>
                        <span className="font-semibold text-slate-800 leading-tight whitespace-nowrap">
                          {account.title && <span className="block text-[10px] text-slate-400 font-medium">{account.title}</span>}
                          {account.fullName}
                        </span>
                      </div>
                    </td>
                    <td className="px-2 py-3 text-slate-500 max-w-[150px]">
                      <div className="truncate" title={account.departmentName || ''}>{account.departmentName || '—'}</div>
                      <div className="truncate text-[10px] text-slate-400" title={account.roomName || ''}>{account.roomName || 'Chưa phân phòng'}</div>
                    </td>
                    <td className="px-2 py-3">
                      <span className={`inline-block px-2 py-0.5 rounded-md text-[10px] font-semibold border whitespace-nowrap ${role?.color}`}>
                        {role?.label || account.role}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-slate-500 max-w-[120px]">
                      <div className="truncate" title={account.email}>{account.email}</div>
                      <div className="text-[10px] text-slate-400 whitespace-nowrap">{formatPhone(account.phone)}</div>
                    </td>
                    <td className="px-2 py-3"><StatusBadge account={account} /></td>
                    <td className="px-2 py-3 text-slate-500 whitespace-nowrap">
                      {account.lastLoginAt ? (
                        <>
                          <div>{formatDate(account.lastLoginAt)}</div>
                          <div className="text-[10px] text-slate-400">{formatTime(account.lastLoginAt)}</div>
                        </>
                      ) : (
                        <span className="text-slate-400">Chưa đăng nhập</span>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <div className="pt-5 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
        <div className="flex items-center gap-3">
          <span>
            Hiển thị {from}–{to} trên tổng số {totalCount} tài khoản
          </span>
          <select
            aria-label="Số dòng mỗi trang"
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="bg-white border border-slate-200 rounded-lg px-2 py-1 text-xs focus:outline-none cursor-pointer"
          >
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>{size} / trang</option>
            ))}
          </select>
        </div>

        <nav aria-label="Phân trang" className="flex items-center gap-1.5">
          <button
            type="button"
            aria-label="Trang trước"
            disabled={page === 1}
            onClick={() => onPageChange(page - 1)}
            className="w-8 h-8 rounded-lg border border-slate-200 flex items-center justify-center text-slate-500 hover:bg-slate-50 disabled:text-slate-300 disabled:cursor-not-allowed cursor-pointer"
          >
            <ChevronsLeft className="w-3.5 h-3.5" />
          </button>
          {pageItems(page, totalPages).map((item) =>
            typeof item === 'string' ? (
              <span key={item} className="w-8 text-center text-slate-400">…</span>
            ) : (
              <button
                key={item}
                type="button"
                aria-current={item === page ? 'page' : undefined}
                onClick={() => onPageChange(item)}
                className={`w-8 h-8 rounded-lg text-xs font-semibold flex items-center justify-center cursor-pointer ${
                  item === page ? 'bg-[#e3f2f0] text-[#127e7a] border border-[#bfe0dc]' : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {item}
              </button>
            )
          )}
          <button
            type="button"
            aria-label="Trang sau"
            disabled={page === totalPages}
            onClick={() => onPageChange(page + 1)}
            className="w-8 h-8 rounded-lg border border-slate-200 flex items-center justify-center text-slate-500 hover:bg-slate-50 disabled:text-slate-300 disabled:cursor-not-allowed cursor-pointer"
          >
            <ChevronsRight className="w-3.5 h-3.5" />
          </button>
        </nav>
      </div>
    </div>
  );
}

export default AccountTable;
