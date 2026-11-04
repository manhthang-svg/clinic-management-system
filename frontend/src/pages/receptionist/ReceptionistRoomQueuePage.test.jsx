import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import ReceptionistRoomQueuePage from './ReceptionistRoomQueuePage';

const NO_ROOM_MESSAGE = 'Hiện không có phòng khám nào có thể tiếp nhận bệnh nhân. Vui lòng báo quản trị viên.';

const room = (overrides) => ({
    roomId: 'room-1',
    roomCode: 'PK01',
    roomName: 'Phòng khám Nội 1',
    location: 'Tầng 1',
    departmentId: 'dept-1',
    departmentName: 'Khoa Nội',
    doctorNames: ['BS Nguyễn Văn An'],
    hasDoctor: true,
    waitingPaymentCount: 0,
    waitingCount: 0,
    inExaminationCount: 0,
    waitingParaclinicalCount: 0,
    waitingConclusionCount: 0,
    skippedCount: 0,
    ...overrides,
});

function deferred() {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return { promise, resolve };
}

afterEach(() => {
    vi.useRealTimers();
});

describe('ReceptionistRoomQueuePage', () => {
    it('shows queue counts per examination room and totals', async () => {
        const visitApi = {
            getQueueStatus: vi.fn().mockResolvedValue([
                room({ waitingCount: 4, inExaminationCount: 1, skippedCount: 2 }),
                room({ roomId: 'room-2', roomCode: 'PK02', roomName: 'Phòng khám Nội 2', waitingCount: 1, inExaminationCount: 1 }),
            ]),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        const busyRow = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        const cells = within(busyRow).getAllByRole('cell');
        expect(cells.slice(1).map((cell) => cell.textContent)).toEqual(['0', '4', '1', '0', '0', '2']);
        expect(within(busyRow).getByText('PK01 · Tầng 1')).toBeInTheDocument();
        expect(within(busyRow).getByText('Đông nhất')).toBeInTheDocument();
        expect(within(screen.getByText('Phòng khám Nội 2').closest('tr')).queryByText('Đông nhất')).not.toBeInTheDocument();
        expect(screen.getByText('Phòng khám tiếp nhận được').nextSibling).toHaveTextContent('2');
        expect(screen.getByText('Tổng bệnh nhân chờ khám').nextSibling).toHaveTextContent('5');
        expect(screen.getByText('Đang khám', { selector: 'p' }).nextSibling).toHaveTextContent('2');
    });

    it('shows a loading state until the first response arrives', async () => {
        const pending = deferred();
        const visitApi = { getQueueStatus: vi.fn().mockReturnValue(pending.promise) };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        expect(screen.getByText('Đang tải tình trạng hàng đợi…')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Tải lại' })).toBeDisabled();

        await act(async () => pending.resolve([room()]));

        expect(await screen.findByText('Phòng khám Nội 1')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Tải lại' })).toBeEnabled();
    });

    it('shows an empty state when no examination room is active', async () => {
        const visitApi = { getQueueStatus: vi.fn().mockResolvedValue([]) };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        expect(await screen.findByText('Chưa có phòng khám đang hoạt động')).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent(NO_ROOM_MESSAGE);
    });

    it('groups rooms by department and shows doctors and the waiting-for-payment count', async () => {
        const visitApi = {
            getQueueStatus: vi.fn().mockResolvedValue([
                room({ waitingPaymentCount: 3 }),
                room({
                    roomId: 'room-2', roomCode: 'PK05', roomName: 'Phòng khám Tim 1',
                    departmentId: 'dept-2', departmentName: 'Khoa Tim mạch', doctorNames: ['ThS.BS Trần Bình'],
                }),
            ]),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        const internalRow = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        expect(within(internalRow).getAllByRole('cell')[1]).toHaveTextContent('3');
        expect(within(internalRow).getByText('Bác sĩ: BS Nguyễn Văn An')).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Chờ thanh toán' })).toBeInTheDocument();
        const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
        expect(headers).toContain('Khoa Nội');
        expect(headers).toContain('Khoa Tim mạch');
        expect(screen.getByText('Bác sĩ: ThS.BS Trần Bình')).toBeInTheDocument();
    });

    it('greys out a room without a doctor and warns when no room can receive patients', async () => {
        const visitApi = {
            getQueueStatus: vi.fn().mockResolvedValue([
                room({ doctorNames: [], hasDoctor: false, waitingCount: 9 }),
            ]),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        const row = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        expect(row).toHaveClass('opacity-60');
        expect(within(row).getByText('Chưa có bác sĩ')).toBeInTheDocument();
        expect(within(row).queryByText('Đông nhất')).not.toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent(NO_ROOM_MESSAGE);
        expect(screen.getByText('Phòng khám tiếp nhận được').nextSibling).toHaveTextContent('0');
    });

    it('opens the waiting list of a room and goes back to the overview', async () => {
        const user = userEvent.setup();
        const visitApi = {
            getQueueStatus: vi.fn().mockResolvedValue([room({ waitingCount: 2 })]),
            getQueue: vi.fn().mockResolvedValue([
                {
                    id: 'visit-1', queueNumber: 1, patientName: 'Lê Văn Cường', patientCode: 'BN0001',
                    isPriority: true, thoigianvaovien: '2026-11-05T01:30:00Z', status: 'WAITING', isSkipped: false,
                },
                {
                    id: 'visit-2', queueNumber: 2, patientName: 'Phạm Thị Dung', patientCode: 'BN0002',
                    isPriority: false, thoigianvaovien: '2026-11-05T01:40:00Z', status: 'WAITING', isSkipped: true,
                },
            ]),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);
        await user.click(await screen.findByRole('button', { name: 'Phòng khám Nội 1' }));

        const detail = await screen.findByRole('region', { name: 'Hàng đợi Phòng khám Nội 1' });
        expect(visitApi.getQueue).toHaveBeenCalledWith({ roomId: 'room-1' }, expect.any(AbortSignal));
        const first = (await within(detail).findByText('Lê Văn Cường')).closest('tr');
        expect(within(first).getByText('Ưu tiên')).toBeInTheDocument();
        expect(within(first).getByText('Chờ khám')).toBeInTheDocument();
        expect(within(within(detail).getByText('Phạm Thị Dung').closest('tr')).getByText('Bỏ lượt')).toBeInTheDocument();

        await user.click(within(detail).getByRole('button', { name: 'Quay lại' }));

        expect(screen.queryByRole('region', { name: 'Hàng đợi Phòng khám Nội 1' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Phòng khám Nội 1' })).toBeInTheDocument();
    });

    it('reloads on demand and disables the button while loading', async () => {
        const user = userEvent.setup();
        const second = deferred();
        const visitApi = {
            getQueueStatus: vi.fn()
                .mockResolvedValueOnce([room({ waitingCount: 1 })])
                .mockReturnValueOnce(second.promise),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);
        await screen.findByText('Phòng khám Nội 1');

        await user.click(screen.getByRole('button', { name: 'Tải lại' }));

        const busy = screen.getByRole('button', { name: 'Đang tải…' });
        expect(busy).toBeDisabled();
        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(2);

        await act(async () => second.resolve([room({ waitingCount: 6 })]));

        expect(screen.getByRole('button', { name: 'Tải lại' })).toBeEnabled();
        expect(screen.getByText('Tổng bệnh nhân chờ khám').nextSibling).toHaveTextContent('6');
    });

    it('refreshes automatically every 10 seconds', async () => {
        vi.useFakeTimers();
        const visitApi = { getQueueStatus: vi.fn().mockResolvedValue([room()]) };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);
        await act(async () => vi.advanceTimersByTimeAsync(0));
        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(1);

        await act(async () => vi.advanceTimersByTimeAsync(10_000));
        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(2);
    });

    it('stops refreshing after leaving the page', async () => {
        vi.useFakeTimers();
        const visitApi = { getQueueStatus: vi.fn().mockResolvedValue([room()]) };

        const { unmount } = render(<ReceptionistRoomQueuePage visitApi={visitApi} />);
        await act(async () => vi.advanceTimersByTimeAsync(0));
        unmount();
        await vi.advanceTimersByTimeAsync(30_000);

        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(1);
    });

    it.each([
        [403, 'Bạn không có quyền xem tình trạng hàng đợi phòng khám.'],
        [500, 'Không thể tải tình trạng hàng đợi do lỗi hệ thống. Vui lòng thử lại sau.'],
        [undefined, 'Không thể tải tình trạng hàng đợi. Vui lòng thử lại.'],
    ])('maps a load failure with status %s to a Vietnamese message', async (status, message) => {
        const visitApi = {
            getQueueStatus: vi.fn().mockRejectedValue(new ApiError('Backend message', { status })),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(message);
        expect(screen.queryByText('Backend message')).not.toBeInTheDocument();
        expect(screen.getByText('Chưa tải được dữ liệu')).toBeInTheDocument();
    });

    it('keeps the last counts and says they are stale when a refresh fails', async () => {
        const user = userEvent.setup();
        const visitApi = {
            getQueueStatus: vi.fn()
                .mockResolvedValueOnce([room({ waitingCount: 3 })])
                .mockRejectedValueOnce(new ApiError('down', { status: 503 })),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);
        await screen.findByText('Phòng khám Nội 1');

        await user.click(screen.getByRole('button', { name: 'Tải lại' }));

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent('lỗi hệ thống');
        expect(alert).toHaveTextContent('Số liệu bên dưới là lần cập nhật lúc');
        expect(screen.getByText('Tổng bệnh nhân chờ khám').nextSibling).toHaveTextContent('3');
    });
});
