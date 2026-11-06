import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import DoctorWaitingListPage from './DoctorWaitingListPage';

function renderPage(props = {}) {
    return render(<MemoryRouter><DoctorWaitingListPage {...props} /></MemoryRouter>);
}

function LocationProbe() {
    const location = useLocation();
    return <div data-testid="location">{location.pathname}|{location.state?.visitId}|{location.state?.visit?.patientName}</div>;
}

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

        renderPage({
            visitApi,
            loadProfile: vi.fn().mockResolvedValue({ fullName: 'Tên từ hồ sơ' }),
        });

        await waitFor(() => expect(visitApi.getDoctorQueue).toHaveBeenCalledWith({
            tab: 'WAITING',
            keyword: '',
            priority: undefined,
            visitType: undefined,
            interruptedStage: undefined,
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

        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}) });

        expect(await screen.findByText('Nguyễn Thị Mai')).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Ghi chú' })).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'STT' })).toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Queue number' })).not.toBeInTheDocument();
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

        renderPage({
            visitApi,
            loadProfile: vi.fn().mockResolvedValue({}),
            initialTab: 'skipped',
        });

        expect(await screen.findByText('Trần Văn Bình')).toBeInTheDocument();
        expect(screen.getByText('Ưu tiên: Người cao tuổi')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Vắng' })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Tiếp nhận lại' }));
        await waitFor(() => expect(visitApi.acceptSkippedVisit).toHaveBeenCalledWith('visit-skipped'));
    });

    it('keeps pagination compact when the queue has many pages', async () => {
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                visits: { content: [], page: { number: 0, size: 10, totalElements: 200, totalPages: 20 } },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn(),
        };

        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}) });

        expect(await screen.findByRole('button', { name: 'Trang 20' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Trang 1' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Trang 10' })).not.toBeInTheDocument();
    });

    it('disables attendance actions outside the assigned shift', async () => {
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                onDuty: false,
                visits: {
                    content: [{ id: 'visit-1', patientName: 'Nguyễn Văn An', queueNumber: 1, status: 'WAITING', isSkipped: false }],
                    page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
                },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn(),
        };

        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}) });

        expect(await screen.findByText(/Bạn đang ngoài ca trực/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Vắng mặt' })).toBeDisabled();
    });

    it('filters skipped visits by their interrupted stage and shows the retained stage', async () => {
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                skippedCount: 1,
                visits: {
                    content: [{ id: 'visit-2', patientName: 'Trần Thị Bình', queueNumber: 2, status: 'WAITING_CONCLUSION', isSkipped: true }],
                    page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
                },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn(),
        };

        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}), initialTab: 'skipped' });
        expect(await screen.findByText('Vắng mặt · Chờ kết luận')).toBeInTheDocument();

        fireEvent.change(screen.getByLabelText('Lọc giai đoạn bị gián đoạn'), {
            target: { value: 'WAITING_CONCLUSION' },
        });
        await waitFor(() => expect(visitApi.getDoctorQueue).toHaveBeenLastCalledWith(
            expect.objectContaining({ tab: 'SKIPPED', interruptedStage: 'WAITING_CONCLUSION' }),
            expect.any(AbortSignal),
        ));
    });

    it('opens the selected patient clinical examination from the in-examination row', async () => {
        const visit = {
            id: 'visit-in-examination',
            patientName: 'Nguyễn Minh Khôi',
            patientCode: 'BN043',
            queueNumber: 1,
            status: 'IN_EXAMINATION',
            isSkipped: false,
        };
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                inExaminationCount: 1,
                visits: { content: [visit], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn(),
        };

        render(
            <MemoryRouter initialEntries={['/doctor/appointments']}>
                <Routes>
                    <Route path="/doctor/appointments" element={<DoctorWaitingListPage visitApi={visitApi} loadProfile={vi.fn().mockResolvedValue({})} initialTab="in-examination" />} />
                    <Route path="/doctor/clinical-examination" element={<LocationProbe />} />
                </Routes>
            </MemoryRouter>,
        );

        const patientName = await screen.findByText('Nguyễn Minh Khôi');
        fireEvent.click(patientName.closest('tr'));

        expect(await screen.findByTestId('location')).toHaveTextContent(
            '/doctor/clinical-examination|visit-in-examination|Nguyễn Minh Khôi',
        );
        expect(visitApi.startExamination).not.toHaveBeenCalled();
    });
});
