import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MedicalRecordsPage } from './MedicalRecordsPage';
import { medicalRecordApi } from '../../../apis/medicalRecordApi';

vi.mock('../../../apis/medicalRecordApi', () => ({
  medicalRecordApi: {
    searchPatients: vi.fn(),
    getPatientVisits: vi.fn(),
    getMedicalRecordDetail: vi.fn(),
    getMedicalRecordByVisitId: vi.fn(),
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
    gender: 'Nam',
    citizenId: '012345678901',
    phoneNumber: '0901234567',
    totalVisits: 3,
    lastVisitDate: '2026-11-10',
    lastDiagnosisIcd: 'J00',
    lastDiagnosisName: 'Viêm mũi họng cấp',
  },
];

const mockVisits = [
  {
    visitId: 'v-101',
    medicalRecordId: 'mr-201',
    registrationCode: 'DK0000000456',
    registeredAt: '2026-11-10T08:30:00Z',
    roomName: 'Phòng Khám Nội 1',
    doctorName: 'BS.CKI Trần Văn B',
    patientType: 'BHYT',
    finalDiagnosisIcd: 'J00',
    finalDiagnosisName: 'Viêm mũi họng cấp',
    status: 'COMPLETED',
    statusLabel: 'Hoàn thành',
  },
];

describe('MedicalRecordsPage (Screen #39)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    medicalRecordApi.getActiveRooms.mockResolvedValue([
      { id: 'room-1', roomName: 'Phòng Khám Nội 1' },
    ]);
    medicalRecordApi.searchPatients.mockResolvedValue({
      content: mockPatients,
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
    });
    medicalRecordApi.getPatientVisits.mockResolvedValue(mockVisits);
  });

  it('renders Level 1 patient list with correct columns and data', async () => {
    render(
      <MemoryRouter initialEntries={['/doctor/medical-records']}>
        <Routes>
          <Route path="/doctor/medical-records" element={<MedicalRecordsPage />} />
        </Routes>
      </MemoryRouter>
    );

    // Title
    expect(await screen.findByText('Hồ sơ bệnh án / Lịch sử hồ sơ bệnh án')).toBeInTheDocument();

    // Patient info
    expect(await screen.findByText('BN00000123')).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Văn A')).toBeInTheDocument();
    expect(screen.getByText('15/05/1985')).toBeInTheDocument();
    expect(screen.getByText('Nam')).toBeInTheDocument();
    expect(screen.getByText('10/11/2026')).toBeInTheDocument();
    expect(screen.getByText('J00 - Viêm mũi họng cấp')).toBeInTheDocument();
  });

  it('validates date range and shows error message when from > to', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/doctor/medical-records']}>
        <Routes>
          <Route path="/doctor/medical-records" element={<MedicalRecordsPage />} />
        </Routes>
      </MemoryRouter>
    );

    const fromInput = screen.getByLabelText('Từ ngày');
    const toInput = screen.getByLabelText('Đến ngày');

    await user.type(fromInput, '2026-11-20');
    await user.type(toInput, '2026-11-10');

    expect(await screen.findByText(/Ngày bắt đầu không được sau ngày kết thúc/i)).toBeInTheDocument();
  });

  it('transitions from Level 1 to Level 2 when clicking a patient row', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/doctor/medical-records']}>
        <Routes>
          <Route path="/doctor/medical-records" element={<MedicalRecordsPage />} />
        </Routes>
      </MemoryRouter>
    );

    const row = await screen.findByText('BN00000123');
    await user.click(row);

    // Should load Level 2 visits
    await waitFor(() => {
      expect(medicalRecordApi.getPatientVisits).toHaveBeenCalledWith('p-1');
    });

    // Level 2 header & data
    expect(screen.getByText('Quay lại danh sách bệnh nhân')).toBeInTheDocument();
    expect(screen.getByText('DK0000000456')).toBeInTheDocument();
    expect(screen.getByText('Phòng Khám Nội 1')).toBeInTheDocument();
    expect(screen.getByText('BS.CKI Trần Văn B')).toBeInTheDocument();
    expect(screen.getByText('Hoàn thành')).toBeInTheDocument();
  });

  it('supports AF-01 shortcut directly opening Level 2 with patientId query param', async () => {
    render(
      <MemoryRouter initialEntries={['/doctor/medical-records?patientId=p-1']}>
        <Routes>
          <Route path="/doctor/medical-records" element={<MedicalRecordsPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(medicalRecordApi.getPatientVisits).toHaveBeenCalledWith('p-1');
    });

    expect(screen.getByText('Quay lại danh sách bệnh nhân')).toBeInTheDocument();
    expect(screen.getByText('DK0000000456')).toBeInTheDocument();
  });
});
