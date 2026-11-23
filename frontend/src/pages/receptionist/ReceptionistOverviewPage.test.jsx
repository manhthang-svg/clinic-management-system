import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import patientApi from '../../apis/patientApi';
import ReceptionistOverviewPage from './ReceptionistOverviewPage';

vi.mock('../../apis/patientApi', () => ({ default: { list: vi.fn() } }));
vi.mock('../../apis/visitApi', () => ({ default: { getActiveExaminationRooms: vi.fn().mockResolvedValue([]) } }));

function patient(index) {
  return {
    id: `patient-${index}`, mabenhnhan: `BN${String(index).padStart(7, '0')}`, hoten: `Bệnh nhân ${index}`,
    ngaysinh: '1990-01-01', gioitinh: 1,
  };
}

function page(from, count, total) {
  return { data: { content: Array.from({ length: count }, (_, i) => patient(from + i)), totalElements: total } };
}

describe('ReceptionistOverviewPage', () => {
  beforeEach(() => {
    patientApi.list.mockReset();
  });

  it('lists the patients 20 per page and loads the next page from the server', async () => {
    const user = userEvent.setup();
    patientApi.list.mockImplementation((params) => Promise.resolve(params.page === 1
      ? page(21, 20, 45)
      : page(1, 20, 45)));
    render(<MemoryRouter><ReceptionistOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Bệnh nhân 20')).toBeInTheDocument();
    expect(patientApi.list).toHaveBeenLastCalledWith({ keyword: undefined, page: 0, size: 20 });
    expect(screen.getByText('Hiển thị 1–20 trên tổng số 45 bệnh nhân')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Trang trước' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));

    expect(await screen.findByText('Bệnh nhân 21')).toBeInTheDocument();
    expect(patientApi.list).toHaveBeenLastCalledWith({ keyword: undefined, page: 1, size: 20 });
    expect(screen.getByText('Trang 2/3')).toBeInTheDocument();
  });

  it('hides the pagination when all patients fit on one page', async () => {
    patientApi.list.mockResolvedValue(page(1, 3, 3));
    render(<MemoryRouter><ReceptionistOverviewPage /></MemoryRouter>);

    expect(await screen.findByText('Bệnh nhân 3')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Phân trang' })).not.toBeInTheDocument();
  });
});
