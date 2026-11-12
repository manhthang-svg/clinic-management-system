import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createStatisticsApi } from '../../apis/statisticsApi';
import { DiagnosisStatisticsPage } from './DiagnosisStatisticsPage';

const admin = { id: 'admin-1', username: 'admin', fullName: 'Quản trị hệ thống', role: 'ADMIN' };
const TODAY = new Date(2026, 10, 12);

const stats = {
  from: '2026-11-01',
  to: '2026-11-12',
  limit: 10,
  conclusions: 40,
  withIcd: 40,
  withoutIcd: 0,
  distinctCodes: 12,
  mainDiagnoses: [
    { icdCode: 'J06.9', icdName: 'Nhiễm trùng hô hấp trên cấp', cases: 9, patients: 8 },
    { icdCode: 'I10', icdName: 'Tăng huyết áp vô căn', cases: 6, patients: 6 },
  ],
  categories: [{ categoryCode: 'J06', categoryName: 'Nhiễm trùng cấp đường hô hấp trên', cases: 11 }],
  secondaryDiagnoses: [{ icdCode: 'E11.9', icdName: 'Đái tháo đường type 2', cases: 4 }],
};

const empty = {
  ...stats, conclusions: 0, withIcd: 0, distinctCodes: 0, mainDiagnoses: [], categories: [], secondaryDiagnoses: [],
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
    <MemoryRouter initialEntries={['/admin/reports/diagnoses']}>
      <DiagnosisStatisticsPage currentAdmin={admin} onLogout={vi.fn()} onChangePassword={vi.fn()} api={api} today={TODAY} />
    </MemoryRouter>,
  );
}

describe('DiagnosisStatisticsPage (UC-32)', () => {
  it('loads the current month with the top 10 and shows the totals', async () => {
    const api = { getDiagnoses: vi.fn().mockResolvedValue(stats) };
    renderPage(api);

    expect(await screen.findByText('Số liệu từ 01/11/2026', { exact: false })).toBeInTheDocument();
    expect(api.getDiagnoses).toHaveBeenCalledWith(
      { from: '2026-11-01', to: '2026-11-12', limit: 10 }, expect.any(AbortSignal));
    expect(screen.getByText('100% số kết luận')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('Nhiễm trùng hô hấp trên cấp · 9 ca')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bệnh thường gặp' })).toHaveAttribute('aria-current', 'page');
  });

  it('ranks the main diagnoses with cases, patients and share', async () => {
    const api = { getDiagnoses: vi.fn().mockResolvedValue(stats) };
    renderPage(api);

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('1J06.9Nhiễm trùng hô hấp trên cấp9822,5%');
    expect(rows[2]).toHaveTextContent('2I10Tăng huyết áp vô căn6615%');
  });

  it('shows the ICD-10 categories and the secondary diagnoses', async () => {
    const api = { getDiagnoses: vi.fn().mockResolvedValue(stats) };
    renderPage(api);

    const categories = await screen.findByRole('region', { name: 'Theo nhóm bệnh' });
    expect(within(categories).getByRole('listitem')).toHaveTextContent('1J06Nhiễm trùng cấp đường hô hấp trên11');
    const secondary = screen.getByRole('region', { name: 'Bệnh kèm theo thường gặp' });
    expect(within(secondary).getByRole('listitem')).toHaveTextContent('E11.9Đái tháo đường type 24');
  });

  it('asks for a longer ranking', async () => {
    const api = { getDiagnoses: vi.fn().mockResolvedValue(stats) };
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('table');

    await user.click(screen.getByRole('button', { name: 'Top 50' }));

    await waitFor(() => expect(api.getDiagnoses).toHaveBeenLastCalledWith(
      { from: '2026-11-01', to: '2026-11-12', limit: 50 }, expect.any(AbortSignal)));
    expect(screen.getByRole('button', { name: 'Top 50' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('shows the empty state when no conclusion was saved in the period', async () => {
    const api = { getDiagnoses: vi.fn().mockResolvedValue(empty) };
    renderPage(api);

    await screen.findByText('Số liệu từ 01/11/2026', { exact: false });
    expect(screen.getAllByText('Chưa có dữ liệu trong khoảng thời gian này.')).toHaveLength(4);
    expect(screen.getByText('Chưa có kết luận')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows a Vietnamese error and retries', async () => {
    const api = {
      getDiagnoses: vi.fn()
        .mockRejectedValueOnce(new ApiError('Bad', { status: 400, errorResponse: { code: 'REQUEST_VALIDATION_FAILED' } }))
        .mockResolvedValueOnce(stats),
    };
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByText('Khoảng thời gian không hợp lệ. Vui lòng chọn lại.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });
});

describe('statisticsApi.getDiagnoses', () => {
  it('sends the period and the ranking size as query parameters', async () => {
    const client = { request: vi.fn().mockResolvedValue(stats) };
    await createStatisticsApi(client).getDiagnoses({ from: '2026-11-01', to: '2026-11-12', limit: 20 });

    expect(client.request).toHaveBeenCalledWith(
      '/statistics/diagnoses?from=2026-11-01&to=2026-11-12&limit=20', { signal: undefined });
  });
});
