import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ConclusionSlip } from './ConclusionSlip';

describe('ConclusionSlip (UC-57)', () => {
    it('shows the closed-record conclusion without a "Bản sao" mark (PO 26/11/2026)', () => {
        render(<ConclusionSlip slip={{
            printedAt: '2026-11-18T02:30:00Z',
            facility: { name: 'Phòng khám Đa khoa A', address: '1 Lê Lợi', phone: '0281234567' },
            patient: {
                fullName: 'Hoàng Thị Mai', patientCode: 'BN00012345', gender: 'Nữ',
                birthDate: '1988-06-14', address: 'Hà Nội',
            },
            visitCode: 'DK0000004521',
            visitDate: '2026-11-18',
            roomName: 'Phòng Nội 1',
            diagnosis: { code: 'J02.9', name: 'Viêm họng cấp, không đặc hiệu' },
            diagnosisText: 'Viêm họng cấp',
            secondaryDiagnoses: [{ code: 'R50.9', name: 'Sốt, không đặc hiệu' }],
            treatmentPlan: 'Điều trị ngoại trú',
            treatmentResult: 'Đỡ',
            advice: 'Uống nhiều nước',
            doctorName: 'ThS.BS Nguyễn Đức Hải',
        }} />);

        const sheet = screen.getByRole('article', { name: 'Phiếu kết luận khám bệnh' });
        expect(sheet).not.toHaveTextContent('BẢN SAO');
        expect(sheet).toHaveTextContent('J02.9 – Viêm họng cấp, không đặc hiệu');
        expect(sheet).toHaveTextContent('Kết quả điều trị: Đỡ');
        expect(sheet).toHaveTextContent('ThS.BS Nguyễn Đức Hải');
    });
});
