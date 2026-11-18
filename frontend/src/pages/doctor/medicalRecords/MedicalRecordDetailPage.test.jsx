import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MedicalRecordDetailPage } from './MedicalRecordDetailPage';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { useSession } from '../../../services/session';

vi.mock('../../../services/session', () => ({
  useSession: vi.fn(),
  apiClient: {},
}));

vi.mock('../../../apis/medicalRecordApi', () => ({
  medicalRecordApi: {
    getMedicalRecordDetail: vi.fn(),
    getMedicalRecordByVisitId: vi.fn(),
    exportPdf: vi.fn(),
    recordFileAccessLog: vi.fn(),
    exportMedicalRecordData: vi.fn(),
  },
}));

const mockRecordDetail = {
  medicalRecordId: 'mr-123',
  recordNumber: 'BA00000012',
  storageNumber: 'LT-01',
  status: 'LOCKED',
  openedAt: '2026-11-10T08:00:00Z',
  closedAt: '2026-11-10T10:00:00Z',
  // Patient header
  patientId: 'p-1',
  patientCode: 'BN00000123',
  patientName: 'Nguyễn Văn A',
  dateOfBirth: '1985-05-15',
  age: 41,
  gender: 'Nam',
  address: 'Hà Nội',
  phoneNumber: '0901234567',
  citizenId: '001085000123',
  drugAllergies: ['Penicillin', 'Aspirin'],
  medicalHistorySummary: 'Tăng huyết áp 5 năm',
  // Visit details
  visitId: 'v-1',
  visitCode: 'DK0000000456',
  visitDate: '2026-11-10',
  registeredAt: '2026-11-10T08:05:00Z',
  roomId: 'r-1',
  roomName: 'Phòng Khám Nội 1',
  doctorAccountId: 'doc-1',
  doctorName: 'BS.CKI Trần Văn B',
  visitType: 'BHYT',
  insuranceCardNumber: 'DN4010123456789',
  isPriority: true,
  priorityReason: 'Người cao tuổi',
  visitStatus: 'COMPLETED',
  isAbandoned: false,
  isEditable: false,
  reasonForVisit: 'Đau đầu, chóng mặt kéo dài',
  transfers: [],
  // Vital signs
  vitalSigns: {
    mach: 82,
    nhietdo: 37.0,
    nhiptho: 18,
    huyetapTamthu: 130,
    huyetapTamtruong: 85,
    cannang: 68.5,
    chieucao: 170.0,
    bmi: 23.7,
    spo2: 98,
    thoidiem: '2026-11-10T08:15:00Z',
  },
  clinicalExam: {
    initialDiagnosis: 'Theo dõi tăng huyết áp',
    processOfIllness: 'Bệnh khởi phát 3 ngày trước',
    generalExamination: 'Bệnh nhân tỉnh, tiếp xúc tốt',
    partExamination: 'Tim đều, phổi trong',
    diagnoses: [{ icdCode: 'I10', icdName: 'Tăng huyết áp vô căn', isPrimary: true }],
  },
  paraclinicalOrders: [
    {
      orderId: 'po-1',
      orderCode: 'CLS00001',
      status: 'COMPLETED',
      orderedAt: '2026-11-10T08:25:00Z',
      services: [
        {
          orderDetailId: 'pod-1',
          orderCode: 'DV01',
          serviceName: 'Điện tim thường (ECG)',
          category: 'Thăm dò chức năng',
          roomName: 'Phòng Điện tim',
          status: 'COMPLETED',
          quantity: 1,
          unitPrice: 50000,
          totalPrice: 50000,
          performerName: 'KTV. Lê Văn C',
          performedAt: '2026-11-10T08:45:00Z',
          resultSummary: 'Nhịp xoang đều 80ck/p',
          imagingResult: {
            imagingResultId: 'img-res-1',
            ticketNumber: 'KQ001',
            description: 'Điện tâm đồ bình thường',
            conclusion: 'Nhịp xoang đều',
            recommendation: 'Theo dõi định kỳ',
            images: [
              {
                imageId: 'att-1',
                imageUrl: 'https://example.com/ecg.jpg',
                fileName: 'ecg_result.jpg',
                caption: 'Bản ghi ECG',
              },
            ],
          },
        },
      ],
    },
  ],
  conclusion: {
    diagnosis: 'Tăng huyết áp nguyên phát',
    icdCode: 'I10',
    icdName: 'Tăng huyết áp vô căn',
    treatmentPlan: 'Điều trị ngoại trú bằng thuốc hạ áp',
    doctorAdvice: 'Hạn chế ăn mặn, tập thể dục nhẹ nhàng',
    concludedAt: '2026-11-10T09:30:00Z',
  },
  prescription: {
    prescriptionId: 'rx-1',
    prescriptionCode: 'DT00000001',
    status: 'ACCEPTED',
    prescribedAt: '2026-11-10T09:20:00Z',
    advice: 'Uống thuốc đều đặn vào buổi sáng sau ăn',
    items: [
      {
        medicineId: 'm-1',
        medicineName: 'Amlodipine 5mg',
        activeIngredient: 'Amlodipine',
        strength: '5mg',
        unit: 'Viên',
        quantity: 30,
        usageInstructions: 'Uống 1 viên vào buổi sáng',
        timesPerDay: 1,
        durationDays: 30,
        dosePerTime: '1 viên',
      },
    ],
  },
  followUpAppointment: {
    appointmentId: 'fa-1',
    appointmentNumber: 'GH00001',
    followUpDate: '2026-12-10',
    doctorAdvice: 'Tái khám kiểm tra lại huyết áp và chức năng thận',
    status: 'SCHEDULED',
  },
  visitTimeline: [
    {
      visitId: 'v-1',
      visitCode: 'DK0000000456',
      visitDate: '2026-11-10',
      roomName: 'Phòng Khám Nội 1',
      doctorName: 'BS.CKI Trần Văn B',
      visitStatus: 'COMPLETED',
      isCurrent: true,
      diagnosisIcd: 'I10',
      diagnosisName: 'Tăng huyết áp vô căn',
    },
  ],
};

