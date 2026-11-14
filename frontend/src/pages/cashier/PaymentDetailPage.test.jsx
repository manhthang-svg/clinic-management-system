import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import paymentApi from '../../apis/paymentApi';
import visitApi from '../../apis/visitApi';
import PaymentDetailPage from './PaymentDetailPage';

vi.mock('../../apis/paymentApi', () => ({
  default: {
    getById: vi.fn(),
    confirm: vi.fn(),
    prepareReceiptPrint: vi.fn(),
    createPayosQr: vi.fn(),
    syncPayos: vi.fn(),
  },
}));
vi.mock('../../apis/visitApi', () => ({
  default: { getTicket: vi.fn(), prepareTicketPrint: vi.fn() },
}));

function payment(type, status) {
  return {
    id: 'pay-1', paymentCode: 'HD0001', type, status, visitId: 'visit-1', sovaovien: 'DK0001',
    patientName: 'Bùi Ngọc Anh', patientCode: 'BN0001', roomName: 'Phòng khám Nội 1',
    patientTotalAmount: 150000, createdAt: '2026-11-12T06:00:00Z', items: [],
  };
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/cashier/payments/pay-1']}>
      <Routes>
        <Route path="/cashier/payments/:id" element={<PaymentDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function confirmCash(user) {
  await user.click(await screen.findByRole('button', { name: /Thu tiền mặt tại quầy/ }));
  await user.click(screen.getByRole('button', { name: 'Xác nhận đã thu' }));
}

describe('PaymentDetailPage – after confirming a payment (UC-71)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, 'print').mockImplementation(() => {});
    visitApi.getTicket.mockResolvedValue({ queueNumber: 5, patientName: 'Bùi Ngọc Anh', roomName: 'Phòng khám Nội 1' });
    paymentApi.confirm.mockResolvedValue({ status: 'PAID' });
  });

  it('opens the queue-number ticket right after the examination fee is paid', async () => {
    paymentApi.getById
      .mockResolvedValueOnce(payment('EXAMINATION_FEE', 'PENDING'))
      .mockResolvedValue(payment('EXAMINATION_FEE', 'PAID'));
    const user = userEvent.setup();
    renderPage();

    await confirmCash(user);

    expect(await screen.findByRole('dialog', { name: 'Phiếu khám' })).toBeInTheDocument();
    await waitFor(() => expect(visitApi.getTicket).toHaveBeenCalledWith('visit-1', expect.anything()));
    expect(paymentApi.prepareReceiptPrint).not.toHaveBeenCalled();
  });

  it('prints the receipt after a paraclinical fee is paid', async () => {
    paymentApi.getById
      .mockResolvedValueOnce(payment('PARACLINICAL_FEE', 'PENDING'))
      .mockResolvedValue(payment('PARACLINICAL_FEE', 'PAID'));
    paymentApi.prepareReceiptPrint.mockResolvedValue({ payment: { ...payment('PARACLINICAL_FEE', 'PAID'), receiptNumber: 'PT-1' }, items: [] });
    const user = userEvent.setup();
    renderPage();

    await confirmCash(user);

    await waitFor(() => expect(paymentApi.prepareReceiptPrint).toHaveBeenCalledWith('pay-1'));
    expect(screen.queryByRole('dialog', { name: 'Phiếu khám' })).not.toBeInTheDocument();
  });
});
