import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import DoctorWaitingListPage from './DoctorWaitingListPage';
import { toast } from 'sonner';
import { ApiError } from '../../apis/apiError';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

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
            tab: 'PATIENT_LIST',
            keyword: '',
            priority: undefined,
            visitType: undefined,
            interruptedStage: undefined,
            page: 0,
            size: 10,
        }, expect.any(AbortSignal)));

        expect(screen.getAllByText('Phòng khám Nội 1')).toHaveLength(2);
        expect(screen.getByText('BS. Nguyễn Văn An')).toBeInTheDocument();
        // The patient list merges the 3 waiting and 1 in-examination visits.
        expect(screen.getByRole('tab', { name: 'Danh sách bệnh nhân (4)' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.queryByRole('tab', { name: /^Chờ khám/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: /^Đang khám/ })).not.toBeInTheDocument();
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
            canAcceptAgain: true,
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

    it('offers no action for a skipped visit that cannot be accepted again (UC-45)', async () => {
        const visit = {
            id: 'visit-not-eligible',
            patientName: 'Lê Thị Hoa',
            queueNumber: 8,
            status: 'WAITING_CONCLUSION',
            isSkipped: true,
            canAcceptAgain: false,
        };
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                skippedCount: 1,
                visits: { content: [visit], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn(),
        };

        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}), initialTab: 'skipped' });

        expect(await screen.findByText('Lê Thị Hoa')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Tiếp nhận lại' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Vắng mặt' })).not.toBeInTheDocument();
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

        expect(await screen.findByText('Bạn không có ca làm việc vào lúc này. Bạn chỉ xem được danh sách, không gọi được bệnh nhân.')).toBeInTheDocument();
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

    it('lists waiting and in-examination patients together with their own status and action', async () => {
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                waitingCount: 1,
                inExaminationCount: 1,
                visits: {
                    content: [
                        { id: 'visit-exam', patientName: 'Phạm Văn Khám', queueNumber: 2, status: 'IN_EXAMINATION', isSkipped: false },
                        { id: 'visit-wait', patientName: 'Lê Thị Chờ', queueNumber: 3, status: 'WAITING', isSkipped: false },
                    ],
                    page: { number: 0, size: 10, totalElements: 2, totalPages: 1 },
                },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn(),
        };

        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}) });

        const examRow = (await screen.findByText('Phạm Văn Khám')).closest('tr');
        const waitRow = screen.getByText('Lê Thị Chờ').closest('tr');
        expect(within(examRow).getByText('Đang khám')).toBeInTheDocument();
        expect(within(examRow).queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument();
        expect(within(waitRow).getByText('Đang chờ')).toBeInTheDocument();
        expect(within(waitRow).getByRole('button', { name: 'Bắt đầu' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Danh sách bệnh nhân' })).toBeInTheDocument();
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
                    <Route path="/doctor/appointments" element={<DoctorWaitingListPage visitApi={visitApi} loadProfile={vi.fn().mockResolvedValue({})} />} />
                    <Route path="/doctor/clinical-examination/:visitId/:section?" element={<LocationProbe />} />
                </Routes>
            </MemoryRouter>,
        );

        const patientName = await screen.findByText('Nguyễn Minh Khôi');
        fireEvent.click(patientName.closest('tr'));

        expect(await screen.findByTestId('location')).toHaveTextContent(
            '/doctor/clinical-examination/visit-in-examination|visit-in-examination|Nguyễn Minh Khôi',
        );
        expect(visitApi.startExamination).not.toHaveBeenCalled();
    });

    it('keeps a waiting-for-paraclinical patient in the list and opens their examination', async () => {
        const visit = {
            id: 'visit-paraclinical',
            patientName: 'Trần Văn CLS',
            patientCode: 'BN044',
            queueNumber: 4,
            status: 'WAITING_PARACLINICAL',
            isSkipped: false,
        };
        const visitApi = {
            getDoctorQueue: vi.fn().mockResolvedValue(queueResponse({
                waitingParaclinicalCount: 1,
                visits: { content: [visit], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } },
            })),
            startExamination: vi.fn(),
            skipVisit: vi.fn(),
            acceptSkippedVisit: vi.fn(),
        };

        render(
            <MemoryRouter initialEntries={['/doctor/appointments']}>
                <Routes>
                    <Route path="/doctor/appointments" element={<DoctorWaitingListPage visitApi={visitApi} loadProfile={vi.fn().mockResolvedValue({})} />} />
                    <Route path="/doctor/clinical-examination/:visitId/:section?" element={<LocationProbe />} />
                </Routes>
            </MemoryRouter>,
        );

        const row = (await screen.findByText('Trần Văn CLS')).closest('tr');
        expect(within(row).getByText('Chờ CLS')).toBeInTheDocument();
        expect(within(row).queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument();
        fireEvent.click(row);

        expect(await screen.findByTestId('location')).toHaveTextContent(
            '/doctor/clinical-examination/visit-paraclinical|visit-paraclinical|Trần Văn CLS',
        );
    });
});

