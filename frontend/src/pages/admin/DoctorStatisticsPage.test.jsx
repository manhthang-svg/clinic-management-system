import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createStatisticsApi } from '../../apis/statisticsApi';
import { fakeStatisticsServer } from '../../test/fakeStatisticsServer';
import { DoctorStatisticsPage } from './DoctorStatisticsPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const TODAY = new Date(2026, 10, 12);

function row(doctorId, doctorName, counts = {}, active = true) {
  const values = { completed: 0, inProgress: 0, abandoned: 0, cancelled: 0, patients: 0, ...counts };
  const visits = values.completed + values.inProgress + values.abandoned + values.cancelled;
  return { doctorId, doctorName, active, visits, ...values };
}

const stats = {
  from: '2026-11-01',
  to: '2026-11-12',
  examinedVisits: 16,
  visitsWithoutDoctor: 3,
  doctors: [
    row('d-1', 'BSCKII Trần Văn An', { completed: 9, inProgress: 2, abandoned: 1, patients: 11 }),
    row('d-2', 'Lê Thị Bình', { completed: 4, patients: 4 }, false),
    row('d-3', 'ThS Phạm Minh Châu'),
  ],
};

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
    <MemoryRouter initialEntries={['/admin/reports/doctors']}>
      <DoctorStatisticsPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} today={TODAY} />
    </MemoryRouter>,
  );
}

describe('DoctorStatisticsPage (UC-28)', () => {
  it('loads the current month and shows the totals', async () => {
    const api = fakeStatisticsServer({ doctors: stats });
    renderPage(api);

    expect(await screen.findByText('Số liệu từ 01/11/2026', { exact: false })).toBeInTheDocument();
    expect(api.getDoctorOverview).toHaveBeenCalledWith({ from: '2026-11-01', to: '2026-11-12' }, expect.any(AbortSignal));
    expect(screen.getByText('Trên 3 bác sĩ')).toBeInTheDocument();
    expect(screen.getByText('16')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument(); // 16 visits / 2 doctors who examined
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bác sĩ' })).toHaveAttribute('aria-current', 'page');
  });

  it('lists every doctor with their visits by outcome and marks locked accounts', async () => {
    const api = fakeStatisticsServer({ doctors: stats });
    renderPage(api);

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    expect(within(rows[1]).getByRole('rowheader')).toHaveTextContent('BSCKII Trần Văn An');
    expect(within(rows[1]).getAllByRole('cell').map((cell) => cell.textContent))
      .toEqual(['12', '11', '9', '2', '1', '0']);
    expect(within(rows[2]).getByRole('rowheader')).toHaveTextContent('Lê Thị BìnhĐã khóa');
    expect(within(rows[3]).getAllByRole('cell')[0]).toHaveTextContent('0');
    // bang lay tu server, 20 dong / trang
    expect(api.getDoctorRows).toHaveBeenCalledWith(
      { from: '2026-11-01', to: '2026-11-12', search: '', page: 0, size: 20 }, expect.any(AbortSignal));
  });

  // go tung ky tu + doi server: cho them thoi gian khi ca bo test chay song song
  it('finds a doctor by name on the server', async () => {
    const api = fakeStatisticsServer({ doctors: stats });
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('table');

    await user.type(screen.getByLabelText('Tìm bác sĩ'), 'tran van');

    await waitFor(() => expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(2));
    expect(within(screen.getByRole('table')).getAllByRole('row')[1]).toHaveTextContent('BSCKII Trần Văn An');
    expect(api.getDoctorRows).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'tran van', page: 0 }), expect.any(AbortSignal));

    await user.clear(screen.getByLabelText('Tìm bác sĩ'));
    await user.type(screen.getByLabelText('Tìm bác sĩ'), 'không có');
    expect(await screen.findByText('Không tìm thấy bác sĩ phù hợp.')).toBeInTheDocument();
  }, 15000);

  it('shows the empty chart when nobody examined patients in the period', async () => {
    const api = fakeStatisticsServer({
      doctors: { ...stats, examinedVisits: 0, visitsWithoutDoctor: 0, doctors: [row('d-3', 'ThS Phạm Minh Châu')] },
    });
    renderPage(api);

    expect(await screen.findByText('Chưa có dữ liệu trong khoảng thời gian này.')).toBeInTheDocument();
    expect(await screen.findByRole('table')).toHaveTextContent('ThS Phạm Minh Châu');
  });

  it('reloads with a quick period and does not call the API for an invalid period', async () => {
    const api = fakeStatisticsServer({ doctors: stats });
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('table');

    await user.click(screen.getByRole('button', { name: 'Hôm nay' }));
    await waitFor(() => expect(api.getDoctorOverview).toHaveBeenLastCalledWith(
      { from: '2026-11-12', to: '2026-11-12' }, expect.any(AbortSignal)));

    api.getDoctorOverview.mockClear();
    api.getDoctorRows.mockClear();
    const from = screen.getByLabelText('Từ ngày');
    await user.clear(from);
    await user.type(from, '2026-11-20');
    expect(await screen.findByRole('alert')).toHaveTextContent('Ngày bắt đầu không được sau ngày kết thúc.');
    expect(api.getDoctorOverview).not.toHaveBeenCalled();
    expect(api.getDoctorRows).not.toHaveBeenCalled();
  });

  it('shows a Vietnamese error and retries', async () => {
    const api = fakeStatisticsServer({ doctors: stats });
    api.getDoctorOverview.mockRejectedValueOnce(
      new ApiError('Forbidden', { status: 403, errorResponse: { code: 'FORBIDDEN' } }));
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByText('Bạn không có quyền xem thống kê.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('asks the server for one page of 20 doctors at a time', async () => {
    const user = userEvent.setup();
    const doctors = Array.from({ length: 25 }, (_, i) => row(`d-${i + 1}`, `Bác sĩ số ${i + 1}`));
    const api = fakeStatisticsServer({ doctors: { ...stats, doctors } });
    renderPage(api);

    const nav = await screen.findByRole('navigation', { name: 'Phân trang danh sách bác sĩ' });
    expect(within(nav).getByText('Hiển thị 1 – 20 / 25')).toBeInTheDocument();
    // trinh duyet chi nhan 20 dong dang xem
    expect(screen.getByText('Bác sĩ số 20')).toBeInTheDocument();
    expect(screen.queryByText('Bác sĩ số 21')).not.toBeInTheDocument();

    await user.click(within(nav).getByRole('button', { name: 'Trang 2' }));

    expect(await screen.findByText('Bác sĩ số 21')).toBeInTheDocument();
    expect(screen.queryByText('Bác sĩ số 1')).not.toBeInTheDocument();
    expect(api.getDoctorRows).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, size: 20 }), expect.any(AbortSignal));
  });
});

describe('statisticsApi doctors', () => {
  it('sends the period as query parameters', async () => {
    const client = { request: vi.fn().mockResolvedValue(stats) };
    await createStatisticsApi(client).getDoctors({ from: '2026-11-01', to: '2026-11-12' });

    expect(client.request).toHaveBeenCalledWith('/statistics/doctors?from=2026-11-01&to=2026-11-12', { signal: undefined });
  });

  it('sends the search and the page of the doctor table', async () => {
    const client = { request: vi.fn().mockResolvedValue({}) };
    await createStatisticsApi(client).getDoctorRows({ from: '2026-11-01', to: '2026-11-12', search: 'an', page: 1, size: 20 });

    expect(client.request).toHaveBeenCalledWith(
      '/statistics/doctors/rows?from=2026-11-01&to=2026-11-12&search=an&page=1&size=20', { signal: undefined });
  });
});
