import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import ParaclinicalOrderDialog from './ParaclinicalOrderDialog';
import { MSG, createdMessage, estimate, groupServices, orderErrorMessage, toServiceList } from './paraclinicalOrder';

const services = [
    { id: 's1', code: 'SA-OB', name: 'Siêu âm ổ bụng', type: 'IMAGING', serviceGroupName: 'Siêu âm', clinicPrice: 200000 },
    { id: 's2', code: 'XN-CTM', name: 'Tổng phân tích máu', type: 'LAB', serviceGroupName: 'Huyết học', clinicPrice: 80000 },
    { id: 's3', code: 'DTD', name: 'Điện tim', type: 'FUNCTIONAL', clinicPrice: 50000 },
];
const visit = { id: 'v1', patientName: 'Nguyễn Văn An', patientCode: 'BN1', lydovaovien: 'Đau bụng' };

function renderDialog(api, onCreated = vi.fn()) {
    render(<ParaclinicalOrderDialog visit={visit} api={api} onClose={vi.fn()} onCreated={onCreated} />);
    return onCreated;
}

describe('ParaclinicalOrderDialog (UC-49)', () => {
    it('groups the active services by type and selects none by default (step 2)', async () => {
        renderDialog({ getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() });

        const lab = await screen.findByRole('group', { name: 'Xét nghiệm' });
        expect(within(lab).getByRole('checkbox', { name: /Tổng phân tích máu/ })).not.toBeChecked();
        expect(screen.getByRole('group', { name: 'Chẩn đoán hình ảnh' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Xác nhận chỉ định' })).toBeDisabled();
    });

    it('shows the estimated cost and creates the order (steps 3–9)', async () => {
        const user = userEvent.setup();
        const order = { paymentCode: 'HD0000000002', tongcong: 280000 };
        const api = { getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn().mockResolvedValue(order) };
        const onCreated = renderDialog(api);

        await user.click(await screen.findByRole('checkbox', { name: /Siêu âm ổ bụng/ }));
        await user.click(screen.getByRole('checkbox', { name: /Tổng phân tích máu/ }));
        expect(screen.getByTestId('cls-total')).toHaveTextContent('280.000');
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(api.orderParaclinical).toHaveBeenCalledWith({ visitId: 'v1', chandoansobo: 'Đau bụng', serviceIds: ['s1', 's2'] });
        expect(onCreated).toHaveBeenCalledWith(order);
    });

    it('filters services by keyword', async () => {
        const user = userEvent.setup();
        renderDialog({ getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() });

        await user.type(await screen.findByLabelText('Tìm dịch vụ'), 'siêu');

        expect(screen.getByRole('checkbox', { name: /Siêu âm ổ bụng/ })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: /Điện tim/ })).not.toBeInTheDocument();
    });

    it('drops a service deactivated meanwhile and reloads the list (EF-02, MSG42)', async () => {
        const user = userEvent.setup();
        const api = {
            getParaclinicalServices: vi.fn().mockResolvedValueOnce(services).mockResolvedValueOnce(services.slice(1)),
            orderParaclinical: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409,
                errorResponse: { code: 'SERVICE_INACTIVE', details: { serviceId: 's1', serviceName: 'Siêu âm ổ bụng' } },
            })),
        };
        renderDialog(api);

        await user.click(await screen.findByRole('checkbox', { name: /Siêu âm ổ bụng/ }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Dịch vụ Siêu âm ổ bụng không còn hoạt động.');
        expect(api.getParaclinicalServices).toHaveBeenCalledTimes(2);
        expect(await screen.findByText('Chưa chọn dịch vụ nào.')).toBeInTheDocument();
    });

    it('never shows the raw backend message (EF-03)', async () => {
        const user = userEvent.setup();
        renderDialog({
            getParaclinicalServices: vi.fn().mockResolvedValue(services),
            orderParaclinical: vi.fn().mockRejectedValue(new ApiError('raw', { status: 500 })),
        });

        await user.click(await screen.findByRole('checkbox', { name: /Điện tim/ }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.SYSTEM_ERROR);
    });
});

describe('paraclinicalOrder helpers', () => {
    it('reads both a list and a page', () => {
        expect(toServiceList(services)).toHaveLength(3);
        expect(toServiceList({ content: [services[0]] })).toHaveLength(1);
    });

    it('orders groups Lab, Imaging, Functional', () => {
        expect(groupServices(services).map((g) => g.type)).toEqual(['LAB', 'IMAGING', 'FUNCTIONAL']);
    });

    it('sums the selected services', () => {
        expect(estimate(services, ['s1', 's3']).total).toBe(250000);
    });

    it('builds MSG67 and maps the visit state error', () => {
        expect(createdMessage({ paymentCode: 'HD1', tongcong: 200000 }))
            .toBe('Đã tạo chỉ định cận lâm sàng. Phiếu thu HD1 – bệnh nhân trả: 200.000 ₫.');
        expect(orderErrorMessage(new ApiError('raw', { status: 409, errorResponse: { code: 'VISIT_NOT_IN_EXAMINATION' } })))
            .toBe(MSG.NOT_IN_EXAMINATION);
    });
});
