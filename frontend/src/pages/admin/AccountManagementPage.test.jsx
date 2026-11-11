import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { toast } from 'sonner';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { AccountManagementPage } from './AccountManagementPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() } }));

beforeEach(() => {
  vi.clearAllMocks();
});

const ADMIN_ID = '0192a000-0005-7000-8000-000000000001';
const STAFF_ID = '0192a000-0005-7000-8000-000000000002';
const OTHER_ADMIN_ID = '0192a000-0005-7000-8000-000000000003';
const admin = { id: ADMIN_ID, username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const staff = {
  id: STAFF_ID, username: 'bs.an', fullName: 'Nguyễn An', academicTitle: 'BS',
  phoneNumber: '0901234567', email: 'an@clinic.test', role: 'DOCTOR', status: 'ACTIVE',
  roomName: 'Phòng Nội', departmentName: 'Khoa Nội', passwordChangeRequired: false,
  failedLoginAttempts: 0, version: 2, createdAt: '2026-10-01T00:00:00Z',
  allowedActions: { update: true, assignRole: true, lock: true, unlock: false, resetPassword: true, assignRoom: true },
};

function page(content = [staff]) {
  return { content, page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 } };
}

function accountApi(overrides = {}) {
  return {
    list: vi.fn().mockResolvedValue(page()),
    departments: vi.fn().mockResolvedValue([{ id: 'dep-1', code: 'NOI', name: 'Khoa Nội', active: true }]),
    detail: vi.fn().mockResolvedValue(staff),
    activities: vi.fn().mockResolvedValue([]),
    changeHistory: vi.fn().mockResolvedValue(page([])),
    workInProgress: vi.fn().mockResolvedValue({ visitsInProgress: 0, ordersInProgress: 0 }),
    resetPassword: vi.fn().mockResolvedValue({ temporaryPassword: 'TempPass1@' }),
    ...overrides,
  };
}

function renderPage(path, api) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AccountManagementPage currentAdmin={admin} accountApi={api} onLogout={vi.fn()} onChangePassword={vi.fn()} />
    </MemoryRouter>,
  );
}