describe('MedicalRecordDetailPage (UC-52)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useSession.mockReturnValue({ account: { role: 'DOCTOR' } });
  });

  it('renders patient header and full record details when loaded', async () => {
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue(mockRecordDetail);

    render(
      <MemoryRouter initialEntries={['/doctor/medical-records/mr-123']}>
        <Routes>
          <Route path="/doctor/medical-records/:id" element={<MedicalRecordDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    // Patient Header
    expect(await screen.findByText('Nguyễn Văn A')).toBeInTheDocument();
    expect(screen.getByText('BN00000123')).toBeInTheDocument();
    expect(screen.getByText(/Penicillin, Aspirin/i)).toBeInTheDocument();
    expect(screen.getByText(/Tăng huyết áp 5 năm/i)).toBeInTheDocument();

    // Vital signs
    expect(screen.getByText('130/85')).toBeInTheDocument();
    expect(screen.getByText('23.7')).toBeInTheDocument();

    // Paraclinical results
    expect(screen.getByText('Điện tim thường (ECG)')).toBeInTheDocument();
    expect(screen.getByText('Nhịp xoang đều')).toBeInTheDocument();

    // Prescription
    expect(screen.getAllByText(/Amlodipine 5mg/i)[0]).toBeInTheDocument();

    // Follow-up appointment
    expect(screen.getByText('10/12/2026')).toBeInTheDocument();
  });

  it('displays "Abandoned – no conclusion" badge for abandoned visits', async () => {
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue({
      ...mockRecordDetail,
      visitStatus: 'ABANDONED',
      isAbandoned: true,
    });

    render(
      <MemoryRouter initialEntries={['/doctor/medical-records/mr-123']}>
        <Routes>
          <Route path="/doctor/medical-records/:id" element={<MedicalRecordDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Abandoned – no conclusion')).toBeInTheDocument();
  });

  it('shows Edit button and navigates when visit is editable by current doctor', async () => {
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue({
      ...mockRecordDetail,
      isEditable: true,
      visitStatus: 'IN_EXAMINATION',
    });

    render(
      <MemoryRouter initialEntries={['/doctor/medical-records/mr-123']}>
        <Routes>
          <Route path="/doctor/medical-records/:id" element={<MedicalRecordDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Chỉnh sửa ca khám hiện tại')).toBeInTheDocument();
  });

  it('supports opening image modal and triggers file access log', async () => {
    const user = userEvent.setup();
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue(mockRecordDetail);
    medicalRecordApi.recordFileAccessLog.mockResolvedValue({});

    render(
      <MemoryRouter initialEntries={['/doctor/medical-records/mr-123']}>
        <Routes>
          <Route path="/doctor/medical-records/:id" element={<MedicalRecordDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    const imageBtn = await screen.findByText('ecg_result.jpg');
    await user.click(imageBtn);

    expect(medicalRecordApi.recordFileAccessLog).toHaveBeenCalledWith('att-1');
  });

  it('opens the UC-53 export dialog for a closed record', async () => {
    const user = userEvent.setup();
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue(mockRecordDetail);

    render(
      <MemoryRouter initialEntries={['/doctor/medical-records/mr-123']}>
        <Routes>
          <Route path="/doctor/medical-records/:id" element={<MedicalRecordDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await user.click(await screen.findByRole('button', { name: /Xuất hồ sơ bệnh án/i }));

    expect(await screen.findByRole('dialog', { name: 'Xuất hồ sơ bệnh án (PDF)' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Mục đích xuất/)).toBeInTheDocument();
  });

  it('disables the export of a record that is not closed (MSG131)', async () => {
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue({ ...mockRecordDetail, status: 'OPEN' });

    render(
      <MemoryRouter initialEntries={['/doctor/medical-records/mr-123']}>
        <Routes>
          <Route path="/doctor/medical-records/:id" element={<MedicalRecordDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    const exportBtn = await screen.findByRole('button', { name: /Xuất hồ sơ bệnh án/i });
    expect(exportBtn).toBeDisabled();
    expect(exportBtn).toHaveAttribute('title', 'Chỉ xuất được hồ sơ bệnh án đã đóng.');
  });
});
