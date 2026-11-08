import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { ParaclinicalOrderList } from './ParaclinicalOrderList';

function order(details) {
    return [{
        id: 'o1', sophieu: 'CD1', createdAt: '2026-11-08T03:00:00Z', chandoansobo: 'Đau bụng', tongcong: 280000,
        details,
    }];
}

const ultrasound = {
    id: 'd1', machidinh: 'CD1-01', tenchidinh: 'Siêu âm ổ bụng', thanhtien: 200000, status: 'PENDING_PAYMENT',
    cancellable: true, cancelReason: null,
};
const blood = {
    id: 'd2', machidinh: 'CD1-02', tenchidinh: 'Tổng phân tích máu', thanhtien: 80000, status: 'IN_PROGRESS',
    cancellable: false, cancelReason: null,
};

describe('ParaclinicalOrderList (BR-25, BR-41)', () => {
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
});
