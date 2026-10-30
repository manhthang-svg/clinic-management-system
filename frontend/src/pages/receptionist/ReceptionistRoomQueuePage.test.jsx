import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

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

describe('ReceptionistRoomQueuePage', () => {
    it('shows queue counts per examination room and totals', async () => {
        const visitApi = {
            getQueueStatus: vi.fn().mockResolvedValue([
                room({ waitingCount: 4, inExaminationCount: 1, skippedCount: 2 }),
                room({ roomId: 'room-2', roomCode: 'PK02', roomName: 'Phòng khám Nội 2', waitingCount: 1 }),
            ]),
        };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        const busyRow = (await screen.findByText('Phòng khám Nội 1')).closest('tr');
        const cells = within(busyRow).getAllByRole('cell');
        expect(cells.slice(1).map((cell) => cell.textContent)).toEqual(['4', '1', '0', '0', '2']);
        expect(within(busyRow).getByText('Đông nhất')).toBeInTheDocument();
        expect(screen.getByText('Tổng bệnh nhân chờ khám').nextSibling).toHaveTextContent('5');
    });

    it('reports a load failure', async () => {
        const visitApi = { getQueueStatus: vi.fn().mockRejectedValue(new Error('down')) };

        render(<ReceptionistRoomQueuePage visitApi={visitApi} />);

        expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải tình trạng hàng đợi');
    });
});
