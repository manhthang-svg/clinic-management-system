import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { FIELD_MSG, MSG } from '../../components/patients/patientForm';
import ReceptionistCreatePatientPage from './ReceptionistCreatePatientPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function DetailPage() {
    const { id } = useParams();
    return <p>Hồ sơ {id}</p>;
}

function renderPage(patientApi, state) {
    return render(
        <MemoryRouter initialEntries={[{ pathname: '/receptionist/patients/new', state }]}>
            <Routes>
                <Route path="/receptionist/patients/new" element={<ReceptionistCreatePatientPage patientApi={patientApi} />} />
                <Route path="/receptionist/patients/:id" element={<DetailPage />} />
                <Route path="/receptionist/patients" element={<p>Danh sách bệnh nhân</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

async function fillAdult(user, { phone = '0901234567' } = {}) {
    await user.type(screen.getByLabelText(/Họ và tên/), 'Nguyễn Văn An');
    await user.type(screen.getByLabelText(/Ngày sinh/), '1990-01-01');
    await user.type(screen.getByLabelText(/^Địa chỉ\s*\*?$/), '12 Lê Lợi');
    if (phone) await user.type(screen.getByLabelText(/^Số điện thoại\s*\*?$/), phone);
}

describe('ReceptionistCreatePatientPage (UC-37)', () => {
    it('automatically formats full name to Title Case on blur', async () => {
        const user = userEvent.setup();
        const patientApi = { possibleDuplicates: vi.fn(), create: vi.fn() };
        renderPage(patientApi);

        const nameInput = screen.getByLabelText(/Họ và tên/);
        await user.type(nameInput, 'nguyễn   văn   an');
        await user.tab();

        expect(nameInput).toHaveValue('Nguyễn Văn An');
    });

    it('validates a field immediately on blur and marks it with error message', async () => {
        const user = userEvent.setup();
        const patientApi = { possibleDuplicates: vi.fn(), create: vi.fn() };
        renderPage(patientApi);

        const nameInput = screen.getByLabelText(/Họ và tên/);
        await user.type(nameInput, 'Nguyễn Văn A 123');
        await user.tab();

        expect(nameInput).toHaveAccessibleDescription(FIELD_MSG.INVALID_FORMAT);
        expect(screen.getByText(FIELD_MSG.INVALID_FORMAT)).toBeInTheDocument();

        await user.clear(nameInput);
        await user.type(nameInput, 'Nguyễn Văn A');
        await user.tab();
        expect(nameInput).not.toHaveAccessibleDescription(FIELD_MSG.INVALID_FORMAT);
    });

    it('shows each wrong field under the field and does not call the API', async () => {
        const user = userEvent.setup();
        const patientApi = { possibleDuplicates: vi.fn(), create: vi.fn() };
        renderPage(patientApi);

        await fillAdult(user, { phone: '' });
        await user.type(screen.getByLabelText('CCCD'), '123');
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));

        expect(screen.getByLabelText(/^Số điện thoại\s*\*?$/)).toHaveAccessibleDescription(FIELD_MSG.REQUIRED);
        expect(screen.getByLabelText('CCCD')).toHaveAccessibleDescription(FIELD_MSG.INVALID_FORMAT);
        expect(patientApi.create).not.toHaveBeenCalled();
    });

    it('creates the profile and opens it (step 9)', async () => {
        const user = userEvent.setup();
        const patientApi = {
            possibleDuplicates: vi.fn().mockResolvedValue([]),
            create: vi.fn().mockResolvedValue({ id: 'p-9' }),
        };
        renderPage(patientApi);

        await fillAdult(user);
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));

        expect(await screen.findByText('Hồ sơ p-9')).toBeInTheDocument();
        expect(patientApi.possibleDuplicates).toHaveBeenCalledWith('Nguyễn Văn An', '1990-01-01');
        expect(patientApi.create.mock.calls[0][0]).toMatchObject({ hoten: 'Nguyễn Văn An', cccdSo: null });
    });

    it('fills the read-only ethnicity code from the chosen ethnicity', async () => {
        const user = userEvent.setup();
        const patientApi = {
            possibleDuplicates: vi.fn().mockResolvedValue([]),
            create: vi.fn().mockResolvedValue({ id: 'p-10' }),
        };
        renderPage(patientApi);

        const code = screen.getByLabelText('Mã dân tộc');
        expect(code).toHaveAttribute('readonly');
        expect(code).toHaveValue('01');
        await fillAdult(user);
        await user.selectOptions(screen.getByLabelText(/^Dân tộc/), 'Mường');
        expect(code).toHaveValue('06');
        const occupationCodeField = screen.getByLabelText('Mã nghề nghiệp');
        expect(occupationCodeField).toHaveAttribute('readonly');
        await user.selectOptions(screen.getByLabelText('Nghề nghiệp'), 'Công nhân');
        expect(occupationCodeField).toHaveValue('04');
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));

        await screen.findByText('Hồ sơ p-10');
        expect(patientApi.create.mock.calls[0][0]).toMatchObject({
            dantoc: 'Mường', dantocMa: '06', nghenghiep: 'Công nhân', nghenghiepMa: '04',
        });
    });

    it('lets a foreigner pick a country only after choosing Ngoại kiều and fills its code', async () => {
        const user = userEvent.setup();
        const patientApi = {
            possibleDuplicates: vi.fn().mockResolvedValue([]),
            create: vi.fn().mockResolvedValue({ id: 'p-11' }),
        };
        renderPage(patientApi);

        const country = screen.getByLabelText(/^Ngoại kiều/);
        const countryCodeField = screen.getByLabelText('Mã ngoại kiều');
        expect(country).toBeDisabled();
        expect(countryCodeField).toHaveAttribute('readonly');
        await fillAdult(user);
        await user.selectOptions(screen.getByLabelText('Nghề nghiệp'), 'Ngoại kiều');
        expect(country).toBeEnabled();
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));
        expect(await screen.findByText('Trường này là bắt buộc.')).toBeInTheDocument();
        expect(patientApi.create).not.toHaveBeenCalled();

        await user.selectOptions(country, 'Nhật');
        expect(countryCodeField).toHaveValue('16');
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));

        await screen.findByText('Hồ sơ p-11');
        expect(patientApi.create.mock.calls[0][0]).toMatchObject({
            nghenghiep: 'Ngoại kiều', nghenghiepMa: '12', ngoaikieu: 'Nhật', ngoaikieuMa: '16',
        });
    });

    it('warns about possible duplicates and opens the chosen profile (AF-02)', async () => {
        const user = userEvent.setup();
        const patientApi = {
            possibleDuplicates: vi.fn().mockResolvedValue([{ id: 'p-1', mabenhnhan: 'BN1', hoten: 'Nguyễn Văn An' }]),
            create: vi.fn(),
        };
        renderPage(patientApi);

        await fillAdult(user);
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));
        const dialog = await screen.findByRole('dialog', { name: 'Có thể trùng hồ sơ' });
        await user.click(within(dialog).getByRole('button', { name: 'Chọn hồ sơ này' }));

        expect(screen.getByText('Hồ sơ p-1')).toBeInTheDocument();
        expect(patientApi.create).not.toHaveBeenCalled();
    });

    it('creates anyway after the duplicate warning (AF-02)', async () => {
        const user = userEvent.setup();
        const patientApi = {
            possibleDuplicates: vi.fn().mockResolvedValue([{ id: 'p-1', mabenhnhan: 'BN1', hoten: 'Nguyễn Văn An' }]),
            create: vi.fn().mockResolvedValue({ id: 'p-2' }),
        };
        renderPage(patientApi);

        await fillAdult(user);
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));
        await user.click(await screen.findByRole('button', { name: 'Vẫn tạo hồ sơ mới' }));

        expect(await screen.findByText('Hồ sơ p-2')).toBeInTheDocument();
    });

    it('offers to open the profile that already has the citizen ID (EF-02, MSG19)', async () => {
        const user = userEvent.setup();
        const patientApi = {
            possibleDuplicates: vi.fn().mockResolvedValue([]),
            create: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409,
                errorResponse: {
                    code: 'PATIENT_IDENTIFICATION_EXISTS',
                    details: { field: 'cccdSo', patientId: 'p-7', patientCode: 'BN7', fullName: 'Trần Thị B' },
                },
            })),
        };
        renderPage(patientApi, { prefill: { cccdSo: '012345678901' } });

        await fillAdult(user);
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent('Bệnh nhân có số CCCD này đã tồn tại (BN7 – Trần Thị B).');
        await user.click(within(alert).getByRole('button', { name: 'Mở hồ sơ' }));
        expect(screen.getByText('Hồ sơ p-7')).toBeInTheDocument();
    });

    it('asks before discarding entered data (AF-03, MSG40)', async () => {
        const user = userEvent.setup();
        renderPage({ possibleDuplicates: vi.fn(), create: vi.fn() });

        await user.type(screen.getByLabelText(/Họ và tên/), 'An');
        await user.click(screen.getByRole('button', { name: 'Hủy' }));
        const dialog = screen.getByRole('dialog', { name: 'Hủy tạo hồ sơ' });
        expect(dialog).toHaveTextContent(MSG.DISCARD);

        await user.click(within(dialog).getByRole('button', { name: 'Loại bỏ' }));
        expect(screen.getByText('Danh sách bệnh nhân')).toBeInTheDocument();
    });
});
