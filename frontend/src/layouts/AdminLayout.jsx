import { AdminSidebar } from './AdminSidebar';
import { AdminTopBar } from './AdminTopBar';

export function AdminLayout({ children, currentAdmin, onLogout, onChangePassword, activeKey = 'accounts' }) {
  return (
    <div className="flex min-h-screen bg-[#f3f6f9] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
      {/* 1. LEFT SIDEBAR */}
      <AdminSidebar currentAdmin={currentAdmin} onLogout={onLogout} activeKey={activeKey} />

      {/* 2. MAIN CONTENT AREA */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <AdminTopBar currentAdmin={currentAdmin} onChangePassword={onChangePassword} />
        <main className="flex-1">
          {children}
        </main>
      </div>
    </div>
  );
}

export default AdminLayout;
