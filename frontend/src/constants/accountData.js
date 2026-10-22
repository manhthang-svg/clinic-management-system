// Mock accounts mirroring the dev seed (docs/demo-master-data.md) plus the bootstrap Admin.
// Replaced by the UC-5 API once the backend exposes it.
export const MOCK_ACCOUNTS = [
  {
    id: '01',
    username: 'admin',
    fullName: 'Quản trị hệ thống',
    email: 'admin@clinic.example',
    phone: '0901000001',
    role: 'ADMIN',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-21T08:12:00+07:00',
    createdAt: '2026-10-01T07:00:00+07:00',
  },
  {
    id: '02',
    username: 'bs.tranvanan',
    title: 'BSCKII',
    fullName: 'Trần Văn An',
    email: 'bs.tranvanan@clinic.example',
    phone: '0901000002',
    role: 'DOCTOR',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-21T07:45:00+07:00',
    createdAt: '2026-10-02T08:00:00+07:00',
  },
  {
    id: '03',
    username: 'bs.lethibinh',
    title: 'ThS.BS',
    fullName: 'Lê Thị Bình',
    email: 'bs.lethibinh@clinic.example',
    phone: '0901000003',
    role: 'DOCTOR',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-20T16:30:00+07:00',
    createdAt: '2026-10-02T08:05:00+07:00',
  },
  {
    id: '04',
    username: 'bs.phamminhchau',
    title: 'BSCKI',
    fullName: 'Phạm Minh Châu',
    email: 'bs.phamminhchau@clinic.example',
    phone: '0901000004',
    role: 'DOCTOR',
    status: 'LOCKED',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-18T13:45:00+07:00',
    createdAt: '2026-10-02T08:10:00+07:00',
  },
  {
    id: '05',
    username: 'ktv.nguyenvanbao',
    fullName: 'Nguyễn Văn Bảo',
    email: 'ktv.nguyenvanbao@clinic.example',
    phone: '0901000005',
    role: 'TECHNICIAN',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-21T07:20:00+07:00',
    createdAt: '2026-10-03T09:00:00+07:00',
  },
  {
    id: '06',
    username: 'ktv.tranthicuc',
    fullName: 'Trần Thị Cúc',
    email: 'ktv.tranthicuc@clinic.example',
    phone: '0901000006',
    role: 'TECHNICIAN',
    status: 'ACTIVE',
    passwordChangeRequired: true,
    lastLoginAt: '2026-10-16T09:12:00+07:00',
    createdAt: '2026-10-03T09:05:00+07:00',
  },
  {
    id: '07',
    username: 'ktv.lethidao',
    fullName: 'Lê Thị Đào',
    email: 'ktv.lethidao@clinic.example',
    phone: '0901000007',
    role: 'TECHNICIAN',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-20T14:02:00+07:00',
    createdAt: '2026-10-03T09:10:00+07:00',
  },
  {
    id: '08',
    username: 'lt.nguyenthihong',
    fullName: 'Nguyễn Thị Hồng',
    email: 'lt.nguyenthihong@clinic.example',
    phone: '0901000008',
    role: 'RECEPTIONIST',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-21T06:55:00+07:00',
    createdAt: '2026-10-04T08:00:00+07:00',
  },
  {
    id: '09',
    username: 'lt.tranvanquang',
    fullName: 'Trần Văn Quang',
    email: 'lt.tranvanquang@clinic.example',
    phone: '0901000009',
    role: 'RECEPTIONIST',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-20T08:50:00+07:00',
    createdAt: '2026-10-04T08:05:00+07:00',
  },
  {
    id: '10',
    username: 'lt.lethisuong',
    fullName: 'Lê Thị Sương',
    email: 'lt.lethisuong@clinic.example',
    phone: '0901000010',
    role: 'RECEPTIONIST',
    status: 'ACTIVE',
    passwordChangeRequired: true,
    lastLoginAt: null,
    createdAt: '2026-10-19T10:30:00+07:00',
  },
  {
    id: '11',
    username: 'tn.phamthithu',
    fullName: 'Phạm Thị Thu',
    email: 'tn.phamthithu@clinic.example',
    phone: '0901000011',
    role: 'CASHIER',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-21T07:58:00+07:00',
    createdAt: '2026-10-05T08:00:00+07:00',
  },
  {
    id: '12',
    username: 'tn.hoangvantuan',
    fullName: 'Hoàng Văn Tuấn',
    email: 'tn.hoangvantuan@clinic.example',
    phone: '0901000012',
    role: 'CASHIER',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-20T16:30:00+07:00',
    createdAt: '2026-10-05T08:05:00+07:00',
  },
  {
    id: '13',
    username: 'tn.vuthiyen',
    fullName: 'Vũ Thị Yến',
    email: 'tn.vuthiyen@clinic.example',
    phone: '0901000013',
    role: 'CASHIER',
    status: 'ACTIVE',
    passwordChangeRequired: false,
    lastLoginAt: '2026-10-19T11:03:00+07:00',
    createdAt: '2026-10-05T08:10:00+07:00',
  },
];

