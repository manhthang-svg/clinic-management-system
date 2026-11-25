import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../apis/apiError';
import { ReceptionistVisitRegisterPage } from './ReceptionistVisitRegisterPage';

const patient = {
    id: 'p-1', mabenhnhan: 'BN00000007', hoten: 'Bùi Thị My', ngaysinh: '2010-04-21', gioitinh: 2,
    cccdSo: '012345678901',
};

const room = {
    id: 'r-1', code: 'PK-NOI', name: 'Phòng khám Nội', location: 'Tòa A', departmentName: 'Khoa Nội',
    doctorName: 'BS Châu',
};

function renderPage(visitApi) {
    const patientApi = { detail: vi.fn().mockResolvedValue(patient) };
    render(
        <MemoryRouter initialEntries={['/receptionist/patients/p-1/register']}>
            <Routes>
                <Route
                    path="/receptionist/patients/:id/register"
                    element={<ReceptionistVisitRegisterPage patientApi={patientApi} visitApi={visitApi} />}
                />
            </Routes>
        </MemoryRouter>,
    );
}

function visitApi(overrides = {}) {
    return {
        getActiveVisit: vi.fn().mockResolvedValue(null),
        getActiveExaminationRooms: vi.fn().mockResolvedValue([room]),
        getFollowUpAppointment: vi.fn().mockResolvedValue(null),
        getQueueStatus: vi.fn().mockResolvedValue([{
            roomId: 'r-1', waitingPaymentCount: 4, waitingCount: 3, inExaminationCount: 1, waitingParaclinicalCount: 2,
            waitingConclusionCount: 2, skippedCount: 1,
        }]),
        getQueue: vi.fn(),
        ...overrides,
    };
}

describe('ReceptionistVisitRegisterPage (UC-40)', () => {
    it('shows the form without waiting for the room queue counts', async () => {
        // The counts never arrive: the form must still open.
        const api = visitApi({ getQueueStatus: vi.fn(() => new Promise(() => {})) });
        renderPage(api);

        expect(await screen.findByLabelText(/Lý do khám/)).toBeInTheDocument();
        expect(screen.getByText('Bùi Thị My')).toBeInTheDocument();
        // Without counts the room is not reported as free.
        expect(await screen.findByText('Đang tải…')).toBeInTheDocument();
        expect(screen.queryByText('Đang trống')).not.toBeInTheDocument();
    });

    it('says the counts are unavailable instead of showing an empty room when they fail to load', async () => {
        renderPage(visitApi({ getQueueStatus: vi.fn().mockRejectedValue(new Error('network')) }));

        expect(await screen.findByText('Không tải được số liệu')).toBeInTheDocument();
        expect(screen.queryByText('Đang trống')).not.toBeInTheDocument();
    });

    it('takes the room counts from the queue status, not from every visit of the day', async () => {
        const api = visitApi();
        renderPage(api);

        // Today's visits with a queue number that have not ended: 3 + 1 + 2 + 2 + 1 skipped = 9; the 4 unpaid ones
        // have no queue number yet and are not counted.
        const row = (await screen.findByText('9')).closest('tr');
        expect(within(row).getByText(/Phòng khám Nội/)).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Bác sĩ còn phải khám' })).toBeInTheDocument();
        expect(screen.queryByText('Đang trống')).not.toBeInTheDocument();
        expect(screen.queryByText('Đang khám')).not.toBeInTheDocument();
        expect(api.getQueue).not.toHaveBeenCalled();
    });

    it('does not show a follow-up appointment past its validity period (BR-124)', async () => {
        renderPage(visitApi({
            getFollowUpAppointment: vi.fn().mockResolvedValue({
                id: 'a-1', appointmentDate: '2020-01-06', validUntil: '2020-01-20', doctorName: 'BS Châu',
                suggestedRoomId: 'r-1', suggestedRoomName: 'Phòng khám Nội', status: 'SCHEDULED', version: 0,
            }),
        }));

        expect(await screen.findByLabelText(/Lý do khám/)).toBeInTheDocument();
        expect(screen.queryByText(/đã quá hạn/)).not.toBeInTheDocument();
        expect(screen.queryByText('Lịch hẹn tái khám')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Đến tái khám' })).not.toBeInTheDocument();
        expect(screen.queryByText('Phòng gợi ý')).not.toBeInTheDocument();
        expect(screen.getByText('Khám bệnh')).toBeInTheDocument();
    });

    it('offers "Đến tái khám" for a due appointment within its validity period (AF-05)', async () => {
        renderPage(visitApi({
            getFollowUpAppointment: vi.fn().mockResolvedValue({
                id: 'a-1', appointmentDate: '2020-01-06', validUntil: '2999-12-31', doctorName: 'BS Châu',
                suggestedRoomId: 'r-1', suggestedRoomName: 'Phòng khám Nội', status: 'SCHEDULED', version: 0,
            }),
        }));

        expect(await screen.findByRole('button', { name: 'Đến tái khám' })).toBeInTheDocument();
        expect(screen.queryByText(/đã quá hạn/)).not.toBeInTheDocument();
    });

    it('switches to Dịch vụ with MSG17 when the card is no longer valid at confirmation (UC-74 EF-02)', async () => {
        const user = userEvent.setup();
        const create = vi.fn().mockRejectedValue(new ApiError('x', {
            status: 422, errorResponse: { code: 'INSURANCE_CARD_INVALID', message: 'x' },
        }));
        renderPage(visitApi({
            verifyInsurance: vi.fn().mockResolvedValue({
                status: 'VALID', validFrom: '2026-01-01', validTo: '2030-12-31', coverageRate: 80,
            }),
            feePreview: vi.fn().mockResolvedValue({ clinicPrice: 100000, insurancePrice: 50000 }),
            create,
        }));

        await user.click(await screen.findByRole('radio', { name: 'BHYT' }));
        await user.type(screen.getByLabelText(/Số thẻ BHYT/), 'DN4017000000102');
        await user.click(screen.getByRole('button', { name: 'Xác minh' }));
        expect(await screen.findByText('Thẻ BHYT hợp lệ.')).toBeInTheDocument();
        await user.type(screen.getByLabelText(/Lý do khám/), 'Đau đầu');
        await user.click(screen.getByRole('radio', { name: /Phòng khám Nội/ }));
        await user.click(screen.getByRole('button', { name: /Tiếp tục/ }));
        await user.click(await screen.findByRole('button', { name: 'Xác nhận đăng ký' }));

        expect(create).toHaveBeenCalledTimes(1);
        expect(await screen.findByText(/Thẻ BHYT không hợp lệ hoặc đã hết hạn/)).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Dịch vụ' })).toBeChecked();
        expect(screen.queryByLabelText(/Số thẻ BHYT/)).not.toBeInTheDocument();
    });
});
