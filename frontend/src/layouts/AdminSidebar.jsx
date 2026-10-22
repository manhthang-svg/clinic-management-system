import { useState } from 'react';
import {
  LayoutGrid,
  Users,
  CalendarDays,
  Stethoscope,
  Microscope,
  Building2,
  CreditCard,
  ClipboardList,
  Settings2,
  Settings,
<<<<<<< Updated upstream
  ChevronDown,
  Plus,
=======
  LogOut,
  DoorOpen,
>>>>>>> Stashed changes
} from 'lucide-react';
import { NAV_ITEMS } from '../constants/accountData';

const ICONS = {
  LayoutGrid,
  Users,
  CalendarDays,
  Stethoscope,
  Microscope,
  Building2,
  CreditCard,
  ClipboardList,
  Settings2,
  Settings,
  DoorOpen,
};

<<<<<<< Updated upstream
const ITEM_CLASS = 'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all cursor-pointer';
const ACTIVE_CLASS = 'bg-[#d3ebe7] text-[#127e7a] font-semibold';
const IDLE_CLASS = 'hover:bg-white hover:text-slate-800';

export function AdminSidebar({ activeKey = 'accounts', onNavigate }) {
  const [openGroups, setOpenGroups] = useState(() =>
    NAV_ITEMS.filter((item) => item.children?.some((child) => child.id === activeKey)).map((item) => item.id)
  );

  const toggleGroup = (id) =>
    setOpenGroups((prev) => (prev.includes(id) ? prev.filter((g) => g !== id) : [...prev, id]));

  return (
    <aside className="w-[224px] bg-[#eef5f4] border-r border-[#dfeae8] flex flex-col shrink-0 select-none min-h-screen">
      <div className="h-20 flex items-center gap-3 px-6">
        <div className="w-9 h-9 rounded-xl bg-[#127e7a] text-white flex items-center justify-center shadow-sm">
          <Plus className="w-5 h-5" strokeWidth={3} />
        </div>
        <div>
          <div className="text-base font-extrabold tracking-tight text-[#1b2533] leading-tight">METPH</div>
          <div className="text-[10px] font-semibold text-slate-400 leading-none">Quản lý phòng khám</div>
        </div>
      </div>

      <nav aria-label="Điều hướng chính" className="px-3 py-2 space-y-1 text-[13px] font-medium text-slate-500">
        {NAV_ITEMS.map((item) => {
          const Icon = ICONS[item.iconName] || LayoutGrid;

          if (item.children) {
            const open = openGroups.includes(item.id);
            const containsActive = item.children.some((child) => child.id === activeKey);
            return (
              <div key={item.id}>
                <button
                  type="button"
                  aria-expanded={open}
                  onClick={() => toggleGroup(item.id)}
                  className={`${ITEM_CLASS} ${containsActive ? ACTIVE_CLASS : IDLE_CLASS}`}
                >
                  <Icon className="w-4 h-4" />
                  <span className="flex-1 text-left">{item.label}</span>
                  <ChevronDown className={`w-4 h-4 transition-transform ${open ? '' : '-rotate-90'}`} />
                </button>
                {open && (
                  <div className="mt-1 ml-5 pl-3 border-l border-slate-200 space-y-0.5">
                    {item.children.map((child) => {
                      const active = child.id === activeKey;
                      return (
                        <button
                          key={child.id}
                          type="button"
                          aria-current={active ? 'page' : undefined}
                          onClick={() => onNavigate?.(child.id)}
                          className={`w-full text-left px-3 py-2 rounded-lg text-[13px] transition-all cursor-pointer ${
                            active ? ACTIVE_CLASS : IDLE_CLASS
                          }`}
                        >
                          {child.label}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          }

          const active = item.id === activeKey;
          return (
            <button
              key={item.id}
              type="button"
              aria-current={active ? 'page' : undefined}
              onClick={() => onNavigate?.(item.id)}
              className={`${ITEM_CLASS} ${active ? ACTIVE_CLASS : IDLE_CLASS}`}
            >
              <Icon className="w-4 h-4" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>
=======
export function AdminSidebar({ currentAdmin, onLogout, activeKey = 'accounts', onSelectNav }) {
  return (
    <aside className="w-[240px] h-screen bg-[#142332] text-slate-300 flex flex-col justify-between shrink-0 shadow-xl select-none sticky top-0 z-30">
      {/* Brand Logo Header: Cố định ở đỉnh */}
      <div className="h-16 flex items-center gap-3 px-5 border-b border-white/5 shrink-0">
        <div className="w-8 h-8 rounded-lg bg-[#0d9488] text-white flex items-center justify-center font-black text-lg shadow-sm">
          +
        </div>
        <div>
          <div className="text-[15px] font-bold tracking-tight text-white leading-tight">METPH</div>
          <div className="text-[9px] uppercase tracking-wider text-slate-400 font-semibold leading-none">
            Hospital Management
          </div>
        </div>
      </div>

      {/* Navigation Menu: Vừa vặn khung hình, cuộn êm nội bộ nếu menu dài hơn màn hình */}
      <nav className="p-3 space-y-1 text-[13px] font-medium flex-1 overflow-y-auto overflow-x-hidden">
        {NAV_ITEMS.map((item) => {
          const Icon = ICONS[item.iconName] || LayoutDashboard;
          const isActive = item.id === activeKey;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelectNav?.(item.id)}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all cursor-pointer ${
                isActive
                  ? 'bg-[#0d9488] text-white font-semibold shadow-sm'
                  : 'text-slate-400 hover:text-white hover:bg-white/5'
              }`}
            >
              <Icon className="w-4 h-4 shrink-0" />
              <span className="truncate">{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* Sidebar Footer User Info: Luôn neo cố định ở đáy */}
      <div className="p-3 border-t border-white/5 shrink-0">
        <div className="flex items-center justify-between p-2 rounded-xl bg-white/5">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-8 h-8 rounded-full bg-[#0d9488]/30 text-[#2dd4bf] font-bold text-xs flex items-center justify-center border border-[#0d9488]/40 shrink-0">
              AD
            </div>
            <div className="truncate">
              <div className="text-[12px] font-semibold text-white leading-tight truncate">
                {currentAdmin?.fullName || 'Nguyễn Minh Ánh'}
              </div>
              <div className="text-[10px] text-slate-400 leading-tight">Quản trị viên</div>
            </div>
          </div>
          <button
            type="button"
            onClick={onLogout}
            className="text-slate-400 hover:text-rose-400 transition-colors p-1 cursor-pointer shrink-0"
            title="Đăng xuất"
          >
            <LogOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
>>>>>>> Stashed changes
    </aside>
  );
}

export default AdminSidebar;
