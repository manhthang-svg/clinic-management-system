import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { code128Values, code128Widths } from './code128';
import { MOCK_RESULT_ORDERS, RESULT_TEMPLATES } from './resultMockData';
import { resultToForm } from './resultForm';
import { ResultPrintView } from './ResultPrintView';

function order(id, patch = {}) {
  return { ...MOCK_RESULT_ORDERS.find((item) => item.id === id), ...patch };
}

function descriptive(id, extra = {}) {
  const base = order(id);
  const template = RESULT_TEMPLATES[base.serviceCode];
  const form = {
    ...resultToForm({ ...base, result: null }),
    ...template,
    recommendation: 'Tái khám sau 1 tháng.',
    files: [{ id: 'f1', name: 'a.jpg', type: 'image/jpeg', size: 1, url: 'blob:a', caption: 'Hang vị' }],
    ...extra,
  };
  return { order: { ...base, result: { status: 'SUBMITTED', submittedAt: '2026-11-01T10:00:00Z' } }, form };
}

describe('code128', () => {
  it('adds start, checksum and stop symbols', () => {
    expect(code128Values('A')).toEqual([104, 33, 34, 106]);
  });

  it('uses 11 modules per symbol and 13 for stop', () => {
    const widths = code128Widths('CLS-2026-000101');
    const symbols = 'CLS-2026-000101'.length + 2;
    expect(widths.reduce((sum, width) => sum + width, 0)).toBe(symbols * 11 + 13);
  });
});

describe('ResultPrintView', () => {
  it('prints the lab slip with header, barcode, times, flagged values and two signatures', () => {
    const lab = order('cls-002', { result: { status: 'SUBMITTED', submittedAt: '2026-11-01T08:09:00Z' } });
    render(<ResultPrintView order={lab} form={{ ...resultToForm(lab), values: { GLU: '11.4' }, receivedAt: '2026-11-01T07:14', conclusion: 'Tăng' }} />);

    const slip = screen.getByRole('article', { name: 'Phiếu kết quả in' });
    expect(slip).toHaveTextContent('PHIẾU KẾT QUẢ XÉT NGHIỆM');
    expect(slip).toHaveTextContent('MS: 17/BV2');
    expect(slip).toHaveTextContent('TG nhận mẫu');
    expect(slip).toHaveTextContent('XÉT NGHIỆM SINH HÓA');
    expect(slip).toHaveTextContent('Cử nhân xét nghiệm');
    expect(slip).toHaveTextContent('Bác sĩ điều trị');
    expect(screen.getByRole('img', { name: 'Mã vạch CLS-2026-000102' })).toBeInTheDocument();
    expect(screen.getByText('11.4')).toHaveClass('text-red-600');
  });

  it('numbers the parent group of a blood count like the paper form', () => {
    const cbc = order('cls-001', { result: { status: 'SUBMITTED' } });
    render(<ResultPrintView order={cbc} form={resultToForm(cbc)} />);
    expect(screen.getByRole('article')).toHaveTextContent('Tổng phân tích tế bào máu ngoại vi');
  });

  it.each([
    ['cls-004', 'PHIẾU SIÊU ÂM', 'Lời dặn của BS chuyên khoa'],
    ['cls-005', 'PHIẾU NỘI SOI', 'Hang vị'],
    ['cls-006', 'PHIẾU CHỤP CẮT LỚP VI TÍNH', '* Mô tả:'],
    ['cls-009', 'PHIẾU CHỤP CỘNG HƯỞNG TỪ', 'dị vật kim loại'],
    ['cls-008', 'PHIẾU CHIẾU/CHỤP X-QUANG', 'Mô tả kết quả chiếu/chụp'],
    ['cls-007', 'PHIẾU ĐIỆN TIM', '2. Kết luận:'],
  ])('prints %s as %s', (id, title, marker) => {
    const { order: slipOrder, form } = descriptive(id);
    render(<ResultPrintView order={slipOrder} form={form} />);
    const slip = screen.getByRole('article', { name: 'Phiếu kết quả in' });
    expect(slip).toHaveTextContent(title);
    expect(slip).toHaveTextContent(marker);
    expect(slip).toHaveTextContent(slipOrder.patient.name);
  });

  it('prints the ECG strip images between the result and the conclusion', () => {
    const files = [
      { id: 'e1', name: 'dt1.png', type: 'image/png', size: 1, url: 'blob:e1', caption: 'DII dài' },
      { id: 'e2', name: 'dt2.png', type: 'image/png', size: 1, url: 'blob:e2', caption: '' },
      { id: 'e3', name: 'dt3.png', type: 'image/png', size: 1, url: 'blob:e3', caption: '' },
    ];
    const { order: slipOrder, form } = descriptive('cls-007', { files });
    render(<ResultPrintView order={slipOrder} form={form} />);
    expect(screen.getByRole('img', { name: 'DII dài' })).toHaveAttribute('src', 'blob:e1');
    expect(screen.getByRole('img', { name: 'dt2.png' })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'dt3.png' })).not.toBeInTheDocument();
  });

  it('marks a reprint as a copy and is signed by the performer, never a typed name', () => {
    const { order: slipOrder, form } = descriptive('cls-004', { readingDoctor: 'BS Nguyễn Thị Nhàn' });
    render(<ResultPrintView order={{ ...slipOrder, printCount: 1 }} form={form} />);
    expect(screen.getByText('BẢN SAO')).toBeInTheDocument();
    expect(screen.getByText(slipOrder.performer.name)).toBeInTheDocument();
    expect(screen.queryByText('BS Nguyễn Thị Nhàn')).not.toBeInTheDocument();
  });
});

