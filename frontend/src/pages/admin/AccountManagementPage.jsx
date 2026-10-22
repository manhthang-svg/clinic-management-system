import { useState, useMemo } from 'react';
import { Users, UserCheck, Lock, KeyRound, Plus, House, ChevronRight, CheckCircle2 } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import {
  AccountFilterBar,
  AccountTable,
  AccountFormModal,
  AccountDetailModal,
  AccountStatCard,
  AccountOverviewCard,
  AccountActivityCard,
} from '../../components/accounts';
import { normalizeSearch, percentOf } from '../../components/accounts/accountFormat';
import { MOCK_ACCOUNTS, MOCK_ACCOUNT_ACTIVITIES, PAGE_SIZE_OPTIONS } from '../../constants';

const EMPTY_FORM = { username: '', fullName: '', title: '', email: '', phone: '', role: 'DOCTOR' };

/** UC-5..UC-11 account management screen. Works on mock data until the account API exists. */
export function AccountManagementPage({ currentAdmin, onLogout, onChangePassword, notice }) {
  const [accounts, setAccounts] = useState(MOCK_ACCOUNTS);
  const [activities, setActivities] = useState(MOCK_ACCOUNT_ACTIVITIES);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);
  const [selectedIds, setSelectedIds] = useState([]);
  const [formMode, setFormMode] = useState(null); // 'create' | 'edit' | null
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [viewing, setViewing] = useState(null);

  const filteredAccounts = useMemo(() => {
    const term = normalizeSearch(searchQuery);
    return accounts.filter((acc) => {
      const matchSearch =
        term === '' ||
        [acc.username, acc.fullName, acc.email, acc.phone].some((field) => normalizeSearch(field || '').includes(term));
      const matchRole = roleFilter === 'ALL' || acc.role === roleFilter;
      const matchStatus = statusFilter === 'ALL' || acc.status === statusFilter;
      return matchSearch && matchRole && matchStatus;
    });
  }, [accounts, searchQuery, roleFilter, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredAccounts.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const pageAccounts = filteredAccounts.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const stats = useMemo(() => {
    const total = accounts.length;
    const active = accounts.filter((a) => a.status === 'ACTIVE').length;
    const locked = accounts.filter((a) => a.status === 'LOCKED').length;
    const pending = accounts.filter((a) => a.passwordChangeRequired).length;
    return { total, active, locked, pending };
  }, [accounts]);

  // Filters change the result set, so go back to the first page.
  const withFirstPage = (setter) => (value) => {
    setter(value);
    setPage(1);
  };

  const resetFilters = () => {
    setSearchQuery('');
    setRoleFilter('ALL');
    setStatusFilter('ALL');
    setPage(1);
  };

  const logActivity = (type, title, account) => {
    setActivities((prev) => [
      { id: `${Date.now()}-${prev.length}`, type, title, detail: `${account.fullName} (${account.username})`, time: 'Vừa xong' },
      ...prev,
    ]);
  };

  const toggleSelectAll = () => {
    const ids = pageAccounts.map((a) => a.id);
    const allSelected = ids.length > 0 && ids.every((id) => selectedIds.includes(id));
    setSelectedIds((prev) => (allSelected ? prev.filter((id) => !ids.includes(id)) : [...new Set([...prev, ...ids])]));
  };

  const toggleSelectOne = (id) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  };

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setFormMode('create');
  };

  const openEdit = (account) => {
    setForm({
      username: account.username,
      fullName: account.fullName,
      title: account.title || '',
      email: account.email || '',
      phone: account.phone || '',
      role: account.role,
    });
    setEditingId(account.id);
    setFormMode('edit');
  };

  const closeForm = () => setFormMode(null);

  const handleFormSubmit = (e) => {
    e.preventDefault();
    const username = form.username.trim().toLowerCase();
    const fullName = form.fullName.trim();
    if (!username || !fullName) return;
    const details = {
      fullName,
      title: form.title.trim() || undefined,
      email: form.email.trim(),
      phone: form.phone.trim(),
      role: form.role,
    };

    if (formMode === 'edit') {
      const current = accounts.find((a) => a.id === editingId);
      setAccounts((prev) => prev.map((a) => (a.id === editingId ? { ...a, ...details } : a)));
      logActivity('UPDATED', 'Cập nhật thông tin', { ...current, ...details });
    } else {
      const created = {
        id: `new-${Date.now()}`,
        username,
        ...details,
        status: 'ACTIVE',
        passwordChangeRequired: true,
        lastLoginAt: null,
        createdAt: new Date().toISOString(),
      };
      setAccounts((prev) => [created, ...prev]);
      logActivity('CREATED', 'Tạo tài khoản mới', created);
      setPage(1);
    }
    closeForm();
  };

  const toggleLock = (account) => {
    const locking = account.status === 'ACTIVE';
    setAccounts((prev) => prev.map((a) => (a.id === account.id ? { ...a, status: locking ? 'LOCKED' : 'ACTIVE' } : a)));
    logActivity(locking ? 'LOCKED' : 'UNLOCKED', locking ? 'Khóa tài khoản' : 'Mở khóa tài khoản', account);
  };

  const resetPassword = (account) => {
    if (!window.confirm(`Đặt lại mật khẩu cho tài khoản ${account.username}?`)) return;
    setAccounts((prev) => prev.map((a) => (a.id === account.id ? { ...a, passwordChangeRequired: true } : a)));
    logActivity('PASSWORD_RESET', 'Đặt lại mật khẩu', account);
  };

  return (
    <AdminLayout currentAdmin={currentAdmin} onLogout={onLogout} onChangePassword={onChangePassword} activeKey="accounts">
      <div className="px-6 pb-8 space-y-6">
        {notice && (
          <div role="status" className="p-3.5 rounded-xl bg-teal-50 border border-teal-200 text-teal-800 text-xs font-semibold flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-[#127e7a]" />
            <span>{notice}</span>
          </div>
        )}

        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">Quản lý tài khoản</h1>
            <p className="text-sm text-slate-500 mt-1">Quản lý tài khoản người dùng, vai trò và quyền truy cập hệ thống</p>
          </div>
          <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-slate-500">
            <House className="w-3.5 h-3.5" />
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            <span>Quản trị hệ thống</span>
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-slate-700 font-medium">Quản lý tài khoản</span>
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
              <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">Danh sách tài khoản</h2>
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
              onView={setViewing}
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

        <AccountFormModal
          isOpen={formMode !== null}
          mode={formMode || 'create'}
          onClose={closeForm}
          onSubmit={handleFormSubmit}
          form={form}
          setForm={setForm}
        />
        <AccountDetailModal account={viewing} onClose={() => setViewing(null)} />
      </div>
    </AdminLayout>
  );
}

export default AccountManagementPage;
