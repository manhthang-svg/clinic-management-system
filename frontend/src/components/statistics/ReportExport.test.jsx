import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createStatisticsApi } from '../../apis/statisticsApi';
import { ReportActions } from './ReportExport';

const period = { from: '2026-11-01', to: '2026-11-12' };
const file = { blob: new Blob(['xlsx']), fileName: 'bao-cao-thong-ke_2026-11-01_2026-11-12.xlsx' };

describe('ReportActions (UC-33)', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:report');
    URL.revokeObjectURL = vi.fn();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  async function openDialog(api, props = {}) {
    const user = userEvent.setup();
    render(<ReportActions api={api} period={period} groupBy="MONTH" {...props} />);
    await user.click(screen.getByRole('button', { name: 'Xuất Excel' }));
    return { user, dialog: screen.getByRole('dialog', { name: 'Xuất báo cáo Excel' }) };
  }

  it('ticks every report by default and downloads them for the page period', async () => {
    const api = { exportExcel: vi.fn().mockResolvedValue(file) };
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const { user, dialog } = await openDialog(api);

    expect(dialog).toHaveTextContent('Từ 01/11/2026 đến 12/11/2026');
    ['Lượt khám', 'Bác sĩ', 'Khoa / Phòng', 'Cận lâm sàng', 'Doanh thu', 'Bệnh thường gặp'].forEach((label) => {
      expect(within(dialog).getByLabelText(label)).toBeChecked();
    });
    expect(within(dialog).getByRole('button', { name: 'Bỏ chọn tất cả' })).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Lượt khám và doanh thu')).toHaveValue('MONTH');

    await user.click(within(dialog).getByRole('button', { name: 'Tải Excel' }));

    await waitFor(() => expect(api.exportExcel).toHaveBeenCalledWith({
      from: '2026-11-01', to: '2026-11-12',
      reports: ['VISITS', 'DOCTORS', 'DEPARTMENTS', 'PARACLINICAL', 'REVENUE', 'DIAGNOSES'],
      groupBy: 'MONTH', limit: undefined,
    }));
    expect(click).toHaveBeenCalled();
    expect(URL.createObjectURL).toHaveBeenCalledWith(file.blob);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('exports only the reports left ticked, in the order of the tabs', async () => {
    const api = { exportExcel: vi.fn().mockResolvedValue(file) };
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const { user, dialog } = await openDialog(api, { limit: 20 });

    await user.click(within(dialog).getByRole('button', { name: 'Bỏ chọn tất cả' }));
    await user.click(within(dialog).getByLabelText('Doanh thu'));
    await user.click(within(dialog).getByLabelText('Lượt khám'));
    await user.click(within(dialog).getByRole('button', { name: 'Tải Excel' }));

    await waitFor(() => expect(api.exportExcel).toHaveBeenCalledWith(expect.objectContaining({
      reports: ['VISITS', 'REVENUE'], limit: 20,
    })));
  });

  it('needs at least one report', async () => {
    const api = { exportExcel: vi.fn() };
    const { user, dialog } = await openDialog(api);

    await user.click(within(dialog).getByRole('button', { name: 'Bỏ chọn tất cả' }));

    expect(within(dialog).getByRole('button', { name: 'Tải Excel' })).toBeDisabled();
    expect(within(dialog).getByText('Chọn ít nhất một báo cáo.')).toBeInTheDocument();
  });

  it('shows a Vietnamese error and keeps the dialog open when the export fails', async () => {
    const api = {
      exportExcel: vi.fn().mockRejectedValue(
        new ApiError('Forbidden', { status: 403, errorResponse: { code: 'FORBIDDEN' } })),
    };
    const { user, dialog } = await openDialog(api);

    await user.click(within(dialog).getByRole('button', { name: 'Tải Excel' }));

    expect(await within(dialog).findByText('Bạn không có quyền xuất báo cáo.')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Tải Excel' })).toBeEnabled();
  });

  it('prints the page for "In / Lưu PDF"', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    const user = userEvent.setup();
    render(<ReportActions api={{ exportExcel: vi.fn() }} period={period} />);

    await user.click(screen.getByRole('button', { name: 'In / Lưu PDF' }));

    expect(print).toHaveBeenCalledTimes(1);
  });
});

describe('statisticsApi.exportExcel', () => {
  it('downloads the chosen reports as a comma-separated list', async () => {
    const client = { download: vi.fn().mockResolvedValue(file) };
    await createStatisticsApi(client).exportExcel({
      from: '2026-11-01', to: '2026-11-12', reports: ['VISITS', 'REVENUE'], groupBy: 'DAY',
    });

    expect(client.download).toHaveBeenCalledWith(
      '/statistics/export?from=2026-11-01&to=2026-11-12&reports=VISITS%2CREVENUE&groupBy=DAY');
  });
});
