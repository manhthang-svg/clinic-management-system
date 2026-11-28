import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { NationalPrescriberPanel } from './NationalPrescriberPanel';

const doctor = { id: 'a1', role: 'DOCTOR', maLienthongBacsi: 'BS0001', lienthongAddedAt: null, version: 4 };

function signInRequired() {
    return new ApiError('x', { status: 409, errorResponse: { code: 'NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED', details: {} } });
}

function api(overrides = {}) {
    return {
        detail: vi.fn().mockResolvedValue({ ...doctor, version: 5 }),
        addToNationalPrescriptionSystem: vi.fn().mockResolvedValue({ ...doctor, lienthongAddedAt: '2026-11-27T02:00:00Z' }),
        removeFromNationalPrescriptionSystem: vi.fn().mockResolvedValue({ ...doctor, lienthongAddedAt: null }),
        ...overrides,
    };
}

describe('NationalPrescriberPanel (UC-93 Thêm bác sĩ vào cơ sở trên Hệ thống đơn thuốc quốc gia)', () => {
    it('asks for the facility password when the server has no facility token, then adds the Doctor', async () => {
        const user = userEvent.setup();
        const client = api({
            addToNationalPrescriptionSystem: vi.fn()
                .mockRejectedValueOnce(signInRequired())
                .mockResolvedValue({ ...doctor, lienthongAddedAt: '2026-11-27T02:00:00Z' }),
        });
        const onUpdated = vi.fn();
        render(<NationalPrescriberPanel account={doctor} accountApi={client} onUpdated={onUpdated} />);

        expect(screen.getByRole('status')).toHaveTextContent('Chưa thêm vào cơ sở');
        await user.click(screen.getByRole('button', { name: 'Thêm vào cơ sở' }));
        expect(client.addToNationalPrescriptionSystem).toHaveBeenLastCalledWith('a1', { version: 5 });
        const password = await screen.findByLabelText('Mật khẩu liên thông của cơ sở');
        expect(screen.getByRole('alert')).toHaveTextContent('Vui lòng nhập mật khẩu liên thông của cơ sở.');
        expect(screen.getByRole('button', { name: 'Thêm vào cơ sở' })).toBeDisabled();

        await user.type(password, 'mat-khau-co-so');
        await user.click(screen.getByRole('button', { name: 'Thêm vào cơ sở' }));

        expect(client.addToNationalPrescriptionSystem)
            .toHaveBeenLastCalledWith('a1', { version: 5, facilityPassword: 'mat-khau-co-so' });
        expect(onUpdated).toHaveBeenCalledWith(expect.objectContaining({ lienthongAddedAt: '2026-11-27T02:00:00Z' }));
    });

    it('removes a Doctor that was added', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<NationalPrescriberPanel account={{ ...doctor, lienthongAddedAt: '2026-11-20T02:00:00Z' }}
            accountApi={client} onUpdated={vi.fn()} />);

        expect(screen.getByRole('status')).toHaveTextContent('Đã thêm vào cơ sở');
        await user.click(screen.getByRole('button', { name: 'Xóa khỏi cơ sở' }));

        expect(client.removeFromNationalPrescriptionSystem).toHaveBeenCalledWith('a1', { version: 5 });
    });

    it('needs the prescriber link code before adding', () => {
        render(<NationalPrescriberPanel account={{ ...doctor, maLienthongBacsi: '' }} accountApi={api()} onUpdated={vi.fn()} />);

        expect(screen.getByText('Cần nhập mã liên thông bác sĩ trước khi thêm vào cơ sở.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Thêm vào cơ sở' })).toBeDisabled();
    });

    it('shows the national system refusal', async () => {
        const user = userEvent.setup();
        render(<NationalPrescriberPanel account={doctor} accountApi={api({
            addToNationalPrescriptionSystem: vi.fn().mockRejectedValue(new ApiError('x', {
                status: 422,
                errorResponse: { code: 'NATIONAL_PRESCRIPTION_REQUEST_REJECTED', details: { errors: 'Mã liên thông bác sĩ không tồn tại.' } },
            })),
        })} onUpdated={vi.fn()} />);

        await user.click(screen.getByRole('button', { name: 'Thêm vào cơ sở' }));

        expect(await screen.findByRole('alert'))
            .toHaveTextContent('Hệ thống đơn thuốc quốc gia từ chối yêu cầu: Mã liên thông bác sĩ không tồn tại.');
    });
});
