import { Search, ChevronDown, RotateCcw } from 'lucide-react';
import { ROLE_MAP, STATUS_LABELS } from '../../constants/accountData';

const SELECT_CLASS =
  'appearance-none w-full pl-3.5 pr-9 py-2.5 text-xs font-medium text-slate-700 bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 cursor-pointer';

/** UC-5 search and filters combine with AND. */
export function AccountFilterBar({
  searchQuery,
  setSearchQuery,
  roleFilter,
  setRoleFilter,
  statusFilter,
  setStatusFilter,
  departmentFilter,
  setDepartmentFilter,
  departments,
  onReset,
}) {
  return (
    <div className="flex flex-wrap xl:flex-nowrap items-center gap-3">
      <div className="relative w-full min-w-[240px] xl:flex-1 xl:min-w-0 xl:max-w-[320px]">
        <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="search"
          aria-label="Tìm kiếm tài khoản"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Tìm theo tên đăng nhập, họ tên, số điện thoại hoặc email..."
          className="w-full pl-10 pr-4 py-2.5 text-xs bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all placeholder:text-slate-400"
        />
      </div>

      <div className="relative w-[176px] shrink-0">
        <select
          aria-label="Lọc theo vai trò"
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          className={SELECT_CLASS}
        >
          <option value="ALL">Tất cả vai trò</option>
          {Object.entries(ROLE_MAP).map(([value, role]) => (
            <option key={value} value={value}>{role.label}</option>
          ))}
        </select>
        <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
      </div>

      <div className="relative w-[208px] shrink-0">
        <select
          aria-label="Lọc theo khoa"
          value={departmentFilter}
          onChange={(e) => setDepartmentFilter(e.target.value)}
          className={SELECT_CLASS}
        >
          <option value="ALL">Tất cả khoa</option>
          {departments.map((department) => (
            <option key={department.id} value={department.id}>{department.name}</option>
          ))}
        </select>
        <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
      </div>

      <div className="relative w-[176px] shrink-0">
        <select
          aria-label="Lọc theo trạng thái"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className={SELECT_CLASS}
        >
          <option value="ALL">Tất cả trạng thái</option>
          {Object.entries(STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
      </div>

      <button
        type="button"
        onClick={onReset}
        className="inline-flex shrink-0 items-center gap-2 px-5 py-2.5 text-xs font-semibold whitespace-nowrap text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 hover:text-slate-800 transition-all cursor-pointer"
      >
        <RotateCcw className="w-3.5 h-3.5" />
        <span>Đặt lại</span>
      </button>
    </div>
  );
}

export default AccountFilterBar;
