import { Search, ChevronDown, RotateCcw } from 'lucide-react';
import { SERVICE_TYPE_MAP, SERVICE_STATUS_LABELS } from '../../constants/serviceData';

const SELECT_CLASS =
  'appearance-none w-full pl-3.5 pr-9 py-2.5 text-xs font-medium text-slate-700 bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 cursor-pointer';

function FilterSelect({ label, value, onChange, allLabel, options, className = 'w-40' }) {
  return (
    <div className={`relative ${className}`}>
      <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value)} className={SELECT_CLASS}>
        <option value="ALL">{allLabel}</option>
        {options.map(([optionValue, optionLabel]) => (
          <option key={optionValue} value={optionValue}>{optionLabel}</option>
        ))}
      </select>
      <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
    </div>
  );
}

/** Service catalog search and filters: search term, type, group, and status combine with AND. */
export function ServiceFilterBar({
  searchQuery,
  setSearchQuery,
  typeFilter,
  setTypeFilter,
  groupFilter,
  setGroupFilter,
  statusFilter,
  setStatusFilter,
  onReset,
  groups = [],
}) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="relative flex-1 min-w-[220px]">
        <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="search"
          aria-label="Tìm kiếm dịch vụ"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Tìm theo mã hoặc tên dịch vụ..."
          className="w-full pl-10 pr-4 py-2.5 text-xs bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all placeholder:text-slate-400"
        />
      </div>

      <FilterSelect
        label="Lọc theo loại dịch vụ"
        value={typeFilter}
        onChange={setTypeFilter}
        allLabel="Tất cả loại"
        options={Object.entries(SERVICE_TYPE_MAP).map(([value, type]) => [value, type.label])}
      />

      <FilterSelect
        label="Lọc theo nhóm dịch vụ"
        value={groupFilter}
        onChange={setGroupFilter}
        allLabel="Tất cả nhóm"
        options={groups.map((group) => [group.id, group.name])}
      />

      <FilterSelect
        label="Lọc theo trạng thái"
        value={statusFilter}
        onChange={setStatusFilter}
        allLabel="Tất cả trạng thái"
        options={Object.entries(SERVICE_STATUS_LABELS)}
      />

      <button
        type="button"
        onClick={onReset}
        className="inline-flex items-center gap-2 px-3.5 py-2.5 text-xs font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 transition-all cursor-pointer whitespace-nowrap"
      >
        <RotateCcw className="w-3.5 h-3.5" />
        <span>Xóa bộ lọc</span>
      </button>
    </div>
  );
}

export default ServiceFilterBar;
