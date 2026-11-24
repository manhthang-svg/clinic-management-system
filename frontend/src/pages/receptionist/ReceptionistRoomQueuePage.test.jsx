import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { MemoryRouter, Route, Routes } from 'react-router';

import ReceptionistRoomQueuePage from './ReceptionistRoomQueuePage';
import { pageOf } from '../../test/fakeStatisticsServer';

// lam viec cua backend moi tren du lieu day du cua test: loc roomId + cat trang
function asServer(visitApi) {
    return {
        ...visitApi,
        getQueueStatusPage: vi.fn(async (params = {}, signal) => {
            const list = await visitApi.getQueueStatus(signal);
            const rooms = params.roomId ? list.filter((item) => item.roomId === params.roomId) : list;
            return { rooms: pageOf(rooms, params), receivableRooms: list.filter((item) => item.hasDoctor).length };
        }),
        getQueuePage: vi.fn(async (params = {}, signal) => (
            pageOf(await visitApi.getQueue({ roomId: params.roomId }, signal), params))),
    };
}

function renderPage(visitApi, path = '/receptionist/room-queue') {
    const server = asServer(visitApi);
    const page = <ReceptionistRoomQueuePage visitApi={server} />;
    const result = render(
        <MemoryRouter initialEntries={[path]}>
            <Routes>
                <Route path="/receptionist/room-queue" element={page} />
                <Route path="/receptionist/room-queue/:roomId" element={page} />
            </Routes>
        </MemoryRouter>,
    );
    return { ...result, server };
}

const NO_ROOM_MESSAGE = 'Không có phòng khám nào đang hoạt động có thể tiếp nhận bệnh nhân. Vui lòng báo quản trị viên.';
const MSG53 = 'Phòng khám đã ngừng hoạt động hoặc không có bác sĩ làm việc trong ngày nên không thể tiếp nhận bệnh nhân.';

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

