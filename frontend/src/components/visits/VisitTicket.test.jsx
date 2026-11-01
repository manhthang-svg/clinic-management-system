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
  it('sends an insured patient with a positive co-payment to the cashier', () => {
    render(<VisitTicket visit={visit} ticket={ticket} onPrint={vi.fn()} onDone={vi.fn()} />);

    expect(screen.getByText('20.000 đ')).toBeInTheDocument();
    expect(screen.getAllByText(/quầy thu ngân/)).not.toHaveLength(0);
    expect(screen.getByText('BS Nguyễn An')).toBeInTheDocument();
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
