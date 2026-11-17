import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import paymentApi from '../../apis/paymentApi';
import CashierOverviewPage from './CashierOverviewPage';

vi.mock('../../apis/paymentApi', () => ({
  default: {
    list: vi.fn(),
    confirm: vi.fn(),
    createPayosQr: vi.fn(),
    syncPayos: vi.fn(),
  },
}));

function pending(index) {
  return {
    id: `pay-${index}`, paymentCode: `HD${String(index).padStart(4, '0')}`, type: 'EXAMINATION_FEE',
    status: 'PENDING', visitId: `visit-${index}`, sovaovien: `DK${index}`, patientName: `Bệnh nhân ${index}`,
    patientCode: `BN${index}`, patientTotalAmount: 150000, createdAt: '2026-11-18T06:00:00Z',
  };
}

// API gia nhu server: loc theo trang thai + loai, cat dung trang, tra tong so phieu
function serve({ pendingList = [], paidList = [] }) {
  paymentApi.list.mockImplementation(async ({ status, type, page = 0, size = 20 }) => {
    const all = (status === 'PAID' ? paidList : pendingList)
      .filter((p) => !type || type === 'ALL' || p.type === type);
    return { data: { content: all.slice(page * size, (page + 1) * size), totalElements: all.length } };
  });
}

describe('CashierOverviewPage (UC-69)', () => {
  beforeEach(() => {
    paymentApi.list.mockReset();
  });

  it('loads only pending receipts and the latest paid ones', async () => {
    const paidToday = {
      ...pending(90), status: 'PAID', patientName: 'Đã thu hôm nay', patientTotalAmount: 200000,
      paidAt: new Date().toISOString(),
    };
    const paidEarlier = {
      ...pending(91), status: 'PAID', patientName: 'Đã thu hôm trước', patientTotalAmount: 999000,
      paidAt: '2020-01-01T03:00:00Z',
    };
    serve({ pendingList: [pending(1)], paidList: [paidToday, paidEarlier] });
    render(<MemoryRouter><CashierOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Bệnh nhân 1')).toBeInTheDocument();
    expect(paymentApi.list).toHaveBeenCalledWith({ status: 'PENDING', type: 'ALL', page: 0, size: 20 });
    expect(paymentApi.list).toHaveBeenCalledWith({ status: 'PAID', page: 0, size: 100, sort: 'paidAt,desc' });
    // chi phieu thu hom nay tinh vao the "Da thu hom nay" va doanh thu
    expect(screen.getAllByText('200.000 ₫').length).toBeGreaterThan(0);
    expect(screen.queryByText('1.199.000 ₫')).not.toBeInTheDocument();
  });

  it('shows the pending receipts 20 per page', async () => {
    const user = userEvent.setup();
    serve({ pendingList: Array.from({ length: 25 }, (_, i) => pending(i + 1)) });
    render(<MemoryRouter><CashierOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Bệnh nhân 20')).toBeInTheDocument();
    expect(screen.queryByText('Bệnh nhân 21')).not.toBeInTheDocument();
    expect(screen.getByText('Hiển thị 1–20 trên tổng số 25 phiếu chờ thu')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));

    expect(screen.getByText('Bệnh nhân 25')).toBeInTheDocument();
    expect(screen.queryByText('Bệnh nhân 1')).not.toBeInTheDocument();
    expect(screen.getByText('Trang 2/2')).toBeInTheDocument();
  });

  // lat 6 trang lien tiep: cho them thoi gian khi ca bo test chay song song
  it('shows every pending receipt, even beyond the first 100', async () => {
    const user = userEvent.setup();
    serve({ pendingList: Array.from({ length: 130 }, (_, i) => pending(i + 1)) });
    render(<MemoryRouter><CashierOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Hiển thị 1–20 trên tổng số 130 phiếu chờ thu')).toBeInTheDocument();
    for (let i = 0; i < 6; i += 1) {
      await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    }

    expect(await screen.findByText('Bệnh nhân 130')).toBeInTheDocument();
    expect(screen.getByText('Trang 7/7')).toBeInTheDocument();
  }, 15000);

  // trang chia 2 nua: tab "Da thu tien" lay 20 phieu da thu / trang tu server
  it('shows the paid receipts in their own half, 20 per page', async () => {
    const user = userEvent.setup();
    const paidList = Array.from({ length: 23 }, (_, i) => ({
      ...pending(200 + i), status: 'PAID', patientName: `Đã thu ${i + 1}`, paidAt: '2026-11-18T03:00:00Z',
    }));
    serve({ pendingList: [pending(1)], paidList });
    render(<MemoryRouter><CashierOverviewPage /></MemoryRouter>);

    await user.click(await screen.findByRole('tab', { name: 'Đã thu tiền (23)' }));

    expect(paymentApi.list).toHaveBeenCalledWith({ status: 'PAID', page: 0, size: 20, sort: 'paidAt,desc' });
    expect(screen.getByText('Đã thu 20')).toBeInTheDocument();
    expect(screen.queryByText('Đã thu 21')).not.toBeInTheDocument();
    expect(screen.queryByText('Bệnh nhân 1')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));

    expect(await screen.findByText('Đã thu 23')).toBeInTheDocument();
    expect(screen.getByText('Trang 2/2')).toBeInTheDocument();
  });

  it('hides the pagination when 20 or fewer receipts wait', async () => {
    serve({ pendingList: [pending(1)] });
    render(<MemoryRouter><CashierOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Bệnh nhân 1')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Phân trang' })).not.toBeInTheDocument();
  });
});
