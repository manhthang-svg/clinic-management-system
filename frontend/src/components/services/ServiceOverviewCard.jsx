import { SERVICE_TYPE_MAP } from '../../constants/serviceData';
import { percentOf } from '../accounts/accountFormat';

const RADIUS = 52;
const STROKE = 16;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const GAP = 2;

/** Donut chart of services per type. */
export function ServiceOverviewCard({ services }) {
  const total = services.length;
  const segments = Object.entries(SERVICE_TYPE_MAP).map(([type, meta]) => ({
    type,
    label: meta.label,
    color: meta.chart,
    count: services.filter((s) => s.type === type).length,
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
      <h2 className="text-sm! leading-5! font-bold! text-[#1b2533]">Cơ cấu dịch vụ</h2>

      <div className="relative w-40 h-40 mx-auto my-5">
        <svg viewBox="0 0 140 140" className="w-full h-full -rotate-90" role="img" aria-label={`Tổng ${total} dịch vụ theo loại`}>
          <circle cx="70" cy="70" r={RADIUS} fill="none" stroke="#f1f5f9" strokeWidth={STROKE} />
          {arcs.map((arc) => (
            <circle
              key={arc.type}
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
          <span className="text-[11px] text-slate-400">dịch vụ</span>
        </div>
      </div>

      <ul className="space-y-2.5 text-xs">
        {segments.map((s) => (
          <li key={s.type} className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
            <span className="flex-1 text-slate-600">{s.label}</span>
            <span className="font-bold text-slate-800 w-6 text-right">{s.count}</span>
            <span className="text-slate-400 w-12 text-right">{percentOf(s.count, total)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default ServiceOverviewCard;
