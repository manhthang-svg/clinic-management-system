import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createStatisticsApi } from '../../apis/statisticsApi';
import { fakeStatisticsServer } from '../../test/fakeStatisticsServer';
import { DepartmentStatisticsPage } from './DepartmentStatisticsPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const TODAY = new Date(2026, 10, 12);

function room(roomId, roomCode, roomName, type, counts = {}, active = true) {
  return {
    roomId, roomCode, roomName, type, active,
    visits: 0, completedVisits: 0, inProgressVisits: 0,
    services: 0, performedServices: 0, waitingServices: 0, cancelledServices: 0,
    ...counts,
  };
}

const lab = {
  departmentId: 'dep-xn', departmentCode: 'XN', departmentName: 'Khoa Xét nghiệm', type: 'PARACLINICAL', active: true,
  visits: 0, completedVisits: 0, services: 18, performedServices: 14,
  rooms: [room('r-sh1', 'XN-SH-1', 'Phòng Xét nghiệm Sinh hóa 1', 'PARACLINICAL',
    { services: 18, performedServices: 14, waitingServices: 3, cancelledServices: 1 })],
};
const clinical = {
  departmentId: 'dep-noi', departmentCode: 'NOI', departmentName: 'Khoa Nội', type: 'CLINICAL', active: true,
  visits: 12, completedVisits: 9, services: 0, performedServices: 0,
  rooms: [
    room('r-pk2', 'PK-NOI-2', 'Phòng khám Nội 2', 'EXAMINATION', { visits: 9, completedVisits: 6, inProgressVisits: 2 }),
    room('r-pk1', 'PK-NOI-1', 'Phòng khám Nội 1', 'EXAMINATION', { visits: 3, completedVisits: 3 }),
    room('r-pk9', 'PK-NOI-9', 'Phòng khám Nội 9', 'EXAMINATION', { visits: 1, completedVisits: 1 }, false),
  ],
};
const idle = {
  departmentId: 'dep-ngoai', departmentCode: 'NGOAI', departmentName: 'Khoa Ngoại', type: 'CLINICAL', active: true,
  visits: 0, completedVisits: 0, services: 0, performedServices: 0, rooms: [],
};
const stats = { from: '2026-11-01', to: '2026-11-12', visits: 12, services: 18, departments: [lab, clinical, idle] };

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
    <MemoryRouter initialEntries={['/admin/reports/departments']}>
      <DepartmentStatisticsPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} today={TODAY} />
    </MemoryRouter>,
  );
}

function rowOf(table, name) {
  return within(table).getByRole('rowheader', { name: new RegExp(name) }).closest('tr');
}

describe('DepartmentStatisticsPage (UC-29)', () => {
  it('loads the current month and shows the totals', async () => {
    const api = fakeStatisticsServer({ departments: stats });
    renderPage(api);

    expect(await screen.findByText('Số liệu từ 01/11/2026', { exact: false })).toBeInTheDocument();
    expect(api.getDepartmentOverview).toHaveBeenCalledWith({ from: '2026-11-01', to: '2026-11-12' }, expect.any(AbortSignal));
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('18')).toBeInTheDocument();
    expect(screen.getByText('Trên 3 khoa')).toBeInTheDocument();
    expect(screen.getByText('Trên 4 phòng')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Khoa / Phòng' })).toHaveAttribute('aria-current', 'page');
  });

  it('lists each department with its rooms and the right workload for each kind of room', async () => {
    const api = fakeStatisticsServer({ departments: stats });
    renderPage(api);

    const table = await screen.findByRole('table');
    expect(rowOf(table, '^Khoa Xét nghiệm')).toHaveTextContent('18 dịch vụ (14 đã làm)');
    expect(rowOf(table, 'Sinh hóa 1')).toHaveTextContent('Phòng CLS18 dịch vụ (14 đã làm)31');
    expect(rowOf(table, '^Khoa Nội')).toHaveTextContent('12 lượt (9 hoàn thành)');
    expect(rowOf(table, 'Phòng khám Nội 2')).toHaveTextContent('Phòng khám9 lượt (6 hoàn thành)2—');
    expect(rowOf(table, 'Phòng khám Nội 9')).toHaveTextContent('Tạm ngưng');
    expect(within(table).getByText('Khoa chưa có phòng.')).toBeInTheDocument();
  });

  it('filters the detail by department type', async () => {
    const api = fakeStatisticsServer({ departments: stats });
    const user = userEvent.setup();
    renderPage(api);
    const table = await screen.findByRole('table');

    // loc loai khoa o server
    await user.click(screen.getByRole('button', { name: 'Cận lâm sàng' }));
    await waitFor(() => expect(within(table).queryByRole('rowheader', { name: /^Khoa Nội/ })).not.toBeInTheDocument());
    expect(within(table).getByRole('rowheader', { name: /^Khoa Xét nghiệm/ })).toBeInTheDocument();
    expect(api.getDepartmentRows).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: 'PARACLINICAL', page: 0, size: 20 }), expect.any(AbortSignal));

    await user.click(screen.getByRole('button', { name: 'Lâm sàng' }));
    await waitFor(() => expect(within(table).queryByRole('rowheader', { name: /^Khoa Xét nghiệm/ })).not.toBeInTheDocument());
    expect(within(table).getByRole('rowheader', { name: /^Khoa Ngoại/ })).toBeInTheDocument();
  });

  it('shows the empty chart when no department worked in the period', async () => {
    const api = fakeStatisticsServer({ departments: { ...stats, visits: 0, services: 0, departments: [idle] } });
    renderPage(api);

    expect(await screen.findByText('Chưa có dữ liệu trong khoảng thời gian này.')).toBeInTheDocument();
    expect(screen.getByRole('table')).toHaveTextContent('Khoa Ngoại');
  });

  it('reloads with a quick period and shows a Vietnamese error with retry', async () => {
    const api = fakeStatisticsServer({ departments: stats });
    api.getDepartmentOverview.mockImplementationOnce(api.getDepartmentOverview.getMockImplementation()).mockRejectedValueOnce(new ApiError('Network', {}));
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('table');

    await user.click(screen.getByRole('button', { name: '7 ngày qua' }));
    await waitFor(() => expect(api.getDepartmentOverview).toHaveBeenLastCalledWith(
      { from: '2026-11-06', to: '2026-11-12' }, expect.any(AbortSignal)));
    expect(await screen.findByText('Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    await waitFor(() => expect(screen.queryByText(/Không kết nối được máy chủ/)).not.toBeInTheDocument());
  });
});

describe('statisticsApi.getDepartments', () => {
  it('sends the period as query parameters', async () => {
    const client = { request: vi.fn().mockResolvedValue(stats) };
    await createStatisticsApi(client).getDepartments({ from: '2026-11-01', to: '2026-11-12' });

    expect(client.request).toHaveBeenCalledWith('/statistics/departments?from=2026-11-01&to=2026-11-12', { signal: undefined });
  });
});
