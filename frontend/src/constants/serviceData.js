export const SERVICE_TYPE_MAP = {
  EXAMINATION: { label: 'Khám bệnh', color: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]', chart: '#127e7a' },
  LAB: { label: 'Xét nghiệm', color: 'bg-[#e8f0fe] text-[#3b6fd8] border-[#cfdffb]', chart: '#3b7be0' },
  IMAGING: { label: 'Chẩn đoán hình ảnh', color: 'bg-[#f1edfd] text-[#7c5cdb] border-[#e2d9fb]', chart: '#8b5cf6' },
  FUNCTIONAL: { label: 'Thăm dò chức năng', color: 'bg-[#fdf3e1] text-[#c98a1b] border-[#f8e2b8]', chart: '#e98f2e' },
};

export const SERVICE_GROUP_LABELS = {
  HEMATOLOGY: 'XN Huyết học',
  BIOCHEMISTRY: 'XN Sinh hóa',
  MICROBIOLOGY: 'XN Vi sinh',
  ULTRASOUND: 'Siêu âm',
  XRAY: 'X-quang',
  CT: 'Chụp cắt lớp vi tính (CT)',
  MRI: 'Chụp cộng hưởng từ (MRI)',
  ENDOSCOPY: 'Nội soi',
  ECG: 'Điện tim',
};

export const SERVICE_STATUS_LABELS = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
};