/** The "Bác sĩ còn phải khám" cell, the last one of the room's row. */
function totalOf(roomName) {
    return within(screen.getByText(roomName).closest('tr')).getAllByRole('cell').at(-1).textContent;
}

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

        renderPage(visitApi);

        const busyRow = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        const cells = within(busyRow).getAllByRole('cell');
        expect(cells.slice(1).map((cell) => cell.textContent)).toEqual(['0', '4', '1', '0', '0', '2', '7']);
        expect(within(busyRow).getByText('PK01 · Tầng 1')).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Bác sĩ còn phải khám' })).toBeInTheDocument();
        expect(totalOf('Phòng khám Nội 2')).toBe('2');
        expect(screen.queryByText('Phòng khám tiếp nhận được')).not.toBeInTheDocument();
    });

    it('shows a loading state until the first response arrives', async () => {
        const pending = deferred();
        const visitApi = { getQueueStatus: vi.fn().mockReturnValue(pending.promise) };

        renderPage(visitApi);

        expect(screen.getByText('Đang tải tình trạng hàng đợi…')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Tải lại' })).toBeDisabled();

        await act(async () => pending.resolve([room()]));

        expect(await screen.findByText('Phòng khám Nội 1')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Tải lại' })).toBeEnabled();
    });

    it('shows only MSG102 when no examination room can receive patients (EF-01)', async () => {
        const visitApi = { getQueueStatus: vi.fn().mockResolvedValue([]) };

        renderPage(visitApi);

        expect(await screen.findByRole('alert')).toHaveTextContent(NO_ROOM_MESSAGE);
        expect(screen.queryByText('Không có phòng khám nào có bác sĩ trực trong ca hiện tại')).not.toBeInTheDocument();
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

        renderPage(visitApi);

        const internalRow = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        expect(within(internalRow).getAllByRole('cell')[1]).toHaveTextContent('3');
        // Visits waiting for payment have no queue number yet: not counted for the doctor (UC-40 step 8).
        expect(totalOf('Phòng khám Nội 1')).toBe('0');
        expect(within(internalRow).getByText('Bác sĩ trực: BS Nguyễn Văn An')).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Chờ thanh toán' })).toBeInTheDocument();
        const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
        expect(headers).toContain('Khoa Nội');
        expect(headers).toContain('Khoa Tim mạch');
        expect(screen.getByText('Bác sĩ trực: ThS.BS Trần Bình')).toBeInTheDocument();
    });

    it('greys out a room without a doctor and warns when no room can receive patients', async () => {
        const visitApi = {
            getQueueStatus: vi.fn().mockResolvedValue([
                room({ doctorNames: [], hasDoctor: false, waitingCount: 9 }),
            ]),
        };

        renderPage(visitApi);

        const row = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        expect(row).toHaveClass('opacity-60');
        expect(within(row).getByText('Chưa có bác sĩ')).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent(NO_ROOM_MESSAGE);
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

        renderPage(visitApi);
        // A click anywhere on the row opens the room, not only on its name.
        const row = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        await user.click(within(row).getAllByRole('cell')[1]);

        const detail = await screen.findByRole('region', { name: 'Hàng đợi Phòng khám Nội 1' });
        expect(visitApi.getQueue).toHaveBeenCalledWith({ roomId: 'room-1' }, expect.any(AbortSignal));
        const first = (await within(detail).findByText('Lê Văn Cường')).closest('tr');
        expect(within(first).getByText('Ưu tiên')).toBeInTheDocument();
        expect(within(first).getByText('Chờ khám')).toBeInTheDocument();
        expect(within(within(detail).getByText('Phạm Thị Dung').closest('tr')).getByText('Bỏ lượt')).toBeInTheDocument();

        await user.click(within(detail).getByRole('button', { name: 'Quay lại' }));

        expect(screen.queryByRole('region', { name: 'Hàng đợi Phòng khám Nội 1' })).not.toBeInTheDocument();
        expect(screen.getByText('Phòng khám Nội 1')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Phòng khám Nội 1' })).not.toBeInTheDocument();
    });

    it('opens the waiting list straight from the room link', async () => {
        const visitApi = {
            getQueueStatus: vi.fn().mockResolvedValue([room()]),
            getQueue: vi.fn().mockResolvedValue([]),
        };

        renderPage(visitApi, '/receptionist/room-queue/room-1');

        expect(await screen.findByRole('region', { name: 'Hàng đợi Phòng khám Nội 1' })).toBeInTheDocument();
        expect(await screen.findByText('Chưa có dữ liệu để hiển thị.')).toBeInTheDocument();
    });

    it('pages the waiting list of a room, 10 visits per page', async () => {
        const user = userEvent.setup();
        const visits = Array.from({ length: 12 }, (_, index) => ({
            id: `visit-${index + 1}`, queueNumber: index + 1, patientName: `Bệnh nhân ${index + 1}`,
            patientCode: `BN${index + 1}`, isPriority: false, thoigianvaovien: '2026-11-05T01:30:00Z',
            status: 'WAITING', isSkipped: false,
        }));
        const { server } = renderPage({
            getQueueStatus: vi.fn().mockResolvedValue([room()]),
            getQueue: vi.fn().mockResolvedValue(visits),
        }, '/receptionist/room-queue/room-1');

        const detail = await screen.findByRole('region', { name: 'Hàng đợi Phòng khám Nội 1' });
        // mo phong tu link: server loc dung phong; danh sach cho lay tung trang 10 benh nhan
        expect(server.getQueueStatusPage).toHaveBeenCalledWith(
            { roomId: 'room-1', page: 0, size: 10 }, expect.any(AbortSignal));
        expect(await within(detail).findByText('Bệnh nhân 10')).toBeInTheDocument();
        expect(within(detail).queryByText('Bệnh nhân 11')).not.toBeInTheDocument();
        expect(within(detail).getByText('Hiển thị 1 – 10 / 12')).toBeInTheDocument();

        await user.click(within(detail).getByRole('button', { name: 'Trang 2' }));

        expect(await within(detail).findByText('Bệnh nhân 12')).toBeInTheDocument();
        expect(server.getQueuePage).toHaveBeenLastCalledWith(
            { roomId: 'room-1', page: 1, size: 10 }, expect.any(AbortSignal));
        expect(within(detail).queryByText('Bệnh nhân 1')).not.toBeInTheDocument();
        expect(within(detail).getByText('Hiển thị 11 – 12 / 12')).toBeInTheDocument();
        expect(within(detail).getByRole('button', { name: 'Trang sau' })).toBeDisabled();
    });

    it.each([
        ['ROOM_NOT_AVAILABLE', 409, MSG53],
        ['ROOM_NOT_FOUND', 404, 'Không tìm thấy phòng khám.'],
    ])('shows the reason a linked room cannot be viewed (%s, AF-03)', async (code, status, message) => {
        const failure = new ApiError('x', { status, errorResponse: { code, message: 'x' } });
        renderPage({
            getQueueStatus: vi.fn().mockResolvedValue([room()]),
            getQueue: vi.fn().mockRejectedValue(failure),
        }, '/receptionist/room-queue/other-room');

        expect(await screen.findByRole('alert')).toHaveTextContent(message);
        expect(screen.getByRole('button', { name: 'Quay lại' })).toBeInTheDocument();
    });

    it('reloads on demand and disables the button while loading', async () => {
        const user = userEvent.setup();
        const second = deferred();
        const visitApi = {
            getQueueStatus: vi.fn()
                .mockResolvedValueOnce([room({ waitingCount: 1 })])
                .mockReturnValueOnce(second.promise),
        };

        renderPage(visitApi);
        await screen.findByText('Phòng khám Nội 1');

        await user.click(screen.getByRole('button', { name: 'Tải lại' }));

        const busy = screen.getByRole('button', { name: 'Đang tải…' });
        expect(busy).toBeDisabled();
        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(2);

        await act(async () => second.resolve([room({ waitingCount: 6 })]));

        expect(screen.getByRole('button', { name: 'Tải lại' })).toBeEnabled();
        expect(totalOf('Phòng khám Nội 1')).toBe('6');
    });

    it('refreshes automatically every 10 seconds', async () => {
        vi.useFakeTimers();
        const visitApi = { getQueueStatus: vi.fn().mockResolvedValue([room()]) };

        renderPage(visitApi);
        await act(async () => vi.advanceTimersByTimeAsync(0));
        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(1);

        await act(async () => vi.advanceTimersByTimeAsync(10_000));
        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(2);
    });

    it('stops refreshing after leaving the page', async () => {
        vi.useFakeTimers();
        const visitApi = { getQueueStatus: vi.fn().mockResolvedValue([room()]) };

        const { unmount } = renderPage(visitApi);
        await act(async () => vi.advanceTimersByTimeAsync(0));
        unmount();
        await vi.advanceTimersByTimeAsync(30_000);

        expect(visitApi.getQueueStatus).toHaveBeenCalledTimes(1);
    });

    it.each([
        [403, 'Bạn không có quyền thực hiện thao tác này.'],
        [500, 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.'],
        [undefined, 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.'],
    ])('maps a load failure with status %s to a Vietnamese message', async (status, message) => {
        const visitApi = {
            getQueueStatus: vi.fn().mockRejectedValue(new ApiError('Backend message', { status })),
        };

        renderPage(visitApi);

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

        renderPage(visitApi);
        await screen.findByText('Phòng khám Nội 1');

        await user.click(screen.getByRole('button', { name: 'Tải lại' }));

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
        expect(alert).toHaveTextContent('Số liệu bên dưới là lần cập nhật lúc');
        expect(totalOf('Phòng khám Nội 1')).toBe('3');
    });

    it('shows every room, 10 per page', async () => {
        const user = userEvent.setup();
        const rooms = Array.from({ length: 15 }, (_, i) => room({
            roomId: `room-${i + 1}`, roomCode: `PK${i + 1}`, roomName: `Phòng số ${i + 1}`,
        }));
        const { server } = renderPage({ getQueueStatus: vi.fn().mockResolvedValue(rooms) });

        expect(await screen.findByText('Phòng số 10')).toBeInTheDocument();
        // trinh duyet chi xin 1 trang 10 phong
        expect(server.getQueueStatusPage).toHaveBeenCalledWith({ page: 0, size: 10 }, expect.any(AbortSignal));
        expect(screen.queryByText('Phòng số 11')).not.toBeInTheDocument();
        expect(screen.getByText('Hiển thị 1 – 10 / 15')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Trang sau' }));

        expect(await screen.findByText('Phòng số 15')).toBeInTheDocument();
        expect(server.getQueueStatusPage).toHaveBeenLastCalledWith({ page: 1, size: 10 }, expect.any(AbortSignal));
        expect(screen.queryByText('Phòng số 1')).not.toBeInTheDocument();
        // tieu de khoa van hien tren trang 2
        expect(screen.getByRole('columnheader', { name: 'Khoa Nội' })).toBeInTheDocument();
    });
});
