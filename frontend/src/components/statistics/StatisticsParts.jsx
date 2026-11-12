import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { NavLink } from 'react-router';
import { RefreshCw } from 'lucide-react';
import { FormAlert } from '../shared/FormFields';
import { PRESETS, formatNumber } from './dashboardPeriod';

/** Shared building blocks of the Admin statistics pages (UC-26 → UC-32). */

const CARD = 'bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5';
const INPUT = 'px-3 py-2 text-sm border border-slate-200 rounded-xl bg-white focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a]';

export function KpiCard({ icon: Icon, label, value, hint }) {
  return (
    <div className={`${CARD} flex items-start gap-4`}>
      <div className="w-11 h-11 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
        <Icon className="w-5 h-5" aria-hidden="true" />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium text-slate-500">{label}</p>
        <p className="text-2xl font-extrabold text-[#1b2533] tracking-tight mt-0.5 truncate">{value}</p>
        {hint && <p className="text-xs text-slate-500 mt-1">{hint}</p>}
      </div>
    </div>
  );
}

export function ChartCard({ title, description, actions, children, className = '' }) {
  return (
    <section className={`${CARD} ${className}`} aria-label={title}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">{title}</h2>
          {description && <p className="text-xs text-slate-500 mt-0.5">{description}</p>}
        </div>
        {actions}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function EmptyChart() {
  return (
    <div className="h-56 flex items-center justify-center text-sm text-slate-400 bg-slate-50 rounded-xl">
      Chưa có dữ liệu trong khoảng thời gian này.
    </div>
  );
}

function Legend({ items }) {
  return (
    <ul className="space-y-2 text-sm">
      {items.map((item) => (
        <li key={item.name} className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: item.color }} aria-hidden="true" />
          <span className="text-slate-600 flex-1">{item.name}</span>
          <span className="font-semibold text-slate-800 tabular-nums">{item.display ?? formatNumber(item.value)}</span>
        </li>
      ))}
    </ul>
  );
}

/** Donut chart with a value legend; items are { name, value, color, display? }. */
export function DonutWithLegend({ items, label }) {
  const hasData = items.some((item) => item.value > 0);
  if (!hasData) return <EmptyChart />;
  return (
    <div className="grid grid-cols-1 sm:grid-cols-[180px_minmax(0,1fr)] gap-4 items-center">
      <div className="h-44" role="img" aria-label={label}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={items} dataKey="value" nameKey="name" innerRadius={50} outerRadius={78} paddingAngle={2} stroke="none">
              {items.map((item) => <Cell key={item.name} fill={item.color} />)}
            </Pie>
            <Tooltip formatter={(value, name, entry) => [entry.payload.display ?? formatNumber(value), name]} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <Legend items={items} />
    </div>
  );
}

/** Segmented single choice, e.g. day / month. */
export function SegmentedControl({ label, options, value, onChange }) {
  return (
    <div className="inline-flex p-1 rounded-xl bg-slate-100" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
          className={`px-3 py-1.5 text-sm rounded-lg cursor-pointer transition-colors ${value === option.value
            ? 'bg-white text-[#127e7a] font-semibold shadow-2xs'
            : 'text-slate-600 hover:text-slate-800'}`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/** Quick periods plus from/to date inputs; `periodError` is shown as the single alert. */
export function PeriodFilter({ idPrefix, period, presetId, periodError, onPreset, onDateChange }) {
  return (
    <section className={`${CARD} flex flex-wrap items-end gap-4`} aria-label="Khoảng thời gian">
      <div className="flex flex-wrap gap-2" role="group" aria-label="Chọn nhanh">
        {PRESETS.map((preset) => (
          <button
            key={preset.id}
            type="button"
            aria-pressed={presetId === preset.id}
            onClick={() => onPreset(preset)}
            className={`px-3.5 py-2 text-sm rounded-xl border cursor-pointer transition-colors ${presetId === preset.id
              ? 'bg-[#127e7a] border-[#127e7a] text-white font-semibold'
              : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'}`}
          >
            {preset.label}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap items-end gap-3 sm:ml-auto">
        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-from`} className="text-xs font-medium text-slate-600">Từ ngày</label>
          <input id={`${idPrefix}-from`} type="date" value={period.from} max={period.to || undefined} onChange={onDateChange('from')} className={INPUT} />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-to`} className="text-xs font-medium text-slate-600">Đến ngày</label>
          <input id={`${idPrefix}-to`} type="date" value={period.to} min={period.from || undefined} onChange={onDateChange('to')} className={INPUT} />
        </div>
      </div>
      {periodError && <p role="alert" className="w-full text-xs text-rose-600">{periodError}</p>}
    </section>
  );
}

export function RefreshButton({ loading, disabled, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={loading || disabled}
      className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
    >
      <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
      {loading ? 'Đang tải…' : 'Làm mới'}
    </button>
  );
}

export function LoadError({ message, onRetry }) {
  return (
    <div className="space-y-3 max-w-xl">
      <FormAlert>{message}</FormAlert>
      <button
        type="button"
        onClick={onRetry}
        className="px-4 py-2 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
      >
        Thử lại
      </button>
    </div>
  );
}

const REPORT_TABS = [
  { to: '/admin/reports', label: 'Lượt khám', end: true },
  { to: '/admin/reports/doctors', label: 'Bác sĩ' },
  { to: '/admin/reports/departments', label: 'Khoa / Phòng' },
  { to: '/admin/reports/paraclinical', label: 'Cận lâm sàng' },
];

/** Tabs of the "Báo cáo thống kê" section, one per statistics use case. */
export function ReportTabs() {
  return (
    <nav aria-label="Loại báo cáo" className="flex flex-wrap gap-1 border-b border-slate-200">
      {REPORT_TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          className={({ isActive }) => `px-4 py-2.5 text-sm -mb-px border-b-2 transition-colors ${isActive
            ? 'border-[#127e7a] text-[#127e7a] font-semibold'
            : 'border-transparent text-slate-500 hover:text-slate-800'}`}
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  );
}
