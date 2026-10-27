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

/**
 * Danh mục dịch vụ khám & cận lâm sàng chuẩn theo tài liệu demo master data (docs/demo-master-data.md)
 */
export const MASTER_SERVICES = [
  // Khám bệnh & Tiểu phẫu (dùng cho các phòng khám lâm sàng Khoa Nội & Khoa Ngoại)
  {
    id: 'srv-001',
    code: 'KB-001',
    name: 'Khám bệnh chuyên khoa',
    type: 'EXAMINATION',
    group: null,
    departmentCode: 'ALL_CLINICAL', // Thuộc các chuyên khoa lâm sàng (DEMO-NOI, DEMO-NGOAI)
    clinicPrice: 150000,
    insurancePrice: 50600,
    status: 'ACTIVE',
    estimatedMinutes: 15,
  },
  {
    id: 'srv-001b',
    code: 'KB-002',
    name: 'Khám chuyên khoa theo yêu cầu (Chuyên gia / PGS - TS)',
    type: 'EXAMINATION',
    group: null,
    departmentCode: 'ALL_CLINICAL',
    clinicPrice: 300000,
    insurancePrice: 50600,
    status: 'ACTIVE',
    estimatedMinutes: 20,
  },
  {
    id: 'srv-001c',
    code: 'KB-003',
    name: 'Hội chẩn chuyên khoa ca bệnh khó / phức tạp',
    type: 'EXAMINATION',
    group: null,
    departmentCode: 'ALL_CLINICAL',
    clinicPrice: 450000,
    insurancePrice: 120000,
    status: 'ACTIVE',
    estimatedMinutes: 30,
  },
  {
    id: 'srv-001d',
    code: 'TT-NGOAI-001',
    name: 'Tiểu phẫu cắt u bã đậu, xử lý u lành dưới da',
    type: 'EXAMINATION',
    group: null,
    departmentCode: 'DEMO-NGOAI',
    clinicPrice: 500000,
    insurancePrice: 220000,
    status: 'ACTIVE',
    estimatedMinutes: 30,
  },
  {
    id: 'srv-001e',
    code: 'TT-NGOAI-002',
    name: 'Thay băng, cắt chỉ vết thương phẫu thuật / chấn thương',
    type: 'EXAMINATION',
    group: null,
    departmentCode: 'DEMO-NGOAI',
    clinicPrice: 100000,
    insurancePrice: 35000,
    status: 'ACTIVE',
    estimatedMinutes: 15,
  },
  // Khoa Xét nghiệm (DEMO-XN)
  {
    id: 'srv-002',
    code: 'XN-HH-001',
    name: 'Tổng phân tích tế bào máu ngoại vi (bằng máy đếm laser)',
    type: 'LAB',
    group: 'HEMATOLOGY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 90000,
    insurancePrice: 46200,
    status: 'ACTIVE',
    estimatedMinutes: 20,
  },
  {
    id: 'srv-003',
    code: 'XN-HH-002',
    name: 'Đông máu cơ bản (PT, APTT, Fibrinogen)',
    type: 'LAB',
    group: 'HEMATOLOGY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 120000,
    insurancePrice: 63000,
    status: 'ACTIVE',
    estimatedMinutes: 30,
  },
  {
    id: 'srv-004',
    code: 'XN-SH-001',
    name: 'Định lượng Glucose máu mao mạch / huyết tương',
    type: 'LAB',
    group: 'BIOCHEMISTRY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 40000,
    insurancePrice: 21500,
    status: 'ACTIVE',
    estimatedMinutes: 15,
  },
  {
    id: 'srv-005',
    code: 'XN-SH-002',
    name: 'Định lượng HbA1c (Đánh giá đường huyết trung bình 3 tháng)',
    type: 'LAB',
    group: 'BIOCHEMISTRY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 150000,
    insurancePrice: 101000,
    status: 'ACTIVE',
    estimatedMinutes: 25,
  },
  {
    id: 'srv-006',
    code: 'XN-SH-003',
    name: 'Bộ mỡ máu toàn phần (Cholesterol, Triglycerid, HDL-C, LDL-C)',
    type: 'LAB',
    group: 'BIOCHEMISTRY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 160000,
    insurancePrice: 107000,
    status: 'ACTIVE',
    estimatedMinutes: 30,
  },
  {
    id: 'srv-007',
    code: 'XN-SH-004',
    name: 'Đo hoạt độ AST (GOT), ALT (GPT) - Men gan',
    type: 'LAB',
    group: 'BIOCHEMISTRY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 80000,
    insurancePrice: 43000,
    status: 'ACTIVE',
    estimatedMinutes: 20,
  },
  {
    id: 'srv-008',
    code: 'XN-SH-005',
    name: 'Tổng phân tích nước tiểu (10 thông số)',
    type: 'LAB',
    group: 'BIOCHEMISTRY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 60000,
    insurancePrice: 27400,
    status: 'ACTIVE',
    estimatedMinutes: 15,
  },
  {
    id: 'srv-009',
    code: 'XN-VS-001',
    name: 'Test nhanh tìm vi khuẩn Helicobacter pylori (H.pylori)',
    type: 'LAB',
    group: 'MICROBIOLOGY',
    departmentCode: 'DEMO-XN',
    clinicPrice: 120000,
    insurancePrice: null,
    status: 'ACTIVE',
    estimatedMinutes: 20,
  },
  // Khoa Chẩn đoán hình ảnh (DEMO-CDHA)
  {
    id: 'srv-010',
    code: 'CDHA-SA-001',
    name: 'Siêu âm ổ bụng tổng quát (gan, mật, tụy, lách, thận)',
    type: 'IMAGING',
    group: 'ULTRASOUND',
    departmentCode: 'DEMO-CDHA',
    clinicPrice: 200000,
    insurancePrice: 43900,
    status: 'ACTIVE',
    estimatedMinutes: 15,
  },
  {
    id: 'srv-011',
    code: 'CDHA-SA-002',
    name: 'Siêu âm tuyến giáp doppler màu',
    type: 'IMAGING',
    group: 'ULTRASOUND',
    departmentCode: 'DEMO-CDHA',
    clinicPrice: 200000,
    insurancePrice: 43900,
    status: 'ACTIVE',
    estimatedMinutes: 15,
  },
  {
    id: 'srv-012',
    code: 'CDHA-XQ-001',
    name: 'Chụp X-quang ngực thẳng kỹ thuật số (1 tư thế)',
    type: 'IMAGING',
    group: 'XRAY',
    departmentCode: 'DEMO-CDHA',
    clinicPrice: 150000,
    insurancePrice: 65400,
    status: 'ACTIVE',
    estimatedMinutes: 10,
  },
  {
    id: 'srv-013',
    code: 'CDHA-XQ-002',
    name: 'Chụp X-quang cột sống thắt lưng thẳng và nghiêng',
    type: 'IMAGING',
    group: 'XRAY',
    departmentCode: 'DEMO-CDHA',
    clinicPrice: 180000,
    insurancePrice: 65400,
    status: 'ACTIVE',
    estimatedMinutes: 15,
  },
  {
    id: 'srv-014',
    code: 'CDHA-CT-001',
    name: 'Chụp CT sọ não không tiêm thuốc cản quang đa dãy',
    type: 'IMAGING',
    group: 'CT',
    departmentCode: 'DEMO-CDHA',
    clinicPrice: 900000,
    insurancePrice: 522000,
    status: 'ACTIVE',
    estimatedMinutes: 20,
  },
  {
    id: 'srv-015',
    code: 'CDHA-MRI-001',
    name: 'Chụp MRI cột sống thắt lưng độ phân giải cao',
    type: 'IMAGING',
    group: 'MRI',
    departmentCode: 'DEMO-CDHA',
    clinicPrice: 2500000,
    insurancePrice: 1309000,
    status: 'ACTIVE',
    estimatedMinutes: 30,
  },
  // Khoa Thăm dò chức năng (DEMO-TDCN)
  {
    id: 'srv-016',
    code: 'TDCN-NS-001',
    name: 'Nội soi thực quản - dạ dày - tá tràng ống mềm',
    type: 'FUNCTIONAL',
    group: 'ENDOSCOPY',
    departmentCode: 'DEMO-TDCN',
    clinicPrice: 500000,
    insurancePrice: 244000,
    status: 'ACTIVE',
    estimatedMinutes: 25,
  },
  {
    id: 'srv-017',
    code: 'TDCN-DT-001',
    name: 'Ghi điện tim đồ (ECG) 12 chuyển đạo',
    type: 'FUNCTIONAL',
    group: 'ECG',
    departmentCode: 'DEMO-TDCN',
    clinicPrice: 80000,
    insurancePrice: 32800,
    status: 'ACTIVE',
    estimatedMinutes: 10,
  },
];

