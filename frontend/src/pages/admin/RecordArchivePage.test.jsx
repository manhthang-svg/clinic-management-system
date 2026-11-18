import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RecordArchivePage } from './RecordArchivePage';
import { recordArchiveApi } from '../../apis/recordArchiveApi';
import { medicalRecordApi } from '../../apis/medicalRecordApi';

vi.mock('../../layouts', () => ({
  AdminLayout: ({ children }) => <div data-testid="admin-layout">{children}</div>,
}));

vi.mock('../../apis/recordArchiveApi', () => ({
  recordArchiveApi: {
    searchArchives: vi.fn(),
  },
}));

vi.mock('../../apis/medicalRecordApi', () => ({
  medicalRecordApi: {
    exportMedicalRecordData: vi.fn(),
  },
}));

const mockArchives = [
  {
    archiveId: 'arch-1',
    medicalRecordId: 'mr-101',
    soluutru: 'LT-DK00000001',
    soba: 'BA00000001',
    visitId: 'v-101',
    visitCode: 'DK00000001',
    admissionTime: '2026-11-10T08:00:00Z',
    patientId: 'p-1',
    patientCode: 'BN000001',
    patientName: 'Nguyễn Văn Lưu Trữ',
    dateOfBirth: '1990-01-01',
    age: 36,
    gender: 1,
    retentionCategory: 'STANDARD',
    retentionYears: 10,
    expiresOn: '2036-12-31',
    packageSha256: 'a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef',
    status: 'ARCHIVED',
    archivedAt: '2026-11-10T11:00:00Z',
  },
];

describe('RecordArchivePage (UC-79 & UC-80 / BR-43)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page header and archive rows', async () => {
    recordArchiveApi.searchArchives.mockResolvedValue({
      content: mockArchives,
      page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
    });

    render(
      <MemoryRouter>
        <RecordArchivePage currentAdmin={{ fullName: 'Admin' }} />
      </MemoryRouter>
    );

    expect(screen.getByText(/Lưu trữ Hồ sơ Bệnh án EMR/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText('LT-DK00000001')).toBeInTheDocument();
    });
    expect(screen.getByText('BA00000001')).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Văn Lưu Trữ')).toBeInTheDocument();
  });

  it('opens export modal and downloads XML file with SHA-256 for Admin (BR-43)', async () => {
    const user = userEvent.setup();
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    window.URL.revokeObjectURL = vi.fn();

    recordArchiveApi.searchArchives.mockResolvedValue({
      content: mockArchives,
      page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
    });

    medicalRecordApi.exportMedicalRecordData.mockResolvedValue({
      blob: new Blob(['<HoSoBenhAn/>']),
      fileName: 'HoSoBenhAn_BA00000001_DK00000001.xml',
      sha256: 'sha256-test-hash',
    });

    render(
      <MemoryRouter>
        <RecordArchivePage currentAdmin={{ fullName: 'Admin' }} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('LT-DK00000001')).toBeInTheDocument();
    });

    const exportBtn = screen.getByRole('button', { name: /Kết xuất \(XML\/JSON\)/i });
    await user.click(exportBtn);

    expect(screen.getByText(/Kết xuất Dữ liệu Bệnh án EMR \(Admin\)/i)).toBeInTheDocument();

    const downloadBtn = screen.getByRole('button', { name: /Tải tệp XML/i });
    await user.click(downloadBtn);

    expect(medicalRecordApi.exportMedicalRecordData).toHaveBeenCalledWith('mr-101', {
      format: 'XML',
      purpose: 'LIEN_THONG',
      recipient: null,
    });

    expect(await screen.findByText(/Đã tải tệp XML thành công!/i)).toBeInTheDocument();
    expect(screen.getByText(/sha256-test-hash/i)).toBeInTheDocument();
  });

  it('validates recipient as required when purpose is CUNG_CAP_NGUOI_BENH (BR-43)', async () => {
    const user = userEvent.setup();

    recordArchiveApi.searchArchives.mockResolvedValue({
      content: mockArchives,
      page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
    });

    render(
      <MemoryRouter>
        <RecordArchivePage currentAdmin={{ fullName: 'Admin' }} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('LT-DK00000001')).toBeInTheDocument();
    });

    const exportBtn = screen.getByRole('button', { name: /Kết xuất \(XML\/JSON\)/i });
    await user.click(exportBtn);

    const purposeSelect = screen.getByLabelText(/Mục đích xuất dữ liệu/i);
    await user.selectOptions(purposeSelect, 'CUNG_CAP_NGUOI_BENH');

    expect(screen.getByText('*')).toBeInTheDocument();

    const downloadBtn = screen.getByRole('button', { name: /Tải tệp XML/i });
    await user.click(downloadBtn);

    expect(await screen.findByText(/Đơn vị \/ người tiếp nhận là bắt buộc/i)).toBeInTheDocument();
    expect(medicalRecordApi.exportMedicalRecordData).not.toHaveBeenCalled();
  });
});

