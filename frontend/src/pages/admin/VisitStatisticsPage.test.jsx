import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createStatisticsApi } from '../../apis/statisticsApi';
import { fakeStatisticsServer } from '../../test/fakeStatisticsServer';
import { VisitStatisticsPage } from './VisitStatisticsPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const TODAY = new Date(2026, 10, 11);

function counts(overrides = {}) {
  return {
    total: 0, completed: 0, inProgress: 0, cancelled: 0, abandoned: 0,
    insurance: 0, selfPay: 0, followUp: 0, priority: 0, ...overrides,
  };
}

const byDay = {
  from: '2026-11-01',
  to: '2026-11-03',
  groupBy: 'DAY',
  summary: counts({ total: 20, completed: 15, inProgress: 2, cancelled: 2, abandoned: 1, insurance: 12, selfPay: 8, followUp: 4, priority: 3 }),
  series: [
    { period: '2026-11-01', counts: counts() },
    { period: '2026-11-02', counts: counts({ total: 12, completed: 9, inProgress: 2, cancelled: 1, insurance: 7, selfPay: 5, followUp: 3, priority: 2 }) },
    { period: '2026-11-03', counts: counts({ total: 8, completed: 6, cancelled: 1, abandoned: 1, insurance: 5, selfPay: 3, followUp: 1, priority: 1 }) },
  ],
};

const byMonth = {
  ...byDay,
  groupBy: 'MONTH',
  series: [{ period: '2026-11-01', counts: byDay.summary }],
};

const empty = { ...byDay, summary: counts(), series: byDay.series.map((s) => ({ ...s, counts: counts() })) };

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
    <MemoryRouter initialEntries={['/admin/reports']}>
      <VisitStatisticsPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} today={TODAY} />
    </MemoryRouter>,
  );
}

describe('VisitStatisticsPage (UC-27)', () => {
  it('loads the current month by day and shows the summary', async () => {
    const api = fakeStatisticsServer({ visits: byDay });
    renderPage(api);

    expect(await screen.findByRole('heading', { name: 'Lượt khám theo ngày' })).toBeInTheDocument();
    expect(api.getVisits).toHaveBeenCalledWith(
      { from: '2026-11-01', to: '2026-11-11', groupBy: 'DAY' }, expect.any(AbortSignal));
    expect(screen.getByText('20')).toBeInTheDocument();
    expect(screen.getByText('75% tổng lượt khám')).toBeInTheDocument();
    expect(screen.getByText('3 lượt đã hủy hoặc bỏ khám')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Theo ngày' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('lists only the days that have visits in the detail table', async () => {
    const api = fakeStatisticsServer({ visits: byDay });
    renderPage(api);

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getByRole('rowheader')).toHaveTextContent('02/11/2026');
    expect(within(rows[1]).getAllByRole('cell').map((c) => c.textContent))
      .toEqual(['12', '9', '2', '1', '0', '7', '5', '3', '2']);
    expect(within(table).queryByText('01/11/2026')).not.toBeInTheDocument();
    // bang lay tu server, 20 dong / trang
    expect(api.getVisitRows).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }), expect.any(AbortSignal));
  });

  it('switches to monthly buckets', async () => {
    const api = fakeStatisticsServer({ visits: (params) => (params.groupBy === 'MONTH' ? byMonth : byDay) });
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('heading', { name: 'Lượt khám theo ngày' });

    await user.click(screen.getByRole('button', { name: 'Theo tháng' }));

    await waitFor(() => expect(api.getVisits).toHaveBeenLastCalledWith(
      { from: '2026-11-01', to: '2026-11-11', groupBy: 'MONTH' }, expect.any(AbortSignal)));
    expect(await screen.findByRole('heading', { name: 'Lượt khám theo tháng' })).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByRole('rowheader')).toHaveTextContent('11/2026');
    expect(screen.getByRole('columnheader', { name: 'Tháng' })).toBeInTheDocument();
  });

  it('shows the visit kinds: insurance, self-pay, new and follow-up', async () => {
    const api = fakeStatisticsServer({ visits: byDay });
    renderPage(api);

    const types = await screen.findByRole('region', { name: 'Đối tượng bệnh nhân' });
    expect(within(types).getByText('12')).toBeInTheDocument();
    expect(within(types).getByText('8')).toBeInTheDocument();
    const kinds = screen.getByRole('region', { name: 'Khám mới và tái khám' });
    expect(within(kinds).getByText('16')).toBeInTheDocument();
    expect(within(kinds).getByText('4')).toBeInTheDocument();
  });

  it('shows the empty state when the period has no visits', async () => {
    const api = fakeStatisticsServer({ visits: empty });
    renderPage(api);

    await screen.findByRole('heading', { name: 'Lượt khám theo ngày' });
    expect(screen.getAllByText('Chưa có dữ liệu trong khoảng thời gian này.')).toHaveLength(4);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('does not call the API for an invalid period', async () => {
    const api = fakeStatisticsServer({ visits: byDay });
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('table');
    api.getVisits.mockClear();
    api.getVisitRows.mockClear();

    const to = screen.getByLabelText('Đến ngày');
    await user.clear(to);
    await user.type(to, '2026-10-01');

    expect(await screen.findByRole('alert')).toHaveTextContent('Ngày bắt đầu không được sau ngày kết thúc.');
    expect(api.getVisits).not.toHaveBeenCalled();
    expect(api.getVisitRows).not.toHaveBeenCalled();
  });

  it('shows a Vietnamese error and retries', async () => {
    const api = fakeStatisticsServer({ visits: byDay });
    api.getVisits.mockRejectedValueOnce(new ApiError('Network', {}));
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByText('Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
  });
});

describe('statisticsApi.getVisits', () => {
  it('sends the period and grouping as query parameters', async () => {
    const client = { request: vi.fn().mockResolvedValue(byDay) };
    await createStatisticsApi(client).getVisits({ from: '2026-11-01', to: '2026-11-03', groupBy: 'MONTH' });

    expect(client.request).toHaveBeenCalledWith(
      '/statistics/visits?from=2026-11-01&to=2026-11-03&groupBy=MONTH', { signal: undefined });
  });
});
