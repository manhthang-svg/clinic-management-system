import { Building2, DoorOpen, CheckCircle2, AlertTriangle } from 'lucide-react';

export function DepartmentStatCards({ stats }) {
  const {
    totalDepartments = 0,
    clinicalDepartments = 0,
    paraclinicalDepartments = 0,
    totalRooms = 0,
    clinicalRooms = 0,
    paraclinicalRooms = 0,
    activeRooms = 0,
    maintenanceRooms = 0,
    activeRate,
    maintenanceNote,
  } = stats || {};

  // Tỷ lệ phần trăm hoạt động tính trên Phòng
  const displayRate =
    activeRate !== undefined && activeRate !== null
      ? activeRate
      : totalRooms > 0
      ? Number(((activeRooms / totalRooms) * 100).toFixed(1))
      : 0;

  // Chú thích cảnh báo bảo trì
  const displayMaintenanceNote =
    maintenanceNote ||
    (maintenanceRooms > 0
      ? `${maintenanceRooms} phòng đang kiểm tra thiết bị`
      : 'Tất cả phòng hoạt động bình thường');

  const cards = [
    {
      id: 'departments',
      title: 'Tổng số khoa',
      value: totalDepartments,
      badges: [
        {
          label: `Lâm sàng: ${clinicalDepartments}`,
          badgeClass: 'bg-blue-50 text-blue-700 border border-blue-200/70',
        },
        {
          label: `Cận lâm sàng: ${paraclinicalDepartments}`,
          badgeClass: 'bg-purple-50 text-purple-700 border border-purple-200/70',
        },
      ],
      note: 'Lâm sàng & Cận lâm sàng',
      icon: Building2,
      iconClass: 'bg-blue-50 text-blue-600',
    },
    {
      id: 'rooms',
      title: 'Tổng số phòng',
      value: totalRooms,
      badges: [
        {
          label: `Lâm sàng: ${clinicalRooms}`,
          badgeClass: 'bg-blue-50 text-blue-700 border border-blue-200/70',
        },
        {
          label: `Cận lâm sàng: ${paraclinicalRooms}`,
          badgeClass: 'bg-purple-50 text-purple-700 border border-purple-200/70',
        },
      ],
      note: 'Khám bệnh, CLS & Nghiệp vụ',
      icon: DoorOpen,
      iconClass: 'bg-teal-50 text-[#0d9488]',
    },
    {
      id: 'active',
      title: 'Đang hoạt động',
      value: activeRooms,
      badge: `Tỷ lệ: ${displayRate}%`,
      badgeClass: 'bg-emerald-50 text-emerald-700 border border-emerald-100/80',
      note: 'Sẵn sàng tiếp nhận bệnh nhân',
      icon: CheckCircle2,
      iconClass: 'bg-emerald-50 text-emerald-600',
    },
    {
      id: 'maintenance',
      title: 'Tạm ngưng / Bảo trì',
      value: maintenanceRooms,
      badge: maintenanceRooms > 0 ? 'Cần kiểm tra' : 'Bình thường',
      badgeClass:
        maintenanceRooms > 0
          ? 'bg-amber-50 text-amber-700 border border-amber-100/80'
          : 'bg-slate-50 text-slate-500 border border-slate-200/60',
      note: displayMaintenanceNote,
      icon: AlertTriangle,
      iconClass:
        maintenanceRooms > 0
          ? 'bg-amber-50 text-amber-600'
          : 'bg-slate-100 text-slate-500',
    },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3.5 2xl:gap-4">
      {cards.map((card) => {
        const Icon = card.icon;
        return (
          <div
            key={card.id}
            className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-3.5 sm:p-4 2xl:p-5 flex items-center gap-3 2xl:gap-4 transition-all hover:border-slate-300 min-w-0"
          >
            <div
              className={`w-11 h-11 2xl:w-14 2xl:h-14 rounded-2xl flex items-center justify-center shrink-0 ${card.iconClass}`}
            >
              <Icon className="w-5 h-5 2xl:w-6 2xl:h-6" />
            </div>
            <div className="min-w-0 flex-1 overflow-hidden">
              <div className="text-xs font-semibold text-slate-500 tracking-wide truncate">
                {card.title}
              </div>
              <div className="flex items-center gap-1 2xl:gap-1.5 mt-1 min-w-0 flex-nowrap">
                <span className="text-xl 2xl:text-2xl font-extrabold text-[#142534] tracking-tight shrink-0">
                  {card.value}
                </span>
                {card.badges ? (
                  card.badges.map((b, idx) => (
                    <span
                      key={idx}
                      className={`inline-flex items-center px-1.5 py-0.5 rounded-md text-[10px] 2xl:text-[10.5px] font-semibold whitespace-nowrap shrink-0 ${b.badgeClass}`}
                    >
                      {b.label}
                    </span>
                  ))
                ) : (
                  <span
                    className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10.5px] 2xl:text-[11px] font-semibold whitespace-nowrap shrink-0 ${card.badgeClass}`}
                  >
                    {card.badge}
                  </span>
                )}
              </div>
              <div
                className="text-[11px] text-slate-400 mt-1 truncate"
                title={card.note}
              >
                {card.note}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default DepartmentStatCards;
