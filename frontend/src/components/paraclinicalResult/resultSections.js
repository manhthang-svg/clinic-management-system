// Per-organ / per-site fields of the descriptive result forms, taken from the clinic's printed slips.
// The fields are joined into one description text, which is what imaging_results.kqcls_mota stores.

/** ULTRASOUND: abdominal ultrasound slip. `sex` limits a field to male (1) or female (2) patients. */
const ULTRASOUND = [
  { key: 'gan', label: 'GAN', normal: 'Kích thước bình thường, nhu mô đều, không có khối.' },
  { key: 'mat', label: 'MẬT', normal: 'Đường mật trong và ngoài gan không giãn, không có sỏi.' },
  { key: 'tuiMat', label: 'Túi mật', normal: 'Thành mỏng, dịch trong, không có sỏi.' },
  { key: 'tinhMach', label: 'Tĩnh mạch trong và ngoài gan', normal: 'Không giãn, không có huyết khối.' },
  { key: 'tuy', label: 'TỤY', normal: 'Kích thước bình thường, nhu mô đều. Ống wirsung không giãn.' },
  { key: 'lach', label: 'LÁCH', normal: 'Kích thước bình thường, nhu mô đều.' },
  { key: 'thanPhai', label: 'Thận phải', normal: 'Kích thước bình thường, nhu mô đều, phân biệt tuỷ vỏ rõ, đài bể thận không giãn, không có sỏi.' },
  { key: 'nieuQuanPhai', label: 'Niệu quản phải', normal: 'Không giãn, không có sỏi.' },
  { key: 'thanTrai', label: 'Thận trái', normal: 'Kích thước bình thường, nhu mô đều, phân biệt tuỷ vỏ rõ, đài bể thận không giãn, không có sỏi.' },
  { key: 'nieuQuanTrai', label: 'Niệu quản trái', normal: 'Không giãn, không có sỏi.' },
  { key: 'bangQuang', label: 'Bàng quang', normal: 'Thành mỏng, dịch trong, không có sỏi.' },
  { key: 'tienLietTuyen', label: 'TIỀN LIỆT TUYẾN', normal: 'Kích thước bình thường, nhu mô đều.', sex: 1 },
  { key: 'tuCung', label: 'TỬ CUNG – PHẦN PHỤ', normal: 'Tử cung và hai phần phụ không thấy bất thường.', sex: 2 },
  { key: 'khac', label: 'NHẬN XÉT KHÁC', normal: 'Ổ bụng không có dịch, không có hạch bất thường.' },
];

/** ENDOSCOPY: upper GI endoscopy slip. */
const ENDOSCOPY = [
  { key: 'thucQuan', label: 'Thực quản', normal: 'Niêm mạc bình thường.' },
  { key: 'tamVi', label: 'Tâm vị', normal: 'Đóng kín bình thường.' },
  { key: 'phinhVi', label: 'Phình vị dạ dày', normal: 'Sạch, niêm mạc bình thường.' },
  { key: 'thanVi', label: 'Thân vị', normal: 'Bình thường.' },
  { key: 'haiBoCong', label: 'Hai bờ cong', normal: 'Mềm mại, không u, không loét.' },
  { key: 'hangVi', label: 'Hang vị', normal: 'Niêm mạc bình thường.' },
  { key: 'monVi', label: 'Môn vị', normal: 'Tròn, lưu thông tốt.' },
  { key: 'hanhTaTrang', label: 'Hành tá tràng', normal: 'Bình thường.' },
  { key: 'taTrang', label: 'Tá tràng', normal: 'Bình thường.' },
];

/** ECG slip: blood pressure, then the analysis lines. */
const ECG = [
  { key: 'huyetAp', label: 'HA', normal: '', unit: 'mmHg', placeholder: '120/70', short: true },
  { key: 'chuyenDao', label: 'Chuyển đạo mẫu', normal: 'DII', short: true },
  { key: 'nhip', label: 'Nhịp, tần số', normal: 'Nhịp xoang, tần số … CK/P' },
  { key: 'truc', label: 'Trục', normal: 'Trung gian', short: true },
  { key: 'p', label: 'P', normal: 'Bình thường', short: true },
  { key: 'pq', label: 'PQ', normal: 'Bình thường', short: true },
  { key: 'qrs', label: 'QRS', normal: 'Bình thường', short: true },
  { key: 'st', label: 'ST', normal: 'Bình thường', short: true },
  { key: 't', label: 'T', normal: 'Bình thường', short: true },
  { key: 'qt', label: 'QT', normal: 'Bình thường', short: true },
  { key: 'truocTim', label: 'Chuyển đạo trước tim', normal: 'Bình thường' },
];

const SECTIONS = { ULTRASOUND, ENDOSCOPY, ECG };

/** Fields of a descriptive form, or null when the form uses one free-text description. */
export function sectionsFor(order) {
  const list = SECTIONS[order.printForm];
  if (!list) return null;
  return list.filter((section) => !section.sex || section.sex === order.patient?.sex);
}

export function emptySections(order) {
  return Object.fromEntries((sectionsFor(order) ?? []).map((section) => [section.key, '']));
}

export function normalSections(order) {
  return Object.fromEntries((sectionsFor(order) ?? []).map((section) => [section.key, section.normal]));
}

/** The description text printed on the slip and stored as kqcls_mota. */
export function composeDescription(order, sections) {
  const list = sectionsFor(order);
  if (!list) return null;
  if (order.printForm === 'ECG') {
    const [pressure, ...analysis] = list;
    return [
      `- ${pressure.label}: ${sections[pressure.key] ?? ''} ${pressure.unit}`.trim(),
      '- PHÂN TÍCH KẾT QUẢ',
      ...analysis.map((section) => `- ${section.label}: ${sections[section.key] ?? ''}`),
    ].join('\n');
  }
  return list.map((section) => `${section.label}: ${sections[section.key] ?? ''}`).join('\n');
}
