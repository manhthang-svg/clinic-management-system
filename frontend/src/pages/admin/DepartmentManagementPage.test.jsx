import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DepartmentManagementPage } from './DepartmentManagementPage';
import { departmentApi } from '../../apis/departmentApi';
import { dutyScheduleApi } from '../../apis/dutyScheduleApi';
import { getVietnamTodayStr } from '../../utils/vietnamTimeUtils';
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

const roomWithTechnician = {
  id: 'room-3',
  unitType: 'ROOM',
  code: 'CDHA-XQ',
  name: 'Phòng X-quang',
  departmentId: 'dept-3',
  departmentName: 'Khoa CĐHA',
  type: 'PARACLINICAL',
  location: 'Tầng 2 - P.203',
  status: 'ACTIVE',
  hasDoctorOnDutyToday: true,
  doctorOnDutyToday: 'KTV Nguyễn Văn B',
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

  it('không cho phép tạm ngưng phòng cận lâm sàng khi phòng có kỹ thuật viên trực trong ngày', async () => {
    departmentApi.getItems.mockResolvedValue([dept1, roomWithDoctor, roomWithoutDoctor, roomWithTechnician]);
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

    expect(await screen.findByText('Phòng X-quang')).toBeInTheDocument();

    const toggleBtnTech = screen.getByTitle(/Không thể tạm ngưng: phòng đang có bác sĩ\/kỹ thuật viên trực trong ngày/i);
    expect(toggleBtnTech).toBeInTheDocument();

    await user.click(toggleBtnTech);

    expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('Không được phép tạm ngừng phòng "Phòng X-quang"')
    );
    expect(departmentApi.toggleRoomStatus).not.toHaveBeenCalled();
  });

  it('đánh dấu phòng cận lâm sàng có KTV trực khi tải lịch trực trong ngày (staffRole: TECHNICIAN)', async () => {
    const todayStr = getVietnamTodayStr();
    const roomTechNoSchedule = {
      id: 'room-tech-1',
      unitType: 'ROOM',
      code: 'CDHA-CT',
      name: 'Phòng CT',
      departmentId: 'dept-3',
      departmentName: 'Khoa CĐHA',
      type: 'PARACLINICAL',
      location: 'Tầng 2 - P.204',
      status: 'ACTIVE',
      hasDoctorOnDutyToday: false,
      doctorOnDutyToday: null,
    };
    departmentApi.getItems.mockResolvedValue([roomTechNoSchedule]);
    dutyScheduleApi.getWeeklySchedule.mockResolvedValue({
      items: [
        {
          id: 'duty-tech-1',
          roomId: 'room-tech-1',
          workDate: todayStr,
          shift: 'MORNING',
          staffRole: 'TECHNICIAN',
          staffName: 'KTV Trần Văn C',
        },
      ],
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

    expect(await screen.findByText('Phòng CT')).toBeInTheDocument();

    const toggleBtn = await screen.findByTitle(/Không thể tạm ngưng: phòng đang có bác sĩ\/kỹ thuật viên trực trong ngày/i);
    expect(toggleBtn).toBeInTheDocument();

    await user.click(toggleBtn);
    expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('Không được phép tạm ngừng phòng "Phòng CT"')
    );
    expect(departmentApi.toggleRoomStatus).not.toHaveBeenCalled();
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

  it('phân bác sĩ vào phòng khám từ nút Phân phòng', async () => {
    const accountApi = {
      list: vi.fn().mockResolvedValue({
        content: [
          { id: 'doctor-1', username: 'doctor01', fullName: 'Nguyễn Văn B', role: 'DOCTOR', status: 'ACTIVE' },
        ],
        page: { totalPages: 1 },
      }),
      assignRoom: vi.fn().mockResolvedValue({ id: 'doctor-1', roomId: 'room-2' }),
    };
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
                accountApi={accountApi}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Phòng khám Ngoại 1')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Phân phòng Phòng khám Ngoại 1' }));

    expect(accountApi.list).toHaveBeenCalledWith({
      role: 'DOCTOR', status: 'ACTIVE', page: 0, size: 50,
    });
    await user.selectOptions(await screen.findByLabelText('Bác sĩ *'), 'doctor-1');
    await user.click(screen.getByRole('button', { name: 'Phân phòng' }));

    await waitFor(() => {
      expect(accountApi.assignRoom).toHaveBeenCalledWith('doctor-1', { roomId: 'room-2' });
    });
    expect(toast.success).toHaveBeenCalledWith(
      'Đã phân Nguyễn Văn B vào phòng "Phòng khám Ngoại 1"'
    );
  });
});
