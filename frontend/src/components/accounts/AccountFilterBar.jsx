import { Search, ChevronDown, RotateCcw } from 'lucide-react';

export function AccountFilterBar({
  searchQuery,
  setSearchQuery,
  roleFilter,
  setRoleFilter,
  statusFilter,
  setStatusFilter,
  deptFilter,
  setDeptFilter,
  onReset,
}) {
  return (
    <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-wrap items-center justify-between gap-3">
      {/* Search Input */}
      <div className="relative flex-1 min-w-[260px]">
        <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Tìm theo mã NV, tên, email hoặc số điện thoại..."
          className="w-full pl-10 pr-4 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white transition-all placeholder:text-slate-400"
        />
      </div>

      {/* Filter Dropdowns */}
      <div className="flex flex-wrap items-center gap-2.5">
        {/* Role filter */}
        <div className="relative">
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="appearance-none pl-3 pr-8 py-2 text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] cursor-pointer"
          >
            <option value="ALL">Tất cả vai trò</option>
            <option value="DOCTOR">Bác sĩ</option>
            <option value="RECEPTIONIST">Lễ tân</option>
            <option value="CASHIER">Thu ngân</option>
            <option value="TECHNICIAN">Kỹ thuật viên</option>
          </select>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>

        {/* Status filter */}
        <div className="relative">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="appearance-none pl-3 pr-8 py-2 text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] cursor-pointer"
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="LOCKED">Bị khóa</option>
          </select>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>

        {/* Department filter */}
        <div className="relative">
          <select
            value={deptFilter}
            onChange={(e) => setDeptFilter(e.target.value)}
            className="appearance-none pl-3 pr-8 py-2 text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] cursor-pointer"
          >
            <option value="ALL">Tất cả khoa / phòng</option>
            <option value="Khoa Nội">Khoa Nội</option>
            <option value="Khoa Ngoại">Khoa Ngoại</option>
            <option value="Phòng Xét nghiệm">Phòng Xét nghiệm</option>
            <option value="Lễ tân">Bộ phận Lễ tân</option>
            <option value="Thu ngân">Phòng Thu ngân</option>
          </select>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>

        {/* Reset filter */}
        <button
          type="button"
          onClick={onReset}
          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          <span>Đặt lại bộ lọc</span>
        </button>
      </div>
    </div>
  );
}

export default AccountFilterBar;