// Badge classes and chart colour per role, in the order the overview chart lists them.
export const ROLE_MAP = {
  ADMIN: { label: 'Quản trị viên', color: 'bg-[#f1edfd] text-[#7c5cdb] border-[#e2d9fb]', chart: '#9b7cf4' },
  DOCTOR: { label: 'Bác sĩ', color: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]', chart: '#4fd1c0' },
  RECEPTIONIST: { label: 'Lễ tân', color: 'bg-[#e8f0fe] text-[#3b6fd8] border-[#cfdffb]', chart: '#5aa7f2' },
  TECHNICIAN: { label: 'Kỹ thuật viên', color: 'bg-[#fdf3e1] text-[#c98a1b] border-[#f8e2b8]', chart: '#f7b955' },
  CASHIER: { label: 'Thu ngân', color: 'bg-[#fde8ec] text-[#d9466a] border-[#f9ccd6]', chart: '#f37b8f' },
};

export const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  RECEPTIONIST: 'Lễ tân',
  DOCTOR: 'Bác sĩ',
  TECHNICIAN: 'Kỹ thuật viên',
  CASHIER: 'Thu ngân',
};

export const STATUS_LABELS = {
  ACTIVE: 'Hoạt động',
  LOCKED: 'Đã khóa',
};

export const PAGE_SIZE_OPTIONS = [10, 20, 50];

// type: CREATED | UPDATED | PASSWORD_RESET | LOCKED | UNLOCKED
export const MOCK_ACCOUNT_ACTIVITIES = [
  { id: 'a1', type: 'CREATED', title: 'Tạo tài khoản mới', detail: 'Lê Thị Sương (lt.lethisuong)', time: '2 giờ trước' },
  { id: 'a2', type: 'UPDATED', title: 'Cập nhật thông tin', detail: 'Nguyễn Văn Bảo (ktv.nguyenvanbao)', time: '5 giờ trước' },
  { id: 'a3', type: 'PASSWORD_RESET', title: 'Đặt lại mật khẩu', detail: 'Trần Thị Cúc (ktv.tranthicuc)', time: '1 ngày trước' },
  { id: 'a4', type: 'LOCKED', title: 'Khóa tài khoản', detail: 'Phạm Minh Châu (bs.phamminhchau)', time: '2 ngày trước' },
  { id: 'a5', type: 'UNLOCKED', title: 'Mở khóa tài khoản', detail: 'Hoàng Văn Tuấn (tn.hoangvantuan)', time: '3 ngày trước' },
];

export const NAV_ITEMS = [
<<<<<<< Updated upstream
  { id: 'dashboard', label: 'Tổng quan', iconName: 'LayoutGrid' },
  { id: 'patients', label: 'Bệnh nhân', iconName: 'Users' },
  { id: 'appointments', label: 'Lịch khám', iconName: 'CalendarDays' },
  { id: 'examinations', label: 'Khám bệnh', iconName: 'Stethoscope' },
  { id: 'subclinical', label: 'Cận lâm sàng', iconName: 'Microscope' },
  { id: 'departments', label: 'Khoa / Phòng', iconName: 'Building2' },
  { id: 'billing', label: 'Thanh toán', iconName: 'CreditCard' },
  { id: 'reports', label: 'Báo cáo thống kê', iconName: 'ClipboardList' },
  {
    id: 'administration',
    label: 'Quản trị hệ thống',
    iconName: 'Settings2',
    children: [
      { id: 'accounts', label: 'Quản lý tài khoản' },
      { id: 'roles', label: 'Vai trò & phân quyền' },
      { id: 'audit', label: 'Nhật ký hệ thống' },
    ],
  },
  { id: 'settings', label: 'Cài đặt', iconName: 'Settings' },
=======
  { id: 'dashboard', label: 'Tổng quan', iconName: 'LayoutDashboard' },
  { id: 'patients', label: 'Quản lý bệnh nhân', iconName: 'Users2' },
  { id: 'appointments', label: 'Quản lý lịch khám', iconName: 'Calendar' },
  { id: 'examinations', label: 'Quản lý khám bệnh', iconName: 'Stethoscope' },
  { id: 'rooms', label: 'Quản lý phòng', iconName: 'DoorOpen' },
  { id: 'subclinical', label: 'Quản lý DV cận lâm sàng', iconName: 'Microscope' },
  { id: 'billing', label: 'Quản lý thanh toán', iconName: 'CreditCard' },
  { id: 'staff', label: 'Quản lý nhân sự', iconName: 'UserCheck' },
  { id: 'accounts', label: 'Quản lý tài khoản', iconName: 'UserCog' },
  { id: 'reports', label: 'Báo cáo thống kê', iconName: 'FileBarChart' },
  { id: 'settings', label: 'Cài đặt hệ thống', iconName: 'Settings' },
>>>>>>> Stashed changes
];