describe('AccountManagementPage backend integration surface', () => {
  it.each([
    new ApiError('missing', { status: 404 }),
    new ApiError('missing', { status: 400, errorResponse: { code: 'ACCOUNT_NOT_FOUND', message: 'missing' } }),
  ])('shows MSG39 and a back-to-list action when account detail does not exist', async (error) => {
    const api = accountApi({ detail: vi.fn().mockRejectedValue(error) });
    renderPage('/admin/accounts/0192a000-0005-7000-8000-000000000099', api);

    expect(await screen.findByRole('heading', { name: 'Không tìm thấy tài khoản.' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Về danh sách tài khoản' })).toBeInTheDocument();
    expect(screen.queryByText('Đang tải thông tin tài khoản…')).not.toBeInTheDocument();
  });

  it('shows MSG39 without calling account APIs when the URL contains a malformed UUID', async () => {
    const api = accountApi();
    renderPage('/admin/accounts/0192a000-0005-7000-8000-0000000000099', api);

    expect(await screen.findByRole('heading', { name: 'Không tìm thấy tài khoản.' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Về danh sách tài khoản' })).toBeInTheDocument();
    expect(api.detail).not.toHaveBeenCalled();
    expect(api.activities).not.toHaveBeenCalled();
    expect(api.changeHistory).not.toHaveBeenCalled();
    expect(api.workInProgress).not.toHaveBeenCalled();
  });

  it('displays MSG01 when no account matches the current criteria', async () => {
    const api = accountApi({ list: vi.fn().mockResolvedValue(page([])) });
    renderPage('/admin/accounts', api);

    expect(await screen.findByText('Không tìm thấy kết quả.')).toBeInTheDocument();
  });

  it('loads department options and sends department plus sorting to the list API', async () => {
    const api = accountApi();
    const user = userEvent.setup();
    renderPage('/admin/accounts', api);

    await user.selectOptions(await screen.findByLabelText('Lọc theo khoa'), 'dep-1');
    await waitFor(() => expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({ departmentId: 'dep-1' }), expect.any(AbortSignal),
    ));

    await user.click(screen.getByRole('button', { name: 'Họ và tên' }));
    await waitFor(() => expect(api.list).toHaveBeenCalledWith(
      expect.objectContaining({ sort: 'fullName,asc' }), expect.any(AbortSignal),
    ));
  });

  it('loads detail, recent activity, change history and work in progress', async () => {
    const api = accountApi({
      changeHistory: vi.fn().mockResolvedValue({
        content: [{
          id: 'history-1', action: 'UPDATE', occurredAt: '2026-10-20T08:00:00Z', actorAccountId: null,
          changedFields: ['email'], oldValue: '{"email":"old@clinic.test"}', newValue: '{"email":"an@clinic.test"}',
        }],
        page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
      }),
      workInProgress: vi.fn().mockResolvedValue({ visitsInProgress: 1, ordersInProgress: 2 }),
    });
    renderPage(`/admin/accounts/${STAFF_ID}`, api);

    expect(await screen.findByRole('heading', { name: 'Chi tiết tài khoản' })).toBeInTheDocument();
    expect(await screen.findByText('Lịch sử thay đổi tài khoản')).toBeInTheDocument();
    expect(screen.getByText('Cập nhật thông tin')).toBeInTheDocument();
    expect(screen.getByText('old@clinic.test')).toBeInTheDocument();
    expect(screen.getByText('Người thực hiện: Hệ thống')).toBeInTheDocument();
    expect(api.activities).toHaveBeenCalledWith(STAFF_ID, 100, expect.any(AbortSignal));
    expect(api.changeHistory).toHaveBeenCalledWith(STAFF_ID, 0, 5, expect.any(AbortSignal));
    expect(api.workInProgress).toHaveBeenCalledWith(STAFF_ID, expect.any(AbortSignal));
  });

  it.each([
    ['the signed-in Admin account', admin],
    ['another Admin account', { ...admin, id: OTHER_ADMIN_ID, username: 'admin.khac' }],
  ])('does not expose the edit form for %s', async (_caseName, protectedAccount) => {
    const api = accountApi({ detail: vi.fn().mockResolvedValue(protectedAccount) });
    renderPage(`/admin/accounts/${protectedAccount.id}/edit`, api);

    expect(await screen.findByRole('heading', { name: 'Chi tiết tài khoản' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Chỉnh sửa tài khoản' })).not.toBeInTheDocument();
  });

  it.each([
    [`/admin/accounts/${STAFF_ID}/edit`, 'Chỉnh sửa tài khoản'],
    [`/admin/accounts/${STAFF_ID}/assign-role`, 'Đổi vai trò'],
  ])('does not open %s directly for a locked account', async (path, blockedHeading) => {
    const locked = {
      ...staff,
      status: 'LOCKED',
      allowedActions: { ...staff.allowedActions, lock: false, unlock: true },
    };
    const api = accountApi({ detail: vi.fn().mockResolvedValue(locked) });

    renderPage(path, api);

    expect(await screen.findByRole('heading', { name: 'Chi tiết tài khoản' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: blockedHeading })).not.toBeInTheDocument();
  });

  it('resets a password from a dialog over the detail, using backend-generated mode by default', async () => {
    const api = accountApi();
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}`, api);

    await user.click(await screen.findByRole('button', { name: 'Đặt lại mật khẩu' }));
    const form = screen.getByRole('dialog', { name: 'Đặt lại mật khẩu' });
    expect(within(form).getByRole('radio', { name: /Hệ thống tự sinh/ })).toBeChecked();
    expect(screen.getByRole('heading', { name: 'Chi tiết tài khoản' })).toBeInTheDocument();

    await user.click(within(form).getByRole('button', { name: 'Đặt lại mật khẩu' }));
    expect(api.resetPassword).not.toHaveBeenCalled();
    const confirm = screen.getByText(/Bạn có chắc muốn đặt lại mật khẩu cho Nguyễn An \(bs\.an\)/).closest('[role="dialog"]');
    await user.click(within(confirm).getByRole('button', { name: 'Đặt lại mật khẩu' }));
    await waitFor(() => expect(api.resetPassword).toHaveBeenCalledWith(STAFF_ID, {
      mode: 'GENERATE', password: null, expectedVersion: 2,
    }));
    expect(await screen.findByText('TempPass1@')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'In' })).toBeInTheDocument();
  });
});

describe('AccountManagementPage UC-09 professional information (I1-D47)', () => {
  const receptionist = {
    ...staff, username: 'lt.binh', fullName: 'Trần Bình', academicTitle: null, practiceLicenseNo: null,
    role: 'RECEPTIONIST', roomName: null, departmentName: null,
  };

  it('requires the academic title and a valid practice license before changing to Doctor', async () => {
    const api = accountApi({ detail: vi.fn().mockResolvedValue(receptionist), checkRoleChange: vi.fn() });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}/assign-role`, api);

    await user.click(await screen.findByRole('radio', { name: /Bác sĩ/ }));
    await user.type(screen.getByLabelText(/Số giấy phép hành nghề/), 'AB');
    await user.click(screen.getByRole('button', { name: /Áp dụng vai trò/ }));

    expect(screen.getByText('Học hàm / học vị là bắt buộc.')).toBeInTheDocument();
    expect(screen.getByText('Số giấy phép hành nghề không đúng định dạng.')).toBeInTheDocument();
    expect(api.checkRoleChange).not.toHaveBeenCalled();
  });

  it('shows MSG07 under the license field when another account uses it', async () => {
    const api = accountApi({
      detail: vi.fn().mockResolvedValue(receptionist),
      checkRoleChange: vi.fn().mockRejectedValue(new ApiError('conflict', {
        status: 409,
        errorResponse: {
          code: 'CONCURRENT_MODIFICATION', message: 'conflict', details: { practiceLicenseNo: 'ALREADY_EXISTS' },
        },
      })),
      changeRole: vi.fn(),
    });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}/assign-role`, api);

    await user.click(await screen.findByRole('radio', { name: /Bác sĩ/ }));
    await user.type(screen.getByLabelText(/Học hàm \/ học vị/), 'ThS.BS');
    await user.type(screen.getByLabelText(/Số giấy phép hành nghề/), '000123/HCM-CCHN');
    await user.click(screen.getByRole('button', { name: /Áp dụng vai trò/ }));

    expect(await screen.findByText('Số giấy phép hành nghề đã tồn tại trong hệ thống.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đổi vai trò' })).not.toBeInTheDocument();
    expect(api.changeRole).not.toHaveBeenCalled();
  });

  it('sends the professional information with the role check and the role change', async () => {
    const api = accountApi({
      detail: vi.fn().mockResolvedValue(receptionist),
      checkRoleChange: vi.fn().mockResolvedValue({ visitsInProgress: 0, ordersInProgress: 0 }),
      changeRole: vi.fn().mockResolvedValue({ ...receptionist, role: 'DOCTOR', academicTitle: 'ThS.BS', practiceLicenseNo: '000123/HCM-CCHN' }),
    });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}/assign-role`, api);

    await user.click(await screen.findByRole('radio', { name: /Bác sĩ/ }));
    await user.type(screen.getByLabelText(/Học hàm \/ học vị/), 'ThS.BS');
    await user.type(screen.getByLabelText(/Số giấy phép hành nghề/), '000123/HCM-CCHN');
    await user.click(screen.getByRole('button', { name: /Áp dụng vai trò/ }));

    const expected = {
      newRole: 'DOCTOR', expectedRole: 'RECEPTIONIST', academicTitle: 'ThS.BS', practiceLicenseNo: '000123/HCM-CCHN',
    };
    await waitFor(() => expect(api.checkRoleChange).toHaveBeenCalledWith(STAFF_ID, expected));
    await user.click(await screen.findByRole('button', { name: 'Đổi vai trò' }));
    await waitFor(() => expect(api.changeRole).toHaveBeenCalledWith(STAFF_ID, expected));
  });
});

describe('AccountManagementPage prescriber link code (BR-49, BR-65)', () => {
  const doctor = { ...staff, practiceLicenseNo: '000123/HCM-CCHN', maLienthongBacsi: null };

  it("lets the Admin enter a Doctor's prescriber link code and sends it with the update", async () => {
    const api = accountApi({
      detail: vi.fn().mockResolvedValue(doctor),
      update: vi.fn().mockResolvedValue({ ...doctor, maLienthongBacsi: 'BS0001', version: 3 }),
    });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}/edit`, api);

    await user.type(await screen.findByLabelText(/Mã liên thông bác sĩ/), ' BS0001 ');
    await user.click(screen.getByRole('button', { name: /Lưu/ }));

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(STAFF_ID, expect.objectContaining({
      maLienthongBacsi: 'BS0001', version: 2,
    })));
  });

  it('does not show the field for a role that does not prescribe', async () => {
    const api = accountApi({ detail: vi.fn().mockResolvedValue({ ...staff, role: 'CASHIER', roomName: null }) });
    renderPage(`/admin/accounts/${STAFF_ID}/edit`, api);

    expect(await screen.findByLabelText(/Họ và tên/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Mã liên thông bác sĩ/)).not.toBeInTheDocument();
  });
});

describe('AccountManagementPage UC-10 Lock/Unlock messages', () => {
  it('shows MSG31 and the BR-97 length message under the lock reason without calling the API', async () => {
    const api = accountApi({ lock: vi.fn() });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}`, api);

    await user.click(await screen.findByRole('button', { name: 'Khóa tài khoản' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Khóa tài khoản' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Vui lòng nhập lý do.');

    await user.type(within(dialog).getByLabelText(/Lý do khóa/), '  abc  ');
    await user.click(within(dialog).getByRole('button', { name: 'Khóa tài khoản' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Lý do phải có từ 5 đến 500 ký tự.');
    expect(api.lock).not.toHaveBeenCalled();
  });

  it('sends the trimmed lock reason', async () => {
    const api = accountApi({ lock: vi.fn().mockResolvedValue({ ...staff, status: 'LOCKED' }) });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}`, api);

    await user.click(await screen.findByRole('button', { name: 'Khóa tài khoản' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/Lý do khóa/), '  Nghỉ việc  ');
    await user.click(within(dialog).getByRole('button', { name: 'Khóa tài khoản' }));

    await waitFor(() => expect(api.lock).toHaveBeenCalledWith(STAFF_ID, {
      reason: 'Nghỉ việc', expectedStatus: 'ACTIVE', expectedVersion: 2,
    }));
    expect(toast.success).toHaveBeenCalledWith('Đã khóa tài khoản bs.an');
  });

  it('locks a staff member who signed in after the detail was opened (login bumps the version)', async () => {
    const detail = vi.fn().mockResolvedValue(staff);
    const api = accountApi({ detail, lock: vi.fn().mockResolvedValue({ ...staff, status: 'LOCKED', version: 4 }) });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}`, api);

    await user.click(await screen.findByRole('button', { name: 'Khóa tài khoản' }));
    const dialog = await screen.findByRole('dialog');
    detail.mockResolvedValue({ ...staff, version: 3, lastLoginAt: '2026-11-11T00:25:00Z' });
    await user.type(within(dialog).getByLabelText(/Lý do khóa/), 'Nhân viên nghỉ việc');
    await user.click(within(dialog).getByRole('button', { name: 'Khóa tài khoản' }));

    await waitFor(() => expect(api.lock).toHaveBeenCalledWith(STAFF_ID, {
      reason: 'Nhân viên nghỉ việc', expectedStatus: 'ACTIVE', expectedVersion: 3,
    }));
    expect(toast.success).toHaveBeenCalledWith('Đã khóa tài khoản bs.an');
  });

  it('shows MSG47 and does not open the lock dialog while work is in progress', async () => {
    const api = accountApi({
      workInProgress: vi.fn().mockResolvedValue({ visitsInProgress: 1, ordersInProgress: 2 }),
      lock: vi.fn(),
    });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}`, api);

    await user.click(await screen.findByRole('button', { name: 'Khóa tài khoản' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(
      'Nguyễn An đang có lượt khám hoặc dịch vụ đang thực hiện. Vui lòng hoàn tất các công việc này trước.',
    ));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(api.lock).not.toHaveBeenCalled();
  });

  it('asks for unlock confirmation with MSG06', async () => {
    const locked = { ...staff, status: 'LOCKED', allowedActions: { ...staff.allowedActions, lock: false, unlock: true } };
    const api = accountApi({ detail: vi.fn().mockResolvedValue(locked) });
    const user = userEvent.setup();
    renderPage(`/admin/accounts/${STAFF_ID}`, api);

    await user.click(await screen.findByRole('button', { name: 'Mở khóa tài khoản' }));

    expect(within(await screen.findByRole('dialog'))
      .getByText('Bạn có chắc chắn muốn mở khóa tài khoản bs.an không?')).toBeInTheDocument();
  });
});
