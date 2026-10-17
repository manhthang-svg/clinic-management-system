import { Building2, Search, Bell, KeyRound } from 'lucide-react';

export function AdminTopBar({ currentAdmin, onChangePassword }) {
  return (
    <header className="h-16 bg-white border-b border-slate-200/80 px-6 flex items-center justify-between shrink-0 shadow-xs">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
        <span className="hover:text-slate-700 cursor-pointer">Trang chủ</span>
        <span>/</span>
        <span className="text-slate-800 font-semibold">Quản lý tài khoản</span>
        <span className="hidden md:inline-flex text-slate-300 mx-2">|</span>
        <span className="hidden md:inline-flex items-center gap-1.5 text-slate-500 text-xs">
          <Building2 className="w-3.5 h-3.5 text-slate-400" />
          Bệnh viện Đa khoa METPH - Cổng Quản trị
        </span>
      </div>

      {/* Right Header Icons & Profile */}
      <div className="flex items-center gap-4">
        {/* Quick Search */}
        <div className="relative hidden lg:block w-64">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Tìm kiếm hồ sơ, nhân sự..."
            className="w-full pl-9 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:border-[#0d9488] focus:bg-white transition-all"
          />
        </div>

        {/* System Online Status */}
        <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50 border border-emerald-200/60 text-emerald-700 text-xs font-medium">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          <span>Hệ thống trực tuyến (Online)</span>
        </div>

        {/* Notification Bell */}
        <button
          type="button"
          className="p-2 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors relative cursor-pointer"
        >
          <Bell className="w-4 h-4" />
          <span className="w-2 h-2 rounded-full bg-rose-500 absolute top-1.5 right-1.5 ring-2 ring-white" />
        </button>

        {/* User Profile */}
        <div className="flex items-center gap-2.5 pl-2 border-l border-slate-200">
          <img
            src="https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=100&h=100&fit=crop&crop=face"
            alt="Admin Avatar"
            className="w-8 h-8 rounded-full object-cover border border-slate-200 shadow-xs"
          />
          <div className="text-left hidden sm:block">
            <div className="text-xs font-bold text-slate-800 leading-tight">
              {currentAdmin?.fullName || 'Nguyễn Minh Ánh'}
            </div>
            <div className="text-[10px] text-slate-400 leading-tight">Admin Portal</div>
          </div>
          {onChangePassword && (
            <button
              type="button"
              onClick={onChangePassword}
              className="text-slate-400 hover:text-[#0d9488] p-1 cursor-pointer"
              title="Đổi mật khẩu tài khoản"
            >
              <KeyRound className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>
    </header>
  );
}

export default AdminTopBar;
