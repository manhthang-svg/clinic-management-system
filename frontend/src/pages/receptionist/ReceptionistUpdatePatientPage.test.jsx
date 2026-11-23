import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { FIELD_MSG, MSG } from '../../components/patients/patientForm';
import ReceptionistUpdatePatientPage from './ReceptionistUpdatePatientPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const stored = {
    id: 'p-1',
    mabenhnhan: 'BN1234567',
    hoten: 'Nguyễn Văn An',
    ngaysinh: '1990-01-01',
    gioitinh: 1,
    cccdSo: '012345678901',
    sodienthoai: '0901234567',
    diachi: '12 Lê Lợi',
    dantoc: 'Kinh',
    dantocMa: '01',
    email: 'an@example.com',
    version: 3,
};

function DetailPage() {
    const { id } = useParams();
    return <p>Hồ sơ {id}</p>;
}

function renderPage(patientApi) {
    return render(
        <MemoryRouter initialEntries={['/receptionist/patients/p-1/edit']}>
            <Routes>
                <Route path="/receptionist/patients/:id/edit" element={<ReceptionistUpdatePatientPage patientApi={patientApi} />} />
                <Route path="/receptionist/patients/:id" element={<DetailPage />} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('ReceptionistUpdatePatientPage (UC-38)', () => {
    it('shows the current data with a read-only patient code (step 2)', async () => {
        renderPage({ detail: vi.fn().mockResolvedValue(stored), update: vi.fn() });

        expect(await screen.findByLabelText('Mã bệnh nhân')).toHaveValue('BN1234567');
        expect(screen.getByLabelText('Mã bệnh nhân')).toHaveAttribute('readonly');
        expect(screen.getByLabelText(/Họ và tên/)).toHaveValue('Nguyễn Văn An');
        expect(screen.queryByLabelText(/Dị ứng/)).not.toBeInTheDocument();
    });

    it('saves the changes with the version and opens the profile (steps 3–7)', async () => {
        const user = userEvent.setup();
        const patientApi = { detail: vi.fn().mockResolvedValue(stored), update: vi.fn().mockResolvedValue(stored) };
        renderPage(patientApi);

        const address = await screen.findByLabelText(/^Địa chỉ\s*\*?$/);
        await user.clear(address);
        await user.type(address, '34 Trần Phú');
        await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

        expect(await screen.findByText('Hồ sơ p-1')).toBeInTheDocument();
        expect(patientApi.update).toHaveBeenCalledWith('p-1', expect.objectContaining({ diachi: '34 Trần Phú', version: 3 }));
    });

    it('goes back without saving when nothing changed (AF-02)', async () => {
        const user = userEvent.setup();
        const patientApi = { detail: vi.fn().mockResolvedValue(stored), update: vi.fn() };
        renderPage(patientApi);

        await user.click(await screen.findByRole('button', { name: 'Lưu thay đổi' }));

        expect(screen.getByText('Hồ sơ p-1')).toBeInTheDocument();
        expect(patientApi.update).not.toHaveBeenCalled();
    });

    it('checks the fields like UC-37 (EF-01)', async () => {
        const user = userEvent.setup();
        const patientApi = { detail: vi.fn().mockResolvedValue(stored), update: vi.fn() };
        renderPage(patientApi);

        await user.clear(await screen.findByLabelText(/^Số điện thoại\s*\*?$/));
        await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

        expect(screen.getByLabelText(/^Số điện thoại\s*\*?$/)).toHaveAccessibleDescription(FIELD_MSG.REQUIRED);
        expect(patientApi.update).not.toHaveBeenCalled();
    });

    it('offers to open the profile that already has the citizen ID (EF-02)', async () => {
        const user = userEvent.setup();
        const patientApi = {
            detail: vi.fn().mockResolvedValue(stored),
            update: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409,
                errorResponse: {
                    code: 'PATIENT_IDENTIFICATION_EXISTS',
                    details: { field: 'cccdSo', patientId: 'p-7', patientCode: 'BN7', fullName: 'Trần Thị B' },
                },
            })),
        };
        renderPage(patientApi);

        const citizenId = await screen.findByLabelText('CCCD');
        await user.clear(citizenId);
        await user.type(citizenId, '098765432109');
        await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent('Bệnh nhân có số CCCD này đã tồn tại (BN7 – Trần Thị B).');
        await user.click(within(alert).getByRole('button', { name: 'Mở hồ sơ' }));
        expect(screen.getByText('Hồ sơ p-7')).toBeInTheDocument();
    });

    it('says the profile changed meanwhile', async () => {
        const user = userEvent.setup();
        const patientApi = {
            detail: vi.fn().mockResolvedValue(stored),
            update: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409, errorResponse: { code: 'CONCURRENT_MODIFICATION' },
            })),
        };
        renderPage(patientApi);

        await user.selectOptions(await screen.findByLabelText('Nghề nghiệp'), 'Y tế');
        await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.STALE);
    });

    it('asks before discarding changes (AF-01, MSG40)', async () => {
        const user = userEvent.setup();
        renderPage({ detail: vi.fn().mockResolvedValue(stored), update: vi.fn() });

        await user.selectOptions(await screen.findByLabelText('Nghề nghiệp'), 'Y tế');
        await user.click(screen.getByRole('button', { name: 'Hủy' }));
        const dialog = screen.getByRole('dialog', { name: 'Hủy cập nhật hồ sơ' });
        await user.click(within(dialog).getByRole('button', { name: 'Loại bỏ' }));

        expect(screen.getByText('Hồ sơ p-1')).toBeInTheDocument();
    });

    it('says the profile does not exist', async () => {
        renderPage({ detail: vi.fn().mockRejectedValue(new ApiError('raw', { status: 404 })), update: vi.fn() });

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.NOT_FOUND);
    });
});
