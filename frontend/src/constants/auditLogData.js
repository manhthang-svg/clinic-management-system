/** UC-78: labels for the business audit trail (backend AuditLogAction / AuditOutcome / entity_type). */
export const AUDIT_ACTION_LABELS = {
  CREATE: 'Tạo mới',
  UPDATE: 'Cập nhật',
  CANCEL: 'Hủy',
  DELETE: 'Xóa',
  RESTORE: 'Khôi phục',
  LOCK: 'Khóa',
  UNLOCK: 'Mở khóa',
  VIEW_RECORD: 'Xem dữ liệu',
  PRINT: 'In',
  EXPORT: 'Xuất dữ liệu',
  SIGN: 'Ký số',
};

export const AUDIT_ACTION_TONES = {
  CREATE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  UPDATE: 'bg-sky-50 text-sky-700 border-sky-200',
  CANCEL: 'bg-amber-50 text-amber-700 border-amber-200',
  DELETE: 'bg-rose-50 text-rose-700 border-rose-200',
  RESTORE: 'bg-teal-50 text-teal-700 border-teal-200',
  LOCK: 'bg-rose-50 text-rose-700 border-rose-200',
  UNLOCK: 'bg-teal-50 text-teal-700 border-teal-200',
  VIEW_RECORD: 'bg-slate-50 text-slate-600 border-slate-200',
  PRINT: 'bg-violet-50 text-violet-700 border-violet-200',
  EXPORT: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  SIGN: 'bg-cyan-50 text-cyan-700 border-cyan-200',
};

export const AUDIT_OUTCOME_LABELS = {
  SUCCESS: 'Thành công',
  FAILURE: 'Thất bại',
};

/** Unknown types fall back to the raw value, so new modules still show up. */
export const AUDIT_ENTITY_LABELS = {
  staff_accounts: 'Tài khoản nhân viên',
  clinic_facility: 'Thông tin cơ sở',
  patients: 'Bệnh nhân',
  visits: 'Lượt khám',
  clinical_examinations: 'Phiếu khám',
  medical_histories: 'Tiền sử bệnh',
  conclusions: 'Kết luận khám',
  prescriptions: 'Đơn thuốc',
  follow_up_appointments: 'Hẹn tái khám',
  paraclinical_orders: 'Phiếu chỉ định CLS',
  paraclinical_order_details: 'Kết quả CLS',
  payments: 'Phiếu thu',
  medical_records: 'Hồ sơ bệnh án',
  MEDICAL_RECORD: 'Hồ sơ bệnh án',
  record_archives: 'Lưu trữ hồ sơ',
  medicine_catalog: 'Danh mục thuốc',
  services: 'Dịch vụ',
  lab_reference_ranges: 'Chỉ số xét nghiệm',
};

export const auditEntityLabel = (type) => AUDIT_ENTITY_LABELS[type] || type || '—';

const CLINIC_TIME_ZONE = 'Asia/Ho_Chi_Minh';

/** Audit times are shown in clinic time whatever the browser's zone. */
export const formatAuditTime = (value) =>
  value
    ? new Date(value).toLocaleString('vi-VN', {
      timeZone: CLINIC_TIME_ZONE,
      hour12: false,
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
    : '—';
