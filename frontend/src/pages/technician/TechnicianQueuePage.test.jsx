import { render, screen, within } from '@testing-library/react';
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
  doctorRoom: 'PK Nội 1', orderTime: '08:00', status: 'IN_PROGRESS', isSkipped: false, isCheckedIn: true, allOrdersToday: [],
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
    expect(toast.success).toHaveBeenCalledWith(PERFORM_MESSAGES.performed, expect.objectContaining({
      action: expect.objectContaining({ label: 'Nhập kết quả' }),
    }));
  });

  it('opens the result form right away when the result can be entered now', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(within(await rowOf('Nguyễn Văn Bình')).getByRole('button', { name: 'Nhập KQ' }));

    expect(await screen.findByText('Màn nhập kết quả')).toBeInTheDocument();
    expect(technicianApi.completeOrder).toHaveBeenCalledWith('detail-1');
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
    expect(toast.info).toHaveBeenCalledWith(PERFORM_MESSAGES.returned);
    expect(screen.queryByRole('dialog', { name: 'Trả chỉ định về hàng chờ' })).not.toBeInTheDocument();
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

  it('keeps a patient whose services were all performed out of "Tiếp nhận" and offers to enter the result', async () => {
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
    const user = userEvent.setup();
    renderPage();

    const row = await rowOf('Nguyễn Văn Bình');
    expect(within(row).getByText('Chờ nhập kết quả')).toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: /Tiếp nhận/ })).not.toBeInTheDocument();
    expect(within(row).queryByText('Đang chờ gọi')).not.toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: 'Nhập KQ' }));
    expect(await screen.findByText('Màn nhập kết quả')).toBeInTheDocument();
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
  });
});
