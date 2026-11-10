// Per-organ / per-site fields of the descriptive result forms, taken from the clinic's printed slips.
// The fields are joined into one description text, which is what imaging_results.kqcls_mota stores.

// ULTRASOUND uses one free-text description like the X-ray slip (no per-organ fields): one fixed list of
// abdominal organs did not fit cardiac, obstetric or vascular ultrasound. "Điền kết quả bình thường" fills the
// description from RESULT_TEMPLATES instead.

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

const SECTIONS = { ENDOSCOPY, ECG };

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
