import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import ParaclinicalOrderPanel from './ParaclinicalOrderPanel';
import {
    MSG, createdMessage, estimate, filterServices, groupOptions, orderErrorMessage, toServiceList,
} from './paraclinicalOrder';

const services = [
    { id: 's1', code: 'SA-OB', name: 'Siêu âm ổ bụng', type: 'IMAGING', serviceGroupId: 'g2', serviceGroupName: 'Siêu âm', clinicPrice: 200000, insurancePrice: 43900 },
    { id: 's2', code: 'XN-CTM', name: 'Tổng phân tích máu', type: 'LAB', serviceGroupId: 'g1', serviceGroupName: 'Huyết học', clinicPrice: 80000, insurancePrice: 46200 },
    { id: 's3', code: 'DTD', name: 'Điện tim', type: 'FUNCTIONAL', clinicPrice: 50000, insurancePrice: null },
];
const visit = { id: 'v1', patientName: 'Nguyễn Văn An', patientCode: 'BN1', lydovaovien: 'Đau bụng' };

function renderPanel(api, { onCreated = vi.fn(), preliminaryDiagnosis, forVisit = visit } = {}) {
    render(<ParaclinicalOrderPanel visit={forVisit} preliminaryDiagnosis={preliminaryDiagnosis} api={api} onCancel={vi.fn()} onCreated={onCreated} />);
    return onCreated;
}

describe('ParaclinicalOrderPanel (UC-49)', () => {
    it('lists the active services with both prices and selects none by default (step 2)', async () => {
        renderPanel({ getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() });

        const catalog = await screen.findByRole('region', { name: 'Danh sách dịch vụ' });
        expect(await within(catalog).findByRole('checkbox', { name: 'Chọn Tổng phân tích máu' })).not.toBeChecked();
        expect(within(catalog).getByText('Không thuộc BHYT')).toBeInTheDocument();
        expect(screen.getByText('Chưa chọn dịch vụ nào.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Xác nhận chỉ định' })).toBeDisabled();
    });

    it('shows the selection with its cost and creates the order (steps 3–9)', async () => {
        const user = userEvent.setup();
        const order = { paymentCode: 'HD0000000002', tongcong: 280000 };
        const api = { getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn().mockResolvedValue(order) };
        const onCreated = renderPanel(api, { preliminaryDiagnosis: 'Viêm dạ dày' });

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));
        await user.click(screen.getByRole('checkbox', { name: 'Chọn Tổng phân tích máu' }));
        expect(screen.getByRole('region', { name: 'Dịch vụ đã chọn (2)' })).toBeInTheDocument();
        expect(screen.getByTestId('cls-total')).toHaveTextContent('280.000');
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(api.orderParaclinical).toHaveBeenCalledWith({ visitId: 'v1', chandoansobo: 'Viêm dạ dày', serviceIds: ['s1', 's2'] });
        expect(onCreated).toHaveBeenCalledWith(order);
    });

    it('splits the insurance and patient amounts for an insured visit (step 4, BR-27)', async () => {
        const user = userEvent.setup();
        renderPanel(
            { getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() },
            { forVisit: { ...visit, doituongbnLoai: 'BHYT', insuranceCoverageRate: 80, insuranceScopeTotal: 400000, coPaymentExemptionThreshold: 351000 } },
        );

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));
        await user.click(screen.getByRole('checkbox', { name: 'Chọn Điện tim' }));
        const selected = screen.getByRole('region', { name: 'Dịch vụ đã chọn (2)' });

        expect(within(selected).getByRole('columnheader', { name: 'BHYT trả' })).toBeInTheDocument();
        expect(within(selected).queryByRole('columnheader', { name: 'Thành tiền' })).not.toBeInTheDocument();
        // 43 900 × 80% = 35 120 covered; the uncovered ECG is paid in full.
        expect(screen.getByTestId('cls-insurance-total')).toHaveTextContent('35.120');
        expect(screen.getByTestId('cls-total')).toHaveTextContent('214.880');
        expect(within(selected).getByText(/mức hưởng áp dụng 80%, thẻ 80%/)).toBeInTheDocument();
    });

    it('removes a selected service from the selection table', async () => {
        const user = userEvent.setup();
        renderPanel({ getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() });

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Điện tim' }));
        await user.click(screen.getByRole('button', { name: 'Gỡ Điện tim' }));

        expect(screen.getByRole('checkbox', { name: 'Chọn Điện tim' })).not.toBeChecked();
        expect(screen.getByText('Chưa chọn dịch vụ nào.')).toBeInTheDocument();
    });

    it('filters services by keyword and type', async () => {
        const user = userEvent.setup();
        renderPanel({ getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() });

        await screen.findByRole('checkbox', { name: 'Chọn Điện tim' });
        await user.type(screen.getByLabelText('Tìm dịch vụ'), 'siêu');
        expect(screen.getByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: 'Chọn Điện tim' })).not.toBeInTheDocument();

        await user.clear(screen.getByLabelText('Tìm dịch vụ'));
        await user.selectOptions(screen.getByLabelText('Loại dịch vụ'), 'LAB');
        expect(screen.getByRole('checkbox', { name: 'Chọn Tổng phân tích máu' })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' })).not.toBeInTheDocument();
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
        renderPanel(api);

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Dịch vụ Siêu âm ổ bụng không còn hoạt động.');
        expect(api.getParaclinicalServices).toHaveBeenCalledTimes(2);
        expect(await screen.findByText('Chưa chọn dịch vụ nào.')).toBeInTheDocument();
    });

    it('never shows the raw backend message (EF-03)', async () => {
        const user = userEvent.setup();
        renderPanel({
            getParaclinicalServices: vi.fn().mockResolvedValue(services),
            orderParaclinical: vi.fn().mockRejectedValue(new ApiError('raw', { status: 500 })),
        });

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Điện tim' }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.SYSTEM_ERROR);
    });
});

