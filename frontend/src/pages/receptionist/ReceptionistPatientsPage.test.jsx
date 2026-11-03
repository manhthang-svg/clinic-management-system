import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
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

function renderPage(patientApi) {
    return render(
        <MemoryRouter initialEntries={['/receptionist/patients']}>
            <Routes>
                <Route path="/receptionist/patients" element={<ReceptionistPatientsPage patientApi={patientApi} />} />
                <Route path="/receptionist/patients/new" element={<CreatePage />} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('ReceptionistPatientsPage (UC-35)', () => {
    it('lists patients on load without a search type', async () => {
        const patientApi = { list: vi.fn().mockResolvedValue(page([patient()])) };

        renderPage(patientApi);

        expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument();
        expect(patientApi.list).toHaveBeenCalledWith({ size: 50 });
    });

    it('searches by citizen ID by default after checking the format', async () => {
        const user = userEvent.setup();
        const patientApi = { list: vi.fn().mockResolvedValue(page([])) };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        expect(screen.getByLabelText('Tìm theo')).toHaveValue('CITIZEN_ID');
        await user.type(screen.getByLabelText('Từ khóa'), '12345');
        await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

        expect(screen.getByRole('alert')).toHaveTextContent(MSG.INVALID_FORMAT);
        expect(patientApi.list).toHaveBeenCalledTimes(1);

        await user.clear(screen.getByLabelText('Từ khóa'));
        await user.type(screen.getByLabelText('Từ khóa'), '012345678901');
        await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

        expect(patientApi.list).toHaveBeenLastCalledWith({ size: 50, type: 'CITIZEN_ID', keyword: '012345678901' });
    });

    it('shows the last visit and the active visit of each result', async () => {
        const user = userEvent.setup();
        const found = patient({
            lastVisitAt: '2026-11-01T02:00:00Z',
            activeVisit: { registrationCode: 'VV001', status: 'WAITING', roomName: 'Phòng khám Nội 1' },
        });
        const patientApi = { list: vi.fn().mockResolvedValueOnce(page([])).mockResolvedValueOnce(page([found])) };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        await user.selectOptions(screen.getByLabelText('Tìm theo'), 'NAME');
        await user.type(screen.getByLabelText('Từ khóa'), 'nguyen van an');
        await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

        const row = (await screen.findByText('Nguyễn Văn An')).closest('tr');
        expect(within(row).getByText('Chờ khám')).toBeInTheDocument();
        expect(within(row).getByText('VV001 · Phòng khám Nội 1')).toBeInTheDocument();
        expect(within(row).getByText(new Date('2026-11-01T02:00:00Z').toLocaleDateString('vi-VN'))).toBeInTheDocument();
    });

    it('asks to refine the keyword when more than 50 patients match', async () => {
        const user = userEvent.setup();
        const patientApi = { list: vi.fn().mockResolvedValueOnce(page([])).mockResolvedValueOnce(page([patient()], 72)) };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        await user.selectOptions(screen.getByLabelText('Tìm theo'), 'NAME');
        await user.type(screen.getByLabelText('Từ khóa'), 'Nguyễn');
        await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));

        expect(await screen.findByText(MSG.TOO_MANY)).toBeInTheDocument();
    });

    it('offers to create a profile pre-filled with the keyword when nothing matches', async () => {
        const user = userEvent.setup();
        const patientApi = { list: vi.fn().mockResolvedValue(page([])) };
        renderPage(patientApi);
        await screen.findByText(MSG.NO_RESULT);

        await user.type(screen.getByLabelText('Từ khóa'), '012345678901');
        await user.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
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
