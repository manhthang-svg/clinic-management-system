import { AdminSidebar } from './AdminSidebar';
import { AdminTopBar } from './AdminTopBar';

export function AdminLayout({ children, currentAdmin, onLogout, onChangePassword, onOpenProfile, onNavigate, activeKey = 'accounts' }) {
  return (
    <div className="flex min-h-screen bg-[#eef5f4] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
      <AdminSidebar activeKey={activeKey} onNavigate={onNavigate} />

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <AdminTopBar currentAdmin={currentAdmin} onOpenProfile={onOpenProfile} onChangePassword={onChangePassword} onLogout={onLogout} />
        <main className="flex-1">
          {children}
        </main>
      </div>
    </div>
  );
}

export default AdminLayout;
