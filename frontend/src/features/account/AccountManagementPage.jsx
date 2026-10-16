import { useState, useMemo } from 'react';
import {
  LayoutDashboard,
  Users2,
  Calendar,
  Stethoscope,
  Microscope,
  CreditCard,
  UserCheck,
  UserCog,
  FileBarChart,
  Settings,
  Plus,
  Download,
  RotateCcw,
  Search,
  MoreVertical,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Building2,
  Bell,
  Lock,
  CheckCircle2,
  ShieldAlert,
  LogOut,
  KeyRound,
  X,
  Phone,
  Mail,
  UserCheck2,
  Filter,
} from 'lucide-react';

const MOCK_ACCOUNTS = [
  {
    id: '01',
    code: 'NV001',
    username: 'minhanh.reception',
    fullName: 'Nguyễn Minh Anh',
    staffId: 'ID: 04022401',
    position: 'NV Lễ tân',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&h=100&fit=crop&crop=face',
    email: 'minhanh.reception@metph.vn',
    phone: '0912 345 678',
    role: 'RECEPTIONIST',
    roleLabel: 'Lễ tân',
    roleColor: 'bg-blue-50 text-blue-700 border-blue-200',
    department: 'Bộ phận Lễ tân',
    status: 'ACTIVE',
    createdAt: '12/01/2024',
  },
  {
    id: '02',
    code: 'NV002',
    username: 'quocbao.doc',
    title: 'BS.CKI',
    fullName: 'Trần Quốc Bảo',
    staffId: 'ID: 04044910',
    position: 'Bác sĩ Chuyên khoa',
    avatar: 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=100&h=100&fit=crop&crop=face',
    email: 'quocbao.doc@metph.vn',
    phone: '0983 221 456',
    role: 'DOCTOR',
    roleLabel: 'Bác sĩ',
    roleColor: 'bg-teal-50 text-teal-700 border-teal-200',
    department: 'Khoa Nội',
    status: 'ACTIVE',
    createdAt: '15/01/2024',
  },
  {
    id: '03',
    code: 'NV003',
    username: 'thuha.tech',
    fullName: 'Lê Thu Hà',
    staffId: 'ID: 04080244',
    position: 'KTV Trưởng',
    avatar: 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=100&h=100&fit=crop&crop=face',
    email: 'thuha.tech@metph.vn',
    phone: '0904 899 123',
    role: 'TECHNICIAN',
    roleLabel: 'Kỹ thuật viên',
    roleColor: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    department: 'Phòng Xét nghiệm',
    status: 'ACTIVE',
    createdAt: '02/02/2024',
  },
  {
    id: '04',
    code: 'NV004',
    username: 'duclong.cash',
    fullName: 'Phạm Đức Long',
    staffId: 'ID: 04108722',
    position: 'Kế toán viên',
    initials: 'PL',
    email: 'duclong.cash@metph.vn',
    phone: '0977 654 321',
    role: 'CASHIER',
    roleLabel: 'Thu ngân',
    roleColor: 'bg-sky-50 text-sky-700 border-sky-200',
    department: 'Phòng Thu ngân',
    status: 'LOCKED',
    createdAt: '10/02/2024',
  },
  {
    id: '05',
    code: 'NV005',
    username: 'hoanglan.doc',
    title: 'ThS.BS',
    fullName: 'Nguyễn Hoàng Lan',
    staffId: 'ID: 04100111',
    position: 'Phó khoa Ngoại',
    initials: 'NL',
    email: 'hoanglan.doc@metph.vn',
    phone: '0938 112 990',
    role: 'DOCTOR',
    roleLabel: 'Bác sĩ',
    roleColor: 'bg-teal-50 text-teal-700 border-teal-200',
    department: 'Khoa Ngoại',
    status: 'ACTIVE',
    createdAt: '20/02/2024',
  },
  {
    id: '06',
    code: 'NV006',
    username: 'minhtuan.recep',
    fullName: 'Đỗ Minh Tuấn',
    staffId: 'ID: 04144200',
    position: 'NV Tiếp đón',
    initials: 'MT',
    email: 'minhtuan.recep@metph.vn',
    phone: '0965 432 109',
    role: 'RECEPTIONIST',
    roleLabel: 'Lễ tân',
    roleColor: 'bg-blue-50 text-blue-700 border-blue-200',
    department: 'Bộ phận Lễ tân',
    status: 'ACTIVE',
    createdAt: '01/03/2024',
  },
  {
    id: '07',
    code: 'NV007',
    username: 'ngocmai.tech',
    fullName: 'Vũ Ngọc Mai',
    staffId: 'ID: 04177812',
    position: 'KTV Sinh hóa',
    initials: 'VM',
    email: 'ngocmai.tech@metph.vn',
    phone: '0912 567 891',
    role: 'TECHNICIAN',
    roleLabel: 'Kỹ thuật viên',
    roleColor: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    department: 'Phòng Xét nghiệm',
    status: 'LOCKED',
    createdAt: '05/03/2024',
  },
  {
    id: '08',
    code: 'NV008',
    username: 'thanhson.cash',
    fullName: 'Bùi Thanh Sơn',
    staffId: 'ID: 04194302',
    position: 'Thu ngân BHYT',
    initials: 'BS',
    email: 'thanhson.cash@metph.vn',
    phone: '0918 765 432',
    role: 'CASHIER',
    roleLabel: 'Thu ngân',
    roleColor: 'bg-sky-50 text-sky-700 border-sky-200',
    department: 'Phòng Thu ngân',
    status: 'ACTIVE',
    createdAt: '12/03/2024',
  },
];

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

    const roleMap = {
      DOCTOR: { label: 'Bác sĩ', color: 'bg-teal-50 text-teal-700 border-teal-200' },
      RECEPTIONIST: { label: 'Lễ tân', color: 'bg-blue-50 text-blue-700 border-blue-200' },
      CASHIER: { label: 'Thu ngân', color: 'bg-sky-50 text-sky-700 border-sky-200' },
      TECHNICIAN: { label: 'Kỹ thuật viên', color: 'bg-indigo-50 text-indigo-700 border-indigo-200' },
      ADMIN: { label: 'Quản trị', color: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
    };

    const added = {
      id: String(accounts.length + 1).padStart(2, '0'),
      code: `NV${String(accounts.length + 1).padStart(3, '0')}`,
      username: newAccount.username,
      fullName: newAccount.fullName,
      staffId: `ID: 04${Math.floor(100000 + Math.random() * 900000)}`,
      position: roleMap[newAccount.role]?.label || 'Nhân viên',
      initials: newAccount.fullName.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase(),
      email: newAccount.email || `${newAccount.username}@metph.vn`,
      phone: newAccount.phone || '0900 000 000',
      role: newAccount.role,
      roleLabel: roleMap[newAccount.role]?.label || newAccount.role,
      roleColor: roleMap[newAccount.role]?.color || 'bg-slate-100 text-slate-700 border-slate-200',
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
    <div className="flex min-h-screen bg-[#f3f6f9] font-['Plus_Jakarta_Sans',sans-serif] text-slate-800 antialiased">
      {/* 1. LEFT SIDEBAR */}
      <aside className="w-[240px] bg-[#142332] text-slate-300 flex flex-col justify-between shrink-0 shadow-xl select-none">
        <div>
          {/* Brand Logo Header */}
          <div className="h-16 flex items-center gap-3 px-5 border-b border-white/5">
            <div className="w-8 h-8 rounded-lg bg-[#0d9488] text-white flex items-center justify-center font-black text-lg shadow-sm">
              +
            </div>
            <div>
              <div className="text-[15px] font-bold tracking-tight text-white leading-tight">METPH</div>
              <div className="text-[9px] uppercase tracking-wider text-slate-400 font-semibold leading-none">
                Hospital Management
              </div>
            </div>
          </div>

          {/* Navigation Menu */}
          <nav className="p-3 space-y-1 text-[13px] font-medium">
            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <LayoutDashboard className="w-4 h-4" />
              <span>Tổng quan</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <Users2 className="w-4 h-4" />
              <span>Quản lý bệnh nhân</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <Calendar className="w-4 h-4" />
              <span>Quản lý lịch khám</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <Stethoscope className="w-4 h-4" />
              <span>Quản lý khám bệnh</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <Microscope className="w-4 h-4" />
              <span>Quản lý DV cận lâm sàng</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <CreditCard className="w-4 h-4" />
              <span>Quản lý thanh toán</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <UserCheck className="w-4 h-4" />
              <span>Quản lý nhân sự</span>
            </button>

            {/* Active item: Quản lý tài khoản */}
            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl bg-[#0d9488] text-white font-semibold shadow-sm cursor-pointer"
            >
              <UserCog className="w-4 h-4" />
              <span>Quản lý tài khoản</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <FileBarChart className="w-4 h-4" />
              <span>Báo cáo thống kê</span>
            </button>

            <button
              type="button"
              className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all cursor-pointer"
            >
              <Settings className="w-4 h-4" />
              <span>Cài đặt hệ thống</span>
            </button>
          </nav>
        </div>

        {/* Sidebar Footer User Info */}
        <div className="p-3 border-t border-white/5">
          <div className="flex items-center justify-between p-2 rounded-xl bg-white/5">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 rounded-full bg-[#0d9488]/30 text-[#2dd4bf] font-bold text-xs flex items-center justify-center border border-[#0d9488]/40 shrink-0">
                AD
              </div>
              <div className="truncate">
                <div className="text-[12px] font-semibold text-white leading-tight truncate">
                  {currentAdmin?.fullName || 'Nguyễn Minh Ánh'}
                </div>
                <div className="text-[10px] text-slate-400 leading-tight">Quản trị viên</div>
              </div>
            </div>
            <button
              type="button"
              onClick={onLogout}
              className="text-slate-400 hover:text-rose-400 transition-colors p-1 cursor-pointer"
              title="Đăng xuất"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </aside>

      {/* 2. MAIN CONTENT AREA */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* Top Navbar Header */}
        <header className="h-16 bg-white border-b border-slate-200/80 px-6 flex items-center justify-between shrink-0 shadow-xs">
          {/* Breadcrumb */}
          <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
            <span className="hover:text-slate-700 cursor-pointer">Trang chủ</span>
            <span>/</span>
            <span className="text-slate-800 font-semibold">Quản lý tài khoản</span>
            <span className="hidden md:inline-flex text-slate-300 mx-2">|</span>
            <span className="hidden md:inline-flex items-center gap-1.5 text-slate-500 text-xs">
              <Building2 className="w-3.5 h-3.5 text-slate-400" />
              Bệnh viện Đa khoa METPH - Cổng Quản trị
            </span>
          </div>

          {/* Right Header Icons & Profile */}
          <div className="flex items-center gap-4">
            {/* Quick Search */}
            <div className="relative hidden lg:block w-64">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Tìm kiếm hồ sơ, nhân sự..."
                className="w-full pl-9 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:border-[#0d9488] focus:bg-white transition-all"
              />
            </div>

            {/* System Online Status */}
            <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50 border border-emerald-200/60 text-emerald-700 text-xs font-medium">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span>Hệ thống trực tuyến (Online)</span>
            </div>

            {/* Notification Bell */}
            <button
              type="button"
              className="p-2 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors relative cursor-pointer"
            >
              <Bell className="w-4 h-4" />
              <span className="w-2 h-2 rounded-full bg-rose-500 absolute top-1.5 right-1.5 ring-2 ring-white" />
            </button>

            {/* User Profile */}
            <div className="flex items-center gap-2.5 pl-2 border-l border-slate-200">
              <img
                src="https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=100&h=100&fit=crop&crop=face"
                alt="Admin Avatar"
                className="w-8 h-8 rounded-full object-cover border border-slate-200 shadow-xs"
              />
              <div className="text-left hidden sm:block">
                <div className="text-xs font-bold text-slate-800 leading-tight">
                  {currentAdmin?.fullName || 'Nguyễn Minh Ánh'}
                </div>
                <div className="text-[10px] text-slate-400 leading-tight">Admin Portal</div>
              </div>
              {onChangePassword && (
                <button
                  type="button"
                  onClick={onChangePassword}
                  className="text-slate-400 hover:text-[#0d9488] p-1 cursor-pointer"
                  title="Đổi mật khẩu tài khoản"
                >
                  <KeyRound className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>
        </header>

        {/* Content Body */}
        <div className="p-6 md:p-8 space-y-6">
          {notice && (
            <div role="status" className="p-3.5 rounded-xl bg-teal-50 border border-teal-200 text-teal-800 text-xs font-semibold flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-[#0d9488]" />
              <span>{notice}</span>
            </div>
          )}

          {/* Page Title & Top Actions */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">
                  Quản lý tài khoản
                </h1>
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80">
                  KM Enterprise
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-1">
                Quản lý tài khoản nhân viên, vai trò và quyền truy cập hệ thống bệnh viện METPH.
              </p>
            </div>

            <div className="flex items-center gap-2.5">
              <button
                type="button"
                onClick={() => alert('Xuất file danh sách tài khoản Excel/CSV thành công!')}
                className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 shadow-2xs transition-all cursor-pointer"
              >
                <Download className="w-3.5 h-3.5 text-slate-500" />
                <span>Xuất danh sách</span>
              </button>

              <button
                type="button"
                onClick={() => setShowAddModal(true)}
                className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm hover:shadow transition-all cursor-pointer"
              >
                <Plus className="w-4 h-4" />
                <span>Thêm tài khoản</span>
              </button>
            </div>
          </div>

          {/* 4 SUMMARY STAT CARDS */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Card 1: Tổng tài khoản */}
            <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-col justify-between">
              <div className="flex items-center justify-between mb-2">
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  Tổng tài khoản
                </span>
                <div className="w-8 h-8 rounded-xl bg-teal-50 text-[#0d9488] flex items-center justify-center">
                  <Users2 className="w-4 h-4" />
                </div>
              </div>
              <div className="text-3xl font-black text-slate-900 tracking-tight mb-2">128</div>
              <div className="text-[11px] text-slate-500 flex items-center gap-1 mb-2">
                <span className="w-1.5 h-1.5 rounded-full bg-teal-500" />
                <span>Tất cả nhân viên</span>
              </div>
              <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
                <span>Khả năng hệ thống</span>
                <span className="font-semibold text-teal-700">100% Quy định danh</span>
              </div>
            </div>

            {/* Card 2: Đang hoạt động */}
            <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-col justify-between">
              <div className="flex items-center justify-between mb-2">
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  Đang hoạt động
                </span>
                <div className="w-8 h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                  <CheckCircle2 className="w-4 h-4" />
                </div>
              </div>
              <div className="text-3xl font-black text-emerald-600 tracking-tight mb-2">116</div>
              <div className="text-[11px] text-slate-500 flex items-center gap-1 mb-2">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                <span>Có thể đăng nhập</span>
              </div>
              <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
                <span>Tỷ lệ khả dụng</span>
                <span className="font-semibold text-emerald-700">90.6% chuẩn hóa</span>
              </div>
            </div>

            {/* Card 3: Đang bị khóa */}
            <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-col justify-between">
              <div className="flex items-center justify-between mb-2">
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  Đang bị khóa
                </span>
                <div className="w-8 h-8 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center">
                  <Lock className="w-4 h-4" />
                </div>
              </div>
              <div className="text-3xl font-black text-rose-600 tracking-tight mb-2">8</div>
              <div className="text-[11px] text-slate-500 flex items-center gap-1 mb-2">
                <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                <span>Không thể đăng nhập</span>
              </div>
              <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
                <span>Yêu cầu bảo mật</span>
                <span className="font-semibold text-rose-600">6.25% Đóng băng</span>
              </div>
            </div>

            {/* Card 4: Tài khoản mới */}
            <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-col justify-between">
              <div className="flex items-center justify-between mb-2">
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  Tài khoản mới
                </span>
                <div className="w-8 h-8 rounded-xl bg-sky-50 text-sky-600 flex items-center justify-center">
                  <CreditCard className="w-4 h-4" />
                </div>
              </div>
              <div className="text-3xl font-black text-sky-600 tracking-tight mb-2">4</div>
              <div className="text-[11px] text-slate-500 flex items-center gap-1 mb-2">
                <span className="w-1.5 h-1.5 rounded-full bg-sky-500" />
                <span>Tạo gần đây (7 ngày)</span>
              </div>
              <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
                <span>Đã kích hoạt 2FA</span>
                <span className="font-semibold text-sky-700">4/4 Khởi tạo</span>
              </div>
            </div>
          </div>

          {/* FILTER BAR */}
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-wrap items-center justify-between gap-3">
            {/* Search Input */}
            <div className="relative flex-1 min-w-[260px]">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Tìm theo mã NV, tên, email hoặc số điện thoại..."
                className="w-full pl-10 pr-4 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white transition-all placeholder:text-slate-400"
              />
            </div>

            {/* Filter Dropdowns */}
            <div className="flex flex-wrap items-center gap-2.5">
              {/* Role filter */}
              <div className="relative">
                <select
                  value={roleFilter}
                  onChange={(e) => setRoleFilter(e.target.value)}
                  className="appearance-none pl-3 pr-8 py-2 text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] cursor-pointer"
                >
                  <option value="ALL">Tất cả vai trò</option>
                  <option value="DOCTOR">Bác sĩ</option>
                  <option value="RECEPTIONIST">Lễ tân</option>
                  <option value="CASHIER">Thu ngân</option>
                  <option value="TECHNICIAN">Kỹ thuật viên</option>
                </select>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>

              {/* Status filter */}
              <div className="relative">
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="appearance-none pl-3 pr-8 py-2 text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] cursor-pointer"
                >
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="ACTIVE">Hoạt động</option>
                  <option value="LOCKED">Bị khóa</option>
                </select>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>

              {/* Department filter */}
              <div className="relative">
                <select
                  value={deptFilter}
                  onChange={(e) => setDeptFilter(e.target.value)}
                  className="appearance-none pl-3 pr-8 py-2 text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] cursor-pointer"
                >
                  <option value="ALL">Tất cả khoa / phòng</option>
                  <option value="Khoa Nội">Khoa Nội</option>
                  <option value="Khoa Ngoại">Khoa Ngoại</option>
                  <option value="Phòng Xét nghiệm">Phòng Xét nghiệm</option>
                  <option value="Lễ tân">Bộ phận Lễ tân</option>
                  <option value="Thu ngân">Phòng Thu ngân</option>
                </select>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>

              {/* Reset filter */}
              <button
                type="button"
                onClick={resetFilters}
                className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Đặt lại bộ lọc</span>
              </button>
            </div>
          </div>

          {/* MAIN TABLE */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50/80 text-[11px] font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200/80">
                    <th className="p-4 w-10 text-center">
                      <input
                        type="checkbox"
                        checked={
                          selectedIds.length > 0 && selectedIds.length === filteredAccounts.length
                        }
                        onChange={toggleSelectAll}
                        className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-teal-500/20 cursor-pointer accent-[#0d9488]"
                      />
                    </th>
                    <th className="p-4">MÃ NV / TÊN ĐĂNG NHẬP</th>
                    <th className="p-4">NHÂN VIÊN</th>
                    <th className="p-4">THÔNG TIN LIÊN HỆ</th>
                    <th className="p-4">VAI TRÒ</th>
                    <th className="p-4">KHOA / PHÒNG</th>
                    <th className="p-4">TRẠNG THÁI</th>
                    <th className="p-4">NGÀY TẠO</th>
                    <th className="p-4 text-center">THAO TÁC</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {filteredAccounts.length === 0 ? (
                    <tr>
                      <td colSpan="9" className="p-8 text-center text-slate-400 text-xs">
                        Không tìm thấy tài khoản nào phù hợp với điều kiện lọc.
                      </td>
                    </tr>
                  ) : (
                    filteredAccounts.map((account) => {
                      const isSelected = selectedIds.includes(account.id);
                      return (
                        <tr
                          key={account.id}
                          className={`hover:bg-slate-50/70 transition-colors ${
                            isSelected ? 'bg-teal-50/30' : ''
                          }`}
                        >
                          {/* Checkbox */}
                          <td className="p-4 text-center">
                            <input
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => toggleSelectOne(account.id)}
                              className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-teal-500/20 cursor-pointer accent-[#0d9488]"
                            />
                          </td>

                          {/* Code & Username */}
                          <td className="p-4">
                            <div className="font-bold text-slate-800 tracking-tight">
                              {account.code}
                            </div>
                            <div className="text-[11px] text-slate-500 font-mono">
                              {account.username}
                            </div>
                          </td>

                          {/* Full Name & Avatar */}
                          <td className="p-4">
                            <div className="flex items-center gap-3">
                              {account.avatar ? (
                                <img
                                  src={account.avatar}
                                  alt={account.fullName}
                                  className="w-9 h-9 rounded-full object-cover border border-slate-200 shrink-0"
                                />
                              ) : (
                                <div className="w-9 h-9 rounded-full bg-slate-100 text-slate-600 font-bold text-xs flex items-center justify-center border border-slate-200 shrink-0">
                                  {account.initials}
                                </div>
                              )}
                              <div>
                                <div className="font-bold text-slate-800 leading-tight">
                                  {account.title && (
                                    <span className="text-[11px] text-[#0d9488] mr-1 font-semibold">
                                      {account.title}
                                    </span>
                                  )}
                                  {account.fullName}
                                </div>
                                <div className="text-[10px] text-slate-400 mt-0.5">
                                  {account.staffId} • {account.position}
                                </div>
                              </div>
                            </div>
                          </td>

                          {/* Contact Info */}
                          <td className="p-4">
                            <div className="text-slate-700 font-medium">{account.email}</div>
                            <div className="text-[11px] text-slate-400 mt-0.5 font-mono">
                              {account.phone}
                            </div>
                          </td>

                          {/* Role Pill */}
                          <td className="p-4">
                            <span
                              className={`inline-block px-2.5 py-0.5 rounded-md text-[11px] font-semibold border ${account.roleColor}`}
                            >
                              {account.roleLabel}
                            </span>
                          </td>

                          {/* Department */}
                          <td className="p-4">
                            <span className="text-slate-700 font-medium">
                              {account.department}
                            </span>
                          </td>

                          {/* Status */}
                          <td className="p-4">
                            {account.status === 'ACTIVE' ? (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                                <span>Hoạt động</span>
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                                <span>Bị khóa</span>
                              </span>
                            )}
                          </td>

                          {/* Created Date */}
                          <td className="p-4 text-slate-500 text-[11px] font-medium">
                            {account.createdAt}
                          </td>

                          {/* Actions Menu */}
                          <td className="p-4 text-center relative">
                            <button
                              type="button"
                              onClick={() =>
                                setActiveMenuId(activeMenuId === account.id ? null : account.id)
                              }
                              className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                            >
                              <MoreVertical className="w-4 h-4" />
                            </button>

                            {/* Dropdown Action Popover */}
                            {activeMenuId === account.id && (
                              <div className="absolute right-6 top-10 w-44 bg-white rounded-xl shadow-lg border border-slate-200 py-1.5 z-20 text-left text-xs">
                                <button
                                  type="button"
                                  onClick={() => {
                                    alert(`Xem chi tiết tài khoản ${account.fullName}`);
                                    setActiveMenuId(null);
                                  }}
                                  className="w-full px-3.5 py-1.5 text-slate-700 hover:bg-slate-50 flex items-center gap-2 cursor-pointer"
                                >
                                  <span>Xem chi tiết</span>
                                </button>

                                <button
                                  type="button"
                                  onClick={() => {
                                    alert(`Cấp lại mật khẩu cho ${account.fullName}`);
                                    setActiveMenuId(null);
                                  }}
                                  className="w-full px-3.5 py-1.5 text-slate-700 hover:bg-slate-50 flex items-center gap-2 cursor-pointer"
                                >
                                  <span>Đặt lại mật khẩu</span>
                                </button>

                                <button
                                  type="button"
                                  onClick={() => toggleLockStatus(account.id)}
                                  className="w-full px-3.5 py-1.5 text-rose-600 hover:bg-rose-50 flex items-center gap-2 cursor-pointer font-medium border-t border-slate-100"
                                >
                                  <span>
                                    {account.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
                                  </span>
                                </button>
                              </div>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            {/* Table Pagination Footer */}
            <div className="p-4 border-t border-slate-200/80 bg-white flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
              <div>
                Hiển thị <span className="font-semibold text-slate-800">1-{filteredAccounts.length}</span> trong
                tổng số <span className="font-semibold text-slate-800">128</span> tài khoản
              </div>

              <div className="flex items-center gap-4">
                <div className="flex items-center gap-1.5">
                  <span>Xem:</span>
                  <select className="bg-slate-50 border border-slate-200 rounded-lg px-2 py-1 text-xs focus:outline-none cursor-pointer">
                    <option>10 / trang</option>
                    <option>20 / trang</option>
                    <option>50 / trang</option>
                  </select>
                </div>

                {/* Page numbers */}
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    disabled
                    className="p-1 rounded-lg border border-slate-200 text-slate-300 cursor-not-allowed"
                  >
                    <ChevronLeft className="w-3.5 h-3.5" />
                  </button>

                  <button
                    type="button"
                    className="w-7 h-7 rounded-lg bg-[#0d9488] text-white font-bold text-xs flex items-center justify-center cursor-pointer shadow-xs"
                  >
                    1
                  </button>

                  <button
                    type="button"
                    className="w-7 h-7 rounded-lg hover:bg-slate-100 text-slate-600 text-xs flex items-center justify-center cursor-pointer"
                  >
                    2
                  </button>

                  <button
                    type="button"
                    className="w-7 h-7 rounded-lg hover:bg-slate-100 text-slate-600 text-xs flex items-center justify-center cursor-pointer"
                  >
                    3
                  </button>

                  <span className="text-slate-400 px-1">…</span>

                  <button
                    type="button"
                    className="w-7 h-7 rounded-lg hover:bg-slate-100 text-slate-600 text-xs flex items-center justify-center cursor-pointer"
                  >
                    16
                  </button>

                  <button
                    type="button"
                    className="p-1 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 cursor-pointer"
                  >
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 3. MODAL THÊM TÀI KHOẢN MỚI */}
      {showAddModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-in fade-in">
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-lg overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-slate-900">Thêm tài khoản nhân viên mới</h3>
                <p className="text-xs text-slate-500">
                  Khởi tạo thông tin định danh và phân quyền làm việc tại bệnh viện.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setShowAddModal(false)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddAccountSubmit} className="p-6 space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="font-semibold text-slate-700">Tên đăng nhập *</label>
                  <input
                    type="text"
                    required
                    placeholder="vd: bs.minhanh"
                    value={newAccount.username}
                    onChange={(e) => setNewAccount({ ...newAccount, username: e.target.value })}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
                  />
                </div>

                <div className="space-y-1">
                  <label className="font-semibold text-slate-700">Họ và tên *</label>
                  <input
                    type="text"
                    required
                    placeholder="vd: Nguyễn Minh Anh"
                    value={newAccount.fullName}
                    onChange={(e) => setNewAccount({ ...newAccount, fullName: e.target.value })}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="font-semibold text-slate-700">Vai trò chức vụ</label>
                  <select
                    value={newAccount.role}
                    onChange={(e) => setNewAccount({ ...newAccount, role: e.target.value })}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs cursor-pointer"
                  >
                    <option value="DOCTOR">Bác sĩ</option>
                    <option value="RECEPTIONIST">Lễ tân</option>
                    <option value="CASHIER">Thu ngân</option>
                    <option value="TECHNICIAN">Kỹ thuật viên</option>
                    <option value="ADMIN">Quản trị viên</option>
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="font-semibold text-slate-700">Khoa / Phòng ban</label>
                  <select
                    value={newAccount.department}
                    onChange={(e) => setNewAccount({ ...newAccount, department: e.target.value })}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs cursor-pointer"
                  >
                    <option value="Khoa Nội">Khoa Nội</option>
                    <option value="Khoa Ngoại">Khoa Ngoại</option>
                    <option value="Phòng Xét nghiệm">Phòng Xét nghiệm</option>
                    <option value="Bộ phận Lễ tân">Bộ phận Lễ tân</option>
                    <option value="Phòng Thu ngân">Phòng Thu ngân</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="font-semibold text-slate-700">Email làm việc</label>
                  <input
                    type="email"
                    placeholder="email@metph.vn"
                    value={newAccount.email}
                    onChange={(e) => setNewAccount({ ...newAccount, email: e.target.value })}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
                  />
                </div>

                <div className="space-y-1">
                  <label className="font-semibold text-slate-700">Số điện thoại</label>
                  <input
                    type="tel"
                    placeholder="09xx xxx xxx"
                    value={newAccount.phone}
                    onChange={(e) => setNewAccount({ ...newAccount, phone: e.target.value })}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="font-semibold text-slate-700">Mật khẩu tạm thời</label>
                <input
                  type="password"
                  placeholder="Để trống hệ thống sẽ tự sinh mật khẩu mặc định..."
                  value={newAccount.password}
                  onChange={(e) => setNewAccount({ ...newAccount, password: e.target.value })}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
                />
              </div>

              <div className="pt-4 flex items-center justify-end gap-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 font-medium text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer"
                >
                  Hủy bỏ
                </button>

                <button
                  type="submit"
                  className="px-4 py-2 font-semibold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm cursor-pointer"
                >
                  Khởi tạo tài khoản
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
