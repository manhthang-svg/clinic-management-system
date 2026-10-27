export const ROLE_MAP = {
  ADMIN: { label: 'Quản trị viên', color: 'bg-[#f1edfd] text-[#7c5cdb] border-[#e2d9fb]', chart: '#9b7cf4' },
  DOCTOR: { label: 'Bác sĩ', color: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]', chart: '#4fd1c0' },
  RECEPTIONIST: { label: 'Lễ tân', color: 'bg-[#e8f0fe] text-[#3b6fd8] border-[#cfdffb]', chart: '#5aa7f2' },
  TECHNICIAN: { label: 'Kỹ thuật viên', color: 'bg-[#fdf3e1] text-[#c98a1b] border-[#f8e2b8]', chart: '#f7b955' },
  CASHIER: { label: 'Thu ngân', color: 'bg-[#fde8ec] text-[#d9466a] border-[#f9ccd6]', chart: '#f37b8f' },
};

export const ROLE_LABELS = Object.fromEntries(
  Object.entries(ROLE_MAP).map(([key, value]) => [key, value.label]),
);

export const STATUS_LABELS = { ACTIVE: 'Hoạt động', LOCKED: 'Đã khóa' };
export const PAGE_SIZE_OPTIONS = [10, 20, 50];
export const GENDER_LABELS = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

export const NAV_ITEMS = [
  { id: 'dashboard', label: 'Tổng quan', iconName: 'LayoutGrid' },
  { id: 'reports', label: 'Báo cáo thống kê', iconName: 'ClipboardList' },
  { id: 'departments', label: 'Khoa / Phòng', iconName: 'Building2' },
  { id: 'services', label: 'Dịch vụ', iconName: 'BriefcaseMedical' },
  { id: 'medicines', label: 'Danh mục thuốc', iconName: 'Pill' },
  { id: 'shared-catalogs', label: 'Danh mục dùng chung', iconName: 'BookOpen' },
  {
    id: 'medical-records', label: 'Hồ sơ bệnh án', iconName: 'FileHeart',
    children: [
      { id: 'unlock-medical-record', label: 'Mở khóa hồ sơ' },
      { id: 'record-archive', label: 'Lưu trữ hồ sơ' },
    ],
  },
  {
    id: 'administration', label: 'Quản trị hệ thống', iconName: 'Settings2',
    children: [
      { id: 'accounts', label: 'Quản lý tài khoản' },
      { id: 'audit', label: 'Nhật ký hệ thống' },
      { id: 'merge-patients', label: 'Gộp hồ sơ trùng' },
    ],
  },
  { id: 'hospital-information', label: 'Thông tin bệnh viện', iconName: 'Hospital' },
  { id: 'settings', label: 'Cài đặt', iconName: 'Settings' },
];

export const ROLE_PERMISSIONS = {
  ADMIN: {
    modules: ['Tài khoản', 'Khoa / Phòng', 'Dịch vụ & giá', 'Báo cáo thống kê'],
    actions: ['Xem, tạo, cập nhật tài khoản', 'Phân quyền', 'Khóa / mở khóa', 'Đặt lại mật khẩu', 'Quản lý khoa', 'Quản lý phòng', 'Phân công nhân sự', 'Quản lý dịch vụ', 'Cập nhật giá', 'Xem thống kê', 'Xuất báo cáo'],
  },
  RECEPTIONIST: {
    modules: ['Bệnh nhân', 'Tiếp đón'],
    actions: ['Tìm kiếm bệnh nhân', 'Tạo, cập nhật hồ sơ hành chính', 'Kiểm tra thẻ BHYT', 'Đăng ký khám', 'In phiếu khám', 'Xem tình trạng phòng khám'],
  },
  DOCTOR: {
    modules: ['Khám bệnh', 'Bệnh án', 'Cận lâm sàng'],
    actions: ['Xem danh sách chờ khám', 'Gọi bệnh nhân', 'Ghi nhận khám', 'Chuyển phòng', 'Chỉ định cận lâm sàng', 'Xem kết quả cận lâm sàng', 'Kết luận khám', 'Kê đơn thuốc', 'Hẹn tái khám', 'Đóng bệnh án', 'Xem, xuất bệnh án'],
  },
  TECHNICIAN: {
    modules: ['Cận lâm sàng'],
    actions: ['Xem danh sách chờ', 'Tiếp nhận chỉ định', 'Thực hiện dịch vụ', 'Nhập và gửi kết quả', 'In kết quả'],
  },
  CASHIER: {
    modules: ['Thanh toán'],
    actions: ['Xem phiếu chờ thanh toán', 'Tìm kiếm, xem lịch sử', 'Xác nhận thanh toán', 'In biên lai'],
  },
};

export const LOGIN_EVENT_LABELS = {
  LOGIN_SUCCEEDED: 'Đăng nhập thành công',
  LOGOUT: 'Đăng xuất',
  LOGIN_FAILED: 'Đăng nhập thất bại',
  LOGIN_THROTTLED: 'Tạm chặn đăng nhập do sai mật khẩu nhiều lần',
};

/** Account change-history actions (backend AuditLogAction) shown in UC-6. */
export const CHANGE_ACTION_LABELS = {
  CREATE: 'Tạo tài khoản',
  UPDATE: 'Cập nhật thông tin',
  LOCK: 'Khóa tài khoản',
  UNLOCK: 'Mở khóa tài khoản',
};

/** Field names recorded in account change history. */
export const CHANGE_FIELD_LABELS = {
  fullName: 'Họ và tên',
  academicTitle: 'Học hàm / học vị',
  dateOfBirth: 'Ngày sinh',
  gender: 'Giới tính',
  phoneNumber: 'Số điện thoại',
  email: 'Email',
  address: 'Địa chỉ',
  role: 'Vai trò',
  status: 'Trạng thái',
  lockReason: 'Lý do khóa',
};
