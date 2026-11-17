import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ParaclinicalOrderSlip } from './ParaclinicalOrderSlip';

const slip = (patient) => ({
    orderId: 'o1', sophieu: 'CD1', reprint: true, orderedAt: '2026-11-17T03:00:00Z',
    facility: { tenCskcb: 'Phòng khám Đa khoa A', diachi: '1 Lê Lợi', sodienthoai: '0281234567' },
    patient: {
        fullName: 'Nguyễn Văn Bình', birthYear: 1950, age: 76, gender: 'Nam', address: 'Hà Nội',
        patientCode: 'BN00000001', visitCode: 'DK0000000001', visitType: 'BHYT', insuranceCardNumber: 'DN4017000000001',
        ...patient,
    },
    orderingRoom: 'Phòng khám Nội 1', chandoansobo: 'Viêm ruột', mucdochidinh: 'Thường', doctorName: 'BS An',
    sheets: [{
        index: 1, total: 1, room: { roomName: 'Phòng siêu âm 1', location: 'Tầng 2' }, serviceGroupName: 'Siêu âm',
        services: [{ machidinh: 'CD1-01', tenchidinh: 'Siêu âm ổ bụng', soluong: 1 }],
    }],
});

describe('ParaclinicalOrderSlip (UC-50)', () => {
    it('prints the gender and the age in years and months on the order date, without a reprint mark', () => {
        render(<ParaclinicalOrderSlip slip={slip({ dateOfBirth: '1950-03-10' })} />);

        expect(screen.getByText('Giới tính:').parentElement).toHaveTextContent('Giới tính: Nam');
        expect(screen.getByText('Tuổi:').parentElement).toHaveTextContent('Tuổi: 76 tuổi 8 tháng');
        expect(screen.queryByText('BẢN IN LẠI')).not.toBeInTheDocument();
    });

    it('prints the months only for a baby under one year', () => {
        render(<ParaclinicalOrderSlip slip={slip({ dateOfBirth: '2026-02-01', gender: 'Nữ' })} />);

        expect(screen.getByText('Tuổi:').parentElement).toHaveTextContent('Tuổi: 9 tháng');
    });
});
