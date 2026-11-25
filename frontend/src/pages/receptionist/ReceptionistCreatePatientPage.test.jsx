import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { FIELD_MSG, MSG } from '../../components/patients/patientForm';
import ReceptionistCreatePatientPage from './ReceptionistCreatePatientPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
// danh muc don vi hanh chinh gia: test khong goi mang that
vi.mock('../../apis/administrativeUnitApi', () => ({
    default: {
        provinces: vi.fn(async () => [{ code: '01', name: 'Thành phố Hà Nội' }, { code: '79', name: 'Thành phố Hồ Chí Minh' }]),
        wards: vi.fn(async (code) => (code === '01'
            ? [{ code: '00004', name: 'Phường Ba Đình' }, { code: '00008', name: 'Phường Ngọc Hà' }]
            : [{ code: '26734', name: 'Phường Sài Gòn' }])),
    },
}));

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

// paste the longer text fields: typing every character makes these tests slow under a full parallel run
async function paste(user, field, text) {
    await user.click(field);
    await user.paste(text);
}

async function fillAdult(user, { phone = '0901234567' } = {}) {
    await user.type(screen.getByLabelText(/Họ và tên/), 'Nguyễn Văn An');
    await user.type(screen.getByLabelText(/Ngày sinh/), '1990-01-01');
    await paste(user, screen.getByLabelText(/^Địa chỉ\s*\*?$/), '12 Lê Lợi');
    await user.selectOptions(screen.getByLabelText(/^Nghề nghiệp/), 'Công nhân');
    await paste(user, screen.getByLabelText(/^Tỉnh\/thành phố/), 'Thành phố Hà Nội');
    await paste(user, screen.getByLabelText(/^Xã\/phường/), 'Phường Ba Đình');
    if (phone) await user.type(screen.getByLabelText(/^Số điện thoại\s*\*?$/), phone);
}

describe('ReceptionistCreatePatientPage (UC-37)', () => {
    it('returns to patient management from the create-page breadcrumb', async () => {
        const user = userEvent.setup();
        renderPage({ possibleDuplicates: vi.fn(), create: vi.fn() });

        await user.click(screen.getByRole('button', { name: 'Quản lý bệnh nhân' }));

        expect(screen.getByText('Danh sách bệnh nhân')).toBeInTheDocument();
    });

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

    it('suggests provinces and wards in a dropdown, filters without accents and fills their codes (BR-89)', async () => {
        const user = userEvent.setup();
        const patientApi = {
            possibleDuplicates: vi.fn().mockResolvedValue([]),
            create: vi.fn().mockResolvedValue({ id: 'p-11' }),
        };
        renderPage(patientApi);

        expect(screen.queryByLabelText(/Mã xã\/phường/)).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/Mã tỉnh\/thành/)).not.toBeInTheDocument();
        const province = screen.getByRole('combobox', { name: /^Tỉnh\/thành phố/ });
        const ward = screen.getByRole('combobox', { name: /^Xã\/phường/ });

        // bam vao o thi bang goi y hien ca danh sach
        await user.click(province);
        const provinceList = await screen.findByRole('listbox', { name: 'Danh sách Tỉnh/thành phố' });
        await vi.waitFor(() => expect(within(provinceList).getAllByRole('option')).toHaveLength(2));

        // go khong dau van loc ra ten co dau; chon bang chuot
        await user.type(province, 'ho chi');
        expect(within(screen.getByRole('listbox')).getAllByRole('option').map((o) => o.textContent))
            .toEqual(['Thành phố Hồ Chí Minh']);
        await user.click(screen.getByRole('option', { name: 'Thành phố Hồ Chí Minh' }));
        expect(province).toHaveValue('Thành phố Hồ Chí Minh');
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

        await user.click(ward);
        await user.click(await screen.findByRole('option', { name: 'Phường Sài Gòn' }));
        expect(ward).toHaveValue('Phường Sài Gòn');

        // doi tinh thi xa da chon bi xoa; chon bang ban phim
        await user.clear(province);
        await user.type(province, 'ha noi');
        await user.keyboard('{ArrowDown}{Enter}');
        expect(province).toHaveValue('Thành phố Hà Nội');
        expect(ward).toHaveValue('');
        await user.click(ward);
        await user.type(ward, 'ba dinh');
        await user.click(await screen.findByRole('option', { name: 'Phường Ba Đình' }));

        await user.type(screen.getByLabelText(/Họ và tên/), 'Nguyễn Văn An');
        await user.type(screen.getByLabelText(/Ngày sinh/), '1990-01-01');
        await paste(user, screen.getByLabelText(/^Địa chỉ\s*\*?$/), '12 Lê Lợi');
        await user.selectOptions(screen.getByLabelText(/^Nghề nghiệp/), 'Công nhân');
        await user.type(screen.getByLabelText(/^Số điện thoại\s*\*?$/), '0901234567');
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));

        expect(await screen.findByText('Hồ sơ p-11')).toBeInTheDocument();
        expect(patientApi.create.mock.calls[0][0]).toMatchObject({
            tinhthanh: 'Thành phố Hà Nội', tinhthanhMa: '01', xaphuong: 'Phường Ba Đình', xaphuongMa: '00004',
        });
    }, 15000);

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
        // ma nghe nghiep, ma ngoai kieu tu dien ngam, khong con o hien tren form
        expect(screen.queryByLabelText('Mã nghề nghiệp')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Mã ngoại kiều')).not.toBeInTheDocument();
        await user.selectOptions(screen.getByLabelText(/^Nghề nghiệp/), 'Công nhân');
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
        expect(country).toBeDisabled();
        await fillAdult(user);
        await user.selectOptions(screen.getByLabelText(/^Nghề nghiệp/), 'Ngoại kiều');
        expect(country).toBeEnabled();
        await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));
        expect(await screen.findByText('Trường này là bắt buộc.')).toBeInTheDocument();
        expect(patientApi.create).not.toHaveBeenCalled();

        await user.selectOptions(country, 'Nhật');
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