describe('ResultPrintView – UC-68 header and performer', () => {
  it('prints the configured clinic, the registration code, the instrument and the performed time', () => {
    const { order: base, form } = descriptive('cls-008');
    render(<ResultPrintView
      order={{
        ...base,
        clinic: { name: 'Phòng khám Đa khoa Thử', address: '1 Đường Số 1', phone: '0281234567' },
        registrationCode: 'DK0000000123',
        performedAt: '2026-11-01T09:00:00Z',
        result: { ...base.result, instrumentName: 'Máy X-quang DR' },
      }}
      form={form}
    />);

    expect(screen.getByText('Phòng khám Đa khoa Thử')).toBeInTheDocument();
    expect(screen.queryByText('PHÒNG KHÁM ĐA KHOA METPH')).not.toBeInTheDocument();
    expect(screen.getByText('DK0000000123')).toBeInTheDocument();
    expect(screen.getByText('Máy X-quang DR')).toBeInTheDocument();
    expect(screen.getByText('Thời gian thực hiện:')).toBeInTheDocument();
  });

  it('shows that the clinic is not configured instead of the sample header', () => {
    const { order: base, form } = descriptive('cls-008');
    render(<ResultPrintView order={{ ...base, clinic: null }} form={form} />);

    expect(screen.getByText('CHƯA CẤU HÌNH THÔNG TIN PHÒNG KHÁM')).toBeInTheDocument();
  });
});

describe('ResultPrintView – X-ray images', () => {
  it('prints the first two film images beside the X-ray description, like the ultrasound slip', () => {
    const { order: base, form } = descriptive('cls-008', {
      files: [1, 2, 3].map((n) => ({
        id: `x${n}`, name: `phim-${n}.jpg`, type: 'image/jpeg', size: 1, url: `blob:x${n}`, caption: `Phim ${n}`,
      })),
    });
    render(<ResultPrintView order={base} form={form} />);

    const films = screen.getAllByRole('img', { name: /^Phim / });
    expect(films.map((image) => image.getAttribute('alt'))).toEqual(['Phim 1', 'Phim 2']);
    expect(screen.getByText('Mô tả kết quả chiếu/chụp')).toBeInTheDocument();
  });
});
