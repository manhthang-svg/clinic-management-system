
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
} from 'lucide-react';

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
      label: 'Quản lý khoa',
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
      id: 'rooms',
      label: 'Quản lý phòng',
      iconName: 'DoorOpen',
      path: '/admin/rooms',
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
  ],

  DOCTOR: [
    {
      id: 'overview',
      label: 'Tổng quan',
      iconName: 'LayoutDashboard',
      path: '/doctor',
    },
    {
      id: 'appointments',
      label: 'Danh sách khám',
      iconName: 'CalendarDays',
      path: '/doctor/appointments',
    },
    {
      id: 'examinations',
      label: 'Khám bệnh',
      iconName: 'Stethoscope',
      path: '/doctor/examinations',
    },
    {
      id: 'medical-records',
      label: 'Hồ sơ bệnh án',
      iconName: 'ClipboardList',
      path: '/doctor/medical-records',
    },
  ],

  TECHNICIAN: [
    {
      id: 'overview',
      label: 'Tổng quan',
      iconName: 'LayoutDashboard',
      path: '/technician',
    },
    {
      id: 'subclinical',
      label: 'Thực hiện cận lâm sàng',
      iconName: 'Microscope',
      path: '/technician/subclinical',
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
  ],
};

const ITEM_CLASS =
  'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all cursor-pointer text-left';

const ACTIVE_CLASS =
  'bg-[#d3ebe7] text-[#127e7a] font-semibold';

const IDLE_CLASS =
  'text-slate-500 hover:bg-white hover:text-slate-800';

export function Sidebar({ user }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [openGroups] = useState([]);

  const rawRole = user?.role?.toUpperCase();
  const role = rawRole?.replace(/^ROLE_/, '');
  const items = NAV_ITEMS[role] ?? [];

  const isActive = (path) => {
    const currentPath = location.pathname;
    const matchedItems = items
      .filter(
        (item) =>
          currentPath === item.path ||
          currentPath.startsWith(`${item.path}/`)
      )
      .sort((a, b) => b.path.length - a.path.length);

    return matchedItems[0]?.path === path;
  };

  return (
    <aside className="w-[224px] bg-[#eef5f4] border-r border-[#dfeae8] flex flex-col shrink-0 select-none min-h-screen">
      {/* Logo */}
      <div className="h-20 flex items-center gap-3 px-6">
        <div className="w-9 h-9 rounded-xl bg-[#127e7a] text-white flex items-center justify-center shadow-sm">
          <Plus className="w-5 h-5" strokeWidth={3} />
        </div>

        <div>
          <div className="text-base font-extrabold tracking-tight text-[#1b2533] leading-tight">
            METPH
          </div>
          <div className="text-[10px] font-semibold text-slate-400 leading-none">
            Quản lý phòng khám
          </div>
        </div>
      </div>

      {/* Menu theo vai trò */}
      <nav
        aria-label="Điều hướng chính"
        className="px-3 py-2 space-y-1 text-[13px] font-medium"
      >
        {items.map((item) => {
          const Icon = ICONS[item.iconName] ?? LayoutDashboard;
          const active = isActive(item.path);

          return (
            <button
              key={item.id}
              type="button"
              aria-current={active ? 'page' : undefined}
              onClick={() => navigate(item.path)}
              className={`${ITEM_CLASS} ${active ? ACTIVE_CLASS : IDLE_CLASS
                }`}
            >
              <Icon className="w-4 h-4 shrink-0" />
              <span>{item.label}</span>
            </button>
          );
        })}

        {items.length === 0 && (
          <p className="px-3 py-4 text-xs text-slate-400">
            Không tìm thấy menu cho tài khoản này.
          </p>
        )}
      </nav>
    </aside>
  );
}

export default Sidebar;
