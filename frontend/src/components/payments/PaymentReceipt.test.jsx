import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ApiError from '../../apis/apiError';
import PaymentReceipt from './PaymentReceipt';
import { PRINT_MSG, printErrorMessage } from './paymentConfirm';

const base = {
    reprint: false,
    facilityName: 'Phòng khám Đa khoa METPH',
    cashierName: 'Thu ngân A',
    visitType: 'DV',
    coverageRate: null,
    visitQueueNumber: null,
};
const paraclinical = {
    ...base,
    payment: {
        type: 'PARACLINICAL_FEE', receiptNumber: 'PT20261103-HD0000000002', patientName: 'Nguyễn Văn An',
        patientCode: 'BN1', sovaovien: 'DK0000000001', roomName: 'Phòng khám 1', paymentMethod: 'CASH',
        totalAmount: 280000, insuranceTotalAmount: 0, patientTotalAmount: 280000, paidAt: '2026-11-03T03:00:00Z',
        items: [
            { id: 'i1', serviceName: 'Siêu âm ổ bụng', unitPrice: 200000, bhytChitra: 0, patientAmount: 200000,
                roomName: 'Phòng siêu âm 2', roomLocation: 'Tầng 2 – P202', queueNumber: 3 },
            { id: 'i2', serviceName: 'Chụp MRI', unitPrice: 80000, bhytChitra: 0, patientAmount: 80000,
                roomName: null, roomLocation: null, queueNumber: null },
        ],
    },
};

describe('PaymentReceipt (UC-72, PF-06)', () => {
    it('prints the room, location and number of each paraclinical service (step 3)', () => {
        render(<PaymentReceipt receipt={paraclinical} />);

        const receipt = screen.getByRole('article', { name: 'Phiếu thu' });
        expect(within(receipt).getByText('Phòng khám Đa khoa METPH')).toBeInTheDocument();
        expect(within(receipt).getByText('PT20261103-HD0000000002')).toBeInTheDocument();
        expect(within(receipt).getByText('Phòng siêu âm 2 (Tầng 2 – P202) – STT 3')).toBeInTheDocument();
        expect(within(receipt).getByText('Chưa có phòng thực hiện')).toBeInTheDocument();
        expect(within(receipt).getByText('Thu ngân A')).toBeInTheDocument();
        expect(screen.queryByText('BẢN SAO')).not.toBeInTheDocument();
    });

    it('prints the room and queue number of the visit for an examination fee', () => {
        render(<PaymentReceipt receipt={{
            ...base,
            visitQueueNumber: 7,
            payment: { ...paraclinical.payment, type: 'EXAMINATION_FEE', items: [] },
        }} />);

        expect(screen.getByText('Phòng khám 1 – STT 7')).toBeInTheDocument();
    });

    it('marks a reprint as a copy (AF-01)', () => {
        render(<PaymentReceipt receipt={{ ...paraclinical, reprint: true }} />);

        expect(screen.getByText('BẢN SAO')).toBeInTheDocument();
    });

    it('shows MSG62 when the receipt is not paid (EF-01)', () => {
        expect(printErrorMessage(new ApiError('raw', { status: 409, errorResponse: { code: 'PAYMENT_NOT_PAID' } })))
            .toBe(PRINT_MSG.NOT_PAID);
    });
});
