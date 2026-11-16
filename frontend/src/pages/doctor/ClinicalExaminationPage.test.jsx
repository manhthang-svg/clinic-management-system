import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ClinicalExaminationPage from './ClinicalExaminationPage';
import visitApi from '../../apis/visitApi';
import { ApiError } from '../../apis/apiError';

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

const clinicalData = {
    visitId: visit.id, medicalRecordId: 'record-1', examinationId: 'exam-1', version: 0,
    visitCode: 'DK0000000001', queueNumber: 12, visitStatus: 'IN_EXAMINATION',
    registeredAt: visit.thoigianvaovien, examinationStartedAt: '2026-10-30T02:30:00Z',
    visitType: 'BHYT', insuranceCardNumber: visit.mabhyt,
    patientId: 'patient-1', patientCode: visit.patientCode, patientName: visit.patientName,
    patientDateOfBirth: visit.patientDob, patientGender: visit.patientGender,
    occupation: 'Nhân viên văn phòng', ethnicity: 'Kinh', address: 'Hà Nội',
    roomId: 'room-1', roomCode: 'P.101', roomName: 'Phòng khám Nội tổng quát',
    doctorAccountId: 'doctor-1', doctorName: 'BSCKII Trần Văn An',
    reasonForVisit: visit.lydovaovien, presentIllness: '', generalFinding: '', vitalSigns: null,
    organFinding: '', preliminaryDiagnosis: '', preliminaryDiagnosisIcd10: null,
    treatmentCarePerformed: '', medicalHistory: [], visitHistory: [
        { visitId: 'old-visit', visitedAt: '2026-10-20T08:00:00Z', reasonForVisit: 'Đau họng', diagnosis: 'Viêm họng cấp' },
    ], availableNextSteps: ['PARACLINICAL', 'CONCLUSION'],
};

