import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../apis/apiError';
import { TechnicianDashboardPage } from './TechnicianDashboardPage';

const ME = { id: 'tech-1', fullName: 'Nguyễn Văn Bảo', role: 'TECHNICIAN' };
// 17/11/2026 14:00, inside the afternoon shift.
const TODAY = new Date('2026-11-17T14:00:00+07:00');
const OWN_SHIFT = {
  id: 's-1', workDate: '2026-11-17', shift: 'AFTERNOON', staffAccountId: ME.id,
  roomId: 'room-1', roomCode: 'P.204', roomName: 'Sinh hóa', startTime: '13:00', endTime: '17:30',
};

function dashboard(overrides = {}) {
  return {
    room: { id: 'room-1', code: 'P.204', name: 'Phòng Xét nghiệm Sinh hóa', location: 'Tầng 2', activeTech: 'KTV. Nguyễn Văn Bảo', isAssigned: true },
    canManage: true,
    stats: { waiting: 7, priority: 2, skipped: 3, pendingResult: 2, draft: 1, overdue: 1, completedToday: 9 },
    nextQueue: [{
      id: 'd-1', queueNumber: 1, patientName: 'Trần Thị Mai', patientId: 'BN0000001', age: 68, gender: 'Nữ',
      insuranceType: 'BHYT', priority: 'PRIORITY', priorityLabel: 'Người cao tuổi', serviceName: 'Định lượng Ure',
      doctorName: 'BS An', doctorRoom: 'PK Nội', orderTime: '13:40', isCheckedIn: false,
    }],
    pendingResults: [{
      id: 'd-2', orderCode: 'CD0000000104', patientName: 'Hoàng Đức Thịnh', patientId: 'BN0000002', age: 52,
      gender: 'Nam', insuranceType: 'Viện phí', serviceName: 'Bộ mỡ máu', doctorName: 'BS Sơn', doctorRoom: 'PK Tim mạch',
      completedAtTime: '13:10', completedMinutesAgo: 50, status: 'DRAFT',
    }],
    ...overrides,
  };
}

function technicianApi(getDashboard) {
  return { getDashboard, getActiveRoomId: vi.fn(() => ''), setActiveRoomId: vi.fn() };
}

function scheduleApi(items = [OWN_SHIFT]) {
  return {
    getPersonalSchedule: vi.fn().mockResolvedValue({ items }),
  };
}

function renderPage(api, schedules = scheduleApi()) {
  return render(
    <MemoryRouter>
      <TechnicianDashboardPage currentUser={ME} api={api} scheduleApi={schedules} today={TODAY} />
    </MemoryRouter>,
  );
}

