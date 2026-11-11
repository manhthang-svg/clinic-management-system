
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import {
  LayoutDashboard,
  Users,
  CalendarDays,
  Stethoscope,
  Microscope,
  Building2,
  DoorOpen,
  BriefcaseMedical,
  CreditCard,
  ClipboardList,
  Settings,
  Plus,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  FolderHeart,
  UserCheck,
  UserRound,
  LogOut,
} from 'lucide-react';
import { useResponsiveSidebar } from '../hooks/useResponsiveSidebar';

const ICONS = {
  LayoutDashboard,
  Users,
  CalendarDays,
  Stethoscope,
  Microscope,
  Building2,
  DoorOpen,
  BriefcaseMedical,
  CreditCard,
  ClipboardList,
  Settings,
  FolderHeart,
  UserCheck,
  UserRound,
};

const NAV_ITEMS = {
  ADMIN: [
    {
      id: 'overview',
      label: 'Tổng quan',
      iconName: 'LayoutDashboard',
      path: '/admin/dashboard',
    },
    {
      id: 'accounts',
      label: 'Quản lý tài khoản',
      iconName: 'Users',
      path: '/admin/accounts',
    },
    {
      id: 'departments',
      label: 'Khoa / Phòng',
      iconName: 'Building2',
      path: '/admin/departments',
    },
    {
      id: 'services',
      label: 'Quản lý dịch vụ',
      iconName: 'BriefcaseMedical',
      path: '/admin/services',
    },
    {
      id: 'schedules',
      label: 'Lịch trực',
      iconName: 'CalendarDays',
      path: '/admin/schedules',
    },
    {
      id: 'settings',
      label: 'Cài đặt',
      iconName: 'Settings',
      path: '/admin/settings',
    },
  ],

  CASHIER: [
    {
      id: 'overview',
      label: 'Tổng quan',
      iconName: 'LayoutDashboard',
      path: '/cashier',
    },
    {
      id: 'payments',
      label: 'Thanh toán',
      iconName: 'CreditCard',
      path: '/cashier/payments',
    },
    {
      id: 'payment-history',
      label: 'Lịch sử thanh toán',
      iconName: 'ClipboardList',
      path: '/cashier/payment-history',
    },
    {
      id: 'schedules',
      label: 'Lịch trực',
      iconName: 'CalendarDays',
      path: '/cashier/schedules',
    },
  ],

  DOCTOR: [
    {
      id: 'overview',
      label: 'Tổng quan',
      iconName: 'LayoutDashboard',
      path: '/doctor',
    },
    {
      id: 'examination-management',
      label: 'Quản lý khám bệnh',
      iconName: 'Stethoscope',
      path: '/doctor/appointments',
    },
    {
      id: 'medical-records',
      label: 'Hồ sơ bệnh án',
      iconName: 'FolderHeart',
      path: '/doctor/medical-records',
    },
    {
      id: 'examined-patients',
      label: 'Bệnh nhân đã khám',
      iconName: 'UserCheck',
      path: '/doctor/examined-patients',
    },
    {
      id: 'profile',
      label: 'Hồ sơ cá nhân',
      iconName: 'UserRound',
      path: '/doctor/profile',
    },
    {
      id: 'schedules',
      label: 'Lịch trực',
      iconName: 'CalendarDays',
      path: '/doctor/schedules',
    },
  ],

  TECHNICIAN: [
    {
      id: 'dashboard',
      label: 'Tổng Quan',
      iconName: 'LayoutDashboard',
      path: '/technician',
    },
    {
      id: 'queue',
      label: 'Danh sách chờ & Phân loại',
      iconName: 'ClipboardList',
      path: '/technician/queue',
    },
    {
      id: 'results',
      label: 'Nhập kết quả',
      iconName: 'Microscope',
      path: '/technician/results',
    },
    {
      id: 'schedules',
      label: 'Lịch trực',
      iconName: 'CalendarDays',
      path: '/technician/schedules',
    },
  ],

  RECEPTIONIST: [
    {
      id: 'overview',
      label: 'Tổng quan',
      iconName: 'LayoutDashboard',
      path: '/receptionist',
    },
    {
      id: 'patients',
      label: 'Quản lý bệnh nhân',
      iconName: 'Users',
      path: '/receptionist/patients',
    },
    {
      id: 'appointments',
      label: 'Lịch khám',
      iconName: 'CalendarDays',
      path: '/receptionist/appointments',
    },
    {
      id: 'room-queue',
      label: 'Hàng đợi phòng khám',
      iconName: 'DoorOpen',
      path: '/receptionist/room-queue',
    },
    {
      id: 'schedules',
      label: 'Lịch trực',
      iconName: 'CalendarDays',
      path: '/receptionist/schedules',
    },
  ],
};