function renderPage(state = { visitId: visit.id, visit, queueInfo: { roomName: 'Phòng khám Nội tổng quát', doctorName: 'BSCKII Trần Văn An' } }) {
    return render(
        <MemoryRouter initialEntries={[{ pathname: '/doctor/clinical-examination', state }]}>
            <Routes>
                <Route path="/doctor/clinical-examination" element={<ClinicalExaminationPage />} />
                <Route path="/doctor/clinical-examination/:visitId/:section?" element={<ClinicalExaminationPage />} />
                <Route path="/doctor/appointments" element={<p>Danh sách chờ khám</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('ClinicalExaminationPage', () => {
    beforeEach(() => {
        vi.spyOn(visitApi, 'openClinicalExamination').mockResolvedValue(clinicalData);
        vi.spyOn(visitApi, 'searchIcd10').mockResolvedValue([]);
        vi.spyOn(visitApi, 'saveClinicalExamination').mockResolvedValue(clinicalData);
        vi.spyOn(visitApi, 'getParaclinicalServices').mockResolvedValue([]);
        vi.spyOn(visitApi, 'getMedicalHistory').mockResolvedValue({
            visitId: visit.id, editable: true, readOnlyReason: null, history: [],
            allergies: [{ drug: 'Penicillin', reaction: null, severity: 'SEVERE' }],
            noKnownDrugAllergy: false, updatedAt: null, updatedByName: null,
        });
    });

    afterEach(() => vi.restoreAllMocks());

    it('shows the medical history panel without drug allergies (UC-39)', async () => {
        renderPage();
        expect(await screen.findByRole('heading', { name: '2. Tiền sử bệnh' })).toBeInTheDocument();
        expect(await screen.findByText('+ Bản thân:')).toBeInTheDocument();
        expect(screen.queryByText(/Penicillin/)).not.toBeInTheDocument();
        expect(visitApi.getMedicalHistory).toHaveBeenCalledWith(visit.id, expect.any(AbortSignal));
    });

    it('shows visit context and the clinical examination form', async () => {
        renderPage();
        expect(await screen.findByRole('heading', { name: 'Khám lâm sàng' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Khám bệnh' })).toBeInTheDocument();
        expect(screen.getByText('Nguyễn Văn Nam')).toBeInTheDocument();
        expect(screen.getAllByText('Phòng khám Nội tổng quát').length).toBeGreaterThan(0);
        expect(screen.getByDisplayValue('Đau đầu, chóng mặt')).toBeInTheDocument();
        expect(screen.queryByText('Hỏi bệnh')).not.toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Khám bệnh' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Lịch sử khám bệnh' })).toBeInTheDocument();
        expect(screen.getByText('Viêm họng cấp')).toBeInTheDocument();
    });

    it('opens the visit from its own link, e.g. after a reload, and gives each tab its own link', async () => {
        render(
            <MemoryRouter initialEntries={['/doctor/clinical-examination/visit-1']}>
                <Routes>
                    <Route path="/doctor/clinical-examination/:visitId/:section?" element={<ClinicalExaminationPage />} />
                </Routes>
            </MemoryRouter>,
        );

        expect(await screen.findByText('Nguyễn Văn Nam')).toBeInTheDocument();
        expect(visitApi.openClinicalExamination).toHaveBeenCalledWith('visit-1', expect.anything());

        fireEvent.click(screen.getByRole('button', { name: 'Chuyển phòng' }));
        expect(await screen.findByRole('heading', { name: 'Chuyển phòng khám' })).toBeInTheDocument();
        // Switching tabs keeps the page: the record is not opened again.
        expect(visitApi.openClinicalExamination).toHaveBeenCalledTimes(1);
    });

    it('shows the examination read-only with the ordered services while waiting for paraclinical', async () => {
        visitApi.openClinicalExamination.mockResolvedValue({ ...clinicalData, visitStatus: 'WAITING_PARACLINICAL' });
        vi.spyOn(visitApi, 'getParaclinicalOrders').mockResolvedValue([{
            id: 'order-1', sophieu: 'CLS0001', chandoansobo: 'Viêm họng', tongcong: 150000,
            createdAt: '2026-10-30T03:00:00Z',
            details: [{ id: 'detail-1', machidinh: 'XN01', tenchidinh: 'Tổng phân tích tế bào máu', thanhtien: 150000, status: 'WAITING' }],
        }]);
        renderPage();

        expect(await screen.findByText('Bệnh nhân đang chờ thực hiện cận lâm sàng. Hồ sơ khám lâm sàng chỉ để xem.')).toBeInTheDocument();
        expect(screen.getByDisplayValue('Đau đầu, chóng mặt')).toBeDisabled();
        expect(screen.queryByRole('button', { name: /Lưu bản nháp/ })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Chỉ định cận lâm sàng' }));

        expect(await screen.findByText('Tổng phân tích tế bào máu')).toBeInTheDocument();
        expect(screen.getByText('Chờ thực hiện')).toBeInTheDocument();
        expect(visitApi.saveClinicalExamination).not.toHaveBeenCalled();
        expect(visitApi.getParaclinicalOrders).toHaveBeenCalledWith(visit.id, expect.any(AbortSignal));
    });

    it('lists the tabs with the ordered list after the order tab and the transfer last', async () => {
        vi.spyOn(visitApi, 'getParaclinicalOrders').mockResolvedValue([{
            id: 'order-1', sophieu: 'CLS0001', chandoansobo: 'Viêm họng', tongcong: 150000,
            createdAt: '2026-10-30T03:00:00Z',
            details: [{ id: 'detail-1', machidinh: 'XN01', tenchidinh: 'Tổng phân tích tế bào máu', thanhtien: 150000, status: 'WAITING' }],
        }]);
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });

        const tabs = within(screen.getByRole('navigation', { name: 'Các bước khám bệnh' })).getAllByRole('button');
        expect(tabs.map((tab) => tab.textContent)).toEqual([
            'Khám lâm sàng', 'Chỉ định cận lâm sàng', 'Danh sách đã chỉ định', 'Kết luận', 'Kê đơn',
            'Hẹn tái khám', 'Chuyển phòng',
        ]);

        // The ordered list opens during the examination too, without saving it.
        fireEvent.click(screen.getByRole('button', { name: 'Danh sách đã chỉ định' }));
        expect(await screen.findByText('Tổng phân tích tế bào máu')).toBeInTheDocument();
        expect(visitApi.saveClinicalExamination).not.toHaveBeenCalled();
    });

    it('opens the conclusion of a patient back from the paraclinical rooms, the examination read-only (UC-56)', async () => {
        visitApi.openClinicalExamination.mockResolvedValue({ ...clinicalData, visitStatus: 'WAITING_CONCLUSION' });
        vi.spyOn(visitApi, 'getConclusion').mockResolvedValue({
            visitId: visit.id, medicalRecordId: 'record-1', conclusionId: null, version: null,
            preliminaryDiagnosis: 'Viêm họng', preliminaryDiagnoses: [], diagnosis: null, mainDiagnosis: null,
            secondaryDiagnoses: [], treatmentPlan: null, treatmentResult: null, advice: null, summary: null, note: null,
            diagnosesLocked: false, doctorName: null, concludedAt: null,
        });
        render(
            <MemoryRouter initialEntries={['/doctor/clinical-examination/visit-1/conclusion']}>
                <Routes>
                    <Route path="/doctor/clinical-examination/:visitId/:section?" element={<ClinicalExaminationPage />} />
                </Routes>
            </MemoryRouter>,
        );

        expect(await screen.findByRole('form', { name: 'Kết luận khám' })).toBeInTheDocument();
        expect(screen.getByText(/Bệnh nhân đã có đủ kết quả cận lâm sàng/)).toBeInTheDocument();
        expect(visitApi.getConclusion).toHaveBeenCalledWith('visit-1', expect.any(AbortSignal));
        expect(visitApi.saveClinicalExamination).not.toHaveBeenCalled();
    });

    it('calculates BMI from weight and height', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.change(screen.getByLabelText(/Cân nặng/), { target: { value: '68' } });
        fireEvent.change(screen.getByLabelText(/Chiều cao/), { target: { value: '170' } });
        expect(screen.getByText('23.5')).toBeInTheDocument();
        expect(screen.getByText('(Người bình thường)')).toBeInTheDocument();
    });

    it('shows a safe empty state when opened without a selected visit', () => {
        renderPage({});
        expect(screen.getByText('Chưa chọn lượt khám')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Về danh sách chờ' })).toBeInTheDocument();
    });

    it('opens the UC-48 transfer tab with the rooms that can receive the patient', async () => {
        vi.spyOn(visitApi, 'getTransferRooms').mockResolvedValue([
            { roomId: 'room-2', roomCode: 'P.201', roomName: 'Tai Mũi Họng', doctorName: 'BS Lê Thị Bình' },
        ]);
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Chuyển phòng' }));
        expect(await screen.findByRole('heading', { name: 'Chuyển phòng khám' })).toBeInTheDocument();
        expect(await screen.findByRole('option', { name: 'P.201 - Tai Mũi Họng' })).toBeInTheDocument();
        expect(visitApi.getTransferRooms).toHaveBeenCalledWith(visit.id, expect.anything());
    });

    it('does not offer a transfer once the examination has been saved (OQ-05)', async () => {
        visitApi.openClinicalExamination.mockResolvedValue({ ...clinicalData, version: 1 });
        renderPage();

        // Wait until the saved examination is shown, so the tab state reflects the loaded version.
        expect(await screen.findByDisplayValue(clinicalData.reasonForVisit)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Chuyển phòng' })).toBeDisabled();
    });

    it('clears clinical validation errors when opening the transfer-room tab', async () => {
        vi.spyOn(visitApi, 'getTransferRooms').mockResolvedValue([
            { roomId: 'room-2', roomCode: 'P.201', roomName: 'Tai Mũi Họng', doctorName: 'BS Lê Thị Bình' },
        ]);
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });

        fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
        expect(screen.getByRole('alert')).toHaveTextContent('Vui lòng kiểm tra các trường được đánh dấu.');

        fireEvent.click(screen.getByRole('button', { name: 'Chuyển phòng' }));
        expect(await screen.findByRole('heading', { name: 'Chuyển phòng khám' })).toBeInTheDocument();
        expect(screen.queryByText('Vui lòng kiểm tra các trường được đánh dấu.')).not.toBeInTheDocument();
        expect(screen.queryByText('Cần nhập ít nhất một chỉ số sinh hiệu hợp lệ.')).not.toBeInTheDocument();
    });

    it('persists a valid draft through the UC-47 API', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.change(screen.getByLabelText(/Mạch/), { target: { value: '72' } });
        fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
        expect(visitApi.saveClinicalExamination).toHaveBeenCalledWith(
            visit.id,
            expect.objectContaining({ mode: 'DRAFT', vitalSigns: expect.objectContaining({ pulse: 72 }) }),
        );
    });

    it('sends every selected preliminary ICD-10 diagnosis', async () => {
        visitApi.openClinicalExamination.mockResolvedValue({
            ...clinicalData,
            preliminaryDiagnoses: [
                { code: 'J02', name: 'Viêm họng cấp' },
                { code: 'J03', name: 'Viêm amidan cấp' },
            ],
        });
        renderPage();
        await screen.findByText('Viêm amidan cấp');
        fireEvent.change(screen.getByLabelText(/Mạch/), { target: { value: '72' } });
        fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(visitApi.saveClinicalExamination).toHaveBeenCalledWith(visit.id, expect.objectContaining({
            preliminaryDiagnosisIcd10Codes: ['J02', 'J03'],
            preliminaryDiagnosis: 'Viêm họng cấp; Viêm amidan cấp',
        }));
    });

    it('shows patient and visit details from the record (step 4)', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        expect(screen.getByText('Nhân viên văn phòng')).toBeInTheDocument();
        expect(screen.getByText('Kinh')).toBeInTheDocument();
        expect(screen.getByText('Hà Nội')).toBeInTheDocument();
        expect(screen.getAllByText('DK0000000001').length).toBeGreaterThan(0);
        expect(screen.getAllByText('Đang khám').length).toBeGreaterThan(0);
        const startedAt = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' })
            .format(new Date(clinicalData.examinationStartedAt));
        expect(screen.getAllByText(startedAt).length).toBeGreaterThan(0);
    });

    it('asks before leaving with unsaved changes and stays on request (AF-06, MSG38)', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.change(screen.getByLabelText(/1\. Toàn thân/), { target: { value: 'Tỉnh, tiếp xúc tốt' } });
        fireEvent.click(screen.getByRole('button', { name: 'Quay lại danh sách' }));

        expect(screen.getByRole('dialog')).toHaveTextContent('Bạn có thay đổi chưa lưu');
        fireEvent.click(screen.getByRole('button', { name: 'Ở lại' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByDisplayValue('Tỉnh, tiếp xúc tốt')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Quay lại danh sách' }));
        fireEvent.click(screen.getByRole('button', { name: 'Rời đi' }));
        expect(await screen.findByText('Danh sách chờ khám')).toBeInTheDocument();
    });

    it('leaves without asking when nothing changed', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.click(screen.getByRole('button', { name: 'Quay lại danh sách' }));
        expect(await screen.findByText('Danh sách chờ khám')).toBeInTheDocument();
    });

    it('shows field errors and does not call the API when no vital sign is entered (EF-02)', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
        expect(screen.getByRole('alert')).toHaveTextContent('Vui lòng kiểm tra các trường được đánh dấu.');
        expect(screen.getByText('Cần nhập ít nhất một chỉ số sinh hiệu hợp lệ.')).toBeInTheDocument();

        fireEvent.change(screen.getByLabelText(/Mạch/), { target: { value: '70' } });
        expect(screen.queryByText('Vui lòng kiểm tra các trường được đánh dấu.')).not.toBeInTheDocument();
        expect(screen.queryByText('Cần nhập ít nhất một chỉ số sinh hiệu hợp lệ.')).not.toBeInTheDocument();
        expect(visitApi.saveClinicalExamination).not.toHaveBeenCalled();
    });

    it('blocks continuing without a preliminary diagnosis (EF-04, MSG98)', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.change(screen.getByLabelText(/Mạch/), { target: { value: '72' } });
        fireEvent.click(screen.getByRole('button', { name: 'Chỉ định cận lâm sàng' }));
        expect(screen.getByLabelText(/Chẩn đoán ban đầu/)).toHaveAttribute('aria-invalid', 'true');
        expect(visitApi.saveClinicalExamination).not.toHaveBeenCalled();
    });

    describe('opening the paraclinical order tab', () => {
        const readyRecord = {
            ...clinicalData,
            vitalSigns: { pulse: 72 },
            preliminaryDiagnoses: [{ code: 'J03', name: 'Viêm amidan cấp' }],
        };

        it('opens at once with the list loaded with the page, and orders only once the save is done', async () => {
            visitApi.openClinicalExamination.mockResolvedValue(readyRecord);
            let finishSave;
            visitApi.saveClinicalExamination.mockReturnValue(new Promise((resolve) => { finishSave = resolve; }));
            renderPage();
            await screen.findByText('Viêm amidan cấp');

            fireEvent.click(screen.getByRole('button', { name: 'Chỉ định cận lâm sàng' }));

            expect(await screen.findByRole('region', { name: 'Danh sách dịch vụ' })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'Đang lưu hồ sơ khám…' })).toBeDisabled();
            await act(async () => finishSave(readyRecord));
            expect(screen.getByRole('button', { name: 'Xác nhận chỉ định' })).toBeInTheDocument();
            // The list request started with the page is reused.
            expect(visitApi.getParaclinicalServices).toHaveBeenCalledTimes(1);
        });

        it('goes back to the examination with the message when the save fails', async () => {
            visitApi.openClinicalExamination.mockResolvedValue(readyRecord);
            visitApi.saveClinicalExamination.mockRejectedValue(new ApiError('Doctor is not on duty.', {
                status: 403, errorResponse: { code: 'DOCTOR_OFF_DUTY', message: 'Doctor is not on duty.' },
            }));
            renderPage();
            await screen.findByText('Viêm amidan cấp');

            fireEvent.click(screen.getByRole('button', { name: 'Chỉ định cận lâm sàng' }));

            expect(await screen.findByRole('alert')).not.toHaveTextContent('Doctor is not on duty.');
            expect(await screen.findByLabelText(/Mạch/)).toBeInTheDocument();
            expect(screen.queryByRole('region', { name: 'Danh sách dịch vụ' })).not.toBeInTheDocument();
        });
    });

    it('shows a Vietnamese message instead of the raw backend message', async () => {
        visitApi.saveClinicalExamination.mockRejectedValue(new ApiError('Doctor is not on duty.', {
            status: 403, errorResponse: { code: 'DOCTOR_OFF_DUTY', message: 'Doctor is not on duty.' },
        }));
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.change(screen.getByLabelText(/Mạch/), { target: { value: '72' } });
        fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('ngoài ca trực');
        expect(screen.queryByText('Doctor is not on duty.')).not.toBeInTheDocument();
    });

    it('does not offer the form when the record is closed (EF-06)', async () => {
        visitApi.openClinicalExamination.mockRejectedValue(new ApiError('closed', {
            status: 409, errorResponse: { code: 'MEDICAL_RECORD_CLOSED' },
        }));
        renderPage();
        expect(await screen.findByRole('alert')).toHaveTextContent('Hồ sơ bệnh án đã đóng');
        expect(screen.queryByRole('heading', { name: 'Khám bệnh' })).not.toBeInTheDocument();
    });

    it('sends the combined organ examination from one field', async () => {
        renderPage();
        await screen.findByRole('heading', { name: 'Khám lâm sàng' });
        fireEvent.change(screen.getByLabelText(/Mạch/), { target: { value: '72' } });
        fireEvent.change(screen.getByLabelText(/^2\. Khám bộ phận/), { target: { value: 'Tim đều, phổi thông khí tốt' } });
        fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
        expect(visitApi.saveClinicalExamination).toHaveBeenCalledWith(visit.id, expect.objectContaining({
            organFinding: 'Tim đều, phổi thông khí tốt',
        }));
    });
});
