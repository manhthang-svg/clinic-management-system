import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../apis/apiError';
import { PrintConclusionButton } from './ConclusionSlip';

const slip = {
    printedAt: '2026-11-18T02:30:00Z',
    facility: { name: 'Phòng khám Đa khoa A', address: '1 Lê Lợi', phone: '0281234567' },
    patient: { fullName: 'Hoàng Thị Mai', patientCode: 'BN00012345', gender: 'Nữ', birthDate: '1988-06-14', address: 'Hà Nội' },
    visitCode: 'DK0000004521', visitType: 'BHYT', insuranceCardNumber: 'GD4797912345678',
    registeredAt: '2026-11-18T01:00:00Z',
    diagnosis: 'Viêm họng cấp', mainDiagnosis: { code: 'J02.9', name: 'Viêm họng cấp, không đặc hiệu' },
    secondaryDiagnoses: [{ code: 'R50.9', name: 'Sốt, không đặc hiệu' }],
    treatmentPlan: 'Điều trị ngoại trú', treatmentResult: 2, advice: 'Uống nhiều nước',
    doctorName: 'ThS.BS Nguyễn Đức Hải', concludedAt: '2026-11-18T02:15:00Z',
};

describe('PrintConclusionButton (BF-04)', () => {
    it('prints the saved conclusion with the print time on the edge', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const api = { getConclusionPrint: vi.fn().mockResolvedValue(slip) };
        render(<PrintConclusionButton visitId="v1" api={api} />);

        await user.click(screen.getByRole('button', { name: 'In phiếu kết luận' }));

        expect(api.getConclusionPrint).toHaveBeenCalledWith('v1');
        const sheet = await screen.findByRole('article', { name: 'Phiếu kết luận khám bệnh' });
        expect(within(sheet).getByText('In lúc: 18/11/2026 09:30')).toBeInTheDocument();
        expect(sheet).toHaveTextContent('J02.9 – Viêm họng cấp, không đặc hiệu');
        expect(sheet).toHaveTextContent('Kết quả điều trị: Đỡ');
        expect(sheet).toHaveTextContent('Thời gian kết luận: 18/11/2026 09:15');
        await vi.waitFor(() => expect(print).toHaveBeenCalled());
        print.mockRestore();
    });

    it('is disabled until the conclusion is saved', () => {
        render(<PrintConclusionButton visitId="v1" api={{ getConclusionPrint: vi.fn() }} disabled />);

        expect(screen.getByRole('button', { name: 'In phiếu kết luận' })).toBeDisabled();
    });

    it('shows MSG80 instead of the raw backend message', async () => {
        const user = userEvent.setup();
        const api = { getConclusionPrint: vi.fn().mockRejectedValue(new ApiError('raw', { status: 500, errorResponse: { code: 'X' } })) };
        render(<PrintConclusionButton visitId="v1" api={api} />);

        await user.click(screen.getByRole('button', { name: 'In phiếu kết luận' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.');
        expect(screen.queryByText('raw')).not.toBeInTheDocument();
    });
});
