// Labels for UC-40 Register Examination. Codes follow the backend enums where they exist.

// VisitDoituongbnLoai (BR-11).
export const VISIT_TYPE_LABELS = {
  BHYT: 'BHYT',
  DV: 'Dịch vụ',
};

// PriorityReason (BR-14, I1-D39).
export const PRIORITY_REASON_LABELS = {
  ELDERLY: 'Người cao tuổi',
  FRAIL_DISABLED: 'Người suy kiệt / khuyết tật',
};

// ASSUMPTION: intake-type codes sent as `lydotiepnhan`; the backend has no enum for them yet.
export const INTAKE_TYPE_LABELS = {
  EXAMINATION: 'Khám bệnh',
  FOLLOW_UP: 'Tái khám',
};

// TBD: the UC names only the default "Tự đến"; the other referral sources need an approved list.
export const REFERRAL_SOURCE_LABELS = {
  TU_DEN: 'Tự đến',
  CO_SO_Y_TE: 'Cơ sở y tế khác giới thiệu',
};

// VisitStatus.
export const VISIT_STATUS_LABELS = {
  WAITING_FEE_PAYMENT: 'Chờ thanh toán',
  WAITING: 'Chờ khám',
  IN_EXAMINATION: 'Đang khám',
  WAITING_PARACLINICAL: 'Chờ cận lâm sàng',
  WAITING_CONCLUSION: 'Chờ kết luận',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  ABANDONED: 'Bỏ khám',
};

// BR-17: a visit in any other status is active.
export const CLOSED_VISIT_STATUSES = ['COMPLETED', 'CANCELLED', 'ABANDONED'];

// patients.gioitinh.
export const PATIENT_GENDER_LABELS = {
  1: 'Nam',
  2: 'Nữ',
  3: 'Khác',
};
