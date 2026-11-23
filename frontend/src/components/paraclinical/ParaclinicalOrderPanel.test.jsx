import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import ParaclinicalOrderPanel from './ParaclinicalOrderPanel';
import {
    MSG, createdMessage, estimate, filterServices, groupOptions, hasOnDutyRoom, orderErrorMessage, roomOptionLabel, toServiceList,
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
        const order = { id: 'o1', sophieu: 'CD0000000002', paymentCode: 'HD0000000002', tongcong: 280000 };
        const api = { getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn().mockResolvedValue(order) };
        const onCreated = renderPanel(api, { preliminaryDiagnosis: 'Viêm dạ dày' });

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));
        await user.click(screen.getByRole('checkbox', { name: 'Chọn Tổng phân tích máu' }));
        expect(screen.getByRole('region', { name: 'Dịch vụ đã chọn (2)' })).toBeInTheDocument();
        expect(screen.getByTestId('cls-total')).toHaveTextContent('280.000');
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(api.orderParaclinical).toHaveBeenCalledWith({
            visitId: 'v1',
            chandoansobo: 'Viêm dạ dày',
            items: [{ serviceId: 's1', roomId: null }, { serviceId: 's2', roomId: null }],
        });
        // UC-50 AF-01: the slip can be printed before leaving
        expect(await screen.findByRole('button', { name: 'In phiếu chỉ định' })).toBeInTheDocument();
        expect(onCreated).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', { name: 'Về danh sách chờ' }));
        expect(onCreated).toHaveBeenCalledWith(order);
    });

    it('assigns no room to a self-paid visit until the payment (BR-50-02)', async () => {
        const user = userEvent.setup();
        const api = { getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() };
        renderPanel(api);

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));

        expect(screen.getByText('Phân phòng sau khi thanh toán')).toBeInTheDocument();
        expect(screen.queryByTestId('assigned-room-s1')).not.toBeInTheDocument();
    });

    it('shows the room the system assigns to an insurance visit and lets the backend assign it', async () => {
        const user = userEvent.setup();
        const withRooms = [{
            ...services[0],
            rooms: [
                { roomId: 'r1', roomCode: 'SA1', roomName: 'Phòng siêu âm 1', location: 'Tầng 2', waitingCount: 0, onDuty: false },
                { roomId: 'r2', roomCode: 'SA2', roomName: 'Phòng siêu âm 2', location: 'Tầng 3', waitingCount: 1, onDuty: true },
            ],
            suggestedRoomId: 'r2',
        }];
        const api = {
            getParaclinicalServices: vi.fn().mockResolvedValue(withRooms),
            orderParaclinical: vi.fn().mockResolvedValue({ id: 'o1', sophieu: 'CD1' }),
        };
        renderPanel(api, { forVisit: { ...visit, doituongbnLoai: 'BHYT' } });

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));
        expect(screen.getByTestId('assigned-room-s1')).toHaveTextContent('Phòng siêu âm 2 – Tầng 3 (đang chờ: 1)');
        expect(screen.getByText('Hệ thống tự xếp phòng')).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: /Phòng thực hiện/ })).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(api.orderParaclinical).toHaveBeenCalledWith(expect.objectContaining({
            items: [{ serviceId: 's1', roomId: null }],
        }));
    });

    it('asks for another room when the chosen one was deactivated (MSG40)', async () => {
        const user = userEvent.setup();
        const api = {
            getParaclinicalServices: vi.fn().mockResolvedValue(services),
            orderParaclinical: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409,
                errorResponse: { code: 'PARACLINICAL_ROOM_NOT_ELIGIBLE', details: { serviceId: 's1', roomName: 'Phòng siêu âm 1' } },
            })),
        };
        renderPanel(api);

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận chỉ định' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Phòng siêu âm 1 đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.');
        expect(api.getParaclinicalServices).toHaveBeenCalledTimes(2);
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
    });

    it('removes a selected service from the selection table', async () => {
        const user = userEvent.setup();
        renderPanel({ getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() });

        await user.click(await screen.findByRole('checkbox', { name: 'Chọn Điện tim' }));
        await user.click(screen.getByRole('button', { name: 'Gỡ Điện tim' }));

        expect(screen.getByRole('checkbox', { name: 'Chọn Điện tim' })).not.toBeChecked();
        expect(screen.getByText('Chưa chọn dịch vụ nào.')).toBeInTheDocument();
    });

    it('reuses the list request the page already started instead of loading it again', async () => {
        const api = { getParaclinicalServices: vi.fn(), orderParaclinical: vi.fn() };
        render(
            <ParaclinicalOrderPanel
                visit={visit}
                api={api}
                initialServices={Promise.resolve(services)}
                onCancel={vi.fn()}
                onCreated={vi.fn()}
            />,
        );

        expect(await screen.findByRole('checkbox', { name: 'Chọn Điện tim' })).toBeInTheDocument();
        expect(api.getParaclinicalServices).not.toHaveBeenCalled();
    });

    it('pages the service list and keeps the selection across pages', async () => {
        const user = userEvent.setup();
        const many = Array.from({ length: 12 }, (_, index) => ({
            id: `x${index + 1}`, code: `XN-${String(index + 1).padStart(2, '0')}`, name: `Xét nghiệm ${index + 1}`,
            type: 'LAB', serviceGroupId: 'g1', serviceGroupName: 'Sinh hóa', clinicPrice: 10000, insurancePrice: null,
        }));
        renderPanel({ getParaclinicalServices: vi.fn().mockResolvedValue(many), orderParaclinical: vi.fn() });

        expect(await screen.findByRole('checkbox', { name: 'Chọn Xét nghiệm 10' })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: 'Chọn Xét nghiệm 11' })).not.toBeInTheDocument();
        expect(screen.getByText('Hiển thị 1 – 10 / 12')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Trang 2' }));
        await user.click(screen.getByRole('checkbox', { name: 'Chọn Xét nghiệm 12' }));
        expect(screen.getByText('Hiển thị 11 – 12 / 12')).toBeInTheDocument();
        expect(screen.getByRole('region', { name: 'Dịch vụ đã chọn (1)' })).toBeInTheDocument();

        // A new search starts again from the first page.
        await user.type(screen.getByLabelText('Tìm dịch vụ'), 'Xét');
        expect(screen.getByRole('checkbox', { name: 'Chọn Xét nghiệm 1' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Trang 1' })).toHaveAttribute('aria-current', 'page');
    });

    it('filters services by keyword and type', async () => {
        const user = userEvent.setup();
        renderPanel({ getParaclinicalServices: vi.fn().mockResolvedValue(services), orderParaclinical: vi.fn() });

        await screen.findByRole('checkbox', { name: 'Chọn Điện tim' });
        expect(screen.getByTestId('cls-count')).toHaveTextContent('Hiển thị 3 dịch vụ');
        await user.type(screen.getByLabelText('Tìm dịch vụ'), 'siêu');
        expect(screen.getByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: 'Chọn Điện tim' })).not.toBeInTheDocument();
        expect(screen.getByTestId('cls-count')).toHaveTextContent('Hiển thị 1 / 3 dịch vụ');
        await user.click(screen.getByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' }));
        expect(screen.getByTestId('cls-count')).toHaveTextContent('Hiển thị 1 / 3 dịch vụ · Đã chọn 1');

        await user.clear(screen.getByLabelText('Tìm dịch vụ'));
        await user.selectOptions(screen.getByLabelText('Loại dịch vụ'), 'LAB');
        expect(screen.getByRole('checkbox', { name: 'Chọn Tổng phân tích máu' })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: 'Chọn Siêu âm ổ bụng' })).not.toBeInTheDocument();
    });

    it('drops a service deactivated meanwhile and reloads the list (EF-02, MSG40)', async () => {
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

        expect(await screen.findByRole('alert')).toHaveTextContent('Dịch vụ Siêu âm ổ bụng đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.');
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
    it('labels a room with nobody on duty but keeps it as a choice', () => {
        const room = { roomName: 'Phòng Huyết học 1', location: 'Tòa B', waitingCount: 1 };
        expect(roomOptionLabel({ ...room, onDuty: true })).toBe('Phòng Huyết học 1 – Tòa B (đang chờ: 1)');
        expect(roomOptionLabel({ ...room, onDuty: false })).toBe('Phòng Huyết học 1 – Tòa B (đang chờ: 1 · chưa có người trực)');
    });

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

    it('builds MSG63 and maps EF-02 / EF-03 to MSG40, MSG75 and MSG68', () => {
        expect(createdMessage({ sophieu: 'CD0000000001', paymentCode: 'HD0000000001' }))
            .toBe('Đã tạo chỉ định cận lâm sàng CD0000000001.');
        const failure = (code, details) => new ApiError('raw', { status: 409, errorResponse: { code, details } });
        expect(orderErrorMessage(failure('SERVICE_INACTIVE', { serviceName: 'Công thức máu' })))
            .toBe('Dịch vụ Công thức máu đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác.');
        expect(orderErrorMessage(failure('VISIT_NOT_IN_EXAMINATION')))
            .toBe('Lượt khám vừa được cập nhật. Vui lòng tải lại và thử lại.');
        expect(orderErrorMessage(failure('MEDICAL_RECORD_CLOSED')))
            .toBe('Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.');
    });

    it('identifies whether a service has an active room on duty (hasOnDutyRoom)', () => {
        expect(hasOnDutyRoom(null)).toBe(false);
        expect(hasOnDutyRoom({ id: 's1' })).toBe(true);
        expect(hasOnDutyRoom({ id: 's1', rooms: [] })).toBe(false);
        expect(hasOnDutyRoom({
            id: 's1',
            rooms: [
                { roomId: 'r1', onDuty: false },
                { roomId: 'r2', onDuty: false },
            ],
        })).toBe(false);
        expect(hasOnDutyRoom({
            id: 's1',
            rooms: [
                { roomId: 'r1', onDuty: false },
                { roomId: 'r2', onDuty: true },
            ],
        })).toBe(true);
    });

    it('disables services whose rooms have no active shift', async () => {
        const user = userEvent.setup();
        const testServices = [
            {
                id: 's-active',
                code: 'SA01',
                name: 'Siêu âm tim',
                type: 'IMAGING',
                clinicPrice: 150000,
                rooms: [{ roomId: 'r1', roomName: 'P1', onDuty: true }],
            },
            {
                id: 's-inactive',
                code: 'XN01',
                name: 'Sinh hóa máu',
                type: 'LAB',
                clinicPrice: 90000,
                rooms: [{ roomId: 'r2', roomName: 'P2', onDuty: false }],
            },
        ];
        const api = { getParaclinicalServices: vi.fn().mockResolvedValue(testServices), orderParaclinical: vi.fn() };
        renderPanel(api);

        const activeCb = await screen.findByRole('checkbox', { name: 'Chọn Siêu âm tim' });
        const inactiveCb = screen.getByRole('checkbox', { name: 'Chọn Sinh hóa máu' });

        expect(activeCb).not.toBeDisabled();
        expect(inactiveCb).toBeDisabled();
        expect(inactiveCb).toHaveAttribute('title', 'Phòng thực hiện hiện chưa có ca trực');
        expect(screen.getByText('Chưa có ca trực')).toBeInTheDocument();

        // Attempt to click disabled checkbox
        await user.click(inactiveCb);
        expect(inactiveCb).not.toBeChecked();
        expect(screen.getByText('Chưa chọn dịch vụ nào.')).toBeInTheDocument();

        // Active service can be clicked
        await user.click(activeCb);
        expect(activeCb).toBeChecked();
        expect(screen.getByRole('region', { name: 'Dịch vụ đã chọn (1)' })).toBeInTheDocument();
    });
});
