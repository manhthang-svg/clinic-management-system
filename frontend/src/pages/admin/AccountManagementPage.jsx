import { useState, useMemo } from "react";
import {
  Navigate,
  generatePath,
  matchPath,
  useLocation,
  useNavigate,
} from "react-router";
import {
  Users,
  UserCheck,
  Lock,
  KeyRound,
  Plus,
  House,
  ChevronRight,
  CheckCircle2,
} from "lucide-react";
import { AdminLayout } from "../../layouts";
import { ROUTES } from "../../routes/routes";
import {
  AccountFilterBar,
  AccountTable,
  AccountDetailView,
  AccountCreateView,
  AccountEditView,
  ProfileView,
  AccountStatCard,
  AccountOverviewCard,
  AccountActivityCard,
} from "../../components/accounts";
import {
  normalizeSearch,
  percentOf,
} from "../../components/accounts/accountFormat";
import { formToAccountFields } from "../../components/accounts/accountForm";
import {
  MOCK_ACCOUNTS,
  MOCK_ACCOUNT_ACTIVITIES,
  MOCK_ACCOUNT_PROFILES,
  PAGE_SIZE_OPTIONS,
  mockLoginEvents,
  ROLE_LABELS,
} from "../../constants";

const INITIAL_ACCOUNTS = MOCK_ACCOUNTS.map((a) => ({
  ...a,
  ...MOCK_ACCOUNT_PROFILES[a.id],
}));

const { ADMIN } = ROUTES;

/** Which account screen the URL shows; null for paths this page does not own. */
function viewFromPath(path) {
  if (matchPath(ADMIN.ACCOUNTS, path)) return { name: "list" };
  if (matchPath(ADMIN.ACCOUNT_CREATE, path)) return { name: "create" };
  if (matchPath(ADMIN.PROFILE, path)) return { name: "profile" };
  const edit = matchPath(ADMIN.ACCOUNT_EDIT, path);
  if (edit) return { name: "edit", id: edit.params.id };
  const detail = matchPath(ADMIN.ACCOUNT_DETAIL, path);
  if (detail) return { name: "detail", id: detail.params.id };
  return null;
}

const PASSWORD_ALPHABET =
  "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";

/** Mock of the 12-character one-time password the backend generates (UC-7, UC-11). */
function generateTemporaryPassword() {
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  return Array.from(
    bytes,
    (b) => PASSWORD_ALPHABET[b % PASSWORD_ALPHABET.length],
  ).join("");
}

