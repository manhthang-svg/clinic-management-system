import { ROLE_MAP } from '../../constants/accountData';
import { percentOf } from './accountFormat';

const RADIUS = 52;
const STROKE = 16;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const GAP = 2;

/** Donut chart of accounts per role. */
export function AccountOverviewCard({ accounts }) {
  const total = accounts.length;
  const segments = Object.entries(ROLE_MAP).map(([role, meta]) => ({
    role,
    label: meta.label,
    color: meta.chart,
    count: accounts.filter((a) => a.role === role).length,
  }));

  let offset = 0;
  const arcs = segments
    .filter((s) => s.count > 0)
    .map((s) => {
      const length = (s.count / total) * CIRCUMFERENCE;
      const arc = { ...s, length: Math.max(length - GAP, 0), offset };
      offset += length;
      return arc;
    });

  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4">
      <h2 className="text-sm! leading-5! font-bold! text-[#1b2533]">Tổng quan tài khoản</h2>

      <div className="relative w-40 h-40 mx-auto my-5">
        <svg viewBox="0 0 140 140" className="w-full h-full -rotate-90" role="img" aria-label={`Tổng ${total} tài khoản theo vai trò`}>
          <circle cx="70" cy="70" r={RADIUS} fill="none" stroke="#f1f5f9" strokeWidth={STROKE} />
          {arcs.map((arc) => (
            <circle
              key={arc.role}
              cx="70"
              cy="70"
              r={RADIUS}
              fill="none"
              stroke={arc.color}
              strokeWidth={STROKE}
              strokeDasharray={`${arc.length} ${CIRCUMFERENCE - arc.length}`}
              strokeDashoffset={-arc.offset}
            />
          ))}
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-2xl font-extrabold text-slate-900">{total}</span>
          <span className="text-[11px] text-slate-400">Tổng tài khoản</span>
        </div>
      </div>

      <ul className="space-y-2.5 text-xs">
        {segments.map((s) => (
          <li key={s.role} className="flex items-center justify-between">
            <span className="flex items-center gap-2 text-slate-600">
              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: s.color }} />
              {s.label}
            </span>
            <span>
              <span className="font-bold text-slate-800">{s.count}</span>
              <span className="text-slate-400 ml-1">({percentOf(s.count, total)})</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default AccountOverviewCard;
