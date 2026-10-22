// Layout pieces shared by the account forms (UC-4, UC-7, UC-8).
import { House, ChevronRight } from 'lucide-react';

export const INPUT_CLASS =
  'w-full px-3.5 py-2.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-800 placeholder:text-slate-400 ' +
  'focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-[#127e7a]/10 ' +
  'disabled:bg-slate-100 disabled:text-slate-500 disabled:cursor-not-allowed aria-invalid:border-rose-400';

export function FormField({ id, label, required, error, hint, icon: Icon, className = '', children }) {
  return (
    <div className={`space-y-1.5 ${className}`}>
      <label htmlFor={id} className="block text-sm font-semibold text-slate-700">
        {label}
        {required && <span className="text-rose-500 ml-0.5">*</span>}
      </label>
      <div className="relative">
        {Icon && <Icon className="w-4 h-4 text-slate-400 absolute left-3.5 top-3 pointer-events-none" />}
        {children}
      </div>
      {error ? (
        <p id={`${id}-error`} className="text-xs text-rose-600">{error}</p>
      ) : (
        hint && <p className="text-xs text-slate-400">{hint}</p>
      )}
    </div>
  );
}

export function FormSection({ icon: Icon, title, description, children, action }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5">
      <div className="flex items-start justify-between gap-3 mb-5">
        <div className="flex items-start gap-3">
          {Icon && (
            <div className="w-10 h-10 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
              <Icon className="w-5 h-5" />
            </div>
          )}
          <div>
            <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">{title}</h2>
            {description && <p className="text-xs text-slate-500 mt-0.5">{description}</p>}
          </div>
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

/** Page title with breadcrumb, shared by the account sub-pages. */
export function AccountPageHeader({ title, description, crumbs, onBack }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
      <div>
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            className="text-xs font-semibold text-[#127e7a] hover:underline cursor-pointer mb-2"
          >
            ← Quay lại
          </button>
        )}
        <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">{title}</h1>
        <p className="text-sm text-slate-500 mt-1">{description}</p>
      </div>
      <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-slate-500 flex-wrap">
        <House className="w-3.5 h-3.5" />
        {crumbs.map((crumb, i) => (
          <span key={crumb.label} className="flex items-center gap-1.5">
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            {crumb.onClick ? (
              <button type="button" onClick={crumb.onClick} className="hover:text-[#127e7a] cursor-pointer">{crumb.label}</button>
            ) : (
              <span className={i === crumbs.length - 1 ? 'text-slate-700 font-medium' : ''}>{crumb.label}</span>
            )}
          </span>
        ))}
      </nav>
    </div>
  );
}
