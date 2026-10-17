import { Plus, Download } from 'lucide-react';

export function PageHeader({
  title = 'Quản lý tài khoản',
  badge = 'KM Enterprise',
  subtitle = 'Quản lý tài khoản nhân viên, vai trò và quyền truy cập hệ thống bệnh viện METPH.',
  exportLabel = 'Xuất danh sách',
  addLabel = 'Thêm tài khoản',
  onExport,
  onAdd,
}) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <div className="flex items-center gap-2.5">
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">
            {title}
          </h1>
          {badge && (
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80">
              {badge}
            </span>
          )}
        </div>
        {subtitle && (
          <p className="text-xs text-slate-500 mt-1">
            {subtitle}
          </p>
        )}
      </div>

      <div className="flex items-center gap-2.5">
        {onExport && (
          <button
            type="button"
            onClick={onExport}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 shadow-2xs transition-all cursor-pointer"
          >
            <Download className="w-3.5 h-3.5 text-slate-500" />
            <span>{exportLabel}</span>
          </button>
        )}

        {onAdd && (
          <button
            type="button"
            onClick={onAdd}
            className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm hover:shadow transition-all cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>{addLabel}</span>
          </button>
        )}
      </div>
    </div>
  );
}

export default PageHeader;
