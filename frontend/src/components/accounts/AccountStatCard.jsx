/** Summary tile on the account management page: icon, count, and a short note. */
export function AccountStatCard({ title, value, icon: Icon, iconTone, highlight, highlightTone, note }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex items-center gap-4">
      <div className={`w-14 h-14 rounded-2xl flex items-center justify-center shrink-0 ${iconTone}`}>
        <Icon className="w-6 h-6" />
      </div>
      <div className="min-w-0">
        <div className="text-xs font-medium text-slate-500">{title}</div>
        <div className="flex items-baseline gap-2 mt-1">
          <span className="text-2xl font-extrabold text-slate-900 tracking-tight">{value}</span>
          {highlight && <span className={`text-xs font-semibold ${highlightTone}`}>{highlight}</span>}
        </div>
        {note && <div className="text-[11px] text-slate-400 mt-0.5">{note}</div>}
      </div>
    </div>
  );
}

export default AccountStatCard;
