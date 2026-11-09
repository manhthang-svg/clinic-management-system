import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { MedicineManagementPage } from './MedicineManagementPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const medicine = {
  id: 'm-1', code: 'T0001', registrationNumber: '840100205826', insuranceCode: null, name: 'EU-Para 500mg Tablet',
  activeIngredient: 'Paracetamol', strength: '500mg', route: 'Uống', unit: 'Viên', controlGroup: 'THUONG',
  status: 'ACTIVE', createdAt: '2026-11-10T01:00:00Z', updatedAt: '2026-11-10T01:00:00Z', version: 0,
};

function fakeApi(overrides = {}) {
  return {
    list: vi.fn().mockResolvedValue({ content: [medicine], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } }),
    formOptions: vi.fn().mockResolvedValue({ routes: ['Uống', 'Tiêm'], units: ['Viên'] }),
    detail: vi.fn().mockResolvedValue(medicine),
    create: vi.fn().mockResolvedValue({ medicineId: 'm-2' }),
    update: vi.fn(),
    updateStatus: vi.fn().mockResolvedValue({ ...medicine, status: 'INACTIVE', version: 1 }),
    ...overrides,
  };
}

function renderPage(api, path = '/admin/medicines') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/admin/medicines/*"
          element={<MedicineManagementPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} medicineApi={api} />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('MedicineManagementPage (UC-81 to UC-84)', { timeout: 15000 }, () => {
  it('lists medicines and filters by control group', async () => {
    const api = fakeApi();
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByText('EU-Para 500mg Tablet')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Lọc theo nhóm kiểm soát'), 'HUONG_THAN');

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ controlGroup: 'HUONG_THAN', page: 0, size: 10 }), expect.anything()));
  });

  it('creates a medicine and shows MSG07 when the registration number exists', async () => {
    const api = fakeApi({
      create: vi.fn()
        .mockRejectedValueOnce(new ApiError('x', {
          status: 409, errorResponse: { code: 'MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS' },
        }))
        .mockResolvedValueOnce({ medicineId: 'm-2' }),
    });
    const user = userEvent.setup();
    renderPage(api);

    await user.click(await screen.findByRole('button', { name: 'Thêm thuốc' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText(/Mã thuốc\s*\*/), 't-0002');
    await user.type(within(dialog).getByLabelText(/Số đăng ký/), '893100111426');
    await user.type(within(dialog).getByLabelText(/Tên thuốc/), 'Vacobufen 400');
    await user.type(within(dialog).getByLabelText(/Hoạt chất/), 'Ibuprofen');
    await user.type(within(dialog).getByLabelText(/Hàm lượng/), '400mg');
    await user.selectOptions(within(dialog).getByLabelText(/Đường dùng/), 'Uống');
    await user.type(within(dialog).getByLabelText(/Đơn vị tính/), 'Viên');
    await user.selectOptions(within(dialog).getByLabelText(/Nhóm kiểm soát/), 'THUONG');
    await user.click(within(dialog).getByRole('button', { name: 'Thêm thuốc' }));

    expect(await within(dialog).findByText('Số đăng ký đã tồn tại trong hệ thống.')).toBeInTheDocument();
    expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ code: 'T-0002', registrationNumber: '893100111426' }));

    await user.click(within(dialog).getByRole('button', { name: 'Thêm thuốc' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('deactivates a medicine after MSG06 confirmation', async () => {
    const api = fakeApi();
    const user = userEvent.setup();
    renderPage(api, '/admin/medicines/m-1');

    await user.click(await screen.findByRole('button', { name: 'Ngừng sử dụng' }));
    expect(screen.getByText('Bạn có chắc chắn muốn ngừng sử dụng thuốc "EU-Para 500mg Tablet" không?'))
      .toBeInTheDocument();
    const confirm = screen.getAllByRole('button', { name: 'Ngừng sử dụng' }).at(-1);
    await user.click(confirm);

    await waitFor(() => expect(api.updateStatus).toHaveBeenCalledWith('m-1', { status: 'INACTIVE', version: 0 }));
    expect(await screen.findByRole('button', { name: 'Kích hoạt' })).toBeInTheDocument();
  });

  it('shows MSG39 for an unknown medicine', async () => {
    const api = fakeApi({
      detail: vi.fn().mockRejectedValue(new ApiError('x', { status: 404, errorResponse: { code: 'MEDICINE_NOT_FOUND' } })),
    });
    renderPage(api, '/admin/medicines/unknown');

    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy thuốc.');
  });
});