const ITEM_CLASS =
  'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all cursor-pointer text-left';

const ACTIVE_CLASS =
  'bg-[#d3ebe7] text-[#127e7a] font-semibold';

const IDLE_CLASS =
  'text-slate-500 hover:bg-white hover:text-slate-800';

export function Sidebar({ user, onNavigate, onLogout }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [openGroups, setOpenGroups] = useState(['examination', 'medical-records', 'prescriptions']);
  const { collapsed, setCollapsed, toggleCollapsed } = useResponsiveSidebar();

  const rawRole = user?.role?.toUpperCase();
  const role = rawRole?.replace(/^ROLE_/, '');
  const items = NAV_ITEMS[role] ?? [];

  const isActive = (path) => {
    const currentPath = location.pathname;
    const activePath = currentPath.startsWith('/doctor/clinical-examination')
      ? '/doctor/appointments'
      : currentPath;
    const navigableItems = items.flatMap((item) => item.children ?? [item]);
    const matchedItems = navigableItems
      .filter(
        (item) =>
          activePath === item.path ||
          activePath.startsWith(`${item.path}/`)
      )
      .sort((a, b) => b.path.length - a.path.length);

    return matchedItems[0]?.path === path;
  };

  return (
    <aside className={`flex h-full shrink-0 select-none flex-col overflow-y-auto border-r border-[#dfeae8] bg-[#eef5f4] transition-[width] duration-200 ${collapsed ? 'w-[72px]' : 'w-[224px]'}`}>
      {/* Logo */}
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

      {/* Menu theo vai trò */}
      <nav
        aria-label="Điều hướng chính"
        className={`space-y-1 py-2 text-[13px] font-medium ${collapsed ? 'px-2' : 'px-3'}`}
      >
        {items.map((item) => {
          const Icon = ICONS[item.iconName] ?? LayoutDashboard;
          if (item.children) {
            const open = openGroups.includes(item.id);
            const groupActive = item.children.some((child) => isActive(child.path));
            return (
              <div key={item.id} className="border-b border-slate-200/70 pb-1">
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
                    setOpenGroups((current) => current.includes(item.id)
                      ? current.filter((id) => id !== item.id)
                      : [...current, item.id]);
                  }}
                  className={`${ITEM_CLASS} ${collapsed ? 'justify-center px-0' : ''} ${groupActive ? 'text-[#127e7a] font-semibold' : IDLE_CLASS}`}
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  {!collapsed && <span className="flex-1">{item.label}</span>}
                  {!collapsed && <ChevronDown className={`h-4 w-4 transition-transform ${open ? '' : '-rotate-90'}`} />}
                </button>
                {open && !collapsed && (
                  <div className="ml-7 space-y-0.5 pb-1">
                    {item.children.map((child) => {
                      const childActive = isActive(child.path);
                      return (
                        <button
                          key={child.id}
                          type="button"
                          aria-current={childActive ? 'page' : undefined}
                          onClick={() => {
                            if (onNavigate?.(child.path) === false) return;
                            navigate(child.path);
                          }}
                          className={`w-full rounded-lg px-3 py-2 text-left text-xs transition-colors ${childActive ? ACTIVE_CLASS : IDLE_CLASS}`}
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
          const active = isActive(item.path);

          return (
            <button
              key={item.id}
              type="button"
              aria-current={active ? 'page' : undefined}
              title={collapsed ? item.label : undefined}
              onClick={() => {
                if (onNavigate?.(item.path) === false) return;
                navigate(item.path);
              }}
              className={`${ITEM_CLASS} ${collapsed ? 'justify-center px-0' : ''} ${active ? ACTIVE_CLASS : IDLE_CLASS
                }`}
            >
              <Icon className="w-4 h-4 shrink-0" />
              {!collapsed && <span>{item.label}</span>}
            </button>
          );
        })}

        {items.length === 0 && !collapsed && (
          <p className="px-3 py-4 text-xs text-slate-400">
            Không tìm thấy menu cho tài khoản này.
          </p>
        )}

        {role === 'DOCTOR' && (
          <button
            type="button"
            onClick={onLogout}
            title={collapsed ? 'Đăng xuất' : undefined}
            className={`${ITEM_CLASS} mt-4 border-t border-slate-200 pt-4 ${collapsed ? 'justify-center px-0' : ''} ${IDLE_CLASS}`}
          >
            <LogOut className="h-4 w-4 shrink-0" />
            {!collapsed && <span>Đăng xuất</span>}
          </button>
        )}
      </nav>
    </aside>
  );
}

export default Sidebar;
