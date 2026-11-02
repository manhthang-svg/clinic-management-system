// Demo data for the UC-67 result form until the technician API exists (ASSUMPTION: shapes follow
// docs/paraclinical-db-guide.md). Patients are fictional. Reference ranges are examples only and are
// NOT clinically validated.

// TBD: read from clinic_facility (UC-77). The endpoint is Admin-only today, and the parent agency
// ("Sở Y tế …") is not a facility field yet.
export const MOCK_CLINIC = {
  parentAgency: 'SỞ Y TẾ TỈNH MẪU',
  name: 'PHÒNG KHÁM ĐA KHOA METPH',
  address: 'Thôn Mẫu, xã Mẫu, tỉnh Mẫu',
  phone: '1900 0000',
};

/**
 * Print layout per service, following the clinic's printed forms: form code ("MS"), title, how the
 * result is laid out and who signs it. ASSUMPTION: form codes copied from the sample forms.
 */
export const PRINT_FORMS = {
  LAB: {
    formCode: '17/BV2', title: 'PHIẾU KẾT QUẢ XÉT NGHIỆM', layout: 'LAB', signer: 'Cử nhân xét nghiệm', priorityBox: true,
  },
  ULTRASOUND: {
    formCode: '11/BV-02', title: 'PHIẾU SIÊU ÂM', layout: 'ULTRASOUND', signer: 'Bác sĩ chuyên khoa', priorityBox: false,
    nameLabel: 'Họ tên người bệnh:', fields: ['address', 'room', 'diagnosis', 'request'],
  },
  ENDOSCOPY: {
    formCode: '11/BV-02', title: 'PHIẾU NỘI SOI', layout: 'ENDOSCOPY', signer: 'Bác sĩ chuyên khoa', priorityBox: false,
    nameLabel: 'Họ tên người bệnh:', fields: ['coverage', 'address', 'room', 'diagnosis', 'request'], ethnicity: true,
  },
  XRAY: {
    formCode: '09/BV2', title: 'PHIẾU CHIẾU/CHỤP X-QUANG', layout: 'XRAY', signer: 'BS CN Hình ảnh', priorityBox: true,
    nameLabel: 'Họ và tên:', fields: ['coverage', 'address', 'room', 'diagnosis'], ethnicity: true,
  },
  CT: {
    formCode: '09/BV2', title: 'PHIẾU CHỤP CẮT LỚP VI TÍNH', layout: 'CT_MRI', signer: 'Bác sĩ chuyên khoa', priorityBox: true,
    nameLabel: 'Họ và tên:', fields: ['coverage', 'address', 'room', 'diagnosis', 'request'],
  },
  MRI: {
    formCode: '09/BV2', title: 'PHIẾU CHỤP CỘNG HƯỞNG TỪ', layout: 'CT_MRI', signer: 'Bác sĩ chuyên khoa', priorityBox: true,
    nameLabel: 'Họ và tên:', fields: ['coverage', 'address', 'room', 'diagnosis', 'request', 'screening'], ethnicity: true,
  },
  ECG: {
    formCode: '12/BV-02', title: 'PHIẾU ĐIỆN TIM', layout: 'ECG', signer: 'Bác sĩ', priorityBox: true,
    nameLabel: 'Họ tên người bệnh:', fields: ['coverage', 'address', 'departmentRoom', 'symptoms', 'diagnosis', 'request'],
  },
};

/** The signed-in technician in demo mode. */
export const MOCK_TECHNICIAN = { id: 'tech-1', name: 'KTV Nguyễn Minh Tuấn' };

const patientA = {
  code: 'BN-000101', name: 'Nguyễn Văn Bình', age: 45, gender: 'Nam', sex: 1,
  address: 'Phường Mẫu, Tỉnh Mẫu', coverage: 'BHYT', insuranceNumber: 'DN4010000000001',
  ethnicity: 'Kinh', phone: '0900000101',
};
const patientB = {
  code: 'BN-000102', name: 'Trần Thị Hoa', age: 52, gender: 'Nữ', sex: 2,
  address: 'Xã Mẫu, Tỉnh Mẫu', coverage: 'Dịch vụ', insuranceNumber: null,
  ethnicity: 'Kinh', phone: '0900000102',
};
const patientC = {
  code: 'BN-000103', name: 'Lê Minh Khang', age: 33, gender: 'Nam', sex: 1,
  address: 'Phường Mẫu, Tỉnh Mẫu', coverage: 'BHYT', insuranceNumber: 'GD4010000000003',
  ethnicity: 'Kinh', phone: '0900000103',
};

