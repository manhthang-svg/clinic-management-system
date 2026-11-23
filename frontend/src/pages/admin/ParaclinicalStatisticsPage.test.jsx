import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createStatisticsApi } from '../../apis/statisticsApi';
import { fakeStatisticsServer } from '../../test/fakeStatisticsServer';
import { ParaclinicalStatisticsPage } from './ParaclinicalStatisticsPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const TODAY = new Date(2026, 10, 12);

const stats = {
  from: '2026-11-01',
  to: '2026-11-12',
  performed: 14,
  withResult: 12,
  byType: [
    { type: 'LAB', performed: 10, withResult: 8 },
    { type: 'IMAGING', performed: 4, withResult: 4 },
  ],
  byGroup: [
    { groupId: 'g-sh', groupCode: 'BIOCHEMISTRY', groupName: 'Sinh hóa', performed: 10, withResult: 8 },
    { groupId: 'g-sa', groupCode: 'ULTRASOUND', groupName: 'Siêu âm', performed: 4, withResult: 4 },
  ],
  byService: [
    { serviceId: 's-1', serviceCode: 'XN-SH-901', serviceName: 'Bộ đường huyết (Glucose, HbA1c)', type: 'LAB', groupName: 'Sinh hóa', performed: 7, withResult: 5 },
    { serviceId: 's-2', serviceCode: 'SA-OB', serviceName: 'Siêu âm ổ bụng', type: 'IMAGING', groupName: 'Siêu âm', performed: 4, withResult: 4 },
    { serviceId: 's-3', serviceCode: 'XN-SH-004', serviceName: 'Đo hoạt độ AST, ALT', type: 'LAB', groupName: 'Sinh hóa', performed: 3, withResult: 3 },
  ],
  byRoom: [
    { roomId: 'r-1', roomCode: 'XN-SH-1', roomName: 'Phòng Xét nghiệm Sinh hóa 1', performed: 10, withResult: 8 },
    { roomId: 'r-2', roomCode: 'SA-1', roomName: 'Phòng Siêu âm 1', performed: 4, withResult: 4 },
  ],
};

const empty = { ...stats, performed: 0, withResult: 0, byType: [], byGroup: [], byService: [], byRoom: [] };

const originalResizeObserver = globalThis.ResizeObserver;
beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});
afterAll(() => {
  globalThis.ResizeObserver = originalResizeObserver;
});

function renderPage(api) {
  return render(
    <MemoryRouter initialEntries={['/admin/reports/paraclinical']}>
      <ParaclinicalStatisticsPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} today={TODAY} />
    </MemoryRouter>,
  );
}

describe('ParaclinicalStatisticsPage (UC-30)', () => {
  it('loads the current month and shows the totals', async () => {
    const api = fakeStatisticsServer({ paraclinical: stats });
    renderPage(api);

    expect(await screen.findByText('Số liệu từ 01/11/2026', { exact: false })).toBeInTheDocument();
    expect(api.getParaclinicalOverview).toHaveBeenCalledWith({ from: '2026-11-01', to: '2026-11-12' }, expect.any(AbortSignal));
    expect(screen.getByText('14')).toBeInTheDocument();
    expect(screen.getByText('85,7% số dịch vụ đã thực hiện')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument(); // awaiting result
    expect(screen.getByRole('link', { name: 'Cận lâm sàng' })).toHaveAttribute('aria-current', 'page');
  });

  it('shows the service types with Vietnamese labels and the service groups', async () => {
    const api = fakeStatisticsServer({ paraclinical: stats });
    renderPage(api);

    const types = await screen.findByRole('region', { name: 'Theo loại dịch vụ' });
    expect(within(types).getByText('Xét nghiệm')).toBeInTheDocument();
    expect(within(types).getByText('Chẩn đoán hình ảnh')).toBeInTheDocument();
    const groups = screen.getByRole('region', { name: 'Theo nhóm dịch vụ' });
    expect(within(groups).getByText('Sinh hóa').closest('li')).toHaveTextContent('10 (8 có KQ)');
  });

  // go tung ky tu + doi server: cho them thoi gian khi ca bo test chay song song
  it('lists every performed service and finds one by name or code without accents', async () => {
    const api = fakeStatisticsServer({ paraclinical: stats });
    const user = userEvent.setup();
    renderPage(api);

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    expect(rows[1]).toHaveTextContent('Bộ đường huyết (Glucose, HbA1c)XN-SH-901Xét nghiệmSinh hóa75');

    // tim o server: cho ket qua tra ve
    await user.type(screen.getByLabelText('Tìm dịch vụ'), 'sieu am');
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(2));
    expect(table).toHaveTextContent('Siêu âm ổ bụng');
    expect(api.getParaclinicalServices).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'sieu am', page: 0, size: 20 }), expect.any(AbortSignal));

    await user.clear(screen.getByLabelText('Tìm dịch vụ'));
    await user.type(screen.getByLabelText('Tìm dịch vụ'), 'xn-sh-004');
    await waitFor(() => expect(table).toHaveTextContent('Đo hoạt độ AST, ALT'));

    await user.clear(screen.getByLabelText('Tìm dịch vụ'));
    await user.type(screen.getByLabelText('Tìm dịch vụ'), 'không có');
    expect(await screen.findByText('Không tìm thấy dịch vụ phù hợp.')).toBeInTheDocument();
  }, 15000);

  it('shows the empty state when nothing was performed in the period', async () => {
    const api = fakeStatisticsServer({ paraclinical: empty });
    renderPage(api);

    await screen.findByText('Số liệu từ 01/11/2026', { exact: false });
    expect(screen.getAllByText('Chưa có dữ liệu trong khoảng thời gian này.')).toHaveLength(4);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('reloads with a quick period and shows a Vietnamese error with retry', async () => {
    const api = fakeStatisticsServer({ paraclinical: stats });
    api.getParaclinicalOverview.mockImplementationOnce(api.getParaclinicalOverview.getMockImplementation()).mockRejectedValueOnce(new ApiError('Forbidden', { status: 403, errorResponse: { code: 'FORBIDDEN' } }));
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('table');

    await user.click(screen.getByRole('button', { name: 'Năm nay' }));
    await waitFor(() => expect(api.getParaclinicalOverview).toHaveBeenLastCalledWith(
      { from: '2026-01-01', to: '2026-11-12' }, expect.any(AbortSignal)));
    expect(await screen.findByText('Bạn không có quyền xem thống kê.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    await waitFor(() => expect(screen.queryByText('Bạn không có quyền xem thống kê.')).not.toBeInTheDocument());
  });
});

describe('statisticsApi.getParaclinical', () => {
  it('sends the period as query parameters', async () => {
    const client = { request: vi.fn().mockResolvedValue(stats) };
    await createStatisticsApi(client).getParaclinical({ from: '2026-11-01', to: '2026-11-12' });

    expect(client.request).toHaveBeenCalledWith('/statistics/paraclinical?from=2026-11-01&to=2026-11-12', { signal: undefined });
  });
});
