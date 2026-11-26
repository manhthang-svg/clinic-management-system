import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DepartmentManagementPage } from './DepartmentManagementPage';
import { departmentApi } from '../../apis/departmentApi';
import { dutyScheduleApi } from '../../apis/dutyScheduleApi';
import { getVietnamTodayStr } from '../../utils/vietnamTimeUtils';
import { toast } from 'sonner';
import { ApiError } from '../../apis/apiError';

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

  it('xếp khoa vào nhóm theo Phân loại đã lưu, không đoán theo tên khoa', async () => {
    const rehabilitation = {
      id: 'dept-9', unitType: 'DEPARTMENT', code: 'K64', name: 'Khoa Phục hồi chức năng',
      type: 'CLINICAL', status: 'ACTIVE',
    };
    departmentApi.getItems.mockResolvedValue([dept1, rehabilitation, roomWithoutDoctor]);
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/admin/departments']}>
        <Routes>
          <Route
            path="/admin/departments"
            element={<DepartmentManagementPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} />}
          />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Phòng khám Ngoại 1')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thêm phòng mới' }));

    expect(screen.getByRole('option', { name: 'Khoa Phục hồi chức năng (K64) - Khoa lâm sàng' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Khoa Phục hồi chức năng (K64) - Khoa cận lâm sàng' })).not.toBeInTheDocument();
  });

  it('phân bác sĩ vào phòng khám từ nút Phân phòng', async () => {
    const accountApi = {
      list: vi.fn().mockResolvedValue({
        content: [
          { id: 'doctor-1', username: 'doctor01', fullName: 'Nguyễn Văn B', role: 'DOCTOR', status: 'ACTIVE' },
          {
            id: 'doctor-2', username: 'doctor02', fullName: 'Trần Thị C', role: 'DOCTOR', status: 'ACTIVE',
            roomId: 'room-2', roomCode: 'PK-NGOAI-01', roomName: 'Phòng khám Ngoại 1',
          },
          {
            id: 'doctor-3', username: 'doctor03', fullName: 'Lê Văn D', role: 'DOCTOR', status: 'ACTIVE',
            roomId: 'room-1', roomCode: 'PK-NOI-01', roomName: 'Phòng khám Nội 1',
          },
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
    const dialog = await screen.findByRole('dialog', { name: 'Phân phòng: Phòng khám Ngoại 1' });
    // Staff already in this room are listed as assigned, not offered again.
    expect(within(dialog).getByText('Nhân sự đang được phân công (1)')).toBeInTheDocument();
    expect(within(dialog).getByText('Trần Thị C')).toBeInTheDocument();
    expect(within(dialog).queryByRole('checkbox', { name: /Trần Thị C/ })).not.toBeInTheDocument();
    // The others are offered with their current room.
    expect(within(dialog).getByText('PK-NOI-01 – Phòng khám Nội 1')).toBeInTheDocument();
    expect(within(dialog).getByText('Chưa có phòng')).toBeInTheDocument();

    // BR-110: no diacritics needed.
    await user.type(within(dialog).getByLabelText('Tìm bác sĩ'), 'le van');
    expect(within(dialog).getByRole('checkbox', { name: /Lê Văn D/ })).toBeInTheDocument();
    expect(within(dialog).queryByRole('checkbox', { name: /Nguyễn Văn B/ })).not.toBeInTheDocument();
    await user.clear(within(dialog).getByLabelText('Tìm bác sĩ'));
    await user.type(within(dialog).getByLabelText('Tìm bác sĩ'), 'khong co ai');
    expect(within(dialog).getByText('Không tìm thấy kết quả.')).toBeInTheDocument();
    await user.clear(within(dialog).getByLabelText('Tìm bác sĩ'));

    await user.click(within(dialog).getByRole('checkbox', { name: 'Chọn Nguyễn Văn B (doctor01)' }));
    await user.click(within(dialog).getByRole('checkbox', { name: 'Chọn Lê Văn D (doctor03)' }));
    await user.click(within(dialog).getByRole('button', { name: 'Phân phòng (2)' }));

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith(
        'Đã phân Nguyễn Văn B, Lê Văn D vào phòng "Phòng khám Ngoại 1"'
      );
    });
    expect(accountApi.assignRoom).toHaveBeenCalledTimes(2);
    expect(accountApi.assignRoom).toHaveBeenCalledWith('doctor-1', { roomId: 'room-2' });
    expect(accountApi.assignRoom).toHaveBeenCalledWith('doctor-3', { roomId: 'room-2' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('không cho đổi phòng nhân sự đã có lịch trực', async () => {
    const lockedError = new ApiError('conflict', {
      status: 409,
      errorResponse: { code: 'STAFF_HAS_DUTY_SCHEDULES', message: 'conflict', details: { shiftCount: '2' } },
    });
    const accountApi = {
      list: vi.fn().mockResolvedValue({
        content: [
          {
            id: 'doctor-4', username: 'doctor04', fullName: 'Phạm Văn E', role: 'DOCTOR', status: 'ACTIVE',
            roomId: 'room-1', roomCode: 'PK-NOI-01', roomName: 'Phòng khám Nội 1', hasUpcomingDutySchedule: true,
          },
          {
            id: 'doctor-5', username: 'doctor05', fullName: 'Hồ Thị F', role: 'DOCTOR', status: 'ACTIVE',
            roomId: 'room-1', roomCode: 'PK-NOI-01', roomName: 'Phòng khám Nội 1', hasUpcomingDutySchedule: false,
          },
        ],
        page: { totalPages: 1 },
      }),
      // The backend still decides: a schedule added after the list loaded is rejected there.
      assignRoom: vi.fn().mockRejectedValue(lockedError),
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
    const dialog = await screen.findByRole('dialog', { name: 'Phân phòng: Phòng khám Ngoại 1' });

    expect(within(dialog).getByRole('checkbox', { name: 'Chọn Phạm Văn E (doctor04)' })).toBeDisabled();
    expect(within(dialog).getByText('Còn ca làm việc từ hôm nay trở đi, chưa thể đổi phòng')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('checkbox', { name: 'Chọn Hồ Thị F (doctor05)' }));
    await user.click(within(dialog).getByRole('button', { name: 'Phân phòng (1)' }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(
        'Hồ Thị F còn 2 ca làm việc từ hôm nay trở đi nên chưa thể đổi hoặc rút khỏi phòng. Vui lòng xóa các ca của những ngày tới; ca của hôm nay chỉ có thể đổi từ ngày mai.'
      );
    });
    // A failed person stays selected and the dialog stays open.
    expect(within(dialog).getByRole('checkbox', { name: 'Chọn Hồ Thị F (doctor05)' })).toBeChecked();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('báo không có nhân sự phù hợp khi không có bác sĩ đang hoạt động', async () => {
    const accountApi = {
      list: vi.fn().mockResolvedValue({ content: [], page: { totalPages: 1 } }),
      assignRoom: vi.fn(),
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

    const dialog = await screen.findByRole('dialog', { name: 'Phân phòng: Phòng khám Ngoại 1' });
    expect(
      await within(dialog).findByText('Không có nhân sự đang hoạt động phù hợp với loại phòng này.')
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Hủy' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(accountApi.assignRoom).not.toHaveBeenCalled();
  });

  it('không cho phép tạm ngưng phòng khi phòng có ca trực trong tương lai', async () => {
    const roomWithFutureShift = {
      id: 'room-4',
      unitType: 'ROOM',
      code: 'PK-NOI-04',
      name: 'Phòng khám Nội 4',
      departmentId: 'dept-1',
      departmentName: 'Khoa Nội',
      type: 'EXAMINATION',
      location: 'Tầng 1 - P.104',
      status: 'ACTIVE',
      hasDoctorOnDutyToday: false,
      doctorOnDutyToday: null,
      hasFutureDutySchedules: true,
      hasActiveVisitsToday: false,
    };

    departmentApi.getItems.mockResolvedValue([dept1, roomWithFutureShift]);
    dutyScheduleApi.getWeeklySchedule.mockResolvedValue({ items: [] });

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

    expect(await screen.findByText('Phòng khám Nội 4')).toBeInTheDocument();

    const toggleBtn = await screen.findByTitle('Không thể tạm ngưng: phòng còn nhân viên được xếp ca trực trong các ngày tới');
    expect(toggleBtn).toBeInTheDocument();

    await user.click(toggleBtn);
    expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('phòng vẫn còn nhân viên được xếp ca trực trong các ngày tới')
    );
    expect(departmentApi.toggleRoomStatus).not.toHaveBeenCalled();
  });

  it('không cho phép tạm ngưng phòng khi phòng có bệnh nhân đang chờ hoặc đang khám dở trong ngày', async () => {
    const roomWithActiveVisits = {
      id: 'room-5',
      unitType: 'ROOM',
      code: 'PK-NOI-05',
      name: 'Phòng khám Nội 5',
      departmentId: 'dept-1',
      departmentName: 'Khoa Nội',
      type: 'EXAMINATION',
      location: 'Tầng 1 - P.105',
      status: 'ACTIVE',
      hasDoctorOnDutyToday: false,
      doctorOnDutyToday: null,
      hasFutureDutySchedules: false,
      hasActiveVisitsToday: true,
    };

    departmentApi.getItems.mockResolvedValue([dept1, roomWithActiveVisits]);
    dutyScheduleApi.getWeeklySchedule.mockResolvedValue({ items: [] });

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

    expect(await screen.findByText('Phòng khám Nội 5')).toBeInTheDocument();

    const toggleBtn = await screen.findByTitle('Không thể tạm ngưng: phòng đang có bệnh nhân chờ hoặc đang khám dở trong ngày');
    expect(toggleBtn).toBeInTheDocument();

    await user.click(toggleBtn);
    expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('phòng vẫn còn bệnh nhân đang chờ hoặc đang khám dở trong ngày hôm nay')
    );
    expect(departmentApi.toggleRoomStatus).not.toHaveBeenCalled();
  });
});
