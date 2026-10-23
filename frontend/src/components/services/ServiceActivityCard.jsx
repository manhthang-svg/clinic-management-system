import { Tag, ShieldPlus, Power, PowerOff } from 'lucide-react';

const ACTIVITY_STYLES = {
  CLINIC_PRICE: { icon: Tag, tone: 'bg-amber-50 text-amber-600' },
  INSURANCE_PRICE: { icon: ShieldPlus, tone: 'bg-orange-50 text-orange-600' },
  ACTIVATED: { icon: Power, tone: 'bg-teal-50 text-teal-600' },
  DEACTIVATED: { icon: PowerOff, tone: 'bg-slate-100 text-slate-500' },
};

/** Latest service catalog changes made by Admin. */
export function ServiceActivityCard({ activities, limit = 5 }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4">
      <div className="flex items-center justify-between gap-2 mb-4 whitespace-nowrap">
        <h2 className="text-sm! leading-5! font-bold! text-[#1b2533] truncate">Thay đổi gần đây</h2>
        <button type="button" className="shrink-0 text-[11px] font-semibold text-[#127e7a] hover:underline cursor-pointer">
          Xem tất cả
        </button>
      </div>

      {activities.length === 0 ? (
        <p className="text-xs text-slate-400">Chưa có thay đổi nào.</p>
      ) : (
        <ul className="space-y-4">
          {activities.slice(0, limit).map((activity) => {
            const style = ACTIVITY_STYLES[activity.type] || ACTIVITY_STYLES.CLINIC_PRICE;
            const Icon = style.icon;
            return (
              <li key={activity.id} className="flex items-start gap-3">
                <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${style.tone}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <div className="min-w-0 text-xs">
                  <div className="font-semibold text-slate-800">{activity.title}</div>
                  <div className="text-slate-500">{activity.detail}</div>
                  <div className="text-[11px] text-slate-400 mt-0.5">{activity.time}</div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

export default ServiceActivityCard;
