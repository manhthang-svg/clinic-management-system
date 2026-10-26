
import { Outlet } from 'react-router';
import { useAuth } from '../hooks';
import { sessionManager } from '../services/session';
import { AdminTopBar } from './AdminTopBar';
import Sidebar from '../components/Sidebar';

export function DashboardLayout({ activeKey = 'overview' }) {
    const { user } = useAuth();

    const handleLogout = async () => {
        await sessionManager.logout();
        window.location.replace('/login');
    };

    return (
        <div className="flex min-h-screen bg-[#f2fafa] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
            {/* Sidebar dùng chung, tự chọn menu theo role */}
            <Sidebar activeKey={activeKey} />

            {/* Nội dung chính */}
            <div className="flex min-w-0 flex-1 flex-col">
                <AdminTopBar
                    currentAdmin={user}
                    onLogout={handleLogout}
                />

                <main className="min-w-0 flex-1 p-6">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

export default DashboardLayout;