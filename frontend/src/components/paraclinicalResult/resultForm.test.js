import { describe, expect, it } from 'vitest';
import { MOCK_RESULT_ORDERS } from './resultMockData';
import {
  MAX_FILES, checkFiles, formForSave, formatReference, interpret, parseNumber, resultToForm, validateResult,
} from './resultForm';
import { normalSections } from './resultSections';

const glucose = { code: 'GLU', valueType: 'NUMERIC', ref: { type: 'RANGE', low: 3.9, high: 6.4 } };
const ldl = { code: 'LDL', valueType: 'NUMERIC', ref: { type: 'UPPER', high: 3.9 } };
const hdl = { code: 'HDL', valueType: 'NUMERIC', ref: { type: 'LOWER', low: 0.9 } };
const protein = { code: 'U_PRO', valueType: 'QUALITATIVE', ref: { type: 'QUALITATIVE', normal: ['NEGATIVE'] } };

const order = (id) => MOCK_RESULT_ORDERS.find((item) => item.id === id);

describe('resultForm', () => {
  it('parses decimal commas and rejects text', () => {
    expect(parseNumber('6,2')).toBe(6.2);
    expect(parseNumber('abc')).toBeNull();
  });

  it('interprets values against each reference type', () => {
    expect(interpret(glucose, '11.4')).toBe('HIGH');
    expect(interpret(glucose, '3.5')).toBe('LOW');
    expect(interpret(glucose, '5')).toBe('NORMAL');
    expect(interpret(ldl, '6.4')).toBe('HIGH');
    expect(interpret(hdl, '1.8')).toBe('NORMAL');
    expect(interpret(hdl, '0.5')).toBe('LOW');
    expect(interpret(protein, 'POSITIVE')).toBe('ABNORMAL');
    expect(interpret(glucose, '')).toBeNull();
  });

  it('formats references like the printed slip', () => {
    expect(formatReference(glucose.ref)).toBe('3.9 – 6.4');
    expect(formatReference(ldl.ref)).toBe('< 3.9');
    expect(formatReference(hdl.ref)).toBe('> 0.9');
    expect(formatReference(protein.ref)).toBe('Âm tính');
  });

  it('requires every lab value, numbers for numeric ones, and a conclusion', () => {
    const lab = order('cls-002');
    const errors = validateResult(lab, { ...resultToForm(lab), values: { GLU: 'x' } });
    expect(errors).toEqual({ 'value:GLU': 'Phải là số', conclusion: 'Vui lòng nhập kết luận.' });
    expect(validateResult(lab, { ...resultToForm(lab), values: { GLU: '5,1' }, conclusion: 'Bình thường' })).toEqual({});
  });

  it('requires every endoscopy site, keeps saved drafts and joins sites into the description', () => {
    const endoscopy = order('cls-005');
    const form = resultToForm(endoscopy);
    expect(form.sections.thucQuan).toContain('vết trợt');
    expect(validateResult(endoscopy, form)).toHaveProperty('section:tamVi', 'Bắt buộc');
    expect(formForSave(endoscopy, form).description).toContain('Thực quản: Có vết trợt');
  });

  it('requires a free-text description for X-ray', () => {
    const xray = order('cls-008');
    expect(validateResult(xray, { ...resultToForm(xray), description: ' ' })).toHaveProperty('description');
  });

  it('prints the ECG as blood pressure then the analysis', () => {
    const ecg = order('cls-007');
    const text = formForSave(ecg, { ...resultToForm(ecg), sections: { ...normalSections(ecg), huyetAp: '120/70' } }).description;
    expect(text.split('\n').slice(0, 3)).toEqual(['- HA: 120/70 mmHg', '- PHÂN TÍCH KẾT QUẢ', '- Chuyển đạo mẫu: DII']);
  });

  it('rejects wrong types, large files and more than 10 files', () => {
    const files = [
      { name: 'a.jpg', type: 'image/jpeg', size: 1000 },
      { name: 'b.docx', type: 'application/msword', size: 1000 },
      { name: 'c.png', type: 'image/png', size: 21 * 1024 * 1024 },
    ];
    const result = checkFiles(files, 0);
    expect(result.accepted.map((file) => file.name)).toEqual(['a.jpg']);
    expect(result.errors).toHaveLength(2);
    expect(checkFiles([files[0]], MAX_FILES).accepted).toEqual([]);
  });

  // The API returns null for every blank field of a draft (here: saved before anything was typed).
  const blankDraft = {
    status: 'DRAFT', savedAt: '2026-11-12T09:31:35Z', submittedAt: null, receivedAt: null, values: {},
    interpretations: {}, conclusion: null, technique: null, description: null, sections: null, recommendation: null,
    imageLink: null, readingDoctor: null, instrumentId: null, instrumentName: null, files: [],
  };

  it('opens a lab draft saved without a conclusion instead of crashing', () => {
    const lab = { ...order('cls-002'), result: blankDraft };
    const form = resultToForm(lab);

    expect(form.conclusion).toBe('');
    expect(form.instrumentId).toBe('');
    expect(Object.values(form.values).every((value) => value === '')).toBe(true);
    expect(validateResult(lab, form)).toHaveProperty('conclusion');
  });

  it('opens a descriptive draft whose fields are all null', () => {
    const xray = { ...order('cls-008'), result: blankDraft };
    const form = resultToForm(xray);

    expect(form).toMatchObject({ conclusion: '', description: '', technique: '', imageLink: '' });
    expect(() => validateResult(xray, form)).not.toThrow();
  });
});

