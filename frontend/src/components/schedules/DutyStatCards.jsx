import { CalendarDays, Sun, Sunset, Users } from 'lucide-react';

export function DutyStatCards({ stats }) {
  const cards = [
    {
      label: 'Tổng ca trực tuần này',
      value: stats.totalShifts,
      subtext: 'Ca sáng & ca chiều kết hợp',
      icon: CalendarDays,
      iconColor: 'text-teal-600',
      bgColor: 'bg-teal-50',
      borderColor: 'border-teal-100',
    },
    {
      label: 'Ca Sáng (08:00 - 11:30)',
      value: stats.morningShifts,
      subtext: 'Phân công tiếp nhận ban sáng',
      icon: Sun,
      iconColor: 'text-amber-600',
      bgColor: 'bg-amber-50',
      borderColor: 'border-amber-100',
    },
    {
      label: 'Ca Chiều (13:00 - 17:30)',
      value: stats.afternoonShifts,
      subtext: 'Phân công thăm khám buổi chiều',
      icon: Sunset,
      iconColor: 'text-sky-600',
      bgColor: 'bg-sky-50',
      borderColor: 'border-sky-100',
    },
    {
      label: 'Nhân sự tham gia trực',
      value: stats.totalStaff,
      subtext: 'Bác sĩ, ĐD & Kỹ thuật viên',
      icon: Users,
      iconColor: 'text-emerald-600',
      bgColor: 'bg-emerald-50',
      borderColor: 'border-emerald-100',
    },
  ];

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      {cards.map((card, idx) => {
        const IconComponent = card.icon;
        return (
          <div
            key={idx}
            className={`p-3 sm:p-3.5 rounded-xl bg-white border ${card.borderColor} shadow-xs hover:shadow-sm transition-all flex items-start justify-between`}
          >
            <div>
              <p className="text-[11px] font-medium text-slate-500 uppercase tracking-wider">
                {card.label}
              </p>
              <h4 className="text-xl sm:text-2xl font-black text-slate-800 mt-0.5">
                {card.value}
              </h4>
              <p className="text-[11px] text-slate-400 mt-0.5 hidden sm:block">{card.subtext}</p>
            </div>
            <div className={`p-2 rounded-lg ${card.bgColor} ${card.iconColor} shrink-0`}>
              <IconComponent className="w-4 h-4" />
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default DutyStatCards;
