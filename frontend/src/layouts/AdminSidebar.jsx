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
  Plus,
} from 'lucide-react';
import { NAV_ITEMS } from '../constants/accountData';

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
};

const ITEM_CLASS = 'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all cursor-pointer';
const ACTIVE_CLASS = 'bg-[#d3ebe7] text-[#127e7a] font-semibold';
const IDLE_CLASS = 'hover:bg-white hover:text-slate-800';

export function AdminSidebar({ activeKey = 'accounts', onNavigate }) {
  const navigate = useNavigate();

  const paths = {
    accounts: '/admin/accounts',
    departments: '/admin/departments',
    rooms: '/admin/rooms',
    services: '/admin/services',
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
    <aside className="w-[224px] h-full overflow-y-auto bg-[#eef5f4] border-r border-[#dfeae8] flex flex-col shrink-0 select-none">
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
              onClick={() => handleNavigate(item.id)}
              className={`${ITEM_CLASS} ${active ? ACTIVE_CLASS : IDLE_CLASS}`}
            >
              <Icon className="w-4 h-4" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}

export default AdminSidebar;
