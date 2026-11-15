import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useParams } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { MSG } from '../../components/patients/patientSearch';
import ReceptionistPatientsPage from './ReceptionistPatientsPage';

const patient = (overrides) => ({
    id: 'p-1',
    mabenhnhan: 'BN1234567',
    hoten: 'Nguyễn Văn An',
    ngaysinh: '1990-01-01',
    gioitinh: 1,
    cccdSo: '012345678901',
    sodienthoai: '0901234567',
    lastVisitAt: null,
    activeVisit: null,
    ...overrides,
});

const page = (content, totalElements = content.length) => ({ content, totalElements });

function CreatePage() {
    const location = useLocation();
    return <p>Trang tạo hồ sơ {JSON.stringify(location.state?.prefill)}</p>;
}

function DetailPage() {
    const { id } = useParams();
    return <p>Hồ sơ {id}</p>;
}

function renderPage(patientApi) {
    return render(
        <MemoryRouter initialEntries={['/receptionist/patients']}>
            <Routes>
                <Route path="/receptionist/patients" element={<ReceptionistPatientsPage patientApi={patientApi} />} />
                <Route path="/receptionist/patients/new" element={<CreatePage />} />
                <Route path="/receptionist/patients/:id" element={<DetailPage />} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('ReceptionistPatientsPage (UC-35)', () => {
    it('lists patients on load without a search type', async () => {
        const patientApi = { list: vi.fn().mockResolvedValue(page([patient()])) };

        renderPage(patientApi);

        expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument();
        expect(patientApi.list).toHaveBeenCalledWith({ page: 0, size: 20 });
    });

    it('filters by citizen ID while typing and never calls the API with a wrong keyword', async () => {
        const user = userEvent.setup();
        const patientApi = { list: vi.fn().mockResolvedValue(page([])) };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        expect(screen.getByLabelText('Tìm theo')).toHaveValue('CITIZEN_ID');
        expect(screen.queryByRole('button', { name: 'Tìm kiếm' })).not.toBeInTheDocument();
        await user.type(screen.getByLabelText('Từ khóa'), '12a');

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.INVALID_FORMAT);
        expect(patientApi.list).toHaveBeenCalledTimes(1);

        await user.clear(screen.getByLabelText('Từ khóa'));
        await user.type(screen.getByLabelText('Từ khóa'), '0123');

        await vi.waitFor(() => expect(patientApi.list)
            .toHaveBeenLastCalledWith({ page: 0, size: 20, type: 'CITIZEN_ID', keyword: '0123' }));
    });

    it('finds patients by part of the patient code', async () => {
        const user = userEvent.setup();
        const patientApi = {
            list: vi.fn().mockImplementation((params) => Promise.resolve(
                page(params.keyword ? [patient({ mabenhnhan: 'BN00000003' })] : []))),
        };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        await user.selectOptions(screen.getByLabelText('Tìm theo'), 'PATIENT_CODE');
        await user.type(screen.getByLabelText('Từ khóa'), 'BN0000');

        expect(await screen.findByText('BN00000003')).toBeInTheDocument();
        expect(patientApi.list).toHaveBeenLastCalledWith({ page: 0, size: 20, type: 'PATIENT_CODE', keyword: 'BN0000' });
    });

    it('opens the patient detail when a row is clicked', async () => {
        const user = userEvent.setup();
        const patientApi = { list: vi.fn().mockResolvedValue(page([patient()])) };
        renderPage(patientApi);

        const row = (await screen.findByText('Nguyễn Văn An')).closest('tr');
        await user.click(within(row).getByText('012345678901'));

        expect(await screen.findByText('Hồ sơ p-1')).toBeInTheDocument();
    });

    it('shows the last visit and the active visit of each result', async () => {
        const found = patient({
            lastVisitAt: '2026-11-01T02:00:00Z',
            activeVisit: { registrationCode: 'VV001', status: 'WAITING', roomName: 'Phòng khám Nội 1' },
        });
        const patientApi = { list: vi.fn().mockResolvedValue(page([found])) };
        renderPage(patientApi);

        const row = (await screen.findByText('Nguyễn Văn An')).closest('tr');
        expect(within(row).getByText('Chờ khám')).toBeInTheDocument();
        expect(within(row).getByText('VV001 · Phòng khám Nội 1')).toBeInTheDocument();
        expect(within(row).getByText(new Date('2026-11-01T02:00:00Z').toLocaleDateString('vi-VN'))).toBeInTheDocument();
    });

    it('pages through search results and keeps the search on every page', async () => {
        const user = userEvent.setup();
        const patientApi = {
            list: vi.fn().mockImplementation((params) => Promise.resolve(params.page === 1
                ? page([patient({ id: 'p-2', hoten: 'Nguyễn Thị Bình' })], 45)
                : page(params.keyword ? [patient()] : [], params.keyword ? 45 : 0))),
        };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        await user.selectOptions(screen.getByLabelText('Tìm theo'), 'NAME');
        await user.type(screen.getByLabelText('Từ khóa'), 'Nguyễn');

        expect(await screen.findByText('Trang 1/3')).toBeInTheDocument();
        expect(screen.getByText('Có 45 bệnh nhân')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Trang trước' })).toBeDisabled();

        await user.click(screen.getByRole('button', { name: 'Trang sau' }));

        expect(await screen.findByText('Nguyễn Thị Bình')).toBeInTheDocument();
        expect(screen.getByText('Trang 2/3')).toBeInTheDocument();
        expect(patientApi.list).toHaveBeenLastCalledWith({ page: 1, size: 20, type: 'NAME', keyword: 'Nguyễn' });
    });

    it('offers to create a profile pre-filled with the keyword when nothing matches', async () => {
        const user = userEvent.setup();
        const patientApi = { list: vi.fn().mockResolvedValue(page([])) };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        await user.type(screen.getByLabelText('Từ khóa'), '012345678901');
        await screen.findByText('Kết quả tìm kiếm');
        const emptyRow = (await screen.findByText(MSG.NO_RESULT)).closest('td');
        await user.click(within(emptyRow).getByRole('button', { name: 'Tạo hồ sơ bệnh nhân mới' }));

        expect(screen.getByText('Trang tạo hồ sơ {"cccdSo":"012345678901"}')).toBeInTheDocument();
    });

    it('shows a Vietnamese message instead of the backend message on failure', async () => {
        const patientApi = { list: vi.fn().mockRejectedValue(new ApiError('Forbidden raw', { status: 403 })) };

        renderPage(patientApi);

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.FORBIDDEN);
        expect(screen.queryByText('Forbidden raw')).not.toBeInTheDocument();
    });
});
