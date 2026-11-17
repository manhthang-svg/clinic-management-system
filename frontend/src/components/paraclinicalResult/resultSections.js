// cac muc mo ta tung co quan / vi tri cua phieu ket qua, gop lai thanh 1 doan mo ta khi luu

// sieu am dung 1 o mo ta tu do (nhieu loai sieu am khac nhau)

// phieu noi soi da day
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

// phieu dien tim: huyet ap roi cac dong phan tich
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

// cac muc cua phieu; null neu phieu chi co 1 o mo ta
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

// gop cac muc thanh doan mo ta de in va luu
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