const common = {
  orderingDoctor: 'BS Phạm Thanh Long',
  orderingRoom: 'PK Nội 1',
  department: 'Khoa Khám bệnh',
  performingDepartment: 'CĐHA-TDCN',
  priority: 'NORMAL',
};

// Reference shapes: RANGE (low–high), UPPER (normal below high), LOWER (normal above low),
// QUALITATIVE (normal values). Mirrors lab_reference_ranges.reference_type.
const CBC = [
  { code: 'WBC', name: 'WBC – Số lượng bạch cầu', unit: '10^9/L', ref: { type: 'RANGE', low: 4, high: 10 } },
  { code: 'LYM#', name: 'Lymph# – Số lượng bạch cầu lympho', unit: '10^9/L', ref: { type: 'RANGE', low: 0.8, high: 4 } },
  { code: 'MID#', name: 'Mid# – Số lượng bạch cầu mono', unit: '10^9/L', ref: { type: 'RANGE', low: 0.1, high: 1.2 } },
  { code: 'GRAN#', name: 'Gran# – Số lượng bạch cầu hạt', unit: '10^9/L', ref: { type: 'RANGE', low: 2, high: 7 } },
  { code: 'LYM%', name: 'Lymph% – Tỷ lệ bạch cầu lympho', unit: '%', ref: { type: 'RANGE', low: 18, high: 48 } },
  { code: 'MID%', name: 'Mid% – Tỷ lệ bạch cầu mono', unit: '%', ref: { type: 'RANGE', low: 3, high: 9 } },
  { code: 'GRAN%', name: 'Gran% – Tỷ lệ bạch cầu hạt', unit: '%', ref: { type: 'RANGE', low: 40, high: 75 } },
  { code: 'HGB', name: 'HGB – Nồng độ huyết sắc tố', unit: 'g/L', ref: { type: 'RANGE', low: 130, high: 180 } },
  { code: 'RBC', name: 'RBC – Số lượng hồng cầu', unit: '10^12/L', ref: { type: 'RANGE', low: 4.2, high: 5.8 } },
  { code: 'HCT', name: 'HCT – Hematocrit', unit: '%', ref: { type: 'RANGE', low: 37, high: 50 } },
  { code: 'MCV', name: 'MCV – Thể tích trung bình hồng cầu', unit: 'fL', ref: { type: 'RANGE', low: 82, high: 95 } },
  { code: 'MCH', name: 'MCH – Lượng huyết sắc tố trung bình hồng cầu', unit: 'pg', ref: { type: 'RANGE', low: 27, high: 33 } },
  { code: 'MCHC', name: 'MCHC – Nồng độ huyết sắc tố trung bình hồng cầu', unit: 'g/L', ref: { type: 'RANGE', low: 317, high: 356 } },
  { code: 'PLT', name: 'PLT – Số lượng tiểu cầu', unit: '10^9/L', ref: { type: 'RANGE', low: 140, high: 400 } },
].map((p) => ({ ...p, valueType: 'NUMERIC', group: 'Tổng phân tích tế bào máu ngoại vi' }));

export const QUALITATIVE_OPTIONS = [
  { value: 'NEGATIVE', label: 'Âm tính' },
  { value: 'TRACE', label: 'Vết' },
  { value: 'POSITIVE', label: 'Dương tính' },
];

