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

describe('CashierOverviewPage (UC-69)', () => {
  beforeEach(() => {
    paymentApi.list.mockReset();
  });

  it('shows the pending receipts 20 per page', async () => {
    const user = userEvent.setup();
    paymentApi.list.mockResolvedValue({ data: { content: Array.from({ length: 25 }, (_, i) => pending(i + 1)) } });
    render(<MemoryRouter><CashierOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Bệnh nhân 20')).toBeInTheDocument();
    expect(screen.queryByText('Bệnh nhân 21')).not.toBeInTheDocument();
    expect(screen.getByText('Hiển thị 1–20 trên tổng số 25 phiếu chờ thu')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));

    expect(screen.getByText('Bệnh nhân 25')).toBeInTheDocument();
    expect(screen.queryByText('Bệnh nhân 1')).not.toBeInTheDocument();
    expect(screen.getByText('Trang 2/2')).toBeInTheDocument();
  });

  it('hides the pagination when 20 or fewer receipts wait', async () => {
    paymentApi.list.mockResolvedValue({ data: { content: [pending(1)] } });
    render(<MemoryRouter><CashierOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Bệnh nhân 1')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Phân trang' })).not.toBeInTheDocument();
  });
});
