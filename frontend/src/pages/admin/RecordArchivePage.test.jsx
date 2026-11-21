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
    patientCccd: '001090123456',
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

  it('disables download button for LIEN_THONG until facility name is entered, then downloads XML', async () => {
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
    // Ban đầu ô tên cơ sở nhận rỗng nên nút Tải tệp bị disable
    expect(downloadBtn).toBeDisabled();

    // Nhập tên cơ sở KCB nhận
    const facilityInput = screen.getByLabelText(/Tên cơ sở KCB nhận/i);
    await user.type(facilityInput, 'Bệnh viện Đa khoa Tỉnh');

    // Nút Tải tệp được kích hoạt
    expect(downloadBtn).not.toBeDisabled();
    await user.click(downloadBtn);

    expect(medicalRecordApi.exportMedicalRecordData).toHaveBeenCalledWith('mr-101', expect.objectContaining({
      format: 'XML',
      purpose: 'LIEN_THONG',
      transferFacilityName: 'Bệnh viện Đa khoa Tỉnh',
      recipient: 'Bệnh viện Đa khoa Tỉnh',
    }));

    expect(await screen.findByText(/Đã tải tệp XML thành công!/i)).toBeInTheDocument();
    expect(screen.getByText(/sha256-test-hash/i)).toBeInTheDocument();
  });

  it('auto-fills patient name and CCCD when purpose is CUNG_CAP_NGUOI_BENH (Bản thân)', async () => {
    const user = userEvent.setup();
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    window.URL.revokeObjectURL = vi.fn();

    recordArchiveApi.searchArchives.mockResolvedValue({
      content: mockArchives,
      page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
    });

    medicalRecordApi.exportMedicalRecordData.mockResolvedValue({
      blob: new Blob(['{"HoSoBenhAn":{}}']),
      fileName: 'HoSoBenhAn_BA00000001_DK00000001.json',
      sha256: 'sha256-json-hash',
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

    // Đổi định dạng sang JSON
    const jsonFormatBtn = screen.getByRole('button', { name: /Dữ liệu JSON/i });
    await user.click(jsonFormatBtn);

    // Chọn mục đích: Cung cấp cho người bệnh
    const purposeSelect = screen.getByLabelText(/Mục đích xuất dữ liệu/i);
    await user.selectOptions(purposeSelect, 'CUNG_CAP_NGUOI_BENH');

    // Mặc định chọn "Bản thân người bệnh" và tự động điền read-only
    expect(screen.getByDisplayValue('Nguyễn Văn Lưu Trữ')).toBeInTheDocument();
    expect(screen.getByDisplayValue('001090123456')).toBeInTheDocument();

    const downloadBtn = screen.getByRole('button', { name: /Tải tệp JSON/i });
    expect(downloadBtn).not.toBeDisabled();
    await user.click(downloadBtn);

    expect(medicalRecordApi.exportMedicalRecordData).toHaveBeenCalledWith('mr-101', expect.objectContaining({
      format: 'JSON',
      purpose: 'CUNG_CAP_NGUOI_BENH',
      recipientType: 'SELF',
      recipientName: 'Nguyễn Văn Lưu Trữ',
      recipientCccd: '001090123456',
    }));
  });

  it('validates 12-digit CCCD and past/present document date for Người đại diện hợp pháp', async () => {
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

    // Chọn radio "Người đại diện hợp pháp"
    const repRadio = screen.getByLabelText(/Người đại diện hợp pháp/i);
    await user.click(repRadio);

    const downloadBtn = screen.getByRole('button', { name: /Tải tệp XML/i });
    expect(downloadBtn).toBeDisabled();

    // Nhập họ tên người đại diện
    const repNameInput = screen.getByLabelText(/Họ và tên người đại diện/i);
    await user.type(repNameInput, 'Trần Thị Đại Diện');

    // Nhập CCCD sai định dạng (chỉ 5 chữ số)
    const repCccdInput = screen.getByLabelText(/Số CCCD người đại diện/i);
    await user.type(repCccdInput, '12345');
    await user.tab(); // Kích hoạt onBlur rời ô

    // Thông báo lỗi CCCD hiển thị
    expect(await screen.findByText(/Số CCCD phải gồm đúng 12 chữ số/i)).toBeInTheDocument();
    expect(downloadBtn).toBeDisabled();

    // Nhập đủ 12 chữ số hợp lệ
    await user.clear(repCccdInput);
    await user.type(repCccdInput, '001099887766');

    // Nút download được bật
    expect(downloadBtn).not.toBeDisabled();
  });

  it('auto-fills Lưu trữ nội bộ and disables inputs when purpose is LUU_TRU', async () => {
    const user = userEvent.setup();

    recordArchiveApi.searchArchives.mockResolvedValue({
      content: mockArchives,
      page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
    });

    medicalRecordApi.exportMedicalRecordData.mockResolvedValue({
      blob: new Blob(['<HoSoBenhAn/>']),
      fileName: 'HoSoBenhAn_BA00000001_DK00000001.xml',
      sha256: 'sha256-hash',
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
    await user.selectOptions(purposeSelect, 'LUU_TRU');

    expect(screen.getAllByText(/Lưu trữ nội bộ/i).length).toBeGreaterThan(0);
    const downloadBtn = screen.getByRole('button', { name: /Tải tệp XML/i });
    expect(downloadBtn).not.toBeDisabled();

    await user.click(downloadBtn);
    expect(medicalRecordApi.exportMedicalRecordData).toHaveBeenCalledWith('mr-101', expect.objectContaining({
      purpose: 'LUU_TRU',
      recipient: 'Lưu trữ nội bộ',
    }));
  });

  it('validates and submits structured form for CO_QUAN_CO_THAM_QUYEN', async () => {
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
      sha256: 'sha256-cq-hash',
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
    await user.selectOptions(purposeSelect, 'CO_QUAN_CO_THAM_QUYEN');

    const downloadBtn = screen.getByRole('button', { name: /Tải tệp XML/i });
    // Ban đầu chưa nhập đủ nên nút bị disable
    expect(downloadBtn).toBeDisabled();

    // Chọn loại cơ quan
    const typeSelect = screen.getByLabelText(/Loại cơ quan/i);
    await user.selectOptions(typeSelect, 'CONG_AN');

    // Nhập tên cơ quan
    const authNameInput = screen.getByLabelText(/Tên cơ quan \/ đơn vị/i);
    await user.type(authNameInput, 'Cơ quan CSĐT Công an Quận Cầu Giấy');

    // Nhập số văn bản
    const docNumInput = screen.getByLabelText(/Số văn bản yêu cầu/i);
    await user.type(docNumInput, '123/CV-CSĐT');

    // Nhập ngày văn bản
    const docDateInput = screen.getByLabelText(/Ngày văn bản/i);
    await user.type(docDateInput, '2026-11-15');

    // Nhập ghi chú
    const notesInput = screen.getByLabelText(/Ghi chú/i);
    await user.type(notesInput, 'Phục vụ điều tra vụ án');

    // Nút download được bật
    expect(downloadBtn).not.toBeDisabled();
    await user.click(downloadBtn);

    expect(medicalRecordApi.exportMedicalRecordData).toHaveBeenCalledWith('mr-101', expect.objectContaining({
      format: 'XML',
      purpose: 'CO_QUAN_CO_THAM_QUYEN',
      authorityType: 'CONG_AN',
      authorityName: 'Cơ quan CSĐT Công an Quận Cầu Giấy',
      documentNumber: '123/CV-CSĐT',
      documentDate: '2026-11-15',
      documentNotes: 'Phục vụ điều tra vụ án',
    }));

    expect(await screen.findByText(/Đã tải tệp XML thành công!/i)).toBeInTheDocument();
    expect(screen.getByText(/sha256-cq-hash/i)).toBeInTheDocument();
  });

  it('shows custom text input when authorityType is KHAC and validates it', async () => {
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
      sha256: 'sha256-cq-hash-2',
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
    await user.selectOptions(purposeSelect, 'CO_QUAN_CO_THAM_QUYEN');

    const downloadBtn = screen.getByRole('button', { name: /Tải tệp XML/i });

    // Chọn loại cơ quan: KHAC
    const typeSelect = screen.getByLabelText(/Loại cơ quan/i);
    await user.selectOptions(typeSelect, 'KHAC');

    // Trường nhập chi tiết loại cơ quan xuất hiện
    expect(screen.getByLabelText(/Chi tiết loại cơ quan/i)).toBeInTheDocument();
    expect(downloadBtn).toBeDisabled();

    // Nhập các trường thông tin bắt buộc
    await user.type(screen.getByLabelText(/Chi tiết loại cơ quan/i), 'Cơ quan Thi hành án dân sự');
    await user.type(screen.getByLabelText(/Tên cơ quan \/ đơn vị/i), 'Cục THADS TP. Hà Nội');
    await user.type(screen.getByLabelText(/Số văn bản yêu cầu/i), '45/QĐ-THADS');
    await user.type(screen.getByLabelText(/Ngày văn bản/i), '2026-11-16');

    expect(downloadBtn).not.toBeDisabled();
    await user.click(downloadBtn);

    expect(medicalRecordApi.exportMedicalRecordData).toHaveBeenCalledWith('mr-101', expect.objectContaining({
      format: 'XML',
      purpose: 'CO_QUAN_CO_THAM_QUYEN',
      authorityType: 'KHAC',
      authorityTypeOther: 'Cơ quan Thi hành án dân sự',
      authorityName: 'Cục THADS TP. Hà Nội',
      documentNumber: '45/QĐ-THADS',
      documentDate: '2026-11-16',
    }));
  });
});
