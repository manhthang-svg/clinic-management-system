import { UserPlus, UserPen, KeyRound, Lock, LockOpen } from 'lucide-react';

const ACTIVITY_STYLES = {
  CREATED: { icon: UserPlus, tone: 'bg-emerald-50 text-emerald-600' },
  UPDATED: { icon: UserPen, tone: 'bg-blue-50 text-blue-600' },
  PASSWORD_RESET: { icon: KeyRound, tone: 'bg-amber-50 text-amber-600' },
  LOCKED: { icon: Lock, tone: 'bg-rose-50 text-rose-600' },
  UNLOCKED: { icon: LockOpen, tone: 'bg-teal-50 text-teal-600' },
};

/** Latest account changes made by Admin. */
export function AccountActivityCard({ activities, limit = 5 }) {
  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4">
      <div className="flex items-center justify-between gap-2 mb-4 whitespace-nowrap">
        <h2 className="text-sm! leading-5! font-bold! text-[#1b2533] truncate">Hoạt động gần đây</h2>
        <button type="button" className="shrink-0 text-[11px] font-semibold text-[#127e7a] hover:underline cursor-pointer">
          Xem tất cả
        </button>
      </div>

      {activities.length === 0 ? (
        <p className="text-xs text-slate-400">Chưa có hoạt động nào.</p>
      ) : (
        <ul className="space-y-4">
          {activities.slice(0, limit).map((activity) => {
            const style = ACTIVITY_STYLES[activity.type] || ACTIVITY_STYLES.UPDATED;
            const Icon = style.icon;
            return (
              <li key={activity.id} className="flex items-start gap-3">
                <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${style.tone}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <div className="min-w-0 text-xs">
                  <div className="font-semibold text-slate-800">{activity.title}</div>
                  <div className="text-slate-500 truncate">{activity.detail}</div>
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

export default AccountActivityCard;
