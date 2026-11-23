import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { MOCK_RESULT_ORDERS } from '../paraclinicalResult/resultMockData';
import { ParaclinicalOrderList } from './ParaclinicalOrderList';

function order(details) {
    return [{
        id: 'o1', sophieu: 'CD1', createdAt: '2026-11-08T03:00:00Z', chandoansobo: 'Đau bụng', tongcong: 280000,
        details,
    }];
}

const ultrasound = {
    id: 'd1', machidinh: 'CD1-01', tenchidinh: 'Siêu âm ổ bụng', thanhtien: 200000, status: 'PENDING_PAYMENT', roomName: 'Phòng siêu âm 1', roomLocation: 'Tầng 2',
    cancellable: true, cancelReason: null,
};
const blood = {
    id: 'd2', machidinh: 'CD1-02', tenchidinh: 'Tổng phân tích máu', thanhtien: 80000, status: 'IN_PROGRESS',
    cancellable: false, cancelReason: null,
};

const slip = {
    orderId: 'o1', sophieu: 'CD1', reprint: false, orderedAt: '2026-11-08T03:00:00Z',
    facility: { tenCskcb: 'Phòng khám Đa khoa A', diachi: '1 Lê Lợi', sodienthoai: '0281234567' },
    patient: {
        fullName: 'Nguyễn Văn An', birthYear: 1980, age: 46, gender: 'Nam', address: 'Hà Nội',
        patientCode: 'BN00000001', visitCode: 'DK0000000001', visitType: 'DV', insuranceCardNumber: null,
    },
    orderingDepartment: 'Khoa Nội', orderingRoom: 'Phòng khám Nội 1', chandoansobo: 'Đau bụng',
    mucdochidinh: 'Thường', doctorName: 'BS. Trần Văn B',
    sheets: [
        {
            index: 1, total: 2, room: { roomCode: 'SA1', roomName: 'Phòng siêu âm 1', location: 'Tầng 2, khu B' },
            serviceGroupName: 'Siêu âm', services: [{ machidinh: 'CD1-01', tenchidinh: 'Siêu âm ổ bụng', soluong: 1 }],
        },
        {
            index: 2, total: 2, room: { roomCode: 'XN1', roomName: 'Phòng xét nghiệm', location: 'Tầng 1' },
            serviceGroupName: 'Huyết học', services: [{ machidinh: 'CD1-02', tenchidinh: 'Tổng phân tích máu', soluong: 1 }],
        },
    ],
};

