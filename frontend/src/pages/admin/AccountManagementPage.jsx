import { useState, useMemo } from 'react';
import { Users2, CheckCircle2, Lock, CreditCard } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { PageHeader, MetricCard } from '../../components/shared';
import { AccountFilterBar, AccountTable, AccountFormModal } from '../../components/accounts';
import { MOCK_ACCOUNTS, ROLE_MAP } from '../../constants';

export function AccountManagementPage({ currentAdmin, onLogout, onChangePassword, notice }) {
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [deptFilter, setDeptFilter] = useState('ALL');
  const [selectedIds, setSelectedIds] = useState([]);
  const [activeMenuId, setActiveMenuId] = useState(null);
  const [showAddModal, setShowAddModal] = useState(false);

  // New account form state
  const [newAccount, setNewAccount] = useState({
    username: '',
    fullName: '',
    email: '',
    phone: '',
    role: 'DOCTOR',
    department: 'Khoa Nội',
    password: '',
  });

  const [accounts, setAccounts] = useState(MOCK_ACCOUNTS);

  const filteredAccounts = useMemo(() => {
    return accounts.filter((acc) => {
      const matchSearch =
        searchQuery === '' ||
        acc.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        acc.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
        acc.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
        acc.phone.includes(searchQuery) ||
        acc.code.toLowerCase().includes(searchQuery.toLowerCase());

      const matchRole = roleFilter === 'ALL' || acc.role === roleFilter;
      const matchStatus = statusFilter === 'ALL' || acc.status === statusFilter;
      const matchDept = deptFilter === 'ALL' || acc.department.includes(deptFilter);

      return matchSearch && matchRole && matchStatus && matchDept;
    });
  }, [accounts, searchQuery, roleFilter, statusFilter, deptFilter]);

  const toggleSelectAll = () => {
    if (selectedIds.length === filteredAccounts.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(filteredAccounts.map((a) => a.id));
    }
  };

  const toggleSelectOne = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const resetFilters = () => {
    setSearchQuery('');
    setRoleFilter('ALL');
    setStatusFilter('ALL');
    setDeptFilter('ALL');
  };

  const handleAddAccountSubmit = (e) => {
    e.preventDefault();
    if (!newAccount.username || !newAccount.fullName) return;

    const added = {
      id: String(accounts.length + 1).padStart(2, '0'),
      code: `NV${String(accounts.length + 1).padStart(3, '0')}`,
      username: newAccount.username,
      fullName: newAccount.fullName,
      staffId: `ID: 04${Math.floor(100000 + Math.random() * 900000)}`,
      position: ROLE_MAP[newAccount.role]?.label || 'Nhân viên',
      initials: newAccount.fullName.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase(),
      email: newAccount.email || `${newAccount.username}@metph.vn`,
      phone: newAccount.phone || '0900 000 000',
      role: newAccount.role,
      roleLabel: ROLE_MAP[newAccount.role]?.label || newAccount.role,
      roleColor: ROLE_MAP[newAccount.role]?.color || 'bg-slate-100 text-slate-700 border-slate-200',
      department: newAccount.department,
      status: 'ACTIVE',
      createdAt: 'Hôm nay',
    };

    setAccounts([added, ...accounts]);
    setShowAddModal(false);
    setNewAccount({
      username: '',
      fullName: '',
      email: '',
      phone: '',
      role: 'DOCTOR',
      department: 'Khoa Nội',
      password: '',
    });
  };

  const toggleLockStatus = (id) => {
    setAccounts((prev) =>
      prev.map((acc) =>
        acc.id === id
          ? { ...acc, status: acc.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE' }
          : acc
      )
    );
    setActiveMenuId(null);
  };

  return (
    <AdminLayout
      currentAdmin={currentAdmin}
      onLogout={onLogout}
      onChangePassword={onChangePassword}
      activeKey="accounts"
    >
      <div className="p-6 md:p-8 space-y-6">
        {notice && (
          <div role="status" className="p-3.5 rounded-xl bg-teal-50 border border-teal-200 text-teal-800 text-xs font-semibold flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-[#0d9488]" />
            <span>{notice}</span>
          </div>
        )}

        {/* Page Title & Top Actions */}
        <PageHeader
          title="Quản lý tài khoản"
          badge="KM Enterprise"
          subtitle="Quản lý tài khoản nhân viên, vai trò và quyền truy cập hệ thống bệnh viện METPH."
          onExport={() => alert('Xuất file danh sách tài khoản Excel/CSV thành công!')}
          onAdd={() => setShowAddModal(true)}
        />

        {/* 4 SUMMARY STAT CARDS */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard
            title="Tổng tài khoản"
            value="128"
            icon={Users2}
            iconBg="bg-teal-50 text-[#0d9488]"
            valueColor="text-slate-900"
            subtitle="Tất cả nhân viên"
            dotColor="bg-teal-500"
            footerLabel="Khả năng hệ thống"
            footerValue="100% Quy định danh"
            footerValueColor="text-teal-700"
          />
          <MetricCard
            title="Đang hoạt động"
            value="116"
            icon={CheckCircle2}
            iconBg="bg-emerald-50 text-emerald-600"
            valueColor="text-emerald-600"
            subtitle="Có thể đăng nhập"
            dotColor="bg-emerald-500"
            footerLabel="Tỷ lệ khả dụng"
            footerValue="90.6% chuẩn hóa"
            footerValueColor="text-emerald-700"
          />
          <MetricCard
            title="Đang bị khóa"
            value="8"
            icon={Lock}
            iconBg="bg-rose-50 text-rose-600"
            valueColor="text-rose-600"
            subtitle="Không thể đăng nhập"
            dotColor="bg-rose-500"
            footerLabel="Yêu cầu bảo mật"
            footerValue="6.25% Đóng băng"
            footerValueColor="text-rose-600"
          />
          <MetricCard
            title="Tài khoản mới"
            value="4"
            icon={CreditCard}
            iconBg="bg-sky-50 text-sky-600"
            valueColor="text-sky-600"
            subtitle="Tạo gần đây (7 ngày)"
            dotColor="bg-sky-500"
            footerLabel="Đã kích hoạt 2FA"
            footerValue="4/4 Khởi tạo"
            footerValueColor="text-sky-700"
          />
        </div>

        {/* FILTER BAR */}
        <AccountFilterBar
          searchQuery={searchQuery}
          setSearchQuery={setSearchQuery}
          roleFilter={roleFilter}
          setRoleFilter={setRoleFilter}
          statusFilter={statusFilter}
          setStatusFilter={setStatusFilter}
          deptFilter={deptFilter}
          setDeptFilter={setDeptFilter}
          onReset={resetFilters}
        />

        {/* MAIN TABLE */}
        <AccountTable
          accounts={filteredAccounts}
          selectedIds={selectedIds}
          onToggleSelectAll={toggleSelectAll}
          onToggleSelectOne={toggleSelectOne}
          activeMenuId={activeMenuId}
          setActiveMenuId={setActiveMenuId}
          onToggleLock={toggleLockStatus}
          totalCount={128}
        />

        {/* MODAL THÊM TÀI KHOẢN MỚI */}
        <AccountFormModal
          isOpen={showAddModal}
          onClose={() => setShowAddModal(false)}
          onSubmit={handleAddAccountSubmit}
          form={newAccount}
          setForm={setNewAccount}
        />
      </div>
    </AdminLayout>
  );
}

export default AccountManagementPage;
