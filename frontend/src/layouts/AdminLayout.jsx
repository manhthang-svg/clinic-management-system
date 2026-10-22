import { AdminSidebar } from './AdminSidebar';
import { AdminTopBar } from './AdminTopBar';

<<<<<<< Updated upstream
export function AdminLayout({ children, currentAdmin, onLogout, onChangePassword, activeKey = 'accounts' }) {
  return (
    <div className="flex min-h-screen bg-[#eef5f4] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
      <AdminSidebar activeKey={activeKey} />

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <AdminTopBar currentAdmin={currentAdmin} onChangePassword={onChangePassword} onLogout={onLogout} />
        <main className="flex-1">
=======
export function AdminLayout({
  children,
  currentAdmin,
  onLogout,
  onChangePassword,
  activeKey = 'accounts',
  onSelectNav,
  pageTitle,
}) {
  const title =
    pageTitle ||
    (activeKey === 'rooms'
      ? 'Quản lý phòng & Tour trực'
      : activeKey === 'accounts'
      ? 'Quản lý tài khoản'
      : 'Hệ thống Quản trị');

  return (
    <div className="flex h-screen overflow-hidden bg-[#f3f6f9] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
      {/* 1. LEFT SIDEBAR: Cố định toàn bộ chiều cao màn hình */}
      <AdminSidebar
        currentAdmin={currentAdmin}
        onLogout={onLogout}
        activeKey={activeKey}
        onSelectNav={onSelectNav}
      />

      {/* 2. MAIN CONTENT AREA: TopBar cố định, chỉ cuộn phần nội dung bên trong */}
      <div className="flex-1 flex flex-col min-w-0 h-screen overflow-hidden">
        <AdminTopBar
          currentAdmin={currentAdmin}
          onChangePassword={onChangePassword}
          pageTitle={title}
        />
        <main className="flex-1 overflow-y-auto">
>>>>>>> Stashed changes
          {children}
        </main>
      </div>
    </div>
  );
}

export default AdminLayout;
