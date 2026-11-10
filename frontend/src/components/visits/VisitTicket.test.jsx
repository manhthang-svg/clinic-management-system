import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { VisitTicket } from './VisitTicket';

const visit = {
  sovaovien: 'DK0000000001',
  status: 'WAITING_FEE_PAYMENT',
};

const ticket = {
  facilityName: 'Phòng khám An Tâm',
  registrationCode: 'DK0000000001',
  patientCode: 'BN00000001',
  patientName: 'Nguyễn Văn A',
  birthYear: 1990,
  gender: 'Nam',
  queueNumber: 12,
  roomName: 'Phòng Nội',
  roomLocation: 'Tầng 2',
  doctorNames: ['BS Nguyễn An'],
  visitType: 'BHYT',
  coverageRate: 80,
  priority: false,
  registeredAt: '2026-11-01T01:00:00Z',
  patientAmount: 20000,
  reprint: false,
};

describe('VisitTicket', () => {
  it('sends an insured patient to the waiting area and to the cashier after the conclusion (BR-15)', () => {
    render(<VisitTicket visit={visit} ticket={ticket} onPrint={vi.fn()} onDone={vi.fn()} />);

    expect(screen.getByText('Mời đến khu chờ – thanh toán sau khi có kết luận')).toBeInTheDocument();
    expect(screen.queryByText('20.000 đ')).toBeNull();
    expect(screen.getByText('BS Nguyễn An')).toBeInTheDocument();
  });

  it('sends a self-paid patient to the cashier before the waiting area (BR-16)', () => {
    render(
      <VisitTicket
        visit={visit}
        ticket={{ ...ticket, visitType: 'DV', patientAmount: 150000, queueNumber: null }}
        onPrint={vi.fn()}
        onDone={vi.fn()}
      />,
    );

    expect(screen.getByText('150.000 đ')).toBeInTheDocument();
    expect(screen.getByText('Thanh toán tại quầy thu ngân trước khi vào khu chờ')).toBeInTheDocument();
    // I1-D48: the queue number is issued with the payment.
    expect(screen.queryByText('Số thứ tự')).toBeNull();
  });

  it('shows the queue number of a visit that already has one', () => {
    render(<VisitTicket visit={visit} ticket={ticket} onPrint={vi.fn()} onDone={vi.fn()} />);

    expect(screen.getByText('Số thứ tự')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
  });

  it('marks a reprint and sends a fully covered patient to the waiting area', () => {
    render(
      <VisitTicket
        visit={{ ...visit, status: 'WAITING' }}
        ticket={{ ...ticket, patientAmount: 0, reprint: true }}
        onPrint={vi.fn()}
        onDone={vi.fn()}
      />,
    );

    expect(screen.getByText('IN LẠI')).toBeInTheDocument();
    expect(screen.getAllByText(/khu chờ/)).not.toHaveLength(0);
  });
});