export const GENDER_LABELS = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
};

// Rooms from the dev seed that have staff assigned (docs/demo-master-data.md).
export const MOCK_ROOMS = {
  'PK-NOI-TQ': { name: 'Phòng khám Nội tổng quát', department: 'Khoa Nội', location: 'Tòa A - Tầng 1 - P.104' },
  'PK-NOI-NT': { name: 'Phòng khám Nội tiết', department: 'Khoa Nội', location: 'Tòa A - Tầng 1 - P.102' },
  'XN-SH': { name: 'Phòng Xét nghiệm Sinh hóa', department: 'Khoa Xét nghiệm', location: 'Tòa B - Tầng 1 - P.B102' },
  'CDHA-SA-1': { name: 'Phòng Siêu âm 1', department: 'Khoa Chẩn đoán hình ảnh', location: 'Tòa B - Tầng 2 - P.B201' },
  'TDCN-DT': { name: 'Phòng Điện tim', department: 'Khoa Thăm dò chức năng', location: 'Tòa B - Tầng 3 - P.B302' },
};

// UC-6 fields not shown in the list (gender and room follow the dev seed; the rest is illustrative).
// createdBy is empty only for the bootstrap Admin (AQ-10).
export const MOCK_ACCOUNT_PROFILES = {
  '01': { gender: 'MALE', dateOfBirth: '1985-03-12', address: '12 Nguyễn Trãi, Thanh Xuân, Hà Nội', passwordChangedAt: '2026-10-01T07:30:00+07:00' },
  '02': { createdBy: 'Quản trị hệ thống', gender: 'MALE', dateOfBirth: '1978-06-20', address: '45 Lê Duẩn, Hoàn Kiếm, Hà Nội', roomCode: 'PK-NOI-TQ', passwordChangedAt: '2026-10-02T09:00:00+07:00' },
  '03': { createdBy: 'Quản trị hệ thống', gender: 'FEMALE', dateOfBirth: '1986-11-02', address: '8 Kim Mã, Ba Đình, Hà Nội', roomCode: 'PK-NOI-NT', passwordChangedAt: '2026-10-02T09:10:00+07:00' },
  '04': {
    createdBy: 'Quản trị hệ thống',
    gender: 'MALE',
    dateOfBirth: '1982-01-15',
    address: '102 Giải Phóng, Hai Bà Trưng, Hà Nội',
    failedLoginCount: 3,
    lockReason: 'Nghỉ phép dài hạn, tạm khóa theo yêu cầu khoa.',
    lockedAt: '2026-10-19T08:00:00+07:00',
    passwordChangedAt: '2026-10-02T09:20:00+07:00',
  },
  '05': { createdBy: 'Quản trị hệ thống', gender: 'MALE', dateOfBirth: '1992-04-08', address: '23 Tây Sơn, Đống Đa, Hà Nội', roomCode: 'XN-SH', passwordChangedAt: '2026-10-03T10:00:00+07:00' },
  '06': { createdBy: 'Quản trị hệ thống', gender: 'FEMALE', dateOfBirth: '1995-09-27', address: '67 Cầu Giấy, Cầu Giấy, Hà Nội', roomCode: 'CDHA-SA-1', failedLoginCount: 5, loginBlockedUntil: new Date(Date.now() + 10 * 60 * 1000).toISOString() },
  '07': { createdBy: 'Quản trị hệ thống', gender: 'FEMALE', dateOfBirth: '1993-12-05', address: '5 Trần Duy Hưng, Cầu Giấy, Hà Nội', roomCode: 'TDCN-DT', passwordChangedAt: '2026-10-03T10:20:00+07:00' },
  '08': { createdBy: 'Quản trị hệ thống', gender: 'FEMALE', dateOfBirth: '1997-07-14', address: '30 Xã Đàn, Đống Đa, Hà Nội', passwordChangedAt: '2026-10-04T08:30:00+07:00' },
  '09': { createdBy: 'Quản trị hệ thống', gender: 'MALE', dateOfBirth: '1996-02-19', address: '14 Láng Hạ, Đống Đa, Hà Nội', passwordChangedAt: '2026-10-04T08:40:00+07:00' },
  '10': { createdBy: 'Quản trị hệ thống', gender: 'FEMALE', dateOfBirth: '2000-05-30', address: '9 Hoàng Quốc Việt, Cầu Giấy, Hà Nội' },
  '11': { createdBy: 'Quản trị hệ thống', gender: 'FEMALE', dateOfBirth: '1990-10-10', address: '77 Bạch Mai, Hai Bà Trưng, Hà Nội', passwordChangedAt: '2026-10-05T08:30:00+07:00' },
  '12': { createdBy: 'Quản trị hệ thống', gender: 'MALE', dateOfBirth: '1988-08-08', address: '21 Phạm Văn Đồng, Bắc Từ Liêm, Hà Nội', passwordChangedAt: '2026-10-05T08:40:00+07:00' },
  '13': { createdBy: 'Quản trị hệ thống', gender: 'FEMALE', dateOfBirth: '1994-03-03', address: '56 Nguyễn Chí Thanh, Đống Đa, Hà Nội', passwordChangedAt: '2026-10-05T08:50:00+07:00' },
};

