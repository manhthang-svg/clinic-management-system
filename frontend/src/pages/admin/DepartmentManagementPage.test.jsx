import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DepartmentManagementPage } from './DepartmentManagementPage';
import { departmentApi } from '../../apis/departmentApi';
import { dutyScheduleApi } from '../../apis/dutyScheduleApi';
import { toast } from 'sonner';

vi.mock('../../apis/departmentApi', () => ({
  departmentApi: {
    getItems: vi.fn(),
    toggleRoomStatus: vi.fn(),
    updateRoom: vi.fn(),
    createRoom: vi.fn(),
    toggleDepartmentStatus: vi.fn(),
    updateDepartment: vi.fn(),
    createDepartment: vi.fn(),
  },
}));

vi.mock('../../apis/dutyScheduleApi', () => ({
  dutyScheduleApi: {
    getWeeklySchedule: vi.fn(),
  },
}));

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };

const roomWithDoctor = {
  id: 'room-1',
  unitType: 'ROOM',
  code: 'PK-NOI-01',
  name: 'Phòng khám Nội 1',
  departmentId: 'dept-1',
  departmentName: 'Khoa Nội',
  type: 'EXAMINATION',
  location: 'Tầng 1 - P.101',
  status: 'ACTIVE',
  hasDoctorOnDutyToday: true,
  doctorOnDutyToday: 'BS. CK1 Nguyễn Văn A',
};

const roomWithoutDoctor = {
  id: 'room-2',
  unitType: 'ROOM',
  code: 'PK-NGOAI-01',
  name: 'Phòng khám Ngoại 1',
  departmentId: 'dept-2',
  departmentName: 'Khoa Ngoại',
  type: 'EXAMINATION',
  location: 'Tầng 1 - P.102',
  status: 'ACTIVE',
  hasDoctorOnDutyToday: false,
  doctorOnDutyToday: null,
};

const dept1 = {
  id: 'dept-1',
  unitType: 'DEPARTMENT',
  code: 'K-NOI',
  name: 'Khoa Nội',
  type: 'CLINICAL',
  status: 'ACTIVE',
};

describe('DepartmentManagementPage - Phòng có bác sĩ trực trong ngày', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    departmentApi.getItems.mockResolvedValue([dept1, roomWithDoctor, roomWithoutDoctor]);
    dutyScheduleApi.getWeeklySchedule.mockResolvedValue({
      items: [
        {
          id: 'duty-1',
          roomId: 'room-1',
          workDate: '2026-11-10',
          shift: 'MORNING',
          staffRole: 'DOCTOR',
          staffName: 'BS. CK1 Nguyễn Văn A',
        },
      ],
    });
  });

  it('không cho phép tạm ngưng phòng khi phòng có bác sĩ trực trong ngày', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin/departments']}>
        <Routes>
          <Route
            path="/admin/departments"
            element={
              <DepartmentManagementPage
                currentAdmin={admin}
                onLogout={vi.fn()}
                onChangePassword={vi.fn()}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    );

    // Kiểm tra tên phòng hiển thị
    expect(await screen.findByText('Phòng khám Nội 1')).toBeInTheDocument();

    // Tìm nút Đổi trạng thái (Power) của phòng khám Nội 1
    const toggleButtons = screen.getAllByTitle(/Không thể tạm ngưng: phòng đang có bác sĩ trực trong ngày/i);
    expect(toggleButtons.length).toBeGreaterThan(0);

    // Click nút tạm ngưng
    await user.click(toggleButtons[0]);

    // Kỳ vọng hiển thị toast.error và KHÔNG gọi API toggleRoomStatus
    expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('Không được phép tạm ngừng phòng "Phòng khám Nội 1"')
    );
    expect(departmentApi.toggleRoomStatus).not.toHaveBeenCalled();
    // Không được xuất hiện dialog xác nhận tạm ngưng
    expect(screen.queryByText('Bạn có chắc muốn tạm ngưng "Phòng khám Nội 1" (PK-NOI-01)?')).not.toBeInTheDocument();
  });

  it('cho phép tạm ngưng phòng khi phòng không có bác sĩ trực trong ngày', async () => {
    departmentApi.toggleRoomStatus.mockResolvedValue({
      ...roomWithoutDoctor,
      status: 'INACTIVE',
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin/departments']}>
        <Routes>
          <Route
            path="/admin/departments"
            element={
              <DepartmentManagementPage
                currentAdmin={admin}
                onLogout={vi.fn()}
                onChangePassword={vi.fn()}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Phòng khám Ngoại 1')).toBeInTheDocument();

    // Nút của phòng khám Ngoại 1 có title bình thường: "Vô hiệu hóa / Tạm ngưng"
    const toggleBtnWithoutDoctor = screen.getByTitle('Vô hiệu hóa / Tạm ngưng');
    await user.click(toggleBtnWithoutDoctor);

    // Xuất hiện modal xác nhận
    expect(await screen.findByText(/Bạn có chắc muốn tạm ngưng "Phòng khám Ngoại 1"/i)).toBeInTheDocument();

    // Click nút Tạm ngưng trong confirm modal
    const confirmBtn = screen.getByRole('button', { name: 'Tạm ngưng' });
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(departmentApi.toggleRoomStatus).toHaveBeenCalledWith('room-2');
    });
  });
});
