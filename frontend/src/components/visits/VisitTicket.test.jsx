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
  it('prints the patient details of the ticket', () => {
    render(
      <VisitTicket
        visit={visit}
        ticket={{
          ...ticket,
          dateOfBirth: '1990-05-18',
          phone: '0901234567',
          address: '12 Đường Số 1, Phường Bến Thành, TP. Hồ Chí Minh',
          occupation: 'Giáo viên',
          ethnicity: 'Kinh',
          insuranceCardNumber: 'HS4010120878811',
          insuranceValidFrom: '2026-01-01',
          insuranceValidTo: '2026-12-31',
          reasonForVisit: 'Đau đầu',
          registeredByName: 'Nguyễn Thị Lan',
          facilityAddress: '78 Giải Phóng, Đống Đa, Hà Nội',
        }}
        onPrint={vi.fn()}
        onDone={vi.fn()}
      />,
    );

    expect(screen.getByText('18/5/1990')).toBeInTheDocument();
    expect(screen.getByText('36 tuổi 5 tháng')).toBeInTheDocument();
    expect(screen.getByText('0901234567')).toBeInTheDocument();
    expect(screen.getByText('12 Đường Số 1, Phường Bến Thành, TP. Hồ Chí Minh')).toBeInTheDocument();
    expect(screen.getByText('HS4010120878811')).toBeInTheDocument();
    expect(screen.getByText('1/1/2026 – 31/12/2026')).toBeInTheDocument();
    expect(screen.getByText('Đau đầu')).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Thị Lan')).toBeInTheDocument();
    expect(screen.getByText('78 Giải Phóng, Đống Đa, Hà Nội')).toBeInTheDocument();
  });

  it('sends an insured patient to the waiting area and to the cashier after the conclusion (BR-15)', () => {
    render(<VisitTicket visit={visit} ticket={ticket} onPrint={vi.fn()} onDone={vi.fn()} />);

    expect(screen.getByText('Mời đến khu chờ – thanh toán sau khi có kết luận')).toBeInTheDocument();
    expect(screen.queryByText('20.000 đ')).toBeNull();
    expect(screen.getByText('BS Nguyễn An')).toBeInTheDocument();
  });

  it('gives an unpaid self-paid patient a payment guide, not an examination ticket (BR-16, I1-D48)', () => {
    render(
      <VisitTicket
        visit={visit}
        ticket={{ ...ticket, visitType: 'DV', patientAmount: 150000, queueNumber: null }}
        onPrint={vi.fn()}
        onDone={vi.fn()}
      />,
    );

    expect(screen.getByRole('article', { name: 'Phiếu hướng dẫn thanh toán' })).toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Phiếu khám bệnh' })).toBeNull();
    expect(screen.getByText('150.000 đ')).toBeInTheDocument();
    expect(screen.getByText('Nơi thanh toán: Quầy thu ngân')).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('Chưa có số thứ tự. Phiếu này không dùng để vào khám.');
    expect(screen.getByRole('button', { name: 'In phiếu hướng dẫn thanh toán' })).toBeInTheDocument();
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
