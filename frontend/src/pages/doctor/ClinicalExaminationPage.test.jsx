import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ClinicalExaminationPage from './ClinicalExaminationPage';
import visitApi from '../../apis/visitApi';

const visit = {
    id: 'visit-1',
    patientName: 'Nguyễn Văn Nam',
    patientCode: 'BN001234',
    patientDob: '1985-08-12',
    patientGender: 'Nam',
    doituongbnLoai: 'BHYT',
    mabhyt: 'TE1011234567890',
    queueNumber: 12,
    lydovaovien: 'Đau đầu, chóng mặt',
    thoigianvaovien: '2026-10-30T02:12:00Z',
};

function renderPage(state = { visitId: visit.id, visit, queueInfo: { roomName: 'Phòng khám Nội tổng quát', doctorName: 'BSCKII Trần Văn An' } }) {
    return render(
        <MemoryRouter initialEntries={[{ pathname: '/doctor/clinical-examination', state }]}>
            <ClinicalExaminationPage />
        </MemoryRouter>,
    );
}

describe('ClinicalExaminationPage', () => {
    beforeEach(() => {
        vi.spyOn(visitApi, 'getMedicalHistory').mockResolvedValue({
            visitId: visit.id, editable: true, readOnlyReason: null, history: [],
            allergies: [{ drug: 'Penicillin', reaction: null, severity: 'SEVERE' }],
            noKnownDrugAllergy: false, updatedAt: null, updatedByName: null,
        });
    });

    it('shows the medical history panel and the allergy label of the patient (UC-39)', async () => {
        renderPage();
        expect(screen.getByRole('heading', { name: 'Tiền sử & dị ứng thuốc' })).toBeInTheDocument();
        expect((await screen.findAllByText('Dị ứng: Penicillin (Nặng)')).length).toBe(2);
        expect(visitApi.getMedicalHistory).toHaveBeenCalledWith(visit.id, expect.any(AbortSignal));
    });

    it('shows visit context and the clinical examination form', () => {
        renderPage();
        expect(screen.getByRole('heading', { name: 'Khám lâm sàng' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Khám bệnh' })).toBeInTheDocument();
        expect(screen.getByText('Nguyễn Văn Nam')).toBeInTheDocument();
        expect(screen.getAllByText('Phòng khám Nội tổng quát').length).toBeGreaterThan(0);
        expect(screen.getByDisplayValue('Đau đầu, chóng mặt')).toBeInTheDocument();
        expect(screen.getByText('III. Hỏi bệnh')).toBeInTheDocument();
        expect(screen.getByText('IV. Khám bệnh')).toBeInTheDocument();
    });

    it('calculates BMI from weight and height', () => {
        renderPage();
        fireEvent.change(screen.getByLabelText(/Cân nặng/), { target: { value: '68' } });
        fireEvent.change(screen.getByLabelText(/Chiều cao/), { target: { value: '170' } });
        expect(screen.getByText('23.5')).toBeInTheDocument();
    });

    it('shows a safe empty state when opened without a selected visit', () => {
        renderPage({});
        expect(screen.getByText('Chưa chọn lượt khám')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Về danh sách chờ' })).toBeInTheDocument();
    });

    it('opens the transfer-room tab and loads active examination rooms', async () => {
        vi.spyOn(visitApi, 'getActiveExaminationRooms').mockResolvedValue([
            { id: 'room-2', code: 'P.201', name: 'Tai Mũi Họng' },
        ]);
        renderPage();

        fireEvent.click(screen.getByRole('button', { name: 'Chuyển phòng' }));
        expect(await screen.findByText('1. Thông tin chuyển phòng')).toBeInTheDocument();
        expect(screen.getByRole('option', { name: 'P.201 - Tai Mũi Họng' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Xác nhận chuyển phòng' })).toBeInTheDocument();
    });
});
