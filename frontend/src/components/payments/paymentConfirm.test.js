import { describe, expect, it } from 'vitest';

import ApiError from '../../apis/apiError';
import { MSG, confirmErrorMessage, confirmedNotice, confirmedNoticeText } from './paymentConfirm';

const paraclinical = {
    type: 'PARACLINICAL_FEE',
    receiptNumber: 'PT20261103-P1',
    items: [
        { serviceName: 'Siêu âm ổ bụng', roomName: 'Phòng siêu âm 2', roomLocation: 'Tầng 2', queueNumber: 7 },
        { serviceName: 'Chụp MRI', roomName: null, roomLocation: null, queueNumber: null },
    ],
};

describe('confirmedNotice (UC-71 steps 8–9)', () => {
    it('shows MSG30 for an examination fee', () => {
        expect(confirmedNoticeText({ type: 'EXAMINATION_FEE', receiptNumber: 'PT20261103-P0', items: [] }))
            .toBe('Đã xác nhận thanh toán phiếu PT20261103-P0.');
    });

    it('lists the room of each paid service and the services without an Active room (AF-02)', () => {
        const notice = confirmedNotice(paraclinical);
        expect(notice.rooms).toEqual([
            { service: 'Siêu âm ổ bụng', room: 'Phòng siêu âm 2', location: 'Tầng 2', queueNumber: 7 },
        ]);
        expect(notice.unrouted).toEqual(['Chụp MRI']);
        expect(confirmedNoticeText(paraclinical)).toBe(
            'Đã xác nhận thanh toán phiếu PT20261103-P1. Siêu âm ổ bụng: Phòng siêu âm 2 (Tầng 2), STT 7.'
            + ' Chưa có phòng đang hoạt động cho: Chụp MRI. Chỉ định vẫn ở trạng thái chờ.',
        );
    });
});

describe('confirmErrorMessage (UC-71 EF-02, EF-03)', () => {
    it('shows MSG64 when the receipt is no longer pending', () => {
        const error = new ApiError('raw', {
            status: 409,
            errorResponse: { code: 'PAYMENT_NOT_PENDING', details: { receiptNumber: 'PT1', status: 'PAID' } },
        });
        expect(confirmErrorMessage(error)).toBe('Phiếu thu PT1 đã được thanh toán.');
    });

    it('explains that an insurance visit pays after the conclusion', () => {
        const error = new ApiError('raw', { status: 409, errorResponse: { code: 'PAYMENT_AWAITS_CONCLUSION' } });
        expect(confirmErrorMessage(error)).toBe(MSG.AWAITS_CONCLUSION);
    });

    it('never shows the raw backend message', () => {
        expect(confirmErrorMessage(new ApiError('raw', { status: 401 }))).toBe(MSG.SESSION_EXPIRED);
        expect(confirmErrorMessage(new ApiError('raw', { status: 500 }))).toBe(MSG.SYSTEM_ERROR);
    });
});