// Functions each role reaches (docs/permission-matrix.md). UC-1 → UC-4 apply to every role.
export const ROLE_PERMISSIONS = {
  ADMIN: {
    modules: ['Tài khoản', 'Khoa / Phòng', 'Dịch vụ & giá', 'Báo cáo thống kê'],
    actions: [
      'Xem, tạo, cập nhật tài khoản', 'Phân quyền', 'Khóa / mở khóa', 'Đặt lại mật khẩu', 'Quản lý khoa',
      'Quản lý phòng', 'Phân công nhân sự', 'Quản lý dịch vụ', 'Cập nhật giá', 'Xem thống kê', 'Xuất báo cáo',
    ],
  },
  RECEPTIONIST: {
    modules: ['Bệnh nhân', 'Tiếp đón'],
    actions: ['Tìm kiếm bệnh nhân', 'Tạo, cập nhật hồ sơ hành chính', 'Kiểm tra thẻ BHYT', 'Đăng ký khám', 'In phiếu khám', 'Xem tình trạng phòng khám'],
  },
  DOCTOR: {
    modules: ['Khám bệnh', 'Bệnh án', 'Cận lâm sàng'],
    actions: [
      'Xem danh sách chờ khám', 'Gọi bệnh nhân', 'Ghi nhận khám', 'Chuyển phòng', 'Chỉ định cận lâm sàng', 'Xem kết quả cận lâm sàng',
      'Kết luận khám', 'Kê đơn thuốc', 'Hẹn tái khám', 'Đóng bệnh án', 'Xem, xuất bệnh án', 'Cập nhật tiền sử, dị ứng', 'Xem bệnh nhân đã khám',
    ],
  },
  TECHNICIAN: {
    modules: ['Cận lâm sàng'],
    actions: ['Xem danh sách chờ', 'Gọi và tiếp nhận chỉ định', 'Thực hiện dịch vụ', 'Nhập và gửi kết quả', 'In kết quả'],
  },
  CASHIER: {
    modules: ['Thanh toán'],
    actions: ['Xem phiếu chờ thanh toán', 'Tìm kiếm, xem lịch sử', 'Xác nhận thanh toán', 'In biên lai'],
  },
};

// UC-6 recent activity: sign-in events only (AQ-18).
export const LOGIN_EVENT_LABELS = {
  LOGIN_SUCCEEDED: 'Đăng nhập thành công',
  LOGOUT: 'Đăng xuất',
  LOGIN_FAILED: 'Đăng nhập thất bại',
};

/** Mock sign-in history for UC-6, newest first, at most 10 entries. */
export function mockLoginEvents(account, failedLoginCount = 0) {
  if (!account.lastLoginAt) return [];
  const last = new Date(account.lastLoginAt).getTime();
  const hour = 60 * 60 * 1000;
  const events = [];
  for (let i = 0; i < failedLoginCount; i += 1) {
    events.push({ type: 'LOGIN_FAILED', at: last + (i + 1) * 10 * 60 * 1000 });
  }
  for (let day = 0; events.length < 10; day += 1) {
    const login = last - day * 24 * hour;
    if (day > 0) events.push({ type: 'LOGOUT', at: login + 8 * hour });
    events.push({ type: 'LOGIN_SUCCEEDED', at: login });
  }
  return events
    .sort((a, b) => b.at - a.at)
    .slice(0, 10)
    .map((e, i) => ({ id: `${account.id}-${i}`, type: e.type, occurredAt: new Date(e.at).toISOString() }));
}
