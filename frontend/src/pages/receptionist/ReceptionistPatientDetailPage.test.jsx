import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { MSG } from '../../components/patients/patientOverview';
import { clinicToday } from '../../components/visits/visitForm';
import ReceptionistPatientDetailPage from './ReceptionistPatientDetailPage';

const patient = {
    id: 'p-1',
    mabenhnhan: 'BN1234567',
    hoten: 'Nguyễn Văn An',
    ngaysinh: '1990-01-01',
    gioitinh: 1,
    cccdSo: '012345678901',
    sodienthoai: '0901234567',
    diachi: '12 Lê Lợi',
    tinhthanh: 'Hà Nội',
    dantoc: 'Kinh',
    hotennguoithan: 'Nguyễn Thị Bình',
    nguoithanQuanhe: 'Mẹ',
};

const overview = (overrides) => ({
    patient,
    drugAllergies: [],
    lastInsuranceCard: null,
    activeVisit: null,
    visits: [],
    ...overrides,
});

function renderPage({ patientApi, visitApi }) {
    return render(
        <MemoryRouter initialEntries={['/receptionist/patients/p-1']}>
            <Routes>
                <Route
                    path="/receptionist/patients/:id"
                    element={<ReceptionistPatientDetailPage patientApi={patientApi} visitApi={visitApi} />}
                />
                <Route path="/receptionist/patients/:id/register" element={<p>Trang đăng ký khám</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

const noAppointment = () => ({ getFollowUpAppointment: vi.fn().mockResolvedValue(null) });

describe('ReceptionistPatientDetailPage (UC-36)', () => {
    it('shows administrative information, allergies and the visit history', async () => {
        const patientApi = {
            overview: vi.fn().mockResolvedValue(overview({
                drugAllergies: ['Penicillin'],
                lastInsuranceCard: 'DN4010123456789',
                visits: [{
                    visitId: 'v-1', registrationCode: 'VV001', registeredAt: '2026-10-01T02:00:00Z',
                    roomName: 'Phòng khám Nội 1', doctorName: 'ThS. Trần Bình', visitType: 'BHYT', status: 'COMPLETED',
                }],
            })),
        };

        renderPage({ patientApi, visitApi: noAppointment() });

        expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument();
        expect(patientApi.overview).toHaveBeenCalledWith('p-1', expect.anything());
        expect(screen.getByText('Dị ứng thuốc: Penicillin')).toBeInTheDocument();
        expect(screen.getByText('DN4010123456789')).toBeInTheDocument();
        expect(screen.getByText('12 Lê Lợi, Hà Nội')).toBeInTheDocument();
        expect(screen.getByText('Nguyễn Thị Bình · Mẹ')).toBeInTheDocument();
        const row = screen.getByText('VV001').closest('tr');
        expect(within(row).getByText('ThS. Trần Bình')).toBeInTheDocument();
        expect(within(row).getByText('Hoàn thành')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Đăng ký khám' })).toBeEnabled();
    });

    it('says there is no visit history (AF-01) and opens registration', async () => {
        const user = userEvent.setup();
        renderPage({ patientApi: { overview: vi.fn().mockResolvedValue(overview()) }, visitApi: noAppointment() });

        expect(await screen.findByText(MSG.NO_HISTORY)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Đăng ký khám' }));
        expect(screen.getByText('Trang đăng ký khám')).toBeInTheDocument();
    });

    it('highlights the active visit, blocks registration and reprints the ticket (AF-02)', async () => {
        const user = userEvent.setup();
        const patientApi = {
            overview: vi.fn().mockResolvedValue(overview({
                activeVisit: {
                    visitId: 'v-2', registrationCode: 'VV002', status: 'WAITING_FEE_PAYMENT',
                    roomName: 'Phòng khám Nội 1', examinationFeeStatus: 'PENDING',
                },
            })),
        };
        const visitApi = {
            ...noAppointment(),
            getTicket: vi.fn().mockResolvedValue({ queueNumber: 3, patientAmount: 0 }),
        };
        renderPage({ patientApi, visitApi });

        const active = await screen.findByRole('region', { name: 'Lượt khám đang mở' });
        expect(within(active).getByText(/VV002/)).toBeInTheDocument();
        expect(within(active).getByText(/Phí khám: Chờ thanh toán/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Đăng ký khám' })).toBeDisabled();

        await user.click(within(active).getByRole('button', { name: 'In lại phiếu khám' }));

        expect(await screen.findByRole('heading', { name: 'In lại phiếu khám' })).toBeInTheDocument();
        expect(visitApi.getTicket).toHaveBeenCalledWith('v-2');
    });

    it('prints the payment guide of an unpaid self-paid visit without recording a ticket print', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const patientApi = {
            overview: vi.fn().mockResolvedValue(overview({
                activeVisit: {
                    visitId: 'v-3', registrationCode: 'VV003', status: 'WAITING_FEE_PAYMENT',
                    roomName: 'Phòng khám Nội 1', examinationFeeStatus: 'PENDING',
                },
            })),
        };
        const visitApi = {
            ...noAppointment(),
            getTicket: vi.fn().mockResolvedValue({ visitType: 'DV', queueNumber: null, patientAmount: 150000 }),
            prepareTicketPrint: vi.fn(),
        };
        renderPage({ patientApi, visitApi });

        const active = await screen.findByRole('region', { name: 'Lượt khám đang mở' });
        await user.click(within(active).getByRole('button', { name: 'In lại phiếu khám' }));
        await user.click(await screen.findByRole('button', { name: 'In phiếu hướng dẫn thanh toán' }));

        expect(print).toHaveBeenCalled();
        expect(visitApi.prepareTicketPrint).not.toHaveBeenCalled();
        print.mockRestore();
    });

    it('shows a follow-up appointment of today (AF-03)', async () => {
        const visitApi = {
            getFollowUpAppointment: vi.fn().mockResolvedValue({
                appointmentDate: clinicToday(), doctorName: 'BS. Lê Cường', suggestedRoomName: 'Phòng khám Nội 2',
            }),
        };
        renderPage({ patientApi: { overview: vi.fn().mockResolvedValue(overview()) }, visitApi });

        const card = await screen.findByRole('region', { name: 'Lịch tái khám hôm nay' });
        expect(card).toHaveTextContent('với BS. Lê Cường, phòng Phòng khám Nội 2');
    });

    it('says the patient does not exist (EF-01)', async () => {
        const patientApi = { overview: vi.fn().mockRejectedValue(new ApiError('raw', { status: 404 })) };

        renderPage({ patientApi, visitApi: noAppointment() });

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.NOT_FOUND);
        expect(screen.queryByText('raw')).not.toBeInTheDocument();
    });
});
