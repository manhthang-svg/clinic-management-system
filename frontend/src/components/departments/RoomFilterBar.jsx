import { Search, ChevronDown, RotateCcw, Plus } from 'lucide-react';

const SELECT_CLASS =
  'appearance-none w-full pl-3.5 pr-8 py-2 text-xs font-medium text-slate-700 bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 cursor-pointer transition-all';

export function RoomFilterBar({
  searchQuery,
  setSearchQuery,
  departmentFilter,
  setDepartmentFilter,
  roomTypeFilter,
  setRoomTypeFilter,
  statusFilter,
  setStatusFilter,
  departments = [],
  onReset,
  onAdd,
}) {
  const isFiltered =
    Boolean(searchQuery) ||
    departmentFilter !== 'ALL' ||
    roomTypeFilter !== 'ALL' ||
    statusFilter !== 'ALL';

  return (
    <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
      {/* Bộ lọc bên trái */}
      <div className="flex flex-wrap items-center gap-2.5 flex-1">
        {/* Ô tìm kiếm */}
        <div className="relative flex-1 min-w-[220px]">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="search"
            aria-label="Tìm kiếm phòng"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo mã phòng, tên phòng, tầng/vị trí..."
            className="w-full pl-9 pr-3.5 py-2 text-xs bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all placeholder:text-slate-400"
          />
        </div>

        {/* Lọc theo Chuyên khoa */}
        <div className="relative min-w-[150px] w-44">
          <select
            aria-label="Lọc theo khoa"
            value={departmentFilter}
            onChange={(e) => setDepartmentFilter(e.target.value)}
            className={SELECT_CLASS}
          >
            <option value="ALL">Tất cả các khoa</option>
            {departments.map((dept) => (
              <option key={dept.id || dept.code} value={dept.id || dept.name}>
                {dept.name}
              </option>
            ))}
          </select>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>

        {/* Lọc theo Loại phòng */}
        <div className="relative min-w-[150px] w-44">
          <select
            aria-label="Lọc theo loại phòng"
            value={roomTypeFilter}
            onChange={(e) => setRoomTypeFilter(e.target.value)}
            className={SELECT_CLASS}
          >
            <option value="ALL">Tất cả loại phòng</option>
            <option value="EXAMINATION">Phòng khám lâm sàng</option>
            <option value="PARACLINICAL">Phòng cận lâm sàng</option>
          </select>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>

        {/* Lọc theo Trạng thái */}
        <div className="relative min-w-[140px] w-40">
          <select
            aria-label="Lọc theo trạng thái"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className={SELECT_CLASS}
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="INACTIVE">Tạm ngưng</option>
          </select>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>

        {/* Nút Đặt lại */}
        {isFiltered && (
          <button
            type="button"
            onClick={onReset}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 transition-all cursor-pointer shrink-0"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Đặt lại</span>
          </button>
        )}
      </div>

      {/* Nút chính bên phải */}
      <div className="shrink-0">
        <button
          type="button"
          onClick={onAdd}
          className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm transition-all cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          <span>Thêm phòng mới</span>
        </button>
      </div>
    </div>
  );
}

export default RoomFilterBar;
