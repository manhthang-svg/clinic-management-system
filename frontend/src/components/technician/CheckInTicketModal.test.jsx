import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CheckInTicketModal } from './CheckInTicketModal';

describe('CheckInTicketModal', () => {
  const sampleTicketData = {
    queueNumber: 5,
    issuedAt: new Date(2026, 10, 11, 10, 42), // 10:42 11/11/2026
    room: {
      code: 'XN-SH-1',
      name: 'Phòng Xét nghiệm Sinh hóa 1',
      location: 'Tòa B - Tầng 1 - P.B102',
    },
    order: {
      patientName: 'Lê Văn Dũng',
      patientId: 'BN00000003',
      age: 28,
      birthYear: 1998,
      gender: 'Nam',
      insuranceType: 'Viện phí',
      priority: 'NORMAL',
      orderCode: 'CD0000000056-01',
      doctorName: 'BSCKII Trần Văn An',
      doctorRoom: 'PK Nội tổng hợp 1',
      roomServices: [
        { id: 's1', serviceName: 'Bộ mỡ máu (Cholesterol, Triglycerid, HDL-C, LDL-C)' },
        { id: 's2', serviceName: 'Bộ đường huyết (Glucose, HbA1c)' },
      ],
    },
  };

  it('does not render when isOpen is false', () => {
    const { container } = render(
      <CheckInTicketModal isOpen={false} onClose={vi.fn()} ticketData={sampleTicketData} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders all 4 sections required by check-in ticket', () => {
    render(
      <CheckInTicketModal isOpen={true} onClose={vi.fn()} ticketData={sampleTicketData} />
    );

    // 1. Header & Clinic title
    expect(screen.getByText('PHÒNG KHÁM ĐA KHOA METPH')).toBeInTheDocument();
    expect(screen.getByText('PHIẾU SỐ THỨ TỰ CẬN LÂM SÀNG')).toBeInTheDocument();
    expect(screen.getByText(/10:42 - 11\/11\/2026/)).toBeInTheDocument();

    // 2. Queue number & Room details
    expect(screen.getByText('05')).toBeInTheDocument();
    expect(screen.getByText('Phòng Xét nghiệm Sinh hóa 1 - XN-SH-1')).toBeInTheDocument();
    expect(screen.getByText('Tòa B - Tầng 1 - P.B102')).toBeInTheDocument();

    // 3. Patient identity (Uppercase, ID, age, gender, insurance)
    expect(screen.getByText('LÊ VĂN DŨNG')).toBeInTheDocument();
    expect(screen.getByText('BN00000003')).toBeInTheDocument();
    expect(screen.getByText('1998 (28 tuổi) - Nam')).toBeInTheDocument();
    expect(screen.getByText('Viện phí')).toBeInTheDocument();
    // Non-priority order should NOT show priority row
    expect(screen.queryByText(/ƯU TIÊN/i)).not.toBeInTheDocument();

    // 4. Services & Ordering doctor
    expect(screen.getByText('Bộ mỡ máu (Cholesterol, Triglycerid, HDL-C, LDL-C)')).toBeInTheDocument();
    expect(screen.getByText('Bộ đường huyết (Glucose, HbA1c)')).toBeInTheDocument();
    expect(screen.getByText('CD0000000056')).toBeInTheDocument();
    expect(screen.getByText('BSCKII Trần Văn An - PK Nội tổng hợp 1')).toBeInTheDocument();

    // 5. Footer notes
    expect(screen.getByText(/Quý khách vui lòng chú ý lắng nghe loa/)).toBeInTheDocument();
    expect(screen.getByText(/Phiếu chỉ có giá trị trong ngày khám/)).toBeInTheDocument();
  });

  it('displays priority row when order has priority', () => {
    const priorityData = {
      ...sampleTicketData,
      order: {
        ...sampleTicketData.order,
        priority: 'PRIORITY',
        priorityLabel: 'Ưu tiên',
      },
    };

    render(
      <CheckInTicketModal isOpen={true} onClose={vi.fn()} ticketData={priorityData} />
    );

    expect(screen.getByText('ƯU TIÊN (Xử lý trước)')).toBeInTheDocument();
  });

  it('triggers window.print when clicking In phiếu số thứ tự', async () => {
    const user = userEvent.setup();
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});

    render(
      <CheckInTicketModal isOpen={true} onClose={vi.fn()} ticketData={sampleTicketData} />
    );

    const printBtn = screen.getByRole('button', { name: /In phiếu số thứ tự/i });
    await user.click(printBtn);

    await vi.waitFor(() => {
      expect(printSpy).toHaveBeenCalled();
    });
    printSpy.mockRestore();
  });

  it('triggers onClose when clicking close button or pressing Escape', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    render(
      <CheckInTicketModal isOpen={true} onClose={onClose} ticketData={sampleTicketData} />
    );

    const closeBtn = screen.getByRole('button', { name: 'Đóng' });
    await user.click(closeBtn);
    expect(onClose).toHaveBeenCalledTimes(1);

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});