describe('anh ket qua: xet nghiem khong co anh, chan doan hinh anh bat buoc co anh', () => {
  const filled = (id, extra) => ({ ...resultToForm(order(id)), description: 'Mô tả', conclusion: 'Bình thường', ...extra });
  const image = { id: 'img-1', name: 'a.png', type: 'image/png', size: 10, url: 'https://x/a.png', caption: '' };

  it('never asks a lab result for an image', () => {
    const lab = order('cls-002');
    const errors = validateResult(lab, { ...resultToForm(lab), values: { GLU: '5' }, conclusion: 'Bình thường' });
    expect(errors).not.toHaveProperty('files');
    expect(errors).not.toHaveProperty('imageLink');
  });

  it('requires at least one image for ultrasound, X-ray and ECG', () => {
    expect(validateResult(order('cls-004'), filled('cls-004', { files: [] })))
      .toHaveProperty('files', 'Vui lòng tải lên ít nhất 1 ảnh kết quả.');
    expect(validateResult(order('cls-008'), filled('cls-008', { files: [] }))).toHaveProperty('files');
    expect(validateResult(order('cls-007'), filled('cls-007', { files: [] }))).toHaveProperty('files');
    expect(validateResult(order('cls-004'), filled('cls-004', { files: [image] }))).not.toHaveProperty('files');
  });

  it('asks CT and MRI for the image link instead of an upload', () => {
    expect(validateResult(order('cls-006'), filled('cls-006', { technique: 'Có tiêm', imageLink: '' })))
      .toHaveProperty('imageLink', 'Vui lòng nhập link xem ảnh.');
    expect(validateResult(order('cls-009'), filled('cls-009', { imageLink: 'https://pacs/1' })))
      .not.toHaveProperty('imageLink');
  });
});

describe('chi so xet nghiem khong bat buoc', () => {
  it('lets an optional parameter stay empty, like the backend', () => {
    const lab = order('cls-002');
    const optional = { ...lab, parameters: lab.parameters.map((parameter, index) => ({ ...parameter, required: index !== 0 })) };
    const values = Object.fromEntries(optional.parameters.slice(1).map((parameter) => [parameter.code, '5']));
    const errors = validateResult(optional, { ...resultToForm(optional), values, conclusion: 'Bình thường' });
    expect(errors).not.toHaveProperty(`value:${optional.parameters[0].code}`);
    expect(validateResult(lab, { ...resultToForm(lab), values: {}, conclusion: 'x' }))
      .toHaveProperty(`value:${lab.parameters[0].code}`, 'Bắt buộc');
  });
});
