import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../apis/apiError';
import { createExaminedPatientApi } from '../../apis/examinedPatientApi';
import { ExaminedPatientsPage } from './ExaminedPatientsPage';

const TODAY = new Date(2026, 10, 14);

function visit(overrides = {}) {
  return {
    visitId: 'v-1', registrationCode: 'DK0001', queueDate: '2026-11-13', examinedAt: '2026-11-13T02:15:00Z',
    status: 'COMPLETED', patientType: 'BHYT', patientId: 'p-1', patientCode: 'BN00000003',
    patientName: 'Lê Văn Dũng', gender: 1, dateOfBirth: '1958-11-02', roomName: 'Phòng khám Nội 1',
    icdCode: 'J06.9', icdName: 'Nhiễm trùng hô hấp trên cấp', ...overrides,
  };
}

function result({ content = [visit()], totalElements = content.length, totalPages = 1, number = 0 } = {}) {
  return {
    from: '2026-11-01', to: '2026-11-14', examinedVisits: 7, distinctPatients: 5,
    visits: { content, page: { number, size: 20, totalElements, totalPages } },
  };
}

function renderPage(api) {
  return render(
    <MemoryRouter initialEntries={['/doctor/examined-patients']}>
      <Routes>
        <Route path="/doctor/examined-patients" element={<ExaminedPatientsPage api={api} today={TODAY} />} />
        <Route path="/doctor/clinical-examination/:visitId" element={<p>Hồ sơ khám</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ExaminedPatientsPage (UC-34)', () => {
  it('lists the visits the doctor examined this month with the totals', async () => {
    const api = { list: vi.fn().mockResolvedValue(result({
      content: [visit(), visit({
        visitId: 'v-2', patientName: 'Phạm Thị Hoa', patientCode: 'BN00000004', gender: 2,
        dateOfBirth: '1990-05-18', status: 'IN_EXAMINATION', patientType: 'DV', icdCode: null, icdName: null,
      })],
    })) };
    renderPage(api);

    const table = await screen.findByRole('table');
    expect(api.list).toHaveBeenCalledWith(
      { from: '2026-11-01', to: '2026-11-14', keyword: '', page: 0, size: 20 }, expect.any(AbortSignal));
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('13/11/2026');
    expect(rows[1]).toHaveTextContent('Lê Văn Dũng');
    expect(rows[1]).toHaveTextContent('Nam · 68 tuổi');
    expect(rows[1]).toHaveTextContent('J06.9 Nhiễm trùng hô hấp trên cấp');
    expect(rows[1]).toHaveTextContent('Hoàn thành');
    expect(rows[2]).toHaveTextContent('Nữ · 36 tuổi');
    expect(rows[2]).toHaveTextContent('Dịch vụ');
    expect(rows[2]).toHaveTextContent('Chưa kết luận');
    expect(rows[2]).toHaveTextContent('Đang khám');
  });

  it('opens the record of a visit', async () => {
    const api = { list: vi.fn().mockResolvedValue(result()) };
    const user = userEvent.setup();
    renderPage(api);

    await user.click(await screen.findByRole('button', { name: 'Xem hồ sơ Lê Văn Dũng' }));

    expect(await screen.findByText('Hồ sơ khám')).toBeInTheDocument();
  });

  it('searches by name or code from the first page', async () => {
    const api = { list: vi.fn().mockResolvedValue(result()) };
    const user = userEvent.setup();
    renderPage(api);
    await screen.findByRole('table');

    await user.type(screen.getByLabelText('Tìm bệnh nhân'), '  le van dung ');
    await user.click(screen.getByRole('button', { name: 'Tìm' }));

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith(
      { from: '2026-11-01', to: '2026-11-14', keyword: 'le van dung', page: 0, size: 20 }, expect.any(AbortSignal)));
    expect(await screen.findByText('Kết quả cho “le van dung”: 1 lượt')).toBeInTheDocument();
  });

  it('pages through a long list', async () => {
    const api = { list: vi.fn()
      .mockResolvedValueOnce(result({ totalElements: 45, totalPages: 3 }))
      .mockResolvedValueOnce(result({ totalElements: 45, totalPages: 3, number: 1 })) };
    const user = userEvent.setup();
    renderPage(api);
    expect(await screen.findByText('Trang 1 / 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Trước/ })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /Sau/ }));

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }), expect.any(AbortSignal)));
    expect(await screen.findByText('Trang 2 / 3')).toBeInTheDocument();
  });

  it('shows an empty list message', async () => {
    const api = { list: vi.fn().mockResolvedValue(result({ content: [] })) };
    renderPage(api);

    expect(await screen.findByText('Bạn chưa khám bệnh nhân nào trong khoảng thời gian này.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows a Vietnamese error and retries', async () => {
    const api = { list: vi.fn()
      .mockRejectedValueOnce(new ApiError('Forbidden', { status: 403, errorResponse: { code: 'FORBIDDEN' } }))
      .mockResolvedValueOnce(result()) };
    const user = userEvent.setup();
    renderPage(api);

    expect(await screen.findByText('Chỉ bác sĩ mới xem được danh sách bệnh nhân đã khám.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });
});

describe('examinedPatientApi', () => {
  it('sends only the filters that are set', async () => {
    const client = { request: vi.fn().mockResolvedValue(result()) };
    const api = createExaminedPatientApi(client);

    await api.list({ from: '2026-11-01', to: '2026-11-14', keyword: 'dung', page: 2, size: 20 });
    await api.list({ from: '2026-11-01', to: '2026-11-14', keyword: '', page: 0 });

    expect(client.request).toHaveBeenNthCalledWith(1,
      '/examined-patients?from=2026-11-01&to=2026-11-14&keyword=dung&page=2&size=20', { signal: undefined });
    expect(client.request).toHaveBeenNthCalledWith(2,
      '/examined-patients?from=2026-11-01&to=2026-11-14', { signal: undefined });
  });
});
