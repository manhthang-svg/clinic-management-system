import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MedicalRecordDetailPage } from './MedicalRecordDetailPage';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { ApiError } from '../../../apis/apiError';
import { toast } from 'sonner';
import { useSession } from '../../../services/session';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn(), info: vi.fn() } }));

vi.mock('../../../services/session', () => ({
  useSession: vi.fn(),
  apiClient: {},
}));

vi.mock('../../../apis/medicalRecordApi', () => ({
  medicalRecordApi: {
    getMedicalRecordDetail: vi.fn(),
    getMedicalRecordByVisitId: vi.fn(),
    exportPdf: vi.fn(),
    downloadResultFile: vi.fn(),
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
  gender: 1,
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
                mimeType: 'image/jpeg',
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
      medicalRecordId: 'mr-123',
      visitStatus: 'COMPLETED',
      isCurrent: true,
      diagnosisIcd: 'I10',
      diagnosisName: 'Tăng huyết áp vô căn',
    },
  ],
};

function renderDetail(path = '/doctor/medical-records/mr-123', props = {}) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/doctor/medical-records/:id" element={<MedicalRecordDetailPage {...props} />} />
        <Route path="/doctor/medical-records/visits/:visitId" element={<MedicalRecordDetailPage isVisitRoute />} />
      </Routes>
    </MemoryRouter>
  );
}

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
    // BR-52-02: the backend sends codes; the page shows their labels.
    expect(screen.getByText('Nam • 41 tuổi')).toBeInTheDocument();
    expect(screen.getByText('Đối tượng khám:').nextElementSibling).toHaveTextContent(/^BHYT/);
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

  it('displays the "Bỏ khám – không có kết luận" badge for abandoned visits (AF-02)', async () => {
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

    expect(await screen.findByText('Bỏ khám – không có kết luận')).toBeInTheDocument();
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

  it('downloads a result file through the backend and frees it on close (BR-52-06)', async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.fn(() => 'blob:result-1');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue(mockRecordDetail);
    medicalRecordApi.downloadResultFile.mockResolvedValue({ blob: new Blob(['x']), fileName: 'KQ-KQ001-1.jpg' });

    try {
      renderDetail();
      await user.click(await screen.findByRole('button', { name: /Bản ghi ECG/ }));

      expect(medicalRecordApi.downloadResultFile).toHaveBeenCalledWith('mr-123', 'att-1');
      expect(await screen.findByRole('img', { name: 'Bản ghi ECG' })).toHaveAttribute('src', 'blob:result-1');

      await user.click(screen.getByRole('button', { name: 'Đóng xem trước' }));
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:result-1');
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('shows MSG80 when the result file cannot be loaded (EF-02)', async () => {
    const user = userEvent.setup();
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue(mockRecordDetail);
    medicalRecordApi.downloadResultFile.mockRejectedValue(new ApiError('storage down', {
      status: 502,
      errorResponse: { code: 'FILE_STORAGE_UNAVAILABLE', message: 'storage down' },
    }));

    renderDetail();
    await user.click(await screen.findByRole('button', { name: /Bản ghi ECG/ }));

    expect(toast.error).toHaveBeenCalledWith('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
    expect(screen.queryByRole('img', { name: 'Bản ghi ECG' })).not.toBeInTheDocument();
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

  it('shows MSG39 when the record does not exist, never the backend message (EF-01)', async () => {
    medicalRecordApi.getMedicalRecordByVisitId.mockRejectedValue(new ApiError('raw backend text', {
      status: 404,
      errorResponse: { code: 'MEDICAL_RECORD_NOT_FOUND', message: 'raw backend text' },
    }));

    renderDetail('/doctor/medical-records/visits/v-9');

    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy hồ sơ bệnh án.');
    expect(screen.queryByText('raw backend text')).not.toBeInTheDocument();
    expect(medicalRecordApi.getMedicalRecordByVisitId).toHaveBeenCalledWith('v-9', expect.any(AbortSignal));
  });

  it('lets the Doctor of the visit print the slips of a closed record (UC-57, UC-60, UC-62 AF-01)', async () => {
    const user = userEvent.setup();
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue(mockRecordDetail);
    const printApi = {
      getConclusionPrint: vi.fn().mockReturnValue(new Promise(() => {})),
      prepareConclusionPrint: vi.fn(),
      getPrescriptionPrint: vi.fn(),
      preparePrescriptionPrint: vi.fn(),
      getFollowUpPrint: vi.fn(),
      prepareFollowUpPrint: vi.fn(),
    };

    renderDetail(undefined, { currentAccountId: 'doc-1', printApi });

    await screen.findByText('Nguyễn Văn A');
    expect(screen.getByRole('button', { name: 'In đơn thuốc' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'In giấy hẹn' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'In kết luận' }));
    expect(printApi.getConclusionPrint).toHaveBeenCalledWith('v-1', expect.any(AbortSignal));
  });

  it('shows no print action to another Doctor or for an open record', async () => {
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue(mockRecordDetail);
    const { unmount } = renderDetail(undefined, { currentAccountId: 'doc-2' });

    await screen.findByText('Nguyễn Văn A');
    expect(screen.queryByRole('button', { name: 'In kết luận' })).not.toBeInTheDocument();
    unmount();

    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue({ ...mockRecordDetail, status: 'OPEN' });
    renderDetail(undefined, { currentAccountId: 'doc-1' });

    await screen.findByText('Nguyễn Văn A');
    expect(screen.queryByRole('button', { name: 'In kết luận' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'In đơn thuốc' })).not.toBeInTheDocument();
  });

  it('notes a record that was unlocked again (BR-52-02)', async () => {
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue({ ...mockRecordDetail, unlockedAt: '2026-11-11T02:00:00Z' });

    renderDetail();

    expect(await screen.findByText(/Hồ sơ đã được mở khóa lại lúc/)).toBeInTheDocument();
  });

  it('lists every visit on one line each and opens another visit with a record (BR-52-08)', async () => {
    const user = userEvent.setup();
    medicalRecordApi.getMedicalRecordDetail.mockResolvedValue({
      ...mockRecordDetail,
      visitId: 'v-1',
      visitTimeline: [
        ...mockRecordDetail.visitTimeline,
        {
          visitId: 'v-2',
          visitCode: 'DK0000000999',
          visitDate: '2026-10-27',
          roomName: 'Phòng Khám Nội 2',
          medicalRecordId: null,
          visitStatus: 'ABANDONED',
          isCurrent: false,
        },
        {
          visitId: 'v-3',
          visitCode: 'DK0000000777',
          visitDate: '2026-09-01',
          roomName: 'Phòng Khám Nội 1',
          medicalRecordId: 'mr-3',
          visitStatus: 'COMPLETED',
          isCurrent: false,
        },
      ],
    });
    medicalRecordApi.getMedicalRecordByVisitId.mockResolvedValue({ ...mockRecordDetail, visitId: 'v-3' });

    renderDetail();

    const list = await screen.findByRole('list');
    const current = within(list).getByText('DK0000000456').closest('button');
    expect(current).toHaveAttribute('aria-current', 'page');
    expect(within(current).getByText('Đang xem')).toBeInTheDocument();
    const noRecord = within(list).getByText('DK0000000999').closest('button');
    expect(noRecord).toBeDisabled();
    expect(within(noRecord).getByText('Bỏ khám')).toBeInTheDocument();
    expect(within(noRecord).getByText('Chưa có hồ sơ')).toBeInTheDocument();

    await user.click(within(list).getByText('DK0000000777'));
    expect(medicalRecordApi.getMedicalRecordByVisitId).toHaveBeenCalledWith('v-3', expect.any(AbortSignal));
  });
});