describe('paraclinicalOrder helpers', () => {
    it('reads both a list and a page', () => {
        expect(toServiceList(services)).toHaveLength(3);
        expect(toServiceList({ content: [services[0]] })).toHaveLength(1);
    });

    it('orders services Lab, Imaging, Functional and filters by group', () => {
        expect(filterServices(services).map((s) => s.type)).toEqual(['LAB', 'IMAGING', 'FUNCTIONAL']);
        expect(filterServices(services, { groupId: 'g2' }).map((s) => s.id)).toEqual(['s1']);
        expect(groupOptions(services)).toEqual([{ id: 'g1', name: 'Huyết học' }, { id: 'g2', name: 'Siêu âm' }]);
    });

    it('keeps the catalogue prices and charges the clinic price', () => {
        const cost = estimate(services, ['s1', 's3']);
        expect(cost.lines.map((line) => line.insurancePrice)).toEqual([43900, null]);
        expect(cost.clinicTotal).toBe(250000);
        expect(cost.insuranceTotal).toBe(43900);
        expect(cost.total).toBe(250000);
        expect(cost.patientAmount).toBe(250000);
    });

    it('covers min(insurance price, clinic price) × rate with a coverage rate (BR-27)', () => {
        const cheaper = [{ id: 'x', clinicPrice: 80000, insurancePrice: 90000 }];
        const aboveThreshold = { cardRate: 95, scopeTotal: 400000, threshold: 351000 };
        expect(estimate(cheaper, ['x'], aboveThreshold)).toMatchObject({ insuranceAmount: 76000, patientAmount: 4000 });
        expect(estimate(services, ['s3'], aboveThreshold)).toMatchObject({ insuranceAmount: 0, patientAmount: 50000 });
    });

    it('covers the visit in full below 15% of the base salary and flags a rate change (BR-27)', () => {
        const coverage = { cardRate: 80, scopeTotal: 300000, threshold: 351000 };
        // 300 000 + 43 900 stays below 351 000: 100% of the insurance price is covered.
        expect(estimate(services, ['s1'], coverage)).toMatchObject({ appliedRate: 100, insuranceAmount: 43900, rateChanges: false });
        // + 46 200 crosses it: the card's 80% applies, and the earlier receipts are repriced.
        expect(estimate(services, ['s1', 's2'], coverage)).toMatchObject({ appliedRate: 80, rateChanges: true });
    });

    it('builds MSG67 and maps the visit state error', () => {
        expect(createdMessage({ paymentCode: 'HD1', tongcong: 200000 }))
            .toBe('Đã tạo chỉ định cận lâm sàng. Phiếu thu HD1 – bệnh nhân trả: 200.000 ₫.');
        expect(createdMessage({ paymentCode: 'HD2', tongcong: 200000, patientAmount: 40000 }, true))
            .toMatch(/bệnh nhân trả: 40.000 ₫\. Chỉ định đã được chuyển tới phòng cận lâm sàng/);
        expect(orderErrorMessage(new ApiError('raw', { status: 409, errorResponse: { code: 'VISIT_NOT_IN_EXAMINATION' } })))
            .toBe(MSG.NOT_IN_EXAMINATION);
    });
});
