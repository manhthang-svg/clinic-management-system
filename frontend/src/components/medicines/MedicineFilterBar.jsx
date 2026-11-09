import { Search, ChevronDown, RotateCcw } from 'lucide-react';
import { MEDICINE_CONTROL_GROUP_LABELS, MEDICINE_STATUS_LABELS } from '../../constants/medicineData';

const SELECT_CLASS =
  'appearance-none w-full pl-3.5 pr-9 py-2.5 text-xs font-medium text-slate-700 bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 cursor-pointer';

function FilterSelect({ label, value, onChange, allLabel, options }) {
  return (
    <div className="relative w-40">
      <select aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className={SELECT_CLASS}>
        <option value="ALL">{allLabel}</option>
        {options.map(([optionValue, optionLabel]) => (
          <option key={optionValue} value={optionValue}>{optionLabel}</option>
        ))}
      </select>
      <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
    </div>
  );
}

/** UC-81 search and filters; they combine with AND. */
export function MedicineFilterBar({ filters, onChange, onReset, routes = [], units = [] }) {
  const set = (key) => (value) => onChange({ ...filters, [key]: value });
  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="relative flex-1 min-w-[220px]">
        <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="search"
          aria-label="Tìm kiếm thuốc"
          value={filters.keyword}
          onChange={(event) => set('keyword')(event.target.value)}
          maxLength={100}
          placeholder="Tìm theo tên, hoạt chất, hàm lượng, mã thuốc, số đăng ký..."
          className="w-full pl-10 pr-4 py-2.5 text-xs bg-white border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all placeholder:text-slate-400"
        />
      </div>
      <FilterSelect
        label="Lọc theo đường dùng"
        value={filters.route}
        onChange={set('route')}
        allLabel="Tất cả đường dùng"
        options={routes.map((route) => [route, route])}
      />
      <FilterSelect
        label="Lọc theo đơn vị tính"
        value={filters.unit}
        onChange={set('unit')}
        allLabel="Tất cả đơn vị"
        options={units.map((unit) => [unit, unit])}
      />
      <FilterSelect
        label="Lọc theo nhóm kiểm soát"
        value={filters.controlGroup}
        onChange={set('controlGroup')}
        allLabel="Tất cả nhóm"
        options={Object.entries(MEDICINE_CONTROL_GROUP_LABELS)}
      />
      <FilterSelect
        label="Lọc theo trạng thái"
        value={filters.status}
        onChange={set('status')}
        allLabel="Tất cả trạng thái"
        options={Object.entries(MEDICINE_STATUS_LABELS)}
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

export default MedicineFilterBar;
