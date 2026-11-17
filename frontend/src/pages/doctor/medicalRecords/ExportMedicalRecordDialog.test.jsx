import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../apis/apiError';
import { ExportMedicalRecordDialog } from './ExportMedicalRecordDialog';

vi.mock('sonner', () => ({ toast: { success: vi.fn() } }));

function setup(api) {
  const save = vi.fn();
  const onClose = vi.fn();
  render(
    <ExportMedicalRecordDialog
      medicalRecordId="mr-1"
      visitCode="DK0000000001"
      onClose={onClose}
      api={api}
      save={save}
    />
  );
  return { save, onClose, user: userEvent.setup() };
}

describe('ExportMedicalRecordDialog (UC-53)', () => {
  it('exports with the purpose and downloads the file', async () => {
    const file = { blob: new Blob(['%PDF']), fileName: 'BA-LT-1.pdf' };
    const api = { exportPdf: vi.fn().mockResolvedValue(file) };
    const { save, onClose, user } = setup(api);

    expect(screen.getByRole('button', { name: 'Xuất PDF' })).toBeDisabled();
    await user.selectOptions(screen.getByLabelText(/Mục đích xuất/), 'LUU_TRU');
    await user.click(screen.getByRole('button', { name: 'Xuất PDF' }));

    expect(api.exportPdf).toHaveBeenCalledWith('mr-1', { purpose: 'LUU_TRU', recipient: '' });
    expect(save).toHaveBeenCalledWith(file);
    expect(onClose).toHaveBeenCalled();
  });

  it('requires the recipient when the record goes to the patient (BR-53-03)', async () => {
    const api = { exportPdf: vi.fn() };
    const { user } = setup(api);

    await user.selectOptions(screen.getByLabelText(/Mục đích xuất/), 'CUNG_CAP_NGUOI_BENH');
    await user.click(screen.getByRole('button', { name: 'Xuất PDF' }));

    expect(screen.getByText('Người nhận là bắt buộc.')).toBeInTheDocument();
    expect(api.exportPdf).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText(/Người nhận/), '  Nguyễn Thị B  ');
    api.exportPdf.mockResolvedValue({ blob: new Blob(['%PDF']), fileName: 'a.pdf' });
    await user.click(screen.getByRole('button', { name: 'Xuất PDF' }));

    expect(api.exportPdf).toHaveBeenCalledWith('mr-1', { purpose: 'CUNG_CAP_NGUOI_BENH', recipient: 'Nguyễn Thị B' });
  });

  it('shows MSG131 for a record that is not closed and MSG80 for a failed export', async () => {
    const api = {
      exportPdf: vi.fn()
        .mockRejectedValueOnce(new ApiError('raw', {
          status: 409, errorResponse: { code: 'EXPORT_RECORD_NOT_CLOSED', message: 'raw' },
        }))
        .mockRejectedValueOnce(new ApiError('raw', {
          status: 500, errorResponse: { code: 'EXPORT_FAILED', message: 'raw' },
        })),
    };
    const { save, onClose, user } = setup(api);

    await user.selectOptions(screen.getByLabelText(/Mục đích xuất/), 'LUU_TRU');
    await user.click(screen.getByRole('button', { name: 'Xuất PDF' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Chỉ xuất được hồ sơ bệnh án đã đóng.');

    await user.click(screen.getByRole('button', { name: 'Xuất PDF' }));
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
    expect(save).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('puts a field error from the backend under its field', async () => {
    const api = {
      exportPdf: vi.fn().mockRejectedValue(new ApiError('raw', {
        status: 422,
        errorResponse: { code: 'EXPORT_INVALID', details: { field: 'recipient', reason: 'TOO_LONG' } },
      })),
    };
    const { user } = setup(api);

    await user.selectOptions(screen.getByLabelText(/Mục đích xuất/), 'LUU_TRU');
    await user.click(screen.getByRole('button', { name: 'Xuất PDF' }));

    expect(await screen.findByText('Vượt quá độ dài tối đa 200 ký tự.')).toBeInTheDocument();
  });
});
