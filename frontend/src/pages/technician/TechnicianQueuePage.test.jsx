import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import technicianApi from '../../apis/technicianApi';
import { PERFORM_MESSAGES } from '../../components/technician/performOrder';
import { TechnicianQueuePage } from './TechnicianQueuePage';

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }));
vi.mock('sonner', () => ({ toast }));
vi.mock('../../apis/technicianApi', () => ({
  default: {
    getRooms: vi.fn(),
    getQueue: vi.fn(),
    checkInOrder: vi.fn(),
    callQueue: vi.fn(),
    acceptOrder: vi.fn(),
    skipOrder: vi.fn(),
    unskipOrder: vi.fn(),
    completeOrder: vi.fn(),
    returnToWaiting: vi.fn(),
    rejectOrder: vi.fn(),
  },
}));

const inProgress = {
  id: 'detail-1', orderId: 'order-1', queueNumber: 7, orderCode: 'CD0000000009', roomId: 'room-1',
  priority: 'NORMAL', priorityLabel: 'Thường', patientId: 'BN-0001', patientName: 'Nguyễn Văn Bình', age: 45,
  gender: 'Nam', serviceName: 'Siêu âm ổ bụng tổng quát', serviceCode: 'SA-01', doctorName: 'BS An',
  doctorRoom: 'PK Nội 1', orderTime: '08:00', status: 'IN_PROGRESS', canComplete: true, isSkipped: false, isCheckedIn: true, allOrdersToday: [],
};

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/technician/queue']}>
      <Routes>
        <Route path="/technician/queue" element={<TechnicianQueuePage />} />
        <Route path="/technician/results/:orderId" element={<p>Màn nhập kết quả</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function rowOf(name) {
  return (await screen.findByText(name)).closest('tr');
}

describe('TechnicianQueuePage – UC-66 Perform Paraclinical Order', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    technicianApi.getRooms.mockResolvedValue([
      { id: 'room-1', code: 'P.105', name: 'Phòng Siêu âm 01', isAssigned: true },
    ]);
    technicianApi.getQueue.mockResolvedValue({
      items: [inProgress], stats: { total: 1, waiting: 0, priority: 0, skipped: 0 }, canManage: true,
    });
    technicianApi.completeOrder.mockResolvedValue({ ...inProgress, status: 'PENDING_RESULT' });
    technicianApi.returnToWaiting.mockResolvedValue({ ...inProgress, status: 'WAITING' });
  });

  it('confirms that the service was performed and offers to record the result', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(within(await rowOf('Nguyễn Văn Bình')).getByRole('button', { name: 'Đã thực hiện' }));

    expect(technicianApi.completeOrder).toHaveBeenCalledWith('detail-1');
    expect(toast.success).toHaveBeenCalledWith(
      expect.stringContaining('Đã hoàn tất thực hiện kỹ thuật cho bệnh nhân Nguyễn Văn Bình'),
      expect.objectContaining({
        action: expect.objectContaining({ label: 'Nhập KQ ngay' }),
      })
    );
  });

  it('does not display patients who are already performed (PENDING_RESULT or COMPLETED) in active queue Tab 2', async () => {
    const pendingResultPatient = { ...inProgress, id: 'detail-2', status: 'PENDING_RESULT' };
    technicianApi.getQueue.mockResolvedValue({
      items: [pendingResultPatient], stats: { total: 1, waiting: 0, priority: 0, skipped: 0 }, canManage: true,
    });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Hàng đợi vào phòng hiện đang trống.')).toBeInTheDocument();
    });
    expect(screen.queryByText('Nguyễn Văn Bình')).not.toBeInTheDocument();
  });

  it('lets an off-duty technician finish only their own in-progress service', async () => {
    const otherService = { ...inProgress, id: 'detail-other', canComplete: false };
    const waiting = {
      ...inProgress, id: 'detail-waiting', orderId: 'order-waiting', patientName: 'Trần Văn Nam',
      status: 'WAITING', canComplete: false,
    };
    technicianApi.getQueue.mockResolvedValue({
      items: [otherService, inProgress, waiting], stats: { total: 3, waiting: 1, priority: 0, skipped: 0 },
      canManage: false,
    });
    const user = userEvent.setup();
    renderPage();

    const row = within(await rowOf(inProgress.patientName));
    expect(row.queryByRole('button', { name: 'Trả về' })).not.toBeInTheDocument();
    expect(within(await rowOf(waiting.patientName)).queryByRole('button', { name: 'Tiếp nhận' })).not.toBeInTheDocument();
    await user.click(row.getByRole('button', { name: 'Đã thực hiện' }));

    expect(technicianApi.completeOrder).toHaveBeenCalledWith(inProgress.id);
  });

  it('returns the service to the waiting list with a reason', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(within(await rowOf('Nguyễn Văn Bình')).getByRole('button', { name: 'Trả về' }));
    const dialog = screen.getByRole('dialog', { name: 'Trả chỉ định về hàng chờ' });
    await user.click(within(dialog).getByRole('button', { name: 'Trả về hàng chờ' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent(PERFORM_MESSAGES.reasonRequired);
    expect(technicianApi.returnToWaiting).not.toHaveBeenCalled();

    await user.type(within(dialog).getByLabelText('Lý do *'), 'Máy siêu âm mất nguồn');
    await user.click(within(dialog).getByRole('button', { name: 'Trả về hàng chờ' }));

    expect(technicianApi.returnToWaiting).toHaveBeenCalledWith('detail-1', 'Máy siêu âm mất nguồn');
    expect(toast.info).toHaveBeenCalledWith(expect.stringContaining('Nguyễn Văn Bình'));
    expect(screen.queryByRole('dialog', { name: 'Trả chỉ định về hàng chờ' })).not.toBeInTheDocument();
  });

  it('lets the on-duty technician accept a handed-over service and completes it immediately to pending result', async () => {
    const waiting = { ...inProgress, status: 'WAITING', canComplete: false, canManageOrder: true };
    const other = {
      ...waiting, id: 'other-detail', orderId: 'other-order', patientName: 'Trần Văn Nam', canManageOrder: false,
    };
    const response = { canManage: true, hasOwnWork: true, items: [waiting, other] };
    technicianApi.getQueue.mockResolvedValue(response);
    technicianApi.acceptOrder.mockImplementation(async () => {
      technicianApi.getQueue.mockResolvedValue({
        ...response,
        items: [{ ...waiting, status: 'PENDING_RESULT', canComplete: false }, other],
      });
    });
    const user = userEvent.setup();
    renderPage();

    expect(within(await rowOf(other.patientName)).queryByRole('button', { name: 'Tiếp nhận' })).not.toBeInTheDocument();
    await user.click(within(await rowOf(waiting.patientName)).getByRole('button', { name: 'Tiếp nhận' }));

    expect(technicianApi.acceptOrder).toHaveBeenCalledWith(waiting.id);
    expect(technicianApi.completeOrder).toHaveBeenCalledWith(waiting.id);
  });

  it.each([
    [403, 'FORBIDDEN', PERFORM_MESSAGES.notPerformer],
    [409, 'PARACLINICAL_SERVICE_NOT_IN_PROGRESS', PERFORM_MESSAGES.notInProgress],
    [401, 'UNAUTHORIZED', PERFORM_MESSAGES.sessionExpired],
    [500, 'INTERNAL_ERROR', PERFORM_MESSAGES.systemError],
  ])('shows a Vietnamese message when confirming fails with %s', async (status, code, message) => {
    technicianApi.completeOrder.mockRejectedValue(new ApiError('raw backend text', { status, errorResponse: { code } }));
    const user = userEvent.setup();
    renderPage();

    await user.click(within(await rowOf('Nguyễn Văn Bình')).getByRole('button', { name: 'Đã thực hiện' }));

    expect(toast.error).toHaveBeenCalledWith(message);
  });

  it('shows no perform actions for an order that is still waiting', async () => {
    technicianApi.getQueue.mockResolvedValue({
      items: [{ ...inProgress, status: 'WAITING' }], stats: { total: 1, waiting: 1, priority: 0, skipped: 0 }, canManage: true,
    });
    renderPage();

    const row = await rowOf('Nguyễn Văn Bình');
    expect(within(row).queryByRole('button', { name: 'Đã thực hiện' })).not.toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: 'Trả về' })).not.toBeInTheDocument();
  });

  it('renders unconfirmed patient in Tab 1 with dash STT, and switches to Tab 2 on check-in', async () => {
    const user = userEvent.setup();
    const unconfirmedPatient = {
      ...inProgress,
      id: 'detail-unconf-1',
      orderId: 'order-unconf-1',
      patientName: 'Lê Văn Dũng',
      patientId: 'BN00000003',
      queueNumber: null,
      isCheckedIn: false,
      status: 'WAITING',
    };
    const checkedInPatient = {
      ...unconfirmedPatient,
      queueNumber: 5,
      isCheckedIn: true,
    };

    technicianApi.getQueue
      .mockResolvedValueOnce({
        items: [unconfirmedPatient],
        stats: { total: 1, waiting: 1, priority: 0, skipped: 0 },
        canManage: true,
      })
      .mockResolvedValue({
        items: [checkedInPatient],
        stats: { total: 1, waiting: 1, priority: 0, skipped: 0 },
        canManage: true,
      });

    technicianApi.checkInOrder.mockResolvedValue({
      orderDetailId: 'detail-unconf-1',
      queueNumber: 5,
      checkedInAt: '10:45',
      roomServices: [checkedInPatient],
    });

    renderPage();

    // Tab 1 contains Lê Văn Dũng with status "Chờ có mặt"
    const row = await rowOf('Lê Văn Dũng');
    expect(within(row).getByText('Chờ có mặt')).toBeInTheDocument();
    const checkInBtn = within(row).getByRole('button', { name: 'Xác nhận có mặt' });
    expect(checkInBtn).toBeInTheDocument();

    // Click "Xác nhận có mặt"
    await user.click(checkInBtn);

    expect(technicianApi.checkInOrder).toHaveBeenCalledWith('detail-unconf-1');
    expect(toast.success).toHaveBeenCalledWith(expect.stringContaining('#05'), expect.anything());

    // Pop-up Phiếu số thứ tự xuất hiện
    expect(screen.getByRole('dialog', { name: 'Đã cấp số thứ tự thành công' })).toBeInTheDocument();
    expect(screen.getByText('PHIẾU SỐ THỨ TỰ CẬN LÂM SÀNG')).toBeInTheDocument();
    expect(screen.getByText('LÊ VĂN DŨNG')).toBeInTheDocument();

    // Đóng popup
    await user.click(screen.getByRole('button', { name: 'Đóng' }));
    expect(screen.queryByRole('dialog', { name: 'Đã cấp số thứ tự thành công' })).not.toBeInTheDocument();

    // Automatically switched to Tab 2 "Hàng đợi vào phòng"
    const activeTabButton = screen.getByRole('button', { name: /Hàng đợi vào phòng/i });
    expect(activeTabButton).toHaveClass('text-[#0d9488]');

    // In Tab 2, Lê Văn Dũng displays STT #05
    const rowTab2 = await rowOf('Lê Văn Dũng');
    expect(within(rowTab2).getByText('#05')).toBeInTheDocument();
    expect(within(rowTab2).queryByRole('button', { name: 'Gọi số' })).not.toBeInTheDocument();
    expect(within(rowTab2).getByRole('button', { name: 'Tiếp nhận' })).toBeInTheDocument();
  });

  it('groups multiple services for the same patient into 1 row with badge +X dịch vụ khác', async () => {
    const svc1 = {
      ...inProgress,
      id: 'detail-svc-1',
      orderId: 'order-multi',
      patientName: 'Nguyễn jafogawg hảgh',
      patientId: 'BN1062602',
      serviceName: 'Bộ đường huyết (Glucose, HbA1c)',
      status: 'WAITING',
      queueNumber: null,
      isCheckedIn: false,
    };
    const svc2 = {
      ...inProgress,
      id: 'detail-svc-2',
      orderId: 'order-multi',
      patientName: 'Nguyễn jafogawg hảgh',
      patientId: 'BN1062602',
      serviceName: 'Tổng phân tích nước tiểu',
      status: 'WAITING',
      queueNumber: null,
      isCheckedIn: false,
    };
    const svc3 = {
      ...inProgress,
      id: 'detail-svc-3',
      orderId: 'order-multi',
      patientName: 'Nguyễn jafogawg hảgh',
      patientId: 'BN1062602',
      serviceName: 'Bộ mỡ máu',
      status: 'WAITING',
      queueNumber: null,
      isCheckedIn: false,
    };

    technicianApi.getQueue.mockResolvedValue({
      items: [svc1, svc2, svc3],
      stats: { total: 3, waiting: 3, priority: 0, skipped: 0 },
      canManage: true,
    });

    renderPage();

    const row = await rowOf('Nguyễn jafogawg hảgh');
    // Only 1 row for Nguyễn jafogawg hảgh
    expect(screen.getAllByText('Nguyễn jafogawg hảgh')).toHaveLength(1);
    // Shows primary service name
    expect(within(row).getByText('Bộ đường huyết (Glucose, HbA1c)')).toBeInTheDocument();
    // Shows badge +2 dịch vụ khác
    expect(within(row).getByText('+2 dịch vụ khác')).toBeInTheDocument();
  });

  it('sorts Tab 2 Active Queue in ascending order of queueNumber (lower numbers first)', async () => {
    const patient4 = {
      ...inProgress,
      id: 'detail-p4',
      orderId: 'order-p4',
      patientName: 'Mai Thanh Hoàng',
      queueNumber: 4,
      isCheckedIn: true,
      status: 'WAITING',
    };
    const patient2 = {
      ...inProgress,
      id: 'detail-p2',
      orderId: 'order-p2',
      patientName: 'Nguyễn jafogawg hảgh',
      queueNumber: 2,
      isCheckedIn: true,
      status: 'WAITING',
    };

    // Even if backend sends patient4 first
    technicianApi.getQueue.mockResolvedValue({
      items: [patient4, patient2],
      stats: { total: 2, waiting: 2, priority: 0, skipped: 0 },
      canManage: true,
    });

    renderPage();

    await screen.findByText('Mai Thanh Hoàng');
    const rows = screen.getAllByRole('row').slice(1); // exclude header
    // Row 0 should be patient2 (#02), Row 1 should be patient4 (#04)
    expect(within(rows[0]).getByText('#02')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Nguyễn jafogawg hảgh')).toBeInTheDocument();
    expect(within(rows[1]).getByText('#04')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Mai Thanh Hoàng')).toBeInTheDocument();
  });

  it('keeps a patient whose services were all performed out of Tab 2 active queue', async () => {
    const performed = (id, serviceName) => ({
      ...inProgress, id, serviceName, status: 'PENDING_RESULT', queueNumber: 1, orderCode: 'CD0000000067',
    });
    technicianApi.getQueue.mockResolvedValue({
      items: [
        performed('detail-a', 'Bộ đường huyết (Glucose, HbA1c)'),
        performed('detail-b', 'Bộ mỡ máu'),
        performed('detail-c', 'Đo hoạt độ AST, ALT'),
      ],
      stats: { total: 3, waiting: 0, priority: 0, skipped: 0 },
      canManage: true,
    });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Hàng đợi vào phòng hiện đang trống.')).toBeInTheDocument();
    });
    expect(screen.queryByText('Nguyễn Văn Bình')).not.toBeInTheDocument();
  });

  it('accepts the waiting service of a row even when another service of the row was performed', async () => {
    technicianApi.getQueue.mockResolvedValue({
      items: [
        { ...inProgress, id: 'detail-done', status: 'PENDING_RESULT' },
        { ...inProgress, id: 'detail-next', status: 'WAITING', serviceName: 'Chụp X-quang ngực thẳng' },
      ],
      stats: { total: 2, waiting: 1, priority: 0, skipped: 0 },
      canManage: true,
    });
    technicianApi.acceptOrder.mockResolvedValue({});
    const user = userEvent.setup();
    renderPage();

    const row = await rowOf('Nguyễn Văn Bình');
    await user.click(within(row).getByRole('button', { name: /Tiếp nhận/ }));

    expect(technicianApi.acceptOrder).toHaveBeenCalledWith('detail-next');
    expect(technicianApi.completeOrder).toHaveBeenCalledWith('detail-next');
  });

  describe('Tab 3: Đã bỏ qua (Skipped Patients) & UC-46 Tiếp nhận lại', () => {
    const skipped1 = {
      ...inProgress,
      id: 'detail-skip-1',
      orderId: 'order-skip-1',
      patientId: 'BN-1062602',
      patientName: 'Nguyễn Văn An',
      insuranceType: 'BHYT',
      insuranceNumber: 'DN47900000001',
      queueNumber: 3,
      status: 'SKIPPED',
      isSkipped: true,
      skippedAtTime: '08:15',
      skippedAt: '2026-11-13T08:15:00Z',
    };

    const skipped2 = {
      ...inProgress,
      id: 'detail-skip-2',
      orderId: 'order-skip-2',
      patientId: 'BN-2088899',
      patientName: 'Trần Thị Bích',
      insuranceType: 'SERVICE',
      insuranceNumber: null,
      queueNumber: 5,
      status: 'PENDING_RESULT',
      isSkipped: true,
      skippedAtTime: '09:30',
      skippedAt: '2026-11-13T09:30:00Z',
    };

    it('renders skipped patients sorted by skip timestamp ascending (oldest first)', async () => {
      // Backend returns skipped2 first, but skipped1 is older (08:15 vs 09:30)
      technicianApi.getQueue.mockResolvedValue({
        items: [skipped2, skipped1],
        stats: { total: 2, waiting: 0, priority: 0, skipped: 2 },
        canManage: true,
      });

      const user = userEvent.setup();
      renderPage();

      // Click Tab 3: Đã bỏ qua
      const skippedTab = await screen.findByRole('button', { name: /Đã bỏ qua/ });
      await user.click(skippedTab);

      await screen.findByText('Nguyễn Văn An');
      const rows = screen.getAllByRole('row').slice(1);
      expect(within(rows[0]).getByText('Nguyễn Văn An')).toBeInTheDocument();
      expect(within(rows[0]).getByText(/08:15/)).toBeInTheDocument();
      expect(within(rows[1]).getByText('Trần Thị Bích')).toBeInTheDocument();
      expect(within(rows[1]).getByText(/09:30/)).toBeInTheDocument();
    });

    it('searches skipped patients by name without diacritics and by health insurance number', async () => {
      technicianApi.getQueue.mockResolvedValue({
        items: [skipped1, skipped2],
        stats: { total: 2, waiting: 0, priority: 0, skipped: 2 },
        canManage: true,
      });

      const user = userEvent.setup();
      renderPage();

      await user.click(await screen.findByRole('button', { name: /Đã bỏ qua/ }));
      await screen.findByText('Nguyễn Văn An');

      const searchInput = screen.getByPlaceholderText(/Tìm theo Tên bệnh nhân/);

      // Search "nguyen van an" (no diacritics)
      await user.clear(searchInput);
      await user.type(searchInput, 'nguyen van an');
      expect(screen.getByText('Nguyễn Văn An')).toBeInTheDocument();
      expect(screen.queryByText('Trần Thị Bích')).not.toBeInTheDocument();

      // Search by insurance number
      await user.clear(searchInput);
      await user.type(searchInput, 'DN47900000001');
      expect(screen.getByText('Nguyễn Văn An')).toBeInTheDocument();
      expect(screen.queryByText('Trần Thị Bích')).not.toBeInTheDocument();
    });

    it('filters by exam type (BHYT / Dịch vụ) and resets correctly', async () => {
      technicianApi.getQueue.mockResolvedValue({
        items: [skipped1, skipped2],
        stats: { total: 2, waiting: 0, priority: 0, skipped: 2 },
        canManage: true,
      });

      const user = userEvent.setup();
      renderPage();

      await user.click(await screen.findByRole('button', { name: /Đã bỏ qua/ }));
      await screen.findByText('Nguyễn Văn An');

      // Filter by exam type: Dịch vụ
      const insuranceSelect = screen.getByDisplayValue('Tất cả loại khám');
      await user.selectOptions(insuranceSelect, 'SERVICE');
      expect(screen.queryByText('Nguyễn Văn An')).not.toBeInTheDocument();
      expect(screen.getByText('Trần Thị Bích')).toBeInTheDocument();

      // Click "Xóa bộ lọc"
      const resetBtn = screen.getByRole('button', { name: 'Xóa bộ lọc' });
      await user.click(resetBtn);
      expect(screen.getByText('Nguyễn Văn An')).toBeInTheDocument();
      expect(screen.getByText('Trần Thị Bích')).toBeInTheDocument();
    });

    it('displays MSG92 when no one is skipped, and MSG01 when search has no results', async () => {
      // 1. No one skipped in room
      technicianApi.getQueue.mockResolvedValue({
        items: [],
        stats: { total: 0, waiting: 0, priority: 0, skipped: 0 },
        canManage: true,
      });

      const user = userEvent.setup();
      renderPage();

      await user.click(await screen.findByRole('button', { name: /Đã bỏ qua/ }));
      expect(await screen.findByText('Chưa có dữ liệu để hiển thị.')).toBeInTheDocument();

      // 2. Someone skipped but search doesn't match
      technicianApi.getQueue.mockResolvedValue({
        items: [skipped1],
        stats: { total: 1, waiting: 0, priority: 0, skipped: 1 },
        canManage: true,
      });
      // Switch back and forth to re-fetch
      await user.click(screen.getByRole('button', { name: /Hàng đợi vào phòng/ }));
      await user.click(screen.getByRole('button', { name: /Đã bỏ qua/ }));

      await screen.findByText('Nguyễn Văn An');
      const searchInput = screen.getByPlaceholderText(/Tìm theo Tên bệnh nhân/);
      await user.type(searchInput, 'Không tồn tại 9999');
      expect(await screen.findByText('Không tìm thấy kết quả.')).toBeInTheDocument();
    });

    it('disables "Tiếp nhận lại" button with MSG48 when canManage is false', async () => {
      technicianApi.getQueue.mockResolvedValue({
        items: [skipped1],
        stats: { total: 1, waiting: 0, priority: 0, skipped: 1 },
        canManage: false,
      });

      const user = userEvent.setup();
      renderPage();

      await user.click(await screen.findByRole('button', { name: /Đã bỏ qua/ }));
      await screen.findByText('Nguyễn Văn An');

      const reacceptBtn = screen.getByRole('button', { name: 'Tiếp nhận lại' });
      expect(reacceptBtn).toBeDisabled();
      expect(reacceptBtn).toHaveAttribute('title', 'Bạn không được phân công phòng khám này hoặc đang ngoài ca trực.');
    });

    it('opens ReacceptSkippedModal (UC-46) and restores patient upon confirmation', async () => {
      technicianApi.getQueue.mockResolvedValue({
        items: [skipped1],
        stats: { total: 1, waiting: 0, priority: 0, skipped: 1 },
        canManage: true,
      });
      technicianApi.unskipOrder.mockResolvedValue({});

      const user = userEvent.setup();
      renderPage();

      await user.click(await screen.findByRole('button', { name: /Đã bỏ qua/ }));
      await screen.findByText('Nguyễn Văn An');

      // Click "Tiếp nhận lại"
      await user.click(screen.getByRole('button', { name: 'Tiếp nhận lại' }));

      // Modal appears
      const modal = await screen.findByRole('dialog', { name: /Tiếp nhận lại bệnh nhân/ });
      expect(modal).toBeInTheDocument();
      expect(within(modal).getByText('Nguyễn Văn An')).toBeInTheDocument();
      expect(within(modal).getByText('#03')).toBeInTheDocument();
      expect(within(modal).getByText(/Chờ thực hiện cận lâm sàng/)).toBeInTheDocument();

      // Type note and confirm
      const noteInput = within(modal).getByLabelText(/Ghi chú tiếp nhận lại/);
      await user.type(noteInput, 'BN đã quay lại');

      await user.click(within(modal).getByRole('button', { name: /Xác nhận tiếp nhận lại/ }));

      expect(technicianApi.unskipOrder).toHaveBeenCalledWith('detail-skip-1');
      expect(toast.success).toHaveBeenCalledWith(
        expect.stringContaining('Đã tiếp nhận lại bệnh nhân Nguyễn Văn An (Số #03) vào hàng đợi! Ghi chú: BN đã quay lại'),
        expect.any(Object)
      );
    });
  });
});