const URINE = [
  { code: 'U_PH', name: 'pH', valueType: 'NUMERIC', unit: '', ref: { type: 'RANGE', low: 5, high: 8 } },
  { code: 'U_SG', name: 'Tỷ trọng (SG)', valueType: 'NUMERIC', unit: '', ref: { type: 'RANGE', low: 1.005, high: 1.03 } },
  { code: 'U_PRO', name: 'Protein niệu', valueType: 'QUALITATIVE', unit: '', ref: { type: 'QUALITATIVE', normal: ['NEGATIVE'] } },
  { code: 'U_GLU', name: 'Glucose niệu', valueType: 'QUALITATIVE', unit: '', ref: { type: 'QUALITATIVE', normal: ['NEGATIVE'] } },
  { code: 'U_LEU', name: 'Bạch cầu niệu', valueType: 'QUALITATIVE', unit: '', ref: { type: 'QUALITATIVE', normal: ['NEGATIVE'] } },
  { code: 'U_NIT', name: 'Nitrit', valueType: 'QUALITATIVE', unit: '', ref: { type: 'QUALITATIVE', normal: ['NEGATIVE'] } },
].map((p) => ({ ...p, group: 'Tổng phân tích nước tiểu' }));

/** Text that pre-fills the description of a descriptive result; no database table until a use case exists (V34). */
export const RESULT_TEMPLATES = {
  'SA-OB': {
    description: [
      'GAN: Kích thước bình thường, nhu mô đều, không có khối.',
      'MẬT: Đường mật trong và ngoài gan không giãn, không có sỏi.',
      'Túi mật: Thành mỏng, dịch trong, không có sỏi.',
      'TỤY: Kích thước bình thường, nhu mô đều.',
      'LÁCH: Kích thước bình thường, nhu mô đều.',
      'THẬN PHẢI: Kích thước bình thường, nhu mô đều, đài bể thận không giãn.',
      'THẬN TRÁI: Kích thước bình thường, nhu mô đều, đài bể thận không giãn.',
      'BÀNG QUANG: Thành mỏng, dịch trong, không có sỏi.',
      'NHẬN XÉT KHÁC: Ổ bụng không có dịch, không có hạch bất thường.',
    ].join('\n'),
    conclusion: 'Hiện tại chưa phát hiện bất thường trên siêu âm.',
  },
  'NS-DDTT': {
    description: [
      'Thực quản: Niêm mạc bình thường.',
      'Tâm vị: Đóng kín bình thường.',
      'Phình vị dạ dày: Sạch, niêm mạc bình thường.',
      'Thân vị: Bình thường.',
      'Hang vị: Niêm mạc bình thường.',
      'Môn vị: Tròn, lưu thông tốt.',
      'Hành tá tràng: Bình thường.',
      'Tá tràng: Bình thường.',
    ].join('\n'),
    conclusion: 'Hình ảnh nội soi thực quản – dạ dày – tá tràng bình thường.',
  },
  'CT-OB': {
    technique: 'Chụp CT ổ bụng độ dày lớp cắt 5 mm từ vòm hoành đến tiểu khung, không tiêm thuốc cản quang.',
    description: [
      '- Gan: Kích thước bình thường, bờ đều. Không thấy khối bất thường.',
      '- Đường mật trong và ngoài gan: Không giãn.',
      '- Tụy, lách: Hình dạng và kích thước bình thường.',
      '- Thận hai bên: Hình dạng và kích thước bình thường, không có sỏi.',
      '- Ổ bụng: Không có dịch, khí tự do.',
    ].join('\n'),
    conclusion: 'Hiện tại chưa phát hiện bất thường trên phim CT ổ bụng.',
  },
  'MRI-CSC': {
    technique: 'Chụp cộng hưởng từ cột sống cổ với các chuỗi xung sagittal T1W, T2W, T2 STIR, axial T2W, không tiêm thuốc đối quang từ.',
    description: [
      '- Đường cong sinh lý cột sống cổ bình thường.',
      '- Các thân đốt sống cổ tín hiệu trong giới hạn bình thường, không có hiện tượng xẹp, trượt đốt sống.',
      '- Các đĩa đệm không thoát vị.',
      '- Hệ thống dây chằng cột sống cổ hình thái và tín hiệu trong giới hạn bình thường.',
      '- Không thấy bất thường về kích thước và tín hiệu của tủy.',
      '- Không thấy bất thường phần mềm quanh thân đốt sống.',
    ].join('\n'),
    conclusion: 'Hiện tại chưa phát hiện bất thường trên phim MRI cột sống cổ.',
  },
  'XQ-NT': {
    description: [
      '- Không thấy bất thường nhu mô phổi.',
      '- Không thấy hạch rốn phổi.',
      '- Không thấy tràn dịch, tràn khí màng phổi hai bên.',
      '- Hình tim và trung thất không thấy bất thường.',
    ].join('\n'),
    conclusion: '- Hình ảnh tim phổi bình thường.',
  },
  'DT': {
    description: [
      'Huyết áp: … mmHg',
      'Chuyển đạo mẫu: DII',
      'Nhịp: Nhịp xoang, tần số … CK/P',
      'Trục: Trung gian',
      'P: Bình thường',
      'PQ: Bình thường',
      'QRS: Bình thường',
      'ST: Bình thường',
      'T: Bình thường',
      'QT: Bình thường',
    ].join('\n'),
    conclusion: 'Hiện tại điện tim đồ bình thường.',
  },
};

