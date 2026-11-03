import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
            acceptSkippedVisit: vi.fn(),
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

    it('shows the simplified columns, combined notes, and direct attendance actions', async () => {
        const visit = {
            id: 'visit-1',
            patientName: 'Nguyễn Thị Mai',
            patientCode: 'BN001',
            patientDob: '1980-01-01',
            patientGender: 'Nữ',
            queueNumber: 12,
            isPriority: true,
            priorityReason: 'ELDERLY',
            doituongbnLoai: 'BHYT',
            lydovaovien: 'Đau đầu',
            thoigianvaovien: '2026-11-02T08:00:00Z',
            status: 'WAITING',
            isSkipped: false,
            sourceRoomName: 'Phòng khám Nội 2',
        };
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                waitingCount: 1,
                visits: { content: [visit], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } },
            })),
            startExamination: vi.fn().mockResolvedValue({}),
            skipVisit: vi.fn().mockResolvedValue({}),
            acceptSkippedVisit: vi.fn().mockResolvedValue({}),
        };

        render(<DoctorWaitingListPage visitApi={visitApi} loadProfile={vi.fn().mockResolvedValue({})} />);

        expect(await screen.findByText('Nguyễn Thị Mai')).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Ghi chú' })).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'STT' })).toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Số thứ tự' })).not.toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Ưu tiên' })).not.toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Lý do khám' })).not.toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Nguồn / Phòng chuyển đến' })).not.toBeInTheDocument();
        expect(screen.getByText('Ưu tiên: Người cao tuổi')).toBeInTheDocument();
        expect(screen.getByText('Chuyển từ: Phòng khám Nội 2')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }));
        await waitFor(() => expect(visitApi.startExamination).toHaveBeenCalledWith('visit-1'));
    });

    it('accepts a skipped patient without showing the skip action', async () => {
        const visit = {
            id: 'visit-skipped',
            patientName: 'Trần Văn Bình',
            queueNumber: 7,
            status: 'WAITING',
            isSkipped: true,
            note: 'Ưu tiên: Người cao tuổi',
        };
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                skippedCount: 1,
                visits: { content: [visit], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn().mockResolvedValue({}),
        };

        render(
            <DoctorWaitingListPage
                visitApi={visitApi}
                loadProfile={vi.fn().mockResolvedValue({})}
                initialTab="skipped"
            />,
        );

        expect(await screen.findByText('Trần Văn Bình')).toBeInTheDocument();
        expect(screen.getByText('Ưu tiên: Người cao tuổi')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Vắng' })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }));
        await waitFor(() => expect(visitApi.acceptSkippedVisit).toHaveBeenCalledWith('visit-skipped'));
    });
});