describe('ParaclinicalOrderList (BR-25, BR-41)', () => {
    it('shows the performing room instead of the amount of each service', async () => {
        render(<ParaclinicalOrderList visitId="v1" api={{ getParaclinicalOrders: vi.fn().mockResolvedValue(order([ultrasound, blood])) }} />);

        const row = (await screen.findByText('Siêu âm ổ bụng')).closest('tr');
        expect(row).toHaveTextContent('Phòng siêu âm 1');
        expect(within(row).getByText('Tầng 2')).toBeInTheDocument();
        expect(within(screen.getByText('Tổng phân tích máu').closest('tr')).getByText('Chưa xếp phòng')).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Nơi thực hiện' })).toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Thành tiền (₫)' })).not.toBeInTheDocument();
        expect(screen.queryByText(/Tổng cộng/)).not.toBeInTheDocument();
    });

    it('offers to cancel only the services that can still be cancelled', async () => {
        render(<ParaclinicalOrderList visitId="v1" api={{ getParaclinicalOrders: vi.fn().mockResolvedValue(order([ultrasound, blood])) }} />);

        expect(await screen.findByRole('button', { name: 'Hủy Siêu âm ổ bụng' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Hủy Tổng phân tích máu' })).not.toBeInTheDocument();
    });

    it('cancels a refused service with its reason and shows MSG70', async () => {
        const user = userEvent.setup();
        const result = { serviceName: 'Siêu âm ổ bụng', visitStatus: 'WAITING_PARACLINICAL' };
        const api = {
            getParaclinicalOrders: vi.fn()
                .mockResolvedValueOnce(order([ultrasound, blood]))
                .mockResolvedValueOnce(order([
                    { ...ultrasound, status: 'CANCELLED', cancellable: false, cancelReason: 'Bệnh nhân từ chối' }, blood,
                ])),
            cancelParaclinicalService: vi.fn().mockResolvedValue(result),
        };
        const onCancelled = vi.fn();
        render(<ParaclinicalOrderList visitId="v1" api={api} onCancelled={onCancelled} />);

        await user.click(await screen.findByRole('button', { name: 'Hủy Siêu âm ổ bụng' }));
        const dialog = screen.getByRole('dialog', { name: 'Hủy dịch vụ Siêu âm ổ bụng' });
        expect(within(dialog).getByRole('button', { name: 'Xác nhận hủy' })).toBeDisabled();
        await user.type(within(dialog).getByLabelText('Lý do hủy *'), '  Bệnh nhân từ chối ');
        await user.click(within(dialog).getByRole('button', { name: 'Xác nhận hủy' }));

        expect(api.cancelParaclinicalService).toHaveBeenCalledWith('d1', 'Bệnh nhân từ chối');
        expect(await screen.findByText('Đã hủy dịch vụ Siêu âm ổ bụng. Khoản thu đã được tính lại.')).toBeInTheDocument();
        expect(await screen.findByText('Lý do: Bệnh nhân từ chối')).toBeInTheDocument();
        expect(onCancelled).toHaveBeenCalledWith(result);
    });

    it('explains why a service can no longer be cancelled', async () => {
        const user = userEvent.setup();
        const api = {
            getParaclinicalOrders: vi.fn().mockResolvedValue(order([ultrasound])),
            cancelParaclinicalService: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409, errorResponse: { code: 'PARACLINICAL_SERVICE_NOT_CANCELLABLE' },
            })),
        };
        render(<ParaclinicalOrderList visitId="v1" api={api} />);

        await user.click(await screen.findByRole('button', { name: 'Hủy Siêu âm ổ bụng' }));
        await user.type(screen.getByLabelText('Lý do hủy *'), 'Bệnh nhân từ chối');
        await user.click(screen.getByRole('button', { name: 'Xác nhận hủy' }));

        expect(await screen.findByRole('alert'))
            .toHaveTextContent('Dịch vụ đã được thanh toán hoặc kỹ thuật viên đã tiếp nhận, không thể hủy.');
    });

    it('asks for a reason of at least five characters (BR-97)', async () => {
        const user = userEvent.setup();
        const api = { getParaclinicalOrders: vi.fn().mockResolvedValue(order([ultrasound])), cancelParaclinicalService: vi.fn() };
        render(<ParaclinicalOrderList visitId="v1" api={api} />);

        await user.click(await screen.findByRole('button', { name: 'Hủy Siêu âm ổ bụng' }));
        await user.type(screen.getByLabelText('Lý do hủy *'), 'abcd');
        await user.click(screen.getByRole('button', { name: 'Xác nhận hủy' }));

        expect(screen.getByRole('alert')).toHaveTextContent('Lý do hủy từ 5 đến 500 ký tự.');
        expect(api.cancelParaclinicalService).not.toHaveBeenCalled();
    });
    it('prints every service with its room and location on one sheet (UC-50)', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const api = {
            getParaclinicalOrders: vi.fn().mockResolvedValue(order([ultrasound, blood])),
            prepareParaclinicalOrderPrint: vi.fn().mockResolvedValue(slip),
        };
        render(<ParaclinicalOrderList visitId="v1" api={api} />);

        await user.click(await screen.findByRole('button', { name: 'In phiếu chỉ định' }));

        expect(api.prepareParaclinicalOrderPrint).toHaveBeenCalledWith('o1');
        const sheet = await screen.findByRole('article', { name: 'Phiếu chỉ định cận lâm sàng' });
        expect(screen.getAllByRole('article', { name: 'Phiếu chỉ định cận lâm sàng' })).toHaveLength(1);
        expect(within(sheet).getByText('Phòng khám Đa khoa A')).toBeInTheDocument();
        expect(within(sheet).getByRole('img', { name: 'Mã vạch CD1' })).toBeInTheDocument();
        const rows = within(sheet).getAllByRole('row').slice(2);
        expect(rows[0]).toHaveTextContent('Siêu âm ổ bụng');
        expect(within(sheet).getByRole('columnheader', { name: 'Mã dòng chỉ định' })).toBeInTheDocument();
        expect(within(sheet).getByText('Bác sĩ chỉ định')).toBeInTheDocument();
        expect(within(sheet).getByText('DK0000000001')).toBeInTheDocument();
        expect(rows[0]).toHaveTextContent('Phòng siêu âm 1 – Tầng 2, khu B');
        expect(rows[1]).toHaveTextContent('Tổng phân tích máu');
        expect(rows[1]).toHaveTextContent('Phòng xét nghiệm – Tầng 1');
        expect(within(sheet).getByText('Thời gian chỉ định: 08/11/2026 10:00')).toBeInTheDocument();
        expect(within(sheet).queryByText(/₫/)).not.toBeInTheDocument();
        await vi.waitFor(() => expect(print).toHaveBeenCalled());
        print.mockRestore();
    });

    it('keeps one "In phiếu chỉ định" button for every print and shows MSG80 when the order was cancelled meanwhile', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const api = {
            getParaclinicalOrders: vi.fn().mockResolvedValue(order([ultrasound])),
            prepareParaclinicalOrderPrint: vi.fn()
                .mockResolvedValueOnce(slip)
                .mockRejectedValueOnce(new ApiError('raw', {
                    status: 409, errorResponse: { code: 'PARACLINICAL_ORDER_NOT_PRINTABLE' },
                })),
        };
        render(<ParaclinicalOrderList visitId="v1" api={api} />);

        await user.click(await screen.findByRole('button', { name: 'In phiếu chỉ định' }));
        expect(print).toHaveBeenCalled();
        // The same button stays after printing: no "In lại", no reprint mark, no status line.
        expect(await screen.findByRole('button', { name: 'In phiếu chỉ định' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'In lại phiếu chỉ định' })).not.toBeInTheDocument();
        expect(screen.queryByText('BẢN IN LẠI')).not.toBeInTheDocument();
        expect(screen.queryByText(/Đã mở hộp thoại in/)).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'In phiếu chỉ định' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
        expect(screen.queryByText('raw')).not.toBeInTheDocument();
        print.mockRestore();
    });

    it('hides the print button of an order whose services were all cancelled (EF-03)', async () => {
        const cancelled = { ...ultrasound, status: 'CANCELLED', cancellable: false, cancelReason: 'Bệnh nhân từ chối' };
        render(<ParaclinicalOrderList visitId="v1" api={{ getParaclinicalOrders: vi.fn().mockResolvedValue(order([cancelled])) }} />);

        expect(await screen.findByText('Phiếu chỉ định CD1')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'In phiếu chỉ định' })).not.toBeInTheDocument();
    });
});

