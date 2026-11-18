import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MedicalRecordsPage } from './MedicalRecordsPage';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';
import { ApiError } from '../../../apis/apiError';

vi.mock('../../../apis/medicalRecordApi', () => ({
  medicalRecordApi: {
    searchPatients: vi.fn(),
    getPatientVisits: vi.fn(),
    getActiveRooms: vi.fn(),
    searchIcd10: vi.fn(),
  },
}));

const mockPatients = [
  {
    patientId: 'p-1',
    patientCode: 'BN00000123',
    fullName: 'Nguyễn Văn A',
    birthDate: '1985-05-15',
    gender: 1,
    totalVisits: 3,
    lastVisitDate: '2026-11-10',
    lastDiagnosisIcd: 'J00',
    lastDiagnosisName: 'Viêm mũi họng cấp',
  },
];

const mockHistory = {
  patient: {
    patientId: 'p-1',
    patientCode: 'BN00000123',
    fullName: 'Nguyễn Văn A',
    birthDate: '1985-05-15',
    gender: 1,
  },
  visits: [
    {
      visitId: 'v-102',
      medicalRecordId: null,
      registrationCode: 'DK0000000999',
      visitDate: '2026-11-12',
      roomName: 'Phòng Khám Nội 1',
      doctorName: null,
      patientType: 'DV',
      status: 'CANCELLED',
      skipped: false,
    },
    {
      visitId: 'v-101',
      medicalRecordId: 'mr-201',
      registrationCode: 'DK0000000456',
      visitDate: '2026-11-10',
      roomName: 'Phòng Khám Nội 1',
      doctorName: 'BS.CKI Trần Văn B',
      patientType: 'BHYT',
      finalDiagnosisIcd: 'J00',
      finalDiagnosisName: 'Viêm mũi họng cấp',
      status: 'COMPLETED',
      skipped: false,
    },
  ],
};

function renderPage(path = '/doctor/medical-records') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/doctor/medical-records" element={<MedicalRecordsPage />} />
        <Route path="/doctor/medical-records/visits/:visitId" element={<p>Trang chi tiết</p>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('MedicalRecordsPage (UC-51)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    medicalRecordApi.getActiveRooms.mockResolvedValue([{ id: 'room-1', roomName: 'Phòng Khám Nội 1' }]);
    medicalRecordApi.searchPatients.mockResolvedValue({
      content: mockPatients,
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
    });
    medicalRecordApi.getPatientVisits.mockResolvedValue(mockHistory);
  });

  it('renders the level 1 patient list with the gender label and no phone number', async () => {
    renderPage();

    expect(await screen.findByText('BN00000123')).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Văn A')).toBeInTheDocument();
    expect(screen.getByText('15/05/1985')).toBeInTheDocument();
    expect(screen.getByText('Nam')).toBeInTheDocument();
    expect(screen.getByText('J00 - Viêm mũi họng cấp')).toBeInTheDocument();
    expect(screen.queryByText('0901234567')).not.toBeInTheDocument();
  });

  it('validates the date range (MSG50) and the keyword length (MSG132)', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('BN00000123');

    await user.type(screen.getByLabelText('Từ ngày'), '2026-11-20');
    await user.type(screen.getByLabelText('Đến ngày'), '2026-11-10');
    expect(await screen.findByText('Ngày bắt đầu không được sau ngày kết thúc.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Từ khóa tìm kiếm'), 'a');
    expect(screen.getByText('Vui lòng nhập ít nhất 2 ký tự để tìm kiếm.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tìm kiếm' })).toBeDisabled();
  });

  it('offers the "Đã hủy" status filter and sends it with the search', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('BN00000123');

    await user.selectOptions(screen.getByLabelText('Trạng thái:'), 'Đã hủy');
    await user.type(screen.getByLabelText('Từ khóa tìm kiếm'), 'Nguyễn');
    await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

    await waitFor(() => {
      expect(medicalRecordApi.searchPatients).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'CANCELLED', keyword: 'Nguyễn', page: 0 }),
        expect.any(AbortSignal),
      );
    });
  });

  it('opens level 2 for the selected patient with labels and visits without a record', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText('BN00000123'));

    await waitFor(() => {
      expect(medicalRecordApi.getPatientVisits).toHaveBeenCalledWith('p-1', expect.any(AbortSignal));
    });
    expect(await screen.findByText('DK0000000456')).toBeInTheDocument();
    expect(screen.getByText('Quay lại danh sách bệnh nhân')).toBeInTheDocument();
    expect(screen.getByText('Hoàn thành')).toBeInTheDocument();
    expect(screen.getByText('Đã hủy')).toBeInTheDocument();
    expect(screen.getByText('Chưa có hồ sơ')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Xem chi tiết/ })).toHaveLength(1);
  });

  it('AF-01: opens level 2 from the URL with the real patient, not a placeholder', async () => {
    renderPage('/doctor/medical-records?patientId=p-1');

    expect(await screen.findByText('Nguyễn Văn A')).toBeInTheDocument();
    expect(screen.queryByText('Bệnh nhân đang khám')).not.toBeInTheDocument();
    expect(medicalRecordApi.searchPatients).not.toHaveBeenCalled();
  });

  it('opens the UC-52 page for a visit with a record', async () => {
    const user = userEvent.setup();
    renderPage('/doctor/medical-records?patientId=p-1');

    await user.click(await screen.findByRole('button', { name: /Xem chi tiết/ }));

    expect(await screen.findByText('Trang chi tiết')).toBeInTheDocument();
  });

  it('maps errors to Vietnamese messages and never shows the backend text', async () => {
    medicalRecordApi.searchPatients.mockRejectedValue(new ApiError('raw backend text', {
      status: 400,
      errorResponse: {
        code: 'REQUEST_VALIDATION_FAILED',
        message: 'raw backend text',
        details: { keyword: 'TOO_SHORT' },
      },
    }));
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('Vui lòng nhập ít nhất 2 ký tự để tìm kiếm.');
    expect(screen.queryByText('raw backend text')).not.toBeInTheDocument();
  });

  it('shows MSG80 for an unexpected failure', async () => {
    medicalRecordApi.getPatientVisits.mockRejectedValue(new Error('network down'));
    renderPage('/doctor/medical-records?patientId=p-1');

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
  });
});
