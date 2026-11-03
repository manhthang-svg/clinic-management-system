import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  LayoutGrid,
  Building2,
  BriefcaseMedical,
  ClipboardList,
  Pill,
  BookOpen,
  FileHeart,
  Settings2,
  Hospital,
  Settings,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Plus,
  CalendarDays,
} from 'lucide-react';
import { NAV_ITEMS } from '../constants/accountData';
import { useResponsiveSidebar } from '../hooks/useResponsiveSidebar';

const ICONS = {
  LayoutGrid,
  Building2,
  BriefcaseMedical,
  ClipboardList,
  Pill,
  BookOpen,
  FileHeart,
  Settings2,
  Hospital,
  Settings,
  CalendarDays,
};

const ITEM_CLASS = 'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all cursor-pointer';
const ACTIVE_CLASS = 'bg-[#d3ebe7] text-[#127e7a] font-semibold';
const IDLE_CLASS = 'hover:bg-white hover:text-slate-800';

export function AdminSidebar({ activeKey = 'accounts', onNavigate }) {
  const navigate = useNavigate();
  const { collapsed, setCollapsed, toggleCollapsed } = useResponsiveSidebar();

  const paths = {
    accounts: '/admin/accounts',
    departments: '/admin/departments',
    rooms: '/admin/rooms',
    services: '/admin/services',
    schedules: '/admin/schedules',
    'hospital-information': '/admin/hospital-information',
  };

  const handleNavigate = (id) => {
    const path = paths[id];

    if (path) {
      if (onNavigate?.(id) === false) return;
      navigate(path);
    }
  };

  const [openGroups, setOpenGroups] = useState(() =>
    NAV_ITEMS.filter((item) =>
      item.children?.some((child) => child.id === activeKey)
    ).map((item) => item.id)
  );
  const toggleGroup = (id) =>
    setOpenGroups((prev) => (prev.includes(id) ? prev.filter((group) => group !== id) : [...prev, id]));

  return (
    <aside className={`flex h-full shrink-0 select-none flex-col overflow-y-auto border-r border-[#dfeae8] bg-[#eef5f4] transition-[width] duration-200 ${collapsed ? 'w-[72px]' : 'w-[224px]'}`}>
      <div className={`flex h-20 shrink-0 items-center ${collapsed ? 'justify-center' : 'justify-between gap-2 px-4'}`}>
        {!collapsed && (
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[#127e7a] text-white shadow-sm">
              <Plus className="h-5 w-5" strokeWidth={3} />
            </div>
            <div className="min-w-0">
              <div className="text-base font-extrabold leading-tight tracking-tight text-[#1b2533]">METPH</div>
              <div className="whitespace-nowrap text-[10px] font-semibold leading-none text-slate-400">Quản lý phòng khám</div>
            </div>
          </div>
        )}
        <button type="button" onClick={toggleCollapsed} aria-label={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'} title={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'} className="flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-xl text-slate-500 hover:bg-white hover:text-[#127e7a]">
          {collapsed ? <ChevronRight className="h-5 w-5" /> : <ChevronLeft className="h-5 w-5" />}
        </button>
      </div>

      <nav aria-label="Điều hướng chính" className={`space-y-1 py-2 text-[13px] font-medium text-slate-500 ${collapsed ? 'px-2' : 'px-3'}`}>
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
                  title={collapsed ? item.label : undefined}
                  onClick={() => {
                    if (collapsed) {
                      setCollapsed(false);
                      setOpenGroups((current) => current.includes(item.id) ? current : [...current, item.id]);
                      return;
                    }
                    toggleGroup(item.id);
                  }}
                  className={`${ITEM_CLASS} ${collapsed ? 'justify-center px-0' : ''} ${containsActive ? ACTIVE_CLASS : IDLE_CLASS}`}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  {!collapsed && <span className="flex-1 text-left">{item.label}</span>}
                  {!collapsed && <ChevronDown className={`w-4 h-4 transition-transform ${open ? '' : '-rotate-90'}`} />}
                </button>
                {open && !collapsed && (
                  <div className="mt-1 ml-5 pl-3 border-l border-slate-200 space-y-0.5">
                    {item.children.map((child) => {
                      const active = child.id === activeKey;
                      return (
                        <button
                          key={child.id}
                          type="button"
                          aria-current={active ? 'page' : undefined}
                          onClick={() => handleNavigate(child.id)}
                          className={`w-full text-left px-3 py-2 rounded-lg text-[13px] transition-all cursor-pointer ${active ? ACTIVE_CLASS : IDLE_CLASS
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
              title={collapsed ? item.label : undefined}
              onClick={() => handleNavigate(item.id)}
              className={`${ITEM_CLASS} ${collapsed ? 'justify-center px-0' : ''} ${active ? ACTIVE_CLASS : IDLE_CLASS}`}
            >
              <Icon className="h-4 w-4 shrink-0" />
              {!collapsed && <span>{item.label}</span>}
            </button>
          );
        })}
      </nav>
    </aside>
  );
}

export default AdminSidebar;