describe('TechnicianDashboardPage', () => {
  it('shows the room, today\'s figures and the short lists from the API', async () => {
    const api = technicianApi(vi.fn().mockResolvedValue(dashboard()));
    renderPage(api);

    expect(await screen.findByRole('heading', { name: 'Phòng Xét nghiệm Sinh hóa' })).toBeInTheDocument();
    expect(screen.getByText('Đang trong ca trực')).toBeInTheDocument();
    expect(await screen.findByText('Ca chiều (13:00 – 17:30)')).toBeInTheDocument();
    expect(screen.getByText('Đã hoàn thành hôm nay').closest('button')).toHaveTextContent('9');
    expect(screen.getByText('Chờ nhập kết quả').closest('button')).toHaveTextContent('3');
    expect(screen.getByText('Đang chờ thực hiện').closest('button')).toHaveTextContent('7');
    expect(screen.getByText('2 bệnh nhân ưu tiên')).toBeInTheDocument();

    const queue = screen.getByRole('region', { name: 'Bệnh nhân sắp tới lượt' });
    expect(within(queue).getByText('Trần Thị Mai')).toBeInTheDocument();
    expect(within(queue).getByText('#01')).toBeInTheDocument();
    expect(within(queue).getByText('Xem tất cả (7)')).toBeInTheDocument();

    const results = screen.getByRole('region', { name: 'Kết quả đang chờ nhập / Bản nháp' });
    expect(within(results).getByText('Bản nháp')).toBeInTheDocument();
    expect(within(results).getByText('>30p')).toBeInTheDocument();
    expect(api.setActiveRoomId).toHaveBeenCalledWith('room-1');
  });

  it('shows that technicians can finish their own work outside the shift', async () => {
    const data = dashboard({ canManage: false });
    data.room.hasOwnWork = true;
    renderPage(technicianApi(vi.fn().mockResolvedValue(data)));

    expect(await screen.findByText('Ngoài ca trực · Còn chỉ định')).toBeInTheDocument();
  });

  it('lists only the current technician\'s shifts for the next 4 days', async () => {
    const schedules = scheduleApi([
      OWN_SHIFT,
      { id: 's-2', workDate: '2026-11-18', shift: 'MORNING', staffAccountId: 'tech-1', roomCode: 'P.205', roomName: 'Huyết học', startTime: '07:30', endTime: '11:30' },
      { id: 's-3', workDate: '2026-11-18', shift: 'AFTERNOON', staffAccountId: 'someone-else', roomCode: 'P.105', roomName: 'Siêu âm' },
    ]);
    renderPage(technicianApi(vi.fn().mockResolvedValue(dashboard())), schedules);

    const shifts = await screen.findByRole('region', { name: 'Lịch trực cá nhân sắp tới' });
    expect(await within(shifts).findByText('Hôm nay, 17/11')).toBeInTheDocument();
    expect(within(shifts).getByText('Đang diễn ra')).toBeInTheDocument();
    expect(within(shifts).getByText('P.205 – Huyết học')).toBeInTheDocument();
    expect(within(shifts).getByText('07:30 – 11:30')).toBeInTheDocument();
    expect(within(shifts).queryByText('P.105 – Siêu âm')).not.toBeInTheDocument();
    expect(within(shifts).getAllByText('Nghỉ ca')).toHaveLength(2);
    expect(schedules.getPersonalSchedule).toHaveBeenCalledWith('2026-11-17', '2026-11-20', expect.anything());
  });

  it('lets the server choose the personal room and hides the other technician listed in the room metadata', async () => {
    const data = dashboard({ canManage: false });
    data.room.activeTech = 'Kỹ thuật viên ca khác';
    const getDashboard = vi.fn().mockResolvedValue(data);
    const api = { getDashboard, getActiveRoomId: vi.fn(() => 'old-room'), setActiveRoomId: vi.fn() };
    renderPage(api);

    expect(await screen.findByRole('heading', { name: 'Phòng Xét nghiệm Sinh hóa' })).toBeInTheDocument();
    expect(getDashboard).toHaveBeenCalledWith({}, expect.anything());
    expect(api.getActiveRoomId).not.toHaveBeenCalled();
    expect(screen.queryByText('Kỹ thuật viên ca khác')).not.toBeInTheDocument();
    expect(screen.queryByText(/Ca của bạn/)).not.toBeInTheDocument();
  });

  it('shows an error with a retry when the dashboard cannot be loaded', async () => {
    renderPage(technicianApi(vi.fn().mockRejectedValue(new ApiError('Server error', { status: 500 }))));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải dữ liệu tổng quan. Vui lòng thử lại.');
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });

  it('groups multiple services for the same patient into 1 row with badge +X dịch vụ khác', async () => {
    const data = dashboard({
      nextQueue: [
        {
          id: 'q-1', queueNumber: 5, patientName: 'Nguyễn Văn Phi', patientId: 'BN6013016', age: 34, gender: 'Nam',
          insuranceType: 'Viện phí', serviceName: 'Đo hoạt độ AST, ALT', doctorName: 'BS An', orderCode: 'CD0000000268-01',
        },
        {
          id: 'q-2', queueNumber: 5, patientName: 'Nguyễn Văn Phi', patientId: 'BN6013016', age: 34, gender: 'Nam',
          insuranceType: 'Viện phí', serviceName: 'Bộ đường huyết (Glucose, HbA1c)', doctorName: 'BS An', orderCode: 'CD0000000268-02',
        },
      ],
      pendingResults: [
        {
          id: 'p-1', orderCode: 'CD0000000268-01', patientName: 'Nguyễn Văn Phi', patientId: 'BN6013016', age: 34,
          gender: 'Nam', insuranceType: 'Viện phí', serviceName: 'Đo hoạt độ AST, ALT', doctorName: 'BS An',
          completedAtTime: '00:19', completedMinutesAgo: 35, status: 'WAITING_RESULT',
        },
        {
          id: 'p-2', orderCode: 'CD0000000268-02', patientName: 'Nguyễn Văn Phi', patientId: 'BN6013016', age: 34,
          gender: 'Nam', insuranceType: 'Viện phí', serviceName: 'Bộ đường huyết (Glucose, HbA1c)', doctorName: 'BS An',
          completedAtTime: '00:19', completedMinutesAgo: 35, status: 'DRAFT',
        },
        {
          id: 'p-3', orderCode: 'CD0000000268-03', patientName: 'Nguyễn Văn Phi', patientId: 'BN6013016', age: 34,
          gender: 'Nam', insuranceType: 'Viện phí', serviceName: 'Tổng phân tích nước tiểu', doctorName: 'BS An',
          completedAtTime: '00:19', completedMinutesAgo: 35, status: 'WAITING_RESULT',
        },
      ],
    });
    renderPage(technicianApi(vi.fn().mockResolvedValue(data)));

    const queue = await screen.findByRole('region', { name: 'Bệnh nhân sắp tới lượt' });
    expect(within(queue).getAllByText('Nguyễn Văn Phi')).toHaveLength(1);
    expect(within(queue).getByText('+1 dịch vụ khác')).toBeInTheDocument();

    const results = screen.getByRole('region', { name: 'Kết quả đang chờ nhập / Bản nháp' });
    expect(within(results).getAllByText('Nguyễn Văn Phi')).toHaveLength(1);
    expect(within(results).getByText('+2 dịch vụ khác')).toBeInTheDocument();
    expect(within(results).getByText('Bản nháp (1/3)')).toBeInTheDocument();
    expect(within(results).getByText('CD0000000268')).toBeInTheDocument();
  });
});
