import { AdminSidebar } from "./AdminSidebar";
import { AdminTopBar } from "./AdminTopBar";

export function AdminLayout({
  children,
  currentAdmin,
  onLogout,
  onChangePassword,
  onOpenProfile,
  onNavigate,
  activeKey = "accounts",
}) {
  return (
    <div className="admin-shell flex h-screen overflow-hidden bg-[#eef5f4] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
      {/* admin-shell*: hooks for the report print styles (UC-33); no effect on screen. */}
      <div className="admin-shell-chrome contents">
        <AdminSidebar
          activeKey={activeKey}
          onNavigate={onNavigate}
        />
      </div>

      {/* relative: absolutely positioned content (sr-only labels, dropdowns) stays inside this scroll area
          instead of stretching the page, which would add a second, page-level scrollbar. */}
      <div className="admin-shell-scroll relative flex-1 flex flex-col min-w-0 overflow-y-auto">
        <div className="admin-shell-chrome contents">
          <AdminTopBar
            currentAdmin={currentAdmin}
            onOpenProfile={onOpenProfile}
            onChangePassword={onChangePassword}
            onLogout={onLogout}
          />
        </div>

        <main className="flex-1">
          {children}
        </main>
      </div>
    </div>
  );
}

export default AdminLayout;
