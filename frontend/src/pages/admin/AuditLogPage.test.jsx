import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuditLogPage } from './AuditLogPage';
import { formatAuditTime } from '../../constants/auditLogData';
import { auditLogApi } from '../../apis/auditLogApi';

vi.mock('../../layouts', () => ({
  AdminLayout: ({ children }) => <div data-testid="admin-layout">{children}</div>,
}));

vi.mock('../../apis/auditLogApi', () => ({
  auditLogApi: {
    search: vi.fn(),
    getDetail: vi.fn(),
    listEntityTypes: vi.fn(),
  },
}));

const adminRow = {
  id: 'log-1',
  occurredAt: '2026-11-20T03:15:00Z',
  actorAccountId: 'acc-1',
  actorUsername: 'admin.an',
  actorFullName: 'Nguyễn Văn An',
  actorRole: 'ADMIN',
  action: 'UPDATE',
  entityType: 'clinic_facility',
  entityId: 'fac-1',
  sovaovien: null,
  outcome: 'SUCCESS',
  changedFields: ['name'],
  ipAddress: null,
};

const systemRow = {
  id: 'log-2',
  occurredAt: '2026-11-20T17:00:00Z',
  actorAccountId: null,
  action: 'UPDATE',
  entityType: 'visits',
  entityId: 'v-1',
  sovaovien: 'VV0001',
  outcome: 'FAILURE',
  changedFields: null,
};

const page = (content, totalElements = content.length, totalPages = 1) => ({
  content,
  page: { number: 0, size: 20, totalElements, totalPages },
});

const lastSearchArgs = () => auditLogApi.search.mock.calls.at(-1)[0];

describe('AuditLogPage (UC-78)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auditLogApi.listEntityTypes.mockResolvedValue(['clinic_facility', 'visits', 'unknown_type']);
  });

  it('shows entries newest first with actor, action, object and outcome', async () => {
    auditLogApi.search.mockResolvedValue(page([adminRow, systemRow]));

    render(<AuditLogPage />);

    expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument();
    expect(screen.getByText('admin.an • Quản trị viên')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /Thông tin cơ sở/ })).toBeInTheDocument();
    expect(screen.getByText('Thay đổi: name')).toBeInTheDocument();
    expect(screen.getByText('Hệ thống')).toBeInTheDocument();
    expect(screen.getByText('VV0001')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Thất bại' })).toBeInTheDocument();
    expect(screen.getByText('Tổng', { exact: false })).toHaveTextContent('Tổng 2 dòng nhật ký');
    expect(lastSearchArgs()).toMatchObject({ page: 0, size: 20 });
  });

  it('lists object types from the server, with unknown ones shown raw', async () => {
    auditLogApi.search.mockResolvedValue(page([]));

    render(<AuditLogPage />);

    const select = screen.getByLabelText('Đối tượng');
    await waitFor(() => expect(within(select).getByRole('option', { name: 'unknown_type' })).toBeInTheDocument());
    expect(within(select).getByRole('option', { name: 'Lượt khám' })).toBeInTheDocument();
  });

  it('sends keyword and filters to the API and resets to the first page', async () => {
    const user = userEvent.setup();
    auditLogApi.search.mockResolvedValue(page([adminRow], 45, 3));

    render(<AuditLogPage />);
    await screen.findByText('Nguyễn Văn An');

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(lastSearchArgs().page).toBe(1));

    await user.selectOptions(screen.getByLabelText('Hành động'), 'PRINT');
    await user.selectOptions(screen.getByLabelText('Kết quả'), 'FAILURE');
    await user.type(screen.getByLabelText('Từ khóa'), 'admin');
    await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

    await waitFor(() => expect(lastSearchArgs()).toMatchObject({
      keyword: 'admin', action: 'PRINT', outcome: 'FAILURE', page: 0,
    }));
  });

  it('blocks a reversed date range without calling the API', async () => {
    const user = userEvent.setup();
    auditLogApi.search.mockResolvedValue(page([]));

    render(<AuditLogPage />);
    await screen.findByText('Không có nhật ký nào khớp với điều kiện tìm kiếm.');
    const calls = auditLogApi.search.mock.calls.length;

    await user.type(screen.getByLabelText('Từ ngày'), '2026-11-22');
    const callsAfterFrom = auditLogApi.search.mock.calls.length;
    await user.type(screen.getByLabelText('Đến ngày'), '2026-11-01');

    expect(await screen.findByRole('alert')).toHaveTextContent('Từ ngày không được sau đến ngày.');
    expect(callsAfterFrom).toBeGreaterThan(calls);
    expect(auditLogApi.search.mock.calls.length).toBe(callsAfterFrom);
  });

  it('shows an error with retry when loading fails', async () => {
    const user = userEvent.setup();
    auditLogApi.search.mockRejectedValueOnce(new Error('boom')).mockResolvedValue(page([adminRow]));

    render(<AuditLogPage />);

    expect(await screen.findByText('Không thể tải nhật ký hệ thống. Vui lòng thử lại.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument();
  });

  it('opens the detail with old and new values, highlighting changed fields', async () => {
    const user = userEvent.setup();
    auditLogApi.search.mockResolvedValue(page([adminRow]));
    auditLogApi.getDetail.mockResolvedValue({
      ...adminRow,
      oldValue: '{"name":"Phòng khám A","phone":"0912345678"}',
      newValue: '{"name":"Phòng khám B","phone":"0912345678"}',
      correlationId: 'req-9',
    });

    render(<AuditLogPage />);
    await user.click(await screen.findByRole('button', { name: 'Xem' }));

    const dialog = await screen.findByRole('dialog', { name: 'Chi tiết nhật ký' });
    expect(auditLogApi.getDetail).toHaveBeenCalledWith('log-1', expect.anything());
    const nameRow = (await within(dialog).findByText('Phòng khám B')).closest('tr');
    expect(nameRow).toHaveTextContent('Phòng khám A');
    expect(nameRow).toHaveClass('bg-amber-50/60');
    expect(within(dialog).getByText('phone').closest('tr')).not.toHaveClass('bg-amber-50/60');
    expect(within(dialog).getByText('req-9')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Đóng' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows a not-found message in the detail', async () => {
    const user = userEvent.setup();
    auditLogApi.search.mockResolvedValue(page([adminRow]));
    auditLogApi.getDetail.mockRejectedValue(Object.assign(new Error('nf'), { status: 404 }));

    render(<AuditLogPage />);
    await user.click(await screen.findByRole('button', { name: 'Xem' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy dòng nhật ký này.');
  });

  it('formats time in the clinic time zone', () => {
    expect(formatAuditTime('2026-11-20T17:00:00Z')).toContain('21/11/2026');
    expect(formatAuditTime(null)).toBe('—');
  });
});
