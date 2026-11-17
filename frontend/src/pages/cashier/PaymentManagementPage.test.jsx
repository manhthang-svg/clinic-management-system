import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import paymentApi from '../../apis/paymentApi';
import PaymentManagementPage from './PaymentManagementPage';

vi.mock('../../apis/paymentApi', () => ({
  default: {
    list: vi.fn(),
    confirm: vi.fn(),
    createPayosQr: vi.fn(),
    syncPayos: vi.fn(),
  },
}));

function payment(index) {
  return {
    id: `pay-${index}`, paymentCode: `HD${String(index).padStart(4, '0')}`, type: 'EXAMINATION_FEE',
    status: 'PAID', visitId: `visit-${index}`, sovaovien: `DK${index}`, patientName: `Bệnh nhân ${index}`,
    patientCode: `BN${index}`, patientTotalAmount: 150000, createdAt: '2026-11-12T06:00:00Z',
  };
}

function page(content, totalElements) {
  return { data: { content, totalElements } };
}

describe('PaymentManagementPage (UC-70)', () => {
  beforeEach(() => {
    paymentApi.list.mockReset();
  });

  it('pages through the receipts and keeps the filters on every page', async () => {
    const user = userEvent.setup();
    paymentApi.list.mockImplementation((params) => Promise.resolve(params.page === 1
      ? page([payment(21)], 45)
      : page([payment(1)], 45)));
    render(<MemoryRouter><PaymentManagementPage /></MemoryRouter>);

    expect(await screen.findByText('Trang 1/3')).toBeInTheDocument();
    expect(screen.getByText('Hiển thị 1–1 trên tổng số 45 phiếu thu')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Trang trước' })).toBeDisabled();
    expect(paymentApi.list).toHaveBeenLastCalledWith({ page: 0, size: 20 });

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));

    expect(await screen.findByText('Trang 2/3')).toBeInTheDocument();
    expect(paymentApi.list).toHaveBeenLastCalledWith({ page: 1, size: 20 });
    expect(screen.getByRole('button', { name: 'Trang trước' })).toBeEnabled();
  });

  it('keeps a single matching pending receipt in the list instead of opening its detail', async () => {
    const user = userEvent.setup();
    const pending = { ...payment(1), status: 'PENDING', sovaovien: 'DK0000000001' };
    paymentApi.list.mockResolvedValue(page([pending], 1));
    render(
      <MemoryRouter initialEntries={['/cashier/payments']}>
        <Routes>
          <Route path="/cashier/payments" element={<PaymentManagementPage />} />
          <Route path="/cashier/payments/:id" element={<p>Chi tiết phiếu thu</p>} />
        </Routes>
      </MemoryRouter>,
    );

    await user.type(screen.getByPlaceholderText('Số phiếu, mã lượt khám, mã BN, họ tên'), 'DK0000000001');

    await vi.waitFor(() => expect(paymentApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'DK0000000001' })));
    expect(await screen.findByRole('link', { name: 'HD0001' })).toBeInTheDocument();
    expect(screen.queryByText('Chi tiết phiếu thu')).not.toBeInTheDocument();
  });

  it('hides the pagination when nothing matches', async () => {
    paymentApi.list.mockResolvedValue(page([], 0));
    render(<MemoryRouter><PaymentManagementPage /></MemoryRouter>);

    expect(await screen.findByText('Không tìm thấy kết quả phù hợp.')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Phân trang' })).not.toBeInTheDocument();
  });
});
