import { useEffect, useRef, useState } from 'react';
import { Search, Bell, ChevronDown, KeyRound, LogOut, UserRound } from 'lucide-react';
import { ROLE_LABELS } from '../constants/accountData';

function initials(fullName = '') {
  const words = fullName.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return 'AD';
  return ((words[0][0] || '') + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase();
}

export function AdminTopBar({ currentAdmin, onOpenProfile, onChangePassword, onLogout }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    if (!menuOpen) return undefined;
    const close = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [menuOpen]);

  const fullName = currentAdmin?.fullName || 'Quản trị viên';

<<<<<<< Updated upstream
  return (
    <header className="h-20 px-6 flex items-center justify-between gap-6 shrink-0">
      <div className="relative flex-1 max-w-lg">
        <Search className="w-4 h-4 text-slate-400 absolute left-4 top-1/2 -translate-y-1/2" />
        <input
          type="search"
          aria-label="Tìm kiếm nhanh"
          placeholder="Tìm bệnh nhân, lịch khám hoặc bất kỳ thông tin nào..."
          className="w-full pl-11 pr-20 py-2.5 text-xs bg-white border border-slate-200/80 rounded-xl shadow-2xs focus:outline-none focus:border-[#127e7a] transition-all placeholder:text-slate-400"
        />
        <span className="hidden md:flex items-center gap-1 absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-slate-400">
          <kbd className="px-1.5 py-0.5 rounded border border-slate-200 bg-slate-50 font-sans">Ctrl</kbd>
          <kbd className="px-1.5 py-0.5 rounded border border-slate-200 bg-slate-50 font-sans">K</kbd>
=======
export function AdminTopBar({ currentAdmin, onChangePassword, pageTitle = 'Quản lý tài khoản' }) {
  return (
    <header className="h-16 bg-white border-b border-slate-200/80 px-6 flex items-center justify-between shrink-0 shadow-xs">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
        <span className="hover:text-slate-700 cursor-pointer">Trang chủ</span>
        <span>/</span>
        <span className="text-slate-800 font-semibold">{pageTitle}</span>
        <span className="hidden md:inline-flex text-slate-300 mx-2">|</span>
        <span className="hidden md:inline-flex items-center gap-1.5 text-slate-500 text-xs">
          <Building2 className="w-3.5 h-3.5 text-slate-400" />
          Bệnh viện Đa khoa METPH - Cổng Quản trị
>>>>>>> Stashed changes
        </span>
      </div>

      <div className="flex items-center gap-5">
        <button
          type="button"
          aria-label="Thông báo"
          className="w-10 h-10 rounded-xl bg-white border border-slate-200/80 shadow-2xs text-slate-500 hover:text-slate-700 flex items-center justify-center relative cursor-pointer"
        >
          <Bell className="w-4 h-4" />
          <span className="w-2 h-2 rounded-full bg-rose-500 absolute top-2 right-2.5 ring-2 ring-white" />
        </button>

        <div className="relative" ref={menuRef}>
          <button
            type="button"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-label="Tài khoản của tôi"
            onClick={() => setMenuOpen((open) => !open)}
            className="flex items-center gap-3 cursor-pointer"
          >
            <span className="w-10 h-10 rounded-full bg-[#dcefec] text-[#127e7a] font-bold text-xs flex items-center justify-center">
              {initials(fullName)}
            </span>
            <span className="text-left hidden sm:block">
              <span className="block text-xs font-bold text-[#1b2533] leading-tight">{fullName}</span>
              <span className="block text-[11px] text-slate-400 leading-tight">
                {ROLE_LABELS[currentAdmin?.role] || ROLE_LABELS.ADMIN}
              </span>
            </span>
            <ChevronDown className="w-4 h-4 text-slate-400" />
          </button>

          {menuOpen && (
            <div role="menu" className="absolute right-0 top-12 w-48 bg-white rounded-xl shadow-lg border border-slate-200 py-1.5 z-30 text-xs">
              {onOpenProfile && (
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setMenuOpen(false);
                    onOpenProfile();
                  }}
                  className="w-full px-3.5 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 cursor-pointer"
                >
                  <UserRound className="w-3.5 h-3.5 text-slate-400" />
                  <span>Hồ sơ cá nhân</span>
                </button>
              )}
              {onChangePassword && (
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setMenuOpen(false);
                    onChangePassword();
                  }}
                  className="w-full px-3.5 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 cursor-pointer"
                >
                  <KeyRound className="w-3.5 h-3.5 text-slate-400" />
                  <span>Đổi mật khẩu</span>
                </button>
              )}
              <button
                type="button"
                role="menuitem"
                onClick={onLogout}
                className="w-full px-3.5 py-2 text-rose-600 hover:bg-rose-50 flex items-center gap-2 cursor-pointer border-t border-slate-100"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span>Đăng xuất</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

export default AdminTopBar;