describe('DoctorWaitingListPage messages', () => {
    const MSG01 = 'Không tìm thấy kết quả.';
    const MSG12 = 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.';
    const MSG80 = 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.';
    const MSG92 = 'Chưa có dữ liệu để hiển thị.';
    const MSG96 = 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';

    function apiWith(getDoctorQueue) {
        return { getDoctorQueue, startExamination: vi.fn(), skipVisit: vi.fn(), acceptSkippedVisit: vi.fn() };
    }

    const emptyQueue = () => queueResponse({
        waitingCount: 0,
        visits: { content: [], page: { number: 0, size: 10, totalElements: 0, totalPages: 0 } },
    });

    it('shows MSG92 for an empty waiting list (AF-01)', async () => {
        renderPage({ visitApi: apiWith(vi.fn().mockResolvedValue(emptyQueue())), loadProfile: vi.fn().mockResolvedValue({}) });

        expect(await screen.findByRole('heading', { name: MSG92 })).toBeInTheDocument();
        expect(screen.getByText(/lượt khám BHYT và lượt khám dịch vụ đã thanh toán/)).toBeInTheDocument();
    });

    it('shows MSG01 with a clear-filter hint when the search matches nothing (AF-03)', async () => {
        const getDoctorQueue = vi.fn().mockResolvedValue(emptyQueue());
        renderPage({ visitApi: apiWith(getDoctorQueue), loadProfile: vi.fn().mockResolvedValue({}) });

        fireEvent.change(screen.getByLabelText('Tìm bệnh nhân'), { target: { value: 'không có' } });

        await waitFor(() => expect(getDoctorQueue).toHaveBeenLastCalledWith(
            expect.objectContaining({ keyword: 'không có' }), expect.any(AbortSignal)));
        expect(await screen.findByRole('heading', { name: MSG01 })).toBeInTheDocument();
        expect(screen.getByText(/chọn "Xóa bộ lọc"/)).toBeInTheDocument();
        expect(screen.getByLabelText('Tìm bệnh nhân')).toHaveValue('không có');
    });

    it('shows MSG80 without an empty-list message when the first load fails (EF-02)', async () => {
        renderPage({
            visitApi: apiWith(vi.fn().mockRejectedValue(new ApiError('Internal error', { status: 500 }))),
            loadProfile: vi.fn().mockResolvedValue({}),
        });

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG80);
        expect(screen.queryByRole('heading', { name: MSG92 })).not.toBeInTheDocument();
    });

    it('keeps the last loaded list and shows MSG80 when a reload fails (EF-02)', async () => {
        const visit = { id: 'visit-1', patientName: 'Nguyễn Văn An', queueNumber: 1, status: 'WAITING', isSkipped: false };
        const getDoctorQueue = vi.fn()
            .mockResolvedValueOnce(queueResponse({
                visits: { content: [visit], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } },
            }))
            .mockRejectedValue(new ApiError('Internal error', { status: 500 }));
        renderPage({ visitApi: apiWith(getDoctorQueue), loadProfile: vi.fn().mockResolvedValue({}) });

        expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument();
        fireEvent.click(screen.getByTitle('Tải lại ngay'));

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG80);
        expect(screen.getByText('Nguyễn Văn An')).toBeInTheDocument();
    });

    it('shows MSG13 when the account may not view the list (UC-45 EF-01)', async () => {
        renderPage({
            visitApi: apiWith(vi.fn().mockRejectedValue(new ApiError('Forbidden', {
                status: 403,
                errorResponse: { code: 'FORBIDDEN', message: 'You do not have permission to access this resource.' },
            }))),
            loadProfile: vi.fn().mockResolvedValue({}),
        });

        expect(await screen.findByRole('alert')).toHaveTextContent('Bạn không có quyền thực hiện thao tác này.');
    });

    it('shows MSG12 when the session has expired (EF-03)', async () => {
        renderPage({
            visitApi: apiWith(vi.fn().mockRejectedValue(new ApiError('Unauthorized', { status: 401 }))),
            loadProfile: vi.fn().mockResolvedValue({}),
        });

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG12);
    });

    it('shows MSG96 when the server cannot be reached', async () => {
        renderPage({
            visitApi: apiWith(vi.fn().mockRejectedValue(new ApiError('Failed to fetch'))),
            loadProfile: vi.fn().mockResolvedValue({}),
        });

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG96);
    });

    const waitingVisit = { id: 'visit-1', patientName: 'Nguyễn Văn An', queueNumber: 1, status: 'WAITING', isSkipped: false };

    function queueWith(visit) {
        return queueResponse({ visits: { content: [visit], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } } });
    }

    it('shows MSG54 after marking a patient absent (UC-44 AF-01)', async () => {
        const visitApi = { ...apiWith(vi.fn().mockResolvedValue(queueWith(waitingVisit))), skipVisit: vi.fn().mockResolvedValue({}) };
        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}) });

        fireEvent.click(await screen.findByRole('button', { name: 'Vắng mặt' }));

        await waitFor(() => expect(toast.success).toHaveBeenCalledWith(
            'Nguyễn Văn An không có mặt khi được gọi và đã được chuyển sang danh sách bỏ qua.'));
    });

    it('shows MSG23 and reloads when the patient was already called (UC-44 EF-04)', async () => {
        const getDoctorQueue = vi.fn().mockResolvedValue(queueWith(waitingVisit));
        const visitApi = {
            ...apiWith(getDoctorQueue),
            startExamination: vi.fn().mockRejectedValue(new ApiError('Conflict', {
                status: 409, errorResponse: { code: 'CONCURRENT_MODIFICATION' },
            })),
        };
        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}) });

        fireEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Bệnh nhân này đã được gọi.'));
        expect(getDoctorQueue.mock.calls.length).toBeGreaterThan(1);
    });

    it('shows MSG80 when a skipped visit is no longer found (UC-46 EF-03)', async () => {
        const skipped = { ...waitingVisit, id: 'visit-skipped', isSkipped: true, canAcceptAgain: true };
        const visitApi = {
            ...apiWith(vi.fn().mockResolvedValue(queueWith(skipped))),
            acceptSkippedVisit: vi.fn().mockRejectedValue(new ApiError('Not found', {
                status: 404, errorResponse: { code: 'NOT_FOUND' },
            })),
        };
        renderPage({ visitApi, loadProfile: vi.fn().mockResolvedValue({}), initialTab: 'skipped' });

        fireEvent.click(await screen.findByRole('button', { name: 'Tiếp nhận lại' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith(MSG80));
    });
});
