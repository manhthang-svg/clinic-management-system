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

export const SERVICE_GROUP_TYPES = {
  HEMATOLOGY: 'LAB',
  BIOCHEMISTRY: 'LAB',
  MICROBIOLOGY: 'LAB',
  ULTRASOUND: 'IMAGING',
  XRAY: 'IMAGING',
  CT: 'IMAGING',
  MRI: 'IMAGING',
  ENDOSCOPY: 'FUNCTIONAL',
  ECG: 'FUNCTIONAL',
};

export const SERVICE_STATUS_LABELS = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
};

/**
 * Xác định phân loại chuyên khoa cận lâm sàng (LAB, IMAGING, FUNCTIONAL)
 * dựa vào mã hoặc tên chuyên khoa.
 */
export function getParaclinicalDepartmentType(dept) {
  if (!dept) return null;
  const code = (dept.code || dept.departmentCode || dept.makhoa || '').toUpperCase();
  const name = (dept.name || dept.departmentName || dept.tenkhoa || '').toLowerCase();

  // LAB: Khoa Xét nghiệm (Huyết học, Sinh hóa, Vi sinh)
  if (
    code.includes('XN') ||
    code.includes('LAB') ||
    name.includes('xét nghiệm') ||
    name.includes('huyết học') ||
    name.includes('sinh hóa') ||
    name.includes('vi sinh')
  ) {
    return 'LAB';
  }

  // IMAGING: Khoa Chẩn đoán hình ảnh (CT, MRI, X-quang, Siêu âm)
  if (
    code.includes('CDHA') ||
    code.includes('IMAGE') ||
    code.includes('IMAGING') ||
    code.includes('XQ') ||
    name.includes('chẩn đoán') ||
    name.includes('hình ảnh') ||
    name.includes('x-quang') ||
    name.includes('siêu âm')
  ) {
    return 'IMAGING';
  }

  // FUNCTIONAL: Khoa Thăm dò chức năng (Nội soi, Điện tim)
  if (
    code.includes('TDCN') ||
    code.includes('FUNC') ||
    name.includes('thăm dò') ||
    name.includes('chức năng') ||
    name.includes('nội soi') ||
    name.includes('điện tim')
  ) {
    return 'FUNCTIONAL';
  }

  return null;
}

/**
 * Chuẩn hóa loại nhóm dịch vụ kỹ thuật (LAB, IMAGING, FUNCTIONAL).
 * Ưu tiên:
 * 1. SERVICE_GROUP_TYPES mapping theo mã code (ULTRASOUND -> IMAGING, HEMATOLOGY -> LAB, ...)
 * 2. Tên tiếng Việt của nhóm kỹ thuật
 * 3. group.type từ backend (nếu có)
 */
export function resolveServiceGroupType(group) {
  if (!group) return null;
  const code = (group.code || group.serviceGroupCode || '').toUpperCase();
  if (code && SERVICE_GROUP_TYPES[code]) {
    return SERVICE_GROUP_TYPES[code];
  }

  const name = (group.name || group.serviceGroupName || '').toLowerCase();
  if (
    name.includes('huyết học') ||
    name.includes('sinh hóa') ||
    name.includes('vi sinh') ||
    name.includes('xét nghiệm')
  ) {
    return 'LAB';
  }
  if (
    name.includes('siêu âm') ||
    name.includes('x-quang') ||
    name.includes('cắt lớp') ||
    name.includes('cộng hưởng') ||
    name.includes('hình ảnh')
  ) {
    return 'IMAGING';
  }
  if (
    name.includes('nội soi') ||
    name.includes('điện tim') ||
    name.includes('thăm dò')
  ) {
    return 'FUNCTIONAL';
  }

  if (group.type) {
    return String(group.type).toUpperCase();
  }
  return null;
}

/**
 * Kiểm tra xem một nhóm dịch vụ kỹ thuật có thuộc về một chuyên khoa hay không.
 * Quy tắc:
 * 1. Nếu group.departmentId có giá trị (từ migration V35): BẮT BUỘC phải trùng khớp với dept.id.
 * 2. Nếu group.departmentId chưa được backfill (null): kiểm tra loại chuyên khoa tương ứng với loại nhóm kỹ thuật.
 */
export function doesServiceGroupMatchDepartment(group, dept) {
  if (!group || !dept) return false;

  // 1. Kiểm tra liên kết chính xác từ cột department_id (Migration V35)
  if (group.departmentId && dept.id) {
    return String(group.departmentId) === String(dept.id);
  }

  // 2. Kiểm tra phân loại tương thích giữa chuyên khoa và nhóm dịch vụ
  const deptType = getParaclinicalDepartmentType(dept);
  const groupType = resolveServiceGroupType(group);

  if (deptType && groupType) {
    return deptType === groupType;
  }

  return false;
}

const _CLEAN_END = {
};
