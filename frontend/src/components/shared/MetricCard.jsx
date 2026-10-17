export function MetricCard({
  title,
  value,
  icon: Icon,
  iconBg = 'bg-teal-50 text-[#0d9488]',
  valueColor = 'text-slate-900',
  subtitle,
  dotColor = 'bg-teal-500',
  footerLabel,
  footerValue,
  footerValueColor = 'text-teal-700',
}) {
  return (
    <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-col justify-between">
      <div className="flex items-center justify-between mb-2">
        <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
          {title}
        </span>
        {Icon && (
          <div className={`w-8 h-8 rounded-xl ${iconBg} flex items-center justify-center`}>
            <Icon className="w-4 h-4" />
          </div>
        )}
      </div>
      <div className={`text-3xl font-black ${valueColor} tracking-tight mb-2`}>
        {value}
      </div>
      {subtitle && (
        <div className="text-[11px] text-slate-500 flex items-center gap-1 mb-2">
          {dotColor && <span className={`w-1.5 h-1.5 rounded-full ${dotColor}`} />}
          <span>{subtitle}</span>
        </div>
      )}
      {(footerLabel || footerValue) && (
        <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
          <span>{footerLabel}</span>
          <span className={`font-semibold ${footerValueColor}`}>{footerValue}</span>
        </div>
      )}
    </div>
  );
}

export default MetricCard;
