import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { createMockResultApi } from '../../components/paraclinicalResult';
import { ParaclinicalResultPage } from './ParaclinicalResultPage';

function renderPage(orderId, api = createMockResultApi({ latency: 0 })) {
  render(
    <MemoryRouter initialEntries={[`/technician/results/${orderId}`]}>
      <Routes>
        <Route path="/technician/results/:orderId" element={<ParaclinicalResultPage api={api} />} />
        <Route path="/technician/results" element={<p>Danh sách</p>} />
      </Routes>
    </MemoryRouter>,
  );
  return api;
}

describe('ParaclinicalResultPage (UC-67, UC-68)', () => {
  it('flags a high glucose value and submits after confirmation', async () => {
    const api = renderPage('cls-002');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Glucose'), '11,4');
    expect(screen.getByText('Cao ↑')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/^Nhận xét/), 'Tăng đường huyết.');
    await user.click(screen.getByRole('button', { name: 'Gửi kết quả' }));

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('không thể sửa');
    await user.click(within(dialog).getByRole('button', { name: 'Gửi kết quả' }));

    expect(await screen.findByText(/Kết quả đã được gửi nên không thể sửa nữa/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'In phiếu kết quả' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Lưu nháp' })).not.toBeInTheDocument();
    expect((await api.get('cls-002')).result).toMatchObject({ status: 'SUBMITTED', values: { GLU: '11,4' } });
  });

  it('blocks submission while required fields are empty', async () => {
    renderPage('cls-002');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Gửi kết quả' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Vui lòng sửa các ô được đánh dấu.');
    expect(screen.getByText('Bắt buộc')).toBeInTheDocument();
  });

  it('fills every organ of the ultrasound with normal findings and saves them as one description', async () => {
    const api = renderPage('cls-004');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Điền kết quả bình thường' }));
    expect(screen.getByLabelText(/^GAN/)).toHaveValue('Kích thước bình thường, nhu mô đều, không có khối.');
    expect(screen.getByLabelText(/^TIỀN LIỆT TUYẾN/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^TỬ CUNG/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Lưu nháp' }));

    await waitFor(async () => expect((await api.get('cls-004')).result?.status).toBe('DRAFT'));
    expect((await api.get('cls-004')).result.description).toContain('GAN: Kích thước bình thường');
    expect(screen.getByText(/Lưu nháp lúc/)).toBeInTheDocument();
  });

  it.each([
    ['cls-001', 'Phiếu kết quả xét nghiệm', /^WBC/],
    ['cls-005', 'Phiếu nội soi', /^Hang vị/],
    ['cls-006', 'Phiếu chụp cắt lớp vi tính', /^Kỹ thuật/],
    ['cls-009', 'Phiếu chụp cộng hưởng từ', /^Kỹ thuật/],
    ['cls-008', 'Phiếu chiếu/chụp x-quang', /^Mô tả/],
    ['cls-007', 'Phiếu điện tim', /^QRS/],
  ])('opens %s on its own page "%s"', async (id, title, field) => {
    renderPage(id);
    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByLabelText(field)).toBeInTheDocument();
  });

  it('shows the MRI safety screening from the order', async () => {
    renderPage('cls-009');
    expect(await screen.findByText('Dị vật kim loại trong cơ thể:')).toBeInTheDocument();
  });

  it('is read-only for a service performed by another technician', async () => {
    renderPage('cls-007');

    expect(await screen.findByText(/Chỉ người thực hiện dịch vụ mới được nhập kết quả/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^QRS/)).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Gửi kết quả' })).not.toBeInTheDocument();
  });

  it('shows a submitted result with its print preview and marks the reprint as a copy', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    renderPage('cls-008');
    const user = userEvent.setup();

    expect(await screen.findByRole('article', { name: 'Phiếu kết quả in' })).toHaveTextContent('Hình ảnh tim phổi bình thường.');
    expect(screen.getByLabelText(/^Kết luận/)).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'In phiếu kết quả' }));
    expect(print).toHaveBeenCalled();
    expect(await screen.findByRole('button', { name: 'In lại (bản sao)' })).toBeInTheDocument();
    expect(screen.getByText('BẢN SAO')).toBeInTheDocument();
    print.mockRestore();
  });

  it('previews a draft but does not allow printing it', async () => {
    renderPage('cls-005');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Xem trước phiếu in' }));

    expect(screen.getByRole('article', { name: 'Phiếu kết quả in' })).toHaveTextContent('PHIẾU NỘI SOI');
    expect(screen.queryByRole('button', { name: 'In phiếu kết quả' })).not.toBeInTheDocument();
  });
});
