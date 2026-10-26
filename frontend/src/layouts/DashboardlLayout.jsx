
import { Outlet } from 'react-router';
import { AdminTopBar } from './AdminTopBar';
import Sidebar from '../components/Sidebar';

export function DashboardLayout({ currentUser, onLogout, onChangePassword, onOpenProfile, activeKey = 'overview' }) {
    return (
        <div className="flex min-h-screen bg-[#f2fafa] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
            {/* Sidebar dùng chung, tự chọn menu theo role */}
            <Sidebar user={currentUser} activeKey={activeKey} />

            {/* Nội dung chính */}
            <div className="flex min-w-0 flex-1 flex-col">
                <AdminTopBar
                    currentAdmin={currentUser}
                    onLogout={onLogout}
                    onChangePassword={onChangePassword}
                    onOpenProfile={onOpenProfile}
                />

                <main className="min-w-0 flex-1 p-6">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

export default DashboardLayout;