/**
 * Lấy danh sách dịch vụ y tế thuộc về chuyên khoa được chọn.
 * @param {object|string} deptOrCode - Chuyên khoa (obj hoặc code hoặc id)
 */
export function getAvailableServicesForDepartment(deptOrCode) {
  if (!deptOrCode) return MASTER_SERVICES;

  const deptCode = typeof deptOrCode === 'string' 
    ? deptOrCode 
    : (deptOrCode.code || deptOrCode.id || '');
  const deptName = typeof deptOrCode === 'object' ? (deptOrCode.name || deptOrCode.departmentName || '') : '';
  const deptType = typeof deptOrCode === 'object' ? deptOrCode.type : '';

  // Khoa Ngoại
  if (
    deptCode === 'DEMO-NGOAI' ||
    deptCode === 'dept-02' ||
    deptName.toLowerCase().includes('ngoại')
  ) {
    return MASTER_SERVICES.filter(
      (s) => s.departmentCode === 'DEMO-NGOAI' || s.departmentCode === 'ALL_CLINICAL'
    );
  }

  // Khoa Nội hoặc Chuyên khoa lâm sàng khác
  const isClinical =
    deptCode === 'DEMO-NOI' ||
    deptCode === 'dept-01' ||
    deptType === 'CLINICAL' ||
    deptName.toLowerCase().includes('nội') ||
    deptName.toLowerCase().includes('khám bệnh');

  if (isClinical) {
    return MASTER_SERVICES.filter(
      (s) => s.departmentCode === 'DEMO-NOI' || s.departmentCode === 'ALL_CLINICAL' || s.type === 'EXAMINATION'
    );
  }

  if (deptCode === 'DEMO-XN' || deptCode === 'dept-03' || deptName.toLowerCase().includes('xét nghiệm')) {
    return MASTER_SERVICES.filter((s) => s.departmentCode === 'DEMO-XN');
  }

  if (deptCode === 'DEMO-CDHA' || deptCode === 'dept-04' || deptName.toLowerCase().includes('chẩn đoán hình ảnh') || deptName.toLowerCase().includes('hình ảnh')) {
    return MASTER_SERVICES.filter((s) => s.departmentCode === 'DEMO-CDHA');
  }

  if (deptCode === 'DEMO-TDCN' || deptCode === 'dept-05' || deptName.toLowerCase().includes('thăm dò chức năng')) {
    return MASTER_SERVICES.filter((s) => s.departmentCode === 'DEMO-TDCN');
  }

  return MASTER_SERVICES;
}