export const MOCK_RESULT_ORDERS = [
  {
    ...common, id: 'cls-001', printForm: 'LAB', orderCode: 'CLS-2026-000101', kind: 'LAB', serviceCode: 'XN-HH-001',
    serviceName: 'Tổng phân tích tế bào máu ngoại vi (bằng máy đếm tổng trở)', section: 'XÉT NGHIỆM HUYẾT HỌC',
    patient: patientA, diagnosis: 'N20.2 – Sỏi thận kèm sỏi niệu quản', clinicalNote: 'Đau hông lưng trái 3 ngày.',
    orderedAt: '2026-11-01T07:40:00Z', performedAt: '2026-11-01T07:55:00Z', status: 'PERFORMED',
    performer: MOCK_TECHNICIAN, parameters: CBC, result: null,
  },
  {
    ...common, id: 'cls-002', printForm: 'LAB', orderCode: 'CLS-2026-000102', kind: 'LAB', serviceCode: 'XN-SH-GLU',
    serviceName: 'Định lượng Glucose [Máu]', section: 'XÉT NGHIỆM SINH HÓA',
    patient: patientB, diagnosis: 'E11.9 – Bệnh đái tháo đường týp 2', clinicalNote: 'Theo dõi đường huyết định kỳ.',
    orderedAt: '2026-11-01T07:10:00Z', performedAt: '2026-11-01T07:20:00Z', status: 'PERFORMED',
    performer: MOCK_TECHNICIAN,
    parameters: [{
      code: 'GLU', name: 'Glucose', valueType: 'NUMERIC', unit: 'mmol/L', group: null,
      ref: { type: 'RANGE', low: 3.9, high: 6.4 },
    }],
    result: null,
  },
  {
    ...common, id: 'cls-003', printForm: 'LAB', orderCode: 'CLS-2026-000103', kind: 'LAB', serviceCode: 'XN-NT-001',
    serviceName: 'Tổng phân tích nước tiểu', section: 'XÉT NGHIỆM NƯỚC TIỂU',
    patient: patientC, diagnosis: 'N39.0 – Nhiễm khuẩn đường tiết niệu', clinicalNote: 'Tiểu buốt, tiểu rắt.',
    orderedAt: '2026-11-01T08:05:00Z', performedAt: '2026-11-01T08:15:00Z', status: 'PERFORMED',
    performer: MOCK_TECHNICIAN, parameters: URINE, result: null,
  },
  {
    ...common, id: 'cls-004', printForm: 'ULTRASOUND', orderCode: 'CLS-2026-000104', kind: 'IMAGING', serviceCode: 'SA-OB',
    serviceName: 'Siêu âm ổ bụng tổng quát', section: 'PHIẾU SIÊU ÂM',
    patient: patientA, diagnosis: 'N20.2 – Sỏi thận kèm sỏi niệu quản', clinicalNote: 'Đau hông lưng trái 3 ngày.',
    orderedAt: '2026-11-01T07:40:00Z', performedAt: '2026-11-01T08:20:00Z', status: 'PERFORMED',
    performer: MOCK_TECHNICIAN, result: null,
  },
  {
    ...common, id: 'cls-005', printForm: 'ENDOSCOPY', orderCode: 'CLS-2026-000105', kind: 'FUNCTIONAL', serviceCode: 'NS-DDTT',
    serviceName: 'Nội soi thực quản – dạ dày – tá tràng', section: 'PHIẾU NỘI SOI', priority: 'EMERGENCY',
    patient: patientB, diagnosis: 'R07.3 – Đau ngực khác', clinicalNote: 'Đau thượng vị, ợ chua.',
    orderedAt: '2026-11-01T08:00:00Z', performedAt: '2026-11-01T08:40:00Z', status: 'PERFORMED',
    performer: MOCK_TECHNICIAN,
    result: {
      status: 'DRAFT', technique: '', description: '', sections: { thucQuan: 'Có vết trợt dài < 5 mm trên đường Z.' },
      conclusion: '', recommendation: '', imageLink: '', files: [], savedAt: '2026-11-01T08:45:00Z',
    },
  },
  {
    ...common, id: 'cls-006', printForm: 'CT', orderCode: 'CLS-2026-000106', kind: 'IMAGING', serviceCode: 'CT-OB',
    serviceName: 'Chụp CT ổ bụng không tiêm thuốc cản quang', section: 'PHIẾU CHỤP CẮT LỚP VI TÍNH',
    patient: patientC, diagnosis: 'N20.2 – Sỏi thận kèm sỏi niệu quản', clinicalNote: 'Siêu âm nghi sỏi niệu quản trái.',
    orderedAt: '2026-11-01T08:30:00Z', performedAt: '2026-11-01T09:00:00Z', status: 'PERFORMED',
    performer: MOCK_TECHNICIAN, result: null,
  },
  {
    ...common, id: 'cls-007', printForm: 'ECG', symptoms: 'Đau tức ngực trái, hồi hộp.', orderCode: 'CLS-2026-000107', kind: 'FUNCTIONAL', serviceCode: 'DT',
    serviceName: 'Điện tim thường', section: 'PHIẾU ĐIỆN TIM',
    patient: patientA, diagnosis: 'R07.3 – Đau ngực khác', clinicalNote: 'Đau tức ngực trái.',
    orderedAt: '2026-11-01T07:40:00Z', performedAt: '2026-11-01T09:10:00Z', status: 'PERFORMED',
    performer: { id: 'tech-2', name: 'KTV Phạm Thu Hà' }, result: null,
  },
  {
    ...common, id: 'cls-009', printForm: 'MRI', orderCode: 'CLS-2026-000109', kind: 'IMAGING', serviceCode: 'MRI-CSC',
    serviceName: 'Chụp MRI cột sống cổ không tiêm thuốc đối quang từ', section: 'PHIẾU CHỤP CỘNG HƯỞNG TỪ',
    patient: patientB, diagnosis: 'M47.8 – Thoái hóa cột sống cổ', clinicalNote: 'Đau vai gáy lan tay trái.',
    screening: { metalImplant: 'Không', priorResults: 'X-quang cột sống cổ: thoái hóa C4–C6' },
    orderedAt: '2026-11-01T09:00:00Z', performedAt: '2026-11-01T09:40:00Z', status: 'PERFORMED',
    performer: MOCK_TECHNICIAN, result: null,
  },
  {
    ...common, id: 'cls-008', printForm: 'XRAY', orderCode: 'CLS-2026-000108', kind: 'IMAGING', serviceCode: 'XQ-NT',
    serviceName: 'Chụp X-quang ngực thẳng (số hóa 1 phim)', section: 'PHIẾU CHIẾU/CHỤP X-QUANG',
    patient: patientC, diagnosis: 'J18.9 – Viêm phổi, không xác định', clinicalNote: 'Ho kéo dài 2 tuần.',
    orderedAt: '2026-11-01T07:00:00Z', performedAt: '2026-11-01T07:15:00Z', status: 'COMPLETED',
    performer: MOCK_TECHNICIAN,
    result: {
      status: 'SUBMITTED', technique: '',
      description: '- Không thấy bất thường nhu mô phổi.\n- Không thấy tràn dịch, tràn khí màng phổi hai bên.\n- Hình tim và trung thất không thấy bất thường.',
      conclusion: 'Hình ảnh tim phổi bình thường.', recommendation: '', imageLink: '', files: [],
      savedAt: '2026-11-01T07:30:00Z', submittedAt: '2026-11-01T07:30:00Z',
    },
  },
];
