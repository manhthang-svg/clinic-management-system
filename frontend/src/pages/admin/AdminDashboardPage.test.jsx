import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { AdminDashboardPage } from './AdminDashboardPage';
import { validatePeriod } from '../../components/statistics/dashboardPeriod';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const TODAY = new Date(2026, 10, 11);

function days(from, count, totals = {}) {
  const [y, m, d] = from.split('-').map(Number);
  return Array.from({ length: count }, (_, i) => {
    const date = new Date(y, m - 1, d + i);
    const iso = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    return { date: iso, total: totals[iso] ?? 0 };
  });
}

const dashboard = {
  from: '2026-11-01',
  to: '2026-11-11',
  visits: { total: 120, completed: 95, inProgress: 10, cancelled: 8, abandoned: 7, insurance: 70, selfPay: 50 },
  distinctPatients: 104,
  paraclinicalServicesCompleted: 63,
  revenue: { paymentCount: 150, totalAmount: 12500000, insuranceAmount: 8000000, patientAmount: 4500000 },
  dailyVisits: days('2026-11-01', 11, { '2026-11-03': 20 }),
  topRooms: [{ roomId: 'r-1', roomName: 'Phòng khám Nội 1', total: 40 }],
  topDiagnoses: [{ icdCode: 'J06.9', icdName: 'Nhiễm trùng hô hấp trên cấp', total: 18 }],
};

const empty = {
  ...dashboard,
  visits: { total: 0, completed: 0, inProgress: 0, cancelled: 0, abandoned: 0, insurance: 0, selfPay: 0 },
  distinctPatients: 0,
  paraclinicalServicesCompleted: 0,
  revenue: { paymentCount: 0, totalAmount: 0, insuranceAmount: 0, patientAmount: 0 },
  dailyVisits: days('2026-11-01', 11),
  topRooms: [],
  topDiagnoses: [],
};

// Recharts' ResponsiveContainer needs ResizeObserver, which jsdom does not provide.
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
    <MemoryRouter initialEntries={['/admin/dashboard']}>
      <AdminDashboardPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} today={TODAY} />
    </MemoryRouter>,
  );
}

describe('AdminDashboardPage (UC-26)', () => {
  it('loads the current month by default and shows the key figures', async () => {
    const api = { getDashboard: vi.fn().mockResolvedValue(dashboard) };
    renderPage(api);

    expect(screen.getByText('Đang tải số liệu thống kê…')).toBeInTheDocument();
    expect(await screen.findByText('Số liệu từ 01/11/2026 đến 11/11/2026.')).toBeInTheDocument();
    expect(api.getDashboard).toHaveBeenCalledWith({ from: '2026-11-01', to: '2026-11-11' }, expect.any(AbortSignal));

    expect(screen.getByText('120')).toBeInTheDocument();
    expect(screen.getByText('95 lượt đã hoàn thành')).toBeInTheDocument();
    expect(screen.getByText('104')).toBeInTheDocument();
    expect(screen.getByText('63')).toBeInTheDocument();
    expect(screen.getByText('150 khoản thu')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tháng này' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('keeps insurance and patient revenue separate', async () => {
    const api = { getDashboard: vi.fn().mockResolvedValue(dashboard) };
    renderPage(api);

    const revenue = await screen.findByRole('region', { name: 'Doanh thu đã thu' });
    expect(within(revenue).getByText('BHYT chi trả')).toBeInTheDocument();
    expect(within(revenue).getByText('Bệnh nhân trả')).toBeInTheDocument();
    expect(within(revenue).getByText(/8\.000\.000/)).toBeInTheDocument();
    expect(within(revenue).getByText(/4\.500\.000/)).toBeInTheDocument();
  });

  it('lists the most common diagnoses', async () => {
    const api = { getDashboard: vi.fn().mockResolvedValue(dashboard) };
    renderPage(api);

    const table = await screen.findByRole('table');
    expect(within(table).getByRole('cell', { name: 'J06.9' })).toBeInTheDocument();
    expect(within(table).getByRole('cell', { name: 'Nhiễm trùng hô hấp trên cấp' })).toBeInTheDocument();
    expect(within(table).getByRole('cell', { name: '18' })).toBeInTheDocument();
  });

  it('reloads with a quick period', async () => {
    const api = { getDashboard: vi.fn().mockResolvedValue(dashboard) };
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByText('Số liệu từ 01/11/2026 đến 11/11/2026.');

    await user.click(screen.getByRole('button', { name: '7 ngày qua' }));

    await waitFor(() => expect(api.getDashboard).toHaveBeenLastCalledWith(
      { from: '2026-11-05', to: '2026-11-11' }, expect.any(AbortSignal)));
    expect(screen.getByRole('button', { name: '7 ngày qua' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('does not call the API when the start date is after the end date', async () => {
    const api = { getDashboard: vi.fn().mockResolvedValue(dashboard) };
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByText('Số liệu từ 01/11/2026 đến 11/11/2026.');
    api.getDashboard.mockClear();

    const from = screen.getByLabelText('Từ ngày');
    await user.clear(from);
    await user.type(from, '2026-11-20');

    expect(await screen.findByRole('alert')).toHaveTextContent('Ngày bắt đầu không được sau ngày kết thúc.');
    expect(api.getDashboard).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Tháng này' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('shows an empty state when the period has no data', async () => {
    const api = { getDashboard: vi.fn().mockResolvedValue(empty) };
    renderPage(api);

    await screen.findByText('Số liệu từ 01/11/2026 đến 11/11/2026.');
    expect(screen.getAllByText('Chưa có dữ liệu trong khoảng thời gian này.').length).toBeGreaterThanOrEqual(5);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows a Vietnamese error and retries', async () => {
    const api = {
      getDashboard: vi.fn()
        .mockRejectedValueOnce(new ApiError('Forbidden', { status: 403, errorResponse: { code: 'FORBIDDEN' } }))
        .mockResolvedValueOnce(dashboard),
    };
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByText('Bạn không có quyền xem thống kê.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByText('Số liệu từ 01/11/2026 đến 11/11/2026.')).toBeInTheDocument();
    expect(screen.queryByText('Bạn không có quyền xem thống kê.')).not.toBeInTheDocument();
  });
});

describe('validatePeriod', () => {
  it('mirrors the backend limits', () => {
    expect(validatePeriod({ from: '2026-11-01', to: '2026-11-01' })).toBe('');
    expect(validatePeriod({ from: '2028-01-01', to: '2028-12-31' })).toBe('');
    expect(validatePeriod({ from: '2025-01-01', to: '2026-01-02' })).toMatch('366 ngày');
    expect(validatePeriod({ from: '', to: '2026-11-01' })).toMatch('chọn đủ');
  });
});
