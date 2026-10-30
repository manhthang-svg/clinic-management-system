import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import ReceptionistRoomQueuePage from './ReceptionistRoomQueuePage';

const room = (overrides) => ({
    roomId: 'room-1',
    roomCode: 'PK01',
    roomName: 'Phòng khám Nội 1',
    location: 'Tầng 1',
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
        expect(cells.slice(1).map((cell) => cell.textContent)).toEqual(['4', '1', '0', '0', '2']);
        expect(within(busyRow).getByText('PK01 · Tầng 1')).toBeInTheDocument();
        expect(within(busyRow).getByText('Đông nhất')).toBeInTheDocument();
        expect(within(screen.getByText('Phòng khám Nội 2').closest('tr')).queryByText('Đông nhất')).not.toBeInTheDocument();
        expect(screen.getByText('Phòng khám đang hoạt động').nextSibling).toHaveTextContent('2');
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
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
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