/**
 * Lấy danh sách dịch vụ y tế được phục vụ / thực hiện tại một phòng khám cụ thể
 */
export function getServicesForRoom(room) {
  if (!room) return [];

  // Nếu phòng đã có sẵn mảng assignedServices thì ưu tiên dùng
  if (Array.isArray(room.assignedServices)) {
    return room.assignedServices;
  }
  if (Array.isArray(room.serviceIds)) {
    return MASTER_SERVICES.filter((s) =>
      room.serviceIds.includes(s.id) || room.serviceIds.includes(s.code)
    );
  }

  const code = (room.code || '').toUpperCase();
  const name = (room.name || '').toLowerCase();
  const type = room.type;

  // 1. Nếu là phòng khám lâm sàng
  if (type === 'EXAMINATION' || code.startsWith('PK-')) {
    const list = [MASTER_SERVICES.find((s) => s.code === 'KB-001')];
    if (code.includes('CXK') || name.includes('xương khớp')) {
      const xq = MASTER_SERVICES.find((s) => s.code === 'CDHA-XQ-002');
      if (xq) list.push(xq);
    } else if (code.includes('NT') || name.includes('nội tiết')) {
      const glu = MASTER_SERVICES.find((s) => s.code === 'XN-SH-001');
      const hba1c = MASTER_SERVICES.find((s) => s.code === 'XN-SH-002');
      if (glu) list.push(glu);
      if (hba1c) list.push(hba1c);
    } else if (code.includes('TH') || name.includes('tiêu hóa')) {
      const hp = MASTER_SERVICES.find((s) => s.code === 'XN-VS-001');
      if (hp) list.push(hp);
    }
    return list.filter(Boolean);
  }

  // 2. Nếu là phòng cận lâm sàng, lọc theo mã / nhóm dịch vụ
  if (code.includes('HH') || name.includes('huyết học')) {
    return MASTER_SERVICES.filter((s) => s.group === 'HEMATOLOGY');
  }
  if (code.includes('SH') || name.includes('sinh hóa')) {
    return MASTER_SERVICES.filter((s) => s.group === 'BIOCHEMISTRY');
  }
  if (code.includes('VS') || name.includes('vi sinh')) {
    return MASTER_SERVICES.filter((s) => s.group === 'MICROBIOLOGY');
  }
  if (code.includes('SA') || name.includes('siêu âm')) {
    return MASTER_SERVICES.filter((s) => s.group === 'ULTRASOUND');
  }
  if (code.includes('XQ') || name.includes('x-quang')) {
    return MASTER_SERVICES.filter((s) => s.group === 'XRAY');
  }
  if (code.includes('CT') || name.includes('ct')) {
    return MASTER_SERVICES.filter((s) => s.group === 'CT');
  }
  if (code.includes('MRI') || name.includes('mri')) {
    return MASTER_SERVICES.filter((s) => s.group === 'MRI');
  }
  if (code.includes('NS') || name.includes('nội soi')) {
    return MASTER_SERVICES.filter((s) => s.group === 'ENDOSCOPY');
  }
  if (code.includes('DT') || name.includes('điện tim')) {
    return MASTER_SERVICES.filter((s) => s.group === 'ECG');
  }

  // Fallback: nếu phòng cận lâm sàng chung
  if (type === 'PARACLINICAL') {
    return MASTER_SERVICES.filter((s) => s.type !== 'EXAMINATION').slice(0, 3);
  }

  return [MASTER_SERVICES[0]];
}