export function AccountManagementPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  notice,
}) {
  const [accounts, setAccounts] = useState(INITIAL_ACCOUNTS);
  const [activities, setActivities] = useState(MOCK_ACCOUNT_ACTIVITIES);
  const [searchQuery, setSearchQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);
  const [selectedIds, setSelectedIds] = useState([]);
  // The URL decides the screen. "Quay lại" / "Hủy" go one page back; the sidebar and breadcrumb open the list.
  const location = useLocation();
  const navigate = useNavigate();
  const matchedView = viewFromPath(location.pathname);
  const view = matchedView || { name: "list" };

  const filteredAccounts = useMemo(() => {
    const term = normalizeSearch(searchQuery);
    return accounts.filter((acc) => {
      const matchSearch =
        term === "" ||
        [acc.username, acc.fullName, acc.email, acc.phone].some((field) =>
          normalizeSearch(field || "").includes(term),
        );
      const matchRole = roleFilter === "ALL" || acc.role === roleFilter;
      const matchStatus = statusFilter === "ALL" || acc.status === statusFilter;
      return matchSearch && matchRole && matchStatus;
    });
  }, [accounts, searchQuery, roleFilter, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredAccounts.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const pageAccounts = filteredAccounts.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize,
  );

  const stats = useMemo(() => {
    const total = accounts.length;
    const active = accounts.filter((a) => a.status === "ACTIVE").length;
    const locked = accounts.filter((a) => a.status === "LOCKED").length;
    const pending = accounts.filter((a) => a.passwordChangeRequired).length;
    return { total, active, locked, pending };
  }, [accounts]);

  // Filters change the result set, so go back to the first page.
  const withFirstPage = (setter) => (value) => {
    setter(value);
    setPage(1);
  };

  const resetFilters = () => {
    setSearchQuery("");
    setRoleFilter("ALL");
    setStatusFilter("ALL");
    setPage(1);
  };

  const logActivity = (type, title, account) => {
    setActivities((prev) => [
      {
        id: `${Date.now()}-${prev.length}`,
        type,
        title,
        detail: `${account.fullName} (${account.username})`,
        time: "Vừa xong",
      },
      ...prev,
    ]);
  };

  const toggleSelectAll = () => {
    const ids = pageAccounts.map((a) => a.id);
    const allSelected =
      ids.length > 0 && ids.every((id) => selectedIds.includes(id));
    setSelectedIds((prev) =>
      allSelected
        ? prev.filter((id) => !ids.includes(id))
        : [...new Set([...prev, ...ids])],
    );
  };

  const toggleSelectOne = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    );
  };

  // A page opened straight from its URL has no previous page in the app ("default" key).
  const goBack = () =>
    location.key === "default"
      ? navigate(ADMIN.ACCOUNTS, { replace: true })
      : navigate(-1);
  const showList = () => navigate(ADMIN.ACCOUNTS);
  const openCreate = () => navigate(ADMIN.ACCOUNT_CREATE);
  const openEdit = (account) =>
    navigate(generatePath(ADMIN.ACCOUNT_EDIT, { id: account.id }));
  const openDetail = (id) =>
    navigate(generatePath(ADMIN.ACCOUNT_DETAIL, { id }));
  // The create result is replaced by the new account's detail, so "Quay lại" skips the empty form.
  const replaceWithDetail = (id) =>
    navigate(generatePath(ADMIN.ACCOUNT_DETAIL, { id }), { replace: true });

  const updateAccount = (id, changes) =>
    setAccounts((prev) =>
      prev.map((a) =>
        a.id === id
          ? { ...a, ...changes, updatedAt: new Date().toISOString() }
          : a,
      ),
    );

  // UC-7: the account starts ACTIVE and must change the one-time password.
  const createAccount = (form) => {
    const now = new Date().toISOString();
    const account = {
      id: `new-${Date.now()}`,
      username: form.username.trim().toLowerCase(),
      role: form.role,
      ...formToAccountFields(form),
      status: "ACTIVE",
      passwordChangeRequired: true,
      failedLoginCount: 0,
      lastLoginAt: null,
      createdAt: now,
      updatedAt: now,
      createdBy: currentAdmin?.fullName,
    };
    setAccounts((prev) => [account, ...prev]);
    logActivity("CREATED", "Tạo tài khoản mới", account);
    setPage(1);
    return { account, temporaryPassword: generateTemporaryPassword() };
  };

  // UC-8: the seven personal fields only.
  const saveAccount = (account, form) => {
    const changes = formToAccountFields(form);
    updateAccount(account.id, changes);
    logActivity("UPDATED", "Cập nhật thông tin", { ...account, ...changes });
    goBack();
  };

  // UC-9: never to or from ADMIN (AQ-17). A Doctor or Technician loses the room assignment.
  const assignRole = (account, role) => {
    const hadRoom = account.role === "DOCTOR" || account.role === "TECHNICIAN";
    const needsRoom = role === "DOCTOR" || role === "TECHNICIAN";
    const lines = [
      `Đổi vai trò của ${account.username}: ${ROLE_LABELS[account.role]} → ${ROLE_LABELS[role]}?`,
      hadRoom && account.roomCode
        ? `Phân công phòng ${account.roomCode} sẽ bị gỡ.`
        : null,
      "Mọi phiên đăng nhập của tài khoản sẽ bị đăng xuất.",
    ];
    if (!window.confirm(lines.filter(Boolean).join("\n"))) return;
    updateAccount(account.id, { role, roomCode: undefined });
    logActivity("UPDATED", "Đổi vai trò", account);
    if (needsRoom)
      window.alert(
        `Hãy phân công phòng làm việc cho ${account.username} ở chức năng Khoa / Phòng.`,
      );
  };

  const toggleLock = (account) => {
    const locking = account.status === "ACTIVE";
    // UC-10: the lock reason is required.
    const reason = locking
      ? window.prompt(`Nhập lý do khóa tài khoản ${account.username}:`)?.trim()
      : "";
    if (locking && !reason) return;
    if (
      !locking &&
      !window.confirm(`Mở khóa tài khoản ${account.username}?`)
    )
      return;
    const lockState = locking
      ? {
          status: "LOCKED",
          lockedAt: new Date().toISOString(),
          lockedBy: currentAdmin?.fullName,
          lockReason: reason.slice(0, 500),
        }
      : {
          status: "ACTIVE",
          lockedAt: undefined,
          lockedBy: undefined,
          lockReason: undefined,
          failedLoginCount: 0,
          loginBlockedUntil: undefined,
        };
    updateAccount(account.id, lockState);
    logActivity(
      locking ? "LOCKED" : "UNLOCKED",
      locking ? "Khóa tài khoản" : "Mở khóa tài khoản",
      account,
    );
  };

  // UC-11: a new one-time password, shown once.
  const resetPassword = (account) => {
    if (!window.confirm(`Đặt lại mật khẩu cho tài khoản ${account.username}?`))
      return;
    const temporaryPassword = generateTemporaryPassword();
    updateAccount(account.id, {
      passwordChangeRequired: true,
      passwordChangedAt: new Date().toISOString(),
      failedLoginCount: 0,
      loginBlockedUntil: undefined,
    });
    logActivity("PASSWORD_RESET", "Đặt lại mật khẩu", account);
    window.alert(
      `Mật khẩu tạm của ${account.username}: ${temporaryPassword}\nMật khẩu chỉ hiển thị một lần.`,
    );
  };

  const selected = accounts.find((a) => a.id === view.id);
  const self = accounts.find(
    (a) => a.username === currentAdmin?.username,
  ) || { status: "ACTIVE", ...currentAdmin };
  const isSelf = (account) => account.username === currentAdmin?.username;

  // Other /admin/* paths have no screen yet; show the list under its own URL.
  if (!matchedView) return <Navigate to={ADMIN.ACCOUNTS} replace />;

  let subPage = null;
  if (view.name === "create") {
    subPage = (
      <AccountCreateView
        accounts={accounts}
        onCreate={createAccount}
        onCancel={goBack}
        onGoToList={showList}
        onViewDetail={replaceWithDetail}
      />
    );
  } else if (view.name === "profile") {
    subPage = (
      <ProfileView
        key={self.id}
        account={self}
        accounts={accounts}
        onSave={(contact) => {
          updateAccount(self.id, contact);
          logActivity("UPDATED", "Cập nhật hồ sơ cá nhân", self);
        }}
        onCancel={goBack}
        onChangePassword={onChangePassword}
      />
    );
  } else if (selected && view.name === "edit") {
    subPage = (
      <AccountEditView
        key={`${selected.id}-${selected.updatedAt}`}
        account={selected}
        accounts={accounts}
        isSelf={isSelf(selected)}
        onSave={(form) => saveAccount(selected, form)}
        onCancel={goBack}
        onGoToList={showList}
        onViewDetail={() => openDetail(selected.id)}
        onToggleLock={() => toggleLock(selected)}
        onResetPassword={() => resetPassword(selected)}
        onAssignRole={(role) => assignRole(selected, role)}
      />
    );
  } else if (selected && view.name === "detail") {
    subPage = (
      <AccountDetailView
        account={selected}
        loginEvents={mockLoginEvents(selected, selected.failedLoginCount)}
        isSelf={isSelf(selected)}
        onBack={goBack}
        onGoToList={showList}
        onEdit={() => openEdit(selected)}
        onAssignRole={() => openEdit(selected)}
        onToggleLock={() => toggleLock(selected)}
        onResetPassword={() => resetPassword(selected)}
      />
    );
  } else if (view.id !== undefined) {
    subPage = (
      <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-10 text-center">
        <h1 className="text-xl! font-extrabold! text-[#1b2533]">
          Không tìm thấy tài khoản
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Tài khoản không tồn tại hoặc đường dẫn không đúng.
        </p>
        <button
          type="button"
          onClick={showList}
          className="mt-5 px-4 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
        >
          Về danh sách tài khoản
        </button>
      </section>
    );
  }

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    onNavigate: (key) => key === "accounts" && showList(),
    activeKey: "accounts",
  };

  if (subPage) {
    return (
      <AdminLayout {...layoutProps}>
        <div className="px-6 pb-8">{subPage}</div>
      </AdminLayout>
    );
  }

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        {notice && (
          <div
            role="status"
            className="p-3.5 rounded-xl bg-teal-50 border border-teal-200 text-teal-800 text-xs font-semibold flex items-center gap-2"
          >
            <CheckCircle2 className="w-4 h-4 text-[#127e7a]" />
            <span>{notice}</span>
          </div>
        )}

        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">
              Quản lý tài khoản
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Quản lý tài khoản người dùng, vai trò và quyền truy cập hệ thống
            </p>
          </div>
          <nav
            aria-label="Breadcrumb"
            className="flex items-center gap-1.5 text-xs text-slate-500"
          >
            <House className="w-3.5 h-3.5" />
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            <span>Quản trị hệ thống</span>
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-slate-700 font-medium">
              Quản lý tài khoản
            </span>
          </nav>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
          <AccountStatCard
            title="Tổng tài khoản"
            value={stats.total}
            icon={Users}
            iconTone="bg-[#e7f0fd] text-[#3b7be0]"
            note="Tất cả tài khoản nhân viên"
          />
          <AccountStatCard
            title="Đang hoạt động"
            value={stats.active}
            icon={UserCheck}
            iconTone="bg-[#e2f5ee] text-[#1f9d70]"
            highlight={percentOf(stats.active, stats.total)}
            highlightTone="text-[#1f9d70]"
            note="trên tổng số tài khoản"
          />
          <AccountStatCard
            title="Đã khóa"
            value={stats.locked}
            icon={Lock}
            iconTone="bg-[#fde9eb] text-[#e5484d]"
            highlight={percentOf(stats.locked, stats.total)}
            highlightTone="text-[#e5484d]"
            note="trên tổng số tài khoản"
          />
          <AccountStatCard
            title="Chờ đổi mật khẩu"
            value={stats.pending}
            icon={KeyRound}
            iconTone="bg-[#fdf1dc] text-[#e9a23b]"
            highlight={percentOf(stats.pending, stats.total)}
            highlightTone="text-slate-600"
            note="cần đổi khi đăng nhập"
          />
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_248px] gap-5 items-start">
          <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-4">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">
                Danh sách tài khoản
              </h2>
              <button
                type="button"
                onClick={openCreate}
                className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm transition-all cursor-pointer"
              >
                <Plus className="w-4 h-4" />
                <span>Tạo tài khoản</span>
              </button>
            </div>

            <AccountFilterBar
              searchQuery={searchQuery}
              setSearchQuery={withFirstPage(setSearchQuery)}
              roleFilter={roleFilter}
              setRoleFilter={withFirstPage(setRoleFilter)}
              statusFilter={statusFilter}
              setStatusFilter={withFirstPage(setStatusFilter)}
              onReset={resetFilters}
            />

            <AccountTable
              accounts={pageAccounts}
              totalCount={filteredAccounts.length}
              page={currentPage}
              pageSize={pageSize}
              onPageChange={setPage}
              onPageSizeChange={(size) => {
                setPageSize(size);
                setPage(1);
              }}
              selectedIds={selectedIds}
              onToggleSelectAll={toggleSelectAll}
              onToggleSelectOne={toggleSelectOne}
              onView={(account) => openDetail(account.id)}
              onEdit={openEdit}
              onToggleLock={toggleLock}
              onResetPassword={resetPassword}
            />
          </section>

          <aside className="space-y-5">
            <AccountOverviewCard accounts={accounts} />
            <AccountActivityCard activities={activities} />
          </aside>
        </div>
      </div>
    </AdminLayout>
  );
}

export default AccountManagementPage;