describe('ParaclinicalOrderList – submitted results', () => {
    it('opens the result of a completed service below its row, as the technician prints it', async () => {
        const user = userEvent.setup();
        const completed = { ...blood, status: 'COMPLETED' };
        const labOrder = {
            ...MOCK_RESULT_ORDERS.find((item) => item.id === 'cls-002'),
            result: { status: 'SUBMITTED', submittedAt: '2026-11-01T08:09:00Z', values: { GLU: '11.4' } },
        };
        const resultApi = { viewForDoctor: vi.fn().mockResolvedValue(labOrder) };
        render(<ParaclinicalOrderList visitId="v1" api={{ getParaclinicalOrders: vi.fn().mockResolvedValue(order([ultrasound, completed])) }} resultApi={resultApi} />);

        // The submitted result is loaded in the background as soon as the list shows, only for completed services.
        await screen.findByText('Siêu âm ổ bụng');
        expect(resultApi.viewForDoctor).toHaveBeenCalledTimes(1);
        expect(resultApi.viewForDoctor).toHaveBeenCalledWith('d2');

        // A service without a result does not open anything.
        await user.click(screen.getByText('Siêu âm ổ bụng'));
        expect(screen.queryByRole('article', { name: 'Phiếu kết quả in' })).not.toBeInTheDocument();

        const row = screen.getByRole('row', { name: 'Xem kết quả Tổng phân tích máu' });
        await user.click(row);
        expect(await screen.findByRole('article', { name: 'Phiếu kết quả in' })).toBeInTheDocument();
        await user.click(row);
        expect(screen.queryByRole('article', { name: 'Phiếu kết quả in' })).not.toBeInTheDocument();

        // Reopening shows it at once, without asking the server again.
        await user.click(row);
        expect(screen.getByRole('article', { name: 'Phiếu kết quả in' })).toBeInTheDocument();
        expect(resultApi.viewForDoctor).toHaveBeenCalledTimes(1);
    });
});
