import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../apis/apiError';
import { TechnicianDashboardPage } from './TechnicianDashboardPage';

const ME = { id: 'tech-1', fullName: 'Nguyễn Văn Bảo', role: 'TECHNICIAN' };
// 17/11/2026 14:00, inside the afternoon shift.
const TODAY = new Date(2026, 10, 17, 14, 0);

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

function scheduleApi(items = []) {
  return {
    getWeeklySchedule: vi.fn().mockResolvedValue({
      shiftConfig: { morningStartTime: '08:00', morningEndTime: '11:30', afternoonStartTime: '13:00', afternoonEndTime: '17:30' },
      items,
    }),
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
    expect(screen.getByText('Ca chiều (13:00 – 17:30)')).toBeInTheDocument();
    expect(screen.getByText('Đã hoàn thành hôm nay').closest('button')).toHaveTextContent('9');
    expect(screen.getByText('Chờ nhập kết quả').closest('button')).toHaveTextContent('3');
    expect(screen.getByText('2 ca ưu tiên')).toBeInTheDocument();

    const queue = screen.getByRole('region', { name: 'Bệnh nhân sắp tới lượt' });
    expect(within(queue).getByText('Trần Thị Mai')).toBeInTheDocument();
    expect(within(queue).getByText('#01')).toBeInTheDocument();
    expect(within(queue).getByText('Xem tất cả (7)')).toBeInTheDocument();

    const results = screen.getByRole('region', { name: 'Kết quả đang chờ nhập / Bản nháp' });
    expect(within(results).getByText('Bản nháp')).toBeInTheDocument();
    expect(within(results).getByText('>30p')).toBeInTheDocument();
    expect(api.setActiveRoomId).toHaveBeenCalledWith('room-1');
  });

  it('marks the page read-only outside the technician\'s shift in this room', async () => {
    renderPage(technicianApi(vi.fn().mockResolvedValue(dashboard({ canManage: false }))));

    expect(await screen.findByText('Ngoài ca trực (Chỉ xem)')).toBeInTheDocument();
    expect(screen.getByText(/Thông báo ngoài ca trực/)).toBeInTheDocument();
  });

  it('lists only the current technician\'s shifts for the next 4 days', async () => {
    const schedules = scheduleApi([
      { id: 's-1', workDate: '2026-11-17', shift: 'AFTERNOON', staffAccountId: 'tech-1', roomCode: 'P.204', roomName: 'Sinh hóa' },
      { id: 's-2', workDate: '2026-11-18', shift: 'MORNING', staffAccountId: 'tech-1', roomCode: 'P.205', roomName: 'Huyết học' },
      { id: 's-3', workDate: '2026-11-18', shift: 'AFTERNOON', staffAccountId: 'someone-else', roomCode: 'P.105', roomName: 'Siêu âm' },
    ]);
    renderPage(technicianApi(vi.fn().mockResolvedValue(dashboard())), schedules);

    const shifts = await screen.findByRole('region', { name: 'Lịch trực cá nhân sắp tới' });
    expect(await within(shifts).findByText('Hôm nay, 17/11')).toBeInTheDocument();
    expect(within(shifts).getByText('Đang diễn ra')).toBeInTheDocument();
    expect(within(shifts).getByText('P.205 – Huyết học')).toBeInTheDocument();
    expect(within(shifts).queryByText('P.105 – Siêu âm')).not.toBeInTheDocument();
    expect(within(shifts).getAllByText('Nghỉ ca')).toHaveLength(2);
    expect(schedules.getWeeklySchedule).toHaveBeenCalledWith('2026-11-17', '2026-11-20', expect.anything());
  });

  it('forgets a stored room that no longer exists and shows the default room', async () => {
    const getDashboard = vi.fn()
      .mockRejectedValueOnce(new ApiError('Not found', { status: 404, errorResponse: { code: 'ROOM_NOT_FOUND' } }))
      .mockResolvedValue(dashboard());
    const api = { getDashboard, getActiveRoomId: vi.fn(() => 'old-room'), setActiveRoomId: vi.fn() };
    renderPage(api);

    expect(await screen.findByRole('heading', { name: 'Phòng Xét nghiệm Sinh hóa' })).toBeInTheDocument();
    expect(getDashboard).toHaveBeenNthCalledWith(1, { roomId: 'old-room' }, expect.anything());
    expect(getDashboard).toHaveBeenNthCalledWith(2, {}, expect.anything());
    expect(api.setActiveRoomId).toHaveBeenCalledWith('');
  });

  it('shows an error with a retry when the dashboard cannot be loaded', async () => {
    renderPage(technicianApi(vi.fn().mockRejectedValue(new ApiError('Server error', { status: 500 }))));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải dữ liệu tổng quan. Vui lòng thử lại.');
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });
});
