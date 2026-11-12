import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createStatisticsApi } from '../../apis/statisticsApi';
import { formatMoneyShort } from '../../components/statistics/dashboardPeriod';
import { RevenueStatisticsPage } from './RevenueStatisticsPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const TODAY = new Date(2026, 10, 12);

function amounts(payments, insurance, patient) {
  return { payments, totalAmount: insurance + patient, insuranceAmount: insurance, patientAmount: patient };
}

const byDay = {
  from: '2026-11-01',
  to: '2026-11-03',
  groupBy: 'DAY',
  summary: amounts(6, 300000, 900000),
  byType: [
    { type: 'EXAMINATION_FEE', amounts: amounts(4, 300000, 300000) },
    { type: 'PARACLINICAL_FEE', amounts: amounts(2, 0, 600000) },
  ],
  byMethod: [
    { method: 'BANK_TRANSFER', amounts: amounts(3, 100000, 650000) },
    { method: 'CASH', amounts: amounts(3, 200000, 250000) },
  ],
  series: [
    { period: '2026-11-01', amounts: amounts(0, 0, 0) },
    { period: '2026-11-02', amounts: amounts(5, 200000, 850000) },
    { period: '2026-11-03', amounts: amounts(1, 100000, 50000) },
  ],
};

const byMonth = { ...byDay, groupBy: 'MONTH', series: [{ period: '2026-11-01', amounts: byDay.summary }] };

const empty = {
  ...byDay, summary: amounts(0, 0, 0), byType: [], byMethod: [],
  series: byDay.series.map((item) => ({ ...item, amounts: amounts(0, 0, 0) })),
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
    <MemoryRouter initialEntries={['/admin/reports/revenue']}>
      <RevenueStatisticsPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} today={TODAY} />
    </MemoryRouter>,
  );
}

describe('RevenueStatisticsPage (UC-31)', () => {
  it('loads the current month by day and separates insurance and patient amounts', async () => {
    const api = { getRevenue: vi.fn().mockResolvedValue(byDay) };
    renderPage(api);

    expect(await screen.findByRole('heading', { name: 'Doanh thu theo ngày' })).toBeInTheDocument();
    expect(api.getRevenue).toHaveBeenCalledWith(
      { from: '2026-11-01', to: '2026-11-12', groupBy: 'DAY' }, expect.any(AbortSignal));
    expect(screen.getAllByText('1.200.000 ₫').length).toBeGreaterThan(0);
    expect(screen.getByText('25% doanh thu')).toBeInTheDocument();
    expect(screen.getByText('75% doanh thu')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Doanh thu' })).toHaveAttribute('aria-current', 'page');
  });

  it('lists only the days with receipts and a total row', async () => {
    const api = { getRevenue: vi.fn().mockResolvedValue(byDay) };
    renderPage(api);

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4); // header, 2 days, total
    expect(rows[1]).toHaveTextContent('02/11/2026');
    expect(within(rows[1]).getAllByRole('cell').map((cell) => cell.textContent))
      .toEqual(['5', '200.000 ₫', '850.000 ₫', '1.050.000 ₫']);
    expect(within(rows[3]).getByRole('rowheader')).toHaveTextContent('Tổng');
    expect(within(table).queryByText('01/11/2026')).not.toBeInTheDocument();
  });

  it('shows the receipt types and the payment methods with Vietnamese labels', async () => {
    const api = { getRevenue: vi.fn().mockResolvedValue(byDay) };
    renderPage(api);

    const types = await screen.findByRole('region', { name: 'Theo loại khoản thu' });
    expect(within(types).getByText('Phí khám')).toBeInTheDocument();
    expect(within(types).getByText('Phí cận lâm sàng')).toBeInTheDocument();
    const methods = screen.getByRole('region', { name: 'Theo hình thức thanh toán' });
    expect(within(methods).getByText('Chuyển khoản').closest('li')).toHaveTextContent('650.000 ₫');
    expect(within(methods).getByText('Tiền mặt').closest('li')).toHaveTextContent('250.000 ₫');
  });

  it('switches to monthly buckets', async () => {
    const api = { getRevenue: vi.fn().mockResolvedValueOnce(byDay).mockResolvedValueOnce(byMonth) };
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('heading', { name: 'Doanh thu theo ngày' });

    await user.click(screen.getByRole('button', { name: 'Theo tháng' }));

    await waitFor(() => expect(api.getRevenue).toHaveBeenLastCalledWith(
      { from: '2026-11-01', to: '2026-11-12', groupBy: 'MONTH' }, expect.any(AbortSignal)));
    expect(await screen.findByRole('heading', { name: 'Doanh thu theo tháng' })).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByRole('rowheader')[0]).toHaveTextContent('11/2026');
  });

  it('shows the empty state when nothing was paid', async () => {
    const api = { getRevenue: vi.fn().mockResolvedValue(empty) };
    renderPage(api);

    await screen.findByRole('heading', { name: 'Doanh thu theo ngày' });
    expect(screen.getAllByText('Chưa có dữ liệu trong khoảng thời gian này.')).toHaveLength(4);
    expect(screen.getAllByText('0% doanh thu')).toHaveLength(2);
  });

  it('shows a Vietnamese error and retries', async () => {
    const api = {
      getRevenue: vi.fn()
        .mockRejectedValueOnce(new ApiError('Forbidden', { status: 403, errorResponse: { code: 'FORBIDDEN' } }))
        .mockResolvedValueOnce(byDay),
    };
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByText('Bạn không có quyền xem thống kê.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });
});

describe('revenue helpers', () => {
  it('sends the period and grouping as query parameters', async () => {
    const client = { request: vi.fn().mockResolvedValue(byDay) };
    await createStatisticsApi(client).getRevenue({ from: '2026-11-01', to: '2026-11-12', groupBy: 'MONTH' });

    expect(client.request).toHaveBeenCalledWith(
      '/statistics/revenue?from=2026-11-01&to=2026-11-12&groupBy=MONTH', { signal: undefined });
  });

  it('shortens money for the chart axis', () => {
    expect(formatMoneyShort(1_250_000_000)).toBe('1,3 tỷ');
    expect(formatMoneyShort(3_500_000)).toBe('3,5 tr');
    expect(formatMoneyShort(350_000)).toBe('350 N');
    expect(formatMoneyShort(500)).toBe('500');
  });
});
