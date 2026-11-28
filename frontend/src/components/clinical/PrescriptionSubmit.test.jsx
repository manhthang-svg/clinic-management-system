import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { PrescriptionPanel } from './PrescriptionPanel';
import { SUBMIT_MSG } from './prescriptionSubmission';

const saved = {
    visitId: 'v1', prescriptionId: 'p1', version: 3, prescriptionCode: '79001abc1234-c', slipNumber: 'DT-DK1',
    conclusionSaved: true, diagnosis: 'Viêm họng cấp', diagnoses: [{ code: 'J02.9', name: 'Viêm họng cấp' }],
    allergies: [], longTermAllowed: false,
    lines: [{
        medicineId: 'm1', code: 'AMO500', name: 'Amoxicilin 500mg', activeIngredient: 'Amoxicilin', strength: '500mg',
        route: 'Uống', unit: 'Viên', quantity: 21, dosePerTime: '1 viên', timesPerDay: 3, days: 7,
        usage: 'Uống sau ăn', note: null,
    }],
    notes: null, advice: null, sent: false, doctorName: 'BS An', prescribedAt: '2026-11-12T03:00:00Z',
    prescriberLinkCodeMissing: false, facilityLinkCodeMissing: false, followUpDate: null, child: null,
    status: 'PENDING', prescriberNotAddedToFacility: false, nationalSignInRequired: false, lastSubmission: null,
};

const acceptedSend = {
    attemptNo: 1, result: 'ACCEPTED', errors: [], submittedAt: '2026-11-12T03:05:00Z',
    respondedAt: '2026-11-12T03:05:01Z', submittedBy: 'BS An',
};
const accepted = { ...saved, version: 6, status: 'ACCEPTED', sent: true, lastSubmission: acceptedSend };

function apiError(code, details = {}, status = 409) {
    return new ApiError('x', { status, errorResponse: { code, details } });
}

function api(overrides = {}) {
    return {
        getPrescription: vi.fn().mockResolvedValue(saved),
        savePrescription: vi.fn(),
        searchMedicines: vi.fn().mockResolvedValue([]),
        submitPrescription: vi.fn().mockResolvedValue(accepted),
        ...overrides,
    };
}

async function openDialog(user) {
    await user.click(await screen.findByRole('button', { name: 'Gửi đơn' }));
    const dialog = screen.getByRole('dialog', { name: 'Gửi đơn thuốc' });
    expect(within(dialog).getByText(SUBMIT_MSG.REMINDER)).toBeInTheDocument();
    return dialog;
}

