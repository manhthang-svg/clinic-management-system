
import { Outlet } from 'react-router';
import { AdminTopBar } from './AdminTopBar';
import Sidebar from '../components/Sidebar';

export function DashboardLayout({ children, currentUser, onLogout, onChangePassword, onOpenProfile, onNavigate, activeKey = 'overview' }) {
    return (
        <div className="flex h-screen overflow-hidden bg-[#f2fafa] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
            {/* Sidebar dùng chung, tự chọn menu theo role */}
            <Sidebar user={currentUser} onNavigate={onNavigate} onLogout={onLogout} activeKey={activeKey} />

            {/* Nội dung chính */}
            <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
                <AdminTopBar
                    currentAdmin={currentUser}
                    onLogout={onLogout}
                    onChangePassword={onChangePassword}
                    onOpenProfile={onOpenProfile}
                />

                <main className="min-h-0 min-w-0 flex-1 overflow-y-auto p-6">
                    {children ?? <Outlet />}
                </main>
            </div>
        </div>
    );
}

export default DashboardLayout;
