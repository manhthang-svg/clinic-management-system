import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueueTicketDialog } from './QueueTicketDialog';

const paidTicket = {
  facilityName: 'Phòng khám An Tâm',
  registrationCode: 'DK0000000001',
  patientCode: 'BN00000001',
  patientName: 'Nguyễn Văn A',
  birthYear: 1990,
  gender: 'Nam',
  queueNumber: 7,
  roomName: 'Phòng Nội 01',
  roomLocation: 'Tầng 1',
  doctorNames: ['BS Nguyễn An'],
  visitType: 'DV',
  coverageRate: null,
  priority: false,
  registeredAt: '2026-11-11T01:00:00Z',
  patientAmount: 150000,
  status: 'WAITING',
  reprint: false,
};

function fakeVisitApi(ticket = paidTicket) {
  return {
    getTicket: vi.fn().mockResolvedValue(ticket),
    prepareTicketPrint: vi.fn().mockResolvedValue({ ...ticket, reprint: true }),
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('QueueTicketDialog (I1-D48)', () => {
  it('shows the queue number and room of a paid visit', async () => {
    render(<QueueTicketDialog visitId="v1" onClose={vi.fn()} visitApi={fakeVisitApi()} />);

    expect(await screen.findByText('7')).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Phiếu khám' })).toBeInTheDocument();
    expect(screen.getByText('Phòng Nội 01')).toBeInTheDocument();
    expect(screen.getByText('Đã thanh toán phí khám – mời đến khu chờ')).toBeInTheDocument();
  });

  it('records the print before opening the print dialog', async () => {
    const user = userEvent.setup();
    const visitApi = fakeVisitApi();
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    render(<QueueTicketDialog visitId="v1" onClose={vi.fn()} visitApi={visitApi} />);

    await user.click(await screen.findByRole('button', { name: 'In phiếu khám' }));

    expect(visitApi.prepareTicketPrint).toHaveBeenCalledWith('v1');
    // UC-41 AF-02: no reprint mark; the printed ticket carries the print time instead.
    expect(screen.queryByText('IN LẠI')).not.toBeInTheDocument();
    expect(screen.getByText(/^In lúc: /)).toBeInTheDocument();
    await vi.waitFor(() => expect(print).toHaveBeenCalled());
  });

  it('does not print a ticket without a queue number', async () => {
    render(
      <QueueTicketDialog
        visitId="v1"
        onClose={vi.fn()}
        visitApi={fakeVisitApi({ ...paidTicket, queueNumber: null, status: 'WAITING_FEE_PAYMENT' })}
      />,
    );

    expect(await screen.findByText('Lượt khám chưa có số thứ tự vì phí khám chưa được thanh toán.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'In phiếu khám' })).toBeDisabled();
  });

  it('closes on request', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<QueueTicketDialog visitId="v1" onClose={onClose} visitApi={fakeVisitApi()} />);

    await user.click(screen.getByRole('button', { name: 'Đóng', exact: true }));

    expect(onClose).toHaveBeenCalled();
  });
});
