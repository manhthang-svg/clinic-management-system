import {
  LayoutDashboard,
  Users2,
  Calendar,
  Stethoscope,
  Microscope,
  CreditCard,
  UserCheck,
  UserCog,
  FileBarChart,
  Settings,
  LogOut,
} from 'lucide-react';
import { NAV_ITEMS } from '../constants/accountData';

const ICONS = {
  LayoutDashboard,
  Users2,
  Calendar,
  Stethoscope,
  Microscope,
  CreditCard,
  UserCheck,
  UserCog,
  FileBarChart,
  Settings,
};

export function AdminSidebar({ currentAdmin, onLogout, activeKey = 'accounts' }) {
  return (
    <aside className="w-[240px] bg-[#142332] text-slate-300 flex flex-col justify-between shrink-0 shadow-xl select-none min-h-screen">
      <div>
        {/* Brand Logo Header */}
        <div className="h-16 flex items-center gap-3 px-5 border-b border-white/5">
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

        {/* Navigation Menu */}
        <nav className="p-3 space-y-1 text-[13px] font-medium">
          {NAV_ITEMS.map((item) => {
            const Icon = ICONS[item.iconName] || LayoutDashboard;
            const isActive = item.id === activeKey || item.active;
            return (
              <button
                key={item.id}
                type="button"
                className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl transition-all cursor-pointer ${
                  isActive
                    ? 'bg-[#0d9488] text-white font-semibold shadow-sm'
                    : 'text-slate-400 hover:text-white hover:bg-white/5'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Sidebar Footer User Info */}
      <div className="p-3 border-t border-white/5">
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
            className="text-slate-400 hover:text-rose-400 transition-colors p-1 cursor-pointer"
            title="Đăng xuất"
          >
            <LogOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </aside>
  );
}

export default AdminSidebar;
