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
  doctorRoom: 'PK Nội 1', orderTime: '08:00', status: 'IN_PROGRESS', isSkipped: false, allOrdersToday: [],
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
});
