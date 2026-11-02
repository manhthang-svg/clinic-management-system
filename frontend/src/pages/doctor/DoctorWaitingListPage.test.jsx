import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DoctorWaitingListPage from './DoctorWaitingListPage';

function queueResponse(overrides = {}) {
    return {
        roomId: '0e85df46-b033-4d14-8e1a-cb476b83ee44',
        roomCode: 'PK01',
        roomName: 'Phòng khám Nội 1',
        doctorName: 'BS. Nguyễn Văn An',
        onDuty: true,
        refreshedAt: '2026-11-02T08:00:00Z',
        waitingCount: 3,
        inExaminationCount: 1,
        waitingConclusionCount: 2,
        skippedCount: 1,
        visits: {
            content: [],
            page: { number: 0, size: 10, totalElements: 3, totalPages: 1 },
        },
        ...overrides,
    };
}

describe('DoctorWaitingListPage backend integration', () => {
    it('loads the authenticated doctor queue with server-side tab and pagination', async () => {
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse()),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
        };

        render(
            <DoctorWaitingListPage
                visitApi={visitApi}
                loadProfile={vi.fn().mockResolvedValue({ fullName: 'Tên từ hồ sơ' })}
            />,
        );

        await waitFor(() => expect(visitApi.getDoctorQueue).toHaveBeenCalledWith({
            tab: 'WAITING',
            keyword: '',
            priority: undefined,
            visitType: 'ALL',
            page: 0,
            size: 10,
        }, expect.any(AbortSignal)));

        expect(screen.getAllByText('Phòng khám Nội 1')).toHaveLength(2);
        expect(screen.getByText('BS. Nguyễn Văn An')).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Chờ khám (3)' })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Chờ kết luận (2)' })).toBeInTheDocument();
        expect(screen.getByText('Hiển thị 1 - 3 / 3')).toBeInTheDocument();
    });
});