describe('PrescriptionPanel (UC-90 Gửi đơn thuốc điện tử)', () => {
    it('sends without a password while the national token is kept and shows the accepted send', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openDialog(user);
        expect(within(dialog).queryByLabelText('Mật khẩu liên thông đơn thuốc')).not.toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: 'Gửi đơn' }));

        expect(client.submitPrescription).toHaveBeenCalledWith('p1', 3, undefined);
        expect(await screen.findByRole('status', { name: 'Kết quả gửi đơn thuốc' }))
            .toHaveTextContent(SUBMIT_MSG.ACCEPTED('79001abc1234-c'));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Gửi đơn' })).not.toBeInTheDocument();
    });

    it('asks for the national password when no token is kept, then sends with it (cách B)', async () => {
        const user = userEvent.setup();
        const client = api({ getPrescription: vi.fn().mockResolvedValue({ ...saved, nationalSignInRequired: true }) });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openDialog(user);
        const password = within(dialog).getByLabelText('Mật khẩu liên thông đơn thuốc');
        expect(password).toHaveAttribute('type', 'password');
        expect(within(dialog).getByRole('button', { name: 'Gửi đơn' })).toBeDisabled();
        await user.type(password, 'mat-khau-bs');
        await user.click(within(dialog).getByRole('button', { name: 'Gửi đơn' }));

        expect(client.submitPrescription).toHaveBeenCalledWith('p1', 3, 'mat-khau-bs');
    });

    it('shows the password field when the server asks for a sign-in after the token ended', async () => {
        const user = userEvent.setup();
        const client = api({
            submitPrescription: vi.fn().mockRejectedValue(apiError('NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED')),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openDialog(user);
        await user.click(within(dialog).getByRole('button', { name: 'Gửi đơn' }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent(SUBMIT_MSG.SIGN_IN_REQUIRED);
        expect(within(dialog).getByLabelText('Mật khẩu liên thông đơn thuốc')).toHaveFocus();
    });

    it('explains a refused sign-in with the national system errors and clears the password', async () => {
        const user = userEvent.setup();
        const client = api({
            getPrescription: vi.fn().mockResolvedValue({ ...saved, nationalSignInRequired: true }),
            submitPrescription: vi.fn().mockRejectedValue(apiError(
                'NATIONAL_PRESCRIPTION_SIGN_IN_FAILED', { errors: 'Mã liên thông hoặc mật khẩu không đúng.' }, 422)),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openDialog(user);
        await user.type(within(dialog).getByLabelText('Mật khẩu liên thông đơn thuốc'), 'sai');
        await user.click(within(dialog).getByRole('button', { name: 'Gửi đơn' }));

        expect(await within(dialog).findByRole('alert'))
            .toHaveTextContent(SUBMIT_MSG.SIGN_IN_FAILED('Mã liên thông hoặc mật khẩu không đúng.'));
        expect(within(dialog).getByLabelText('Mật khẩu liên thông đơn thuốc')).toHaveValue('');
    });

    it('cannot send while the facility has not added the Doctor (BR-59-17)', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({ ...saved, prescriberNotAddedToFacility: true }),
        })} />);

        expect(await screen.findByText(SUBMIT_MSG.NOT_ADDED)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Gửi đơn' })).not.toBeInTheDocument();
    });

    it('names a child data item that is missing (EF-05, MSG107)', async () => {
        const user = userEvent.setup();
        const client = api({
            submitPrescription: vi.fn().mockRejectedValue(apiError('PRESCRIPTION_CHILD_DATA_REQUIRED', { item: 'WEIGHT' })),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openDialog(user);
        await user.click(within(dialog).getByRole('button', { name: 'Gửi đơn' }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent(SUBMIT_MSG.CHILD_DATA('cân nặng'));
    });

    it('reloads to show the attempt without an answer (AF-02)', async () => {
        const user = userEvent.setup();
        const client = api({
            getPrescription: vi.fn()
                .mockResolvedValueOnce(saved)
                .mockResolvedValue({
                    ...saved, status: 'SENT', sent: true,
                    lastSubmission: { ...acceptedSend, result: 'NO_RESPONSE', respondedAt: null },
                }),
            submitPrescription: vi.fn().mockRejectedValue(apiError('NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE', {}, 502)),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openDialog(user);
        await user.click(within(dialog).getByRole('button', { name: 'Gửi đơn' }));

        expect(await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' })).toHaveTextContent(SUBMIT_MSG.NO_ANSWER);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('lists the errors of a rejected send (UC-91, MSG134)', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'REJECTED',
                lastSubmission: { ...acceptedSend, result: 'REJECTED', errors: ['Thiếu thông tin dia_chi.'] },
            }),
        })} />);

        const result = await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' });
        expect(result).toHaveTextContent(SUBMIT_MSG.REJECTED);
        expect(result).toHaveTextContent('Thiếu thông tin dia_chi.');
        expect(screen.getByRole('button', { name: 'Gửi đơn' })).toBeEnabled();
    });

    it('keeps the rejection errors visible while correcting and asks to save before resending (UC-92)', async () => {
        const user = userEvent.setup();
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'REJECTED',
                lastSubmission: { ...acceptedSend, result: 'REJECTED', errors: ['Sai liều dùng.'] },
            }),
        })} />);

        const result = await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' });
        expect(result).toHaveTextContent(SUBMIT_MSG.CORRECT_ELSEWHERE);
        expect(within(result).getByRole('button', { name: 'Gửi lại' })).toBeEnabled();

        await user.click(within(result).getByRole('button', { name: 'Sửa đơn' }));
        expect(screen.getByLabelText('Thêm thuốc')).toHaveFocus();
        await user.type(screen.getByLabelText(/^Cách dùng/), ', sau ăn');

        expect(result).toHaveTextContent('Sai liều dùng.');
        expect(within(result).getByRole('button', { name: 'Gửi lại' })).toBeDisabled();
        expect(result).toHaveTextContent(SUBMIT_MSG.SAVE_BEFORE_RESEND);
    });

    it('resends a rejected prescription without changing it (UC-92 AF-01)', async () => {
        const user = userEvent.setup();
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'REJECTED',
                lastSubmission: { ...acceptedSend, result: 'REJECTED', errors: ['Sai mã bác sĩ.'] },
            }),
        })} />);

        const result = await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' });
        await user.click(within(result).getByRole('button', { name: 'Gửi lại' }));

        expect(screen.getByRole('dialog', { name: 'Gửi đơn thuốc' })).toBeInTheDocument();
    });
});
