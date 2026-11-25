import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { PrescriptionPanel } from './PrescriptionPanel';
import { SIGNING_MSG } from './prescriptionSigning';

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
    status: 'PENDING', signingCertificateRegistered: true, lastSubmission: null,
};

const accepted = {
    ...saved, version: 6, status: 'ACCEPTED', sent: true,
    lastSubmission: {
        attemptNo: 1, result: 'ACCEPTED', errors: [], submittedAt: '2026-11-12T03:05:00Z',
        respondedAt: '2026-11-12T03:05:01Z', signedAt: '2026-11-12T03:05:00Z', signerName: 'BS An',
        certificateSerial: 'abc123', certificateIssuer: 'CN=CA ký số từ xa (giả lập)',
    },
};

const pending = { id: 'r1', status: 'PENDING', documentVersion: 5, expiresAt: '2099-01-01T00:00:00Z' };

function api(overrides = {}) {
    return {
        getPrescription: vi.fn().mockResolvedValue(saved),
        savePrescription: vi.fn(),
        searchMedicines: vi.fn().mockResolvedValue([]),
        getCurrentPrescriptionSigning: vi.fn().mockResolvedValue(null),
        requestPrescriptionSignature: vi.fn().mockResolvedValue(pending),
        completePrescriptionSigning: vi.fn().mockResolvedValue({ ...pending }),
        cancelPrescriptionSigning: vi.fn().mockResolvedValue(undefined),
        ...overrides,
    };
}

async function openAndSign(user) {
    await user.click(await screen.findByRole('button', { name: 'Ký và gửi đơn' }));
    const dialog = screen.getByRole('dialog', { name: 'Ký số và gửi đơn thuốc' });
    expect(within(dialog).getByText(SIGNING_MSG.REMINDER)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Ký số' }));
    return dialog;
}

describe('PrescriptionPanel (UC-90 Ký số và gửi đơn thuốc)', () => {
    it('signs, waits for the approval in the CA app and shows the accepted send with the valid signature', async () => {
        const user = userEvent.setup();
        const client = api({
            completePrescriptionSigning: vi.fn()
                .mockResolvedValueOnce({ ...pending })
                .mockResolvedValue({ ...pending, status: 'SIGNED', prescription: accepted }),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        await openAndSign(user);

        expect(client.requestPrescriptionSignature).toHaveBeenCalledWith('p1', 3);
        expect(await screen.findByText(/Vui lòng xác nhận ký trên ứng dụng ký số của bạn/)).toBeInTheDocument();
        expect(await screen.findByText(SIGNING_MSG.ACCEPTED('79001abc1234-c'), {}, { timeout: 5000 }))
            .toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByText(SIGNING_MSG.SIGNATURE_VALID)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Ký và gửi đơn' })).not.toBeInTheDocument();
        expect(client.completePrescriptionSigning).toHaveBeenCalledWith('p1', 'r1');
    }, 10000);

    it('tells the Doctor the signature was declined and sends nothing (AF-04)', async () => {
        const user = userEvent.setup();
        const client = api({ completePrescriptionSigning: vi.fn().mockResolvedValue({ ...pending, status: 'DECLINED' }) });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openAndSign(user);

        expect(await within(dialog).findByText(SIGNING_MSG.DECLINED)).toBeInTheDocument();
        expect(within(dialog).getByRole('button', { name: 'Ký lại' })).toBeEnabled();
    });

    it('signs the new version when the Doctor signs again after a decline', async () => {
        const user = userEvent.setup();
        const client = api({ completePrescriptionSigning: vi.fn().mockResolvedValue({ ...pending, status: 'EXPIRED' }) });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openAndSign(user);
        await within(dialog).findByText(SIGNING_MSG.EXPIRED);
        await user.click(within(dialog).getByRole('button', { name: 'Ký lại' }));

        expect(client.requestPrescriptionSignature).toHaveBeenLastCalledWith('p1', 5);
    });

    it('explains an invalid certificate with its reason (EF-11, MSG118)', async () => {
        const user = userEvent.setup();
        const client = api({
            requestPrescriptionSignature: vi.fn().mockRejectedValue(new ApiError('x', {
                status: 409,
                errorResponse: { code: 'SIGNING_CERTIFICATE_INVALID', details: { reason: 'CERTIFICATE_REVOKED' } },
            })),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openAndSign(user);

        expect(await within(dialog).findByRole('alert'))
            .toHaveTextContent(SIGNING_MSG.CERTIFICATE_INVALID('chứng thư đã bị thu hồi'));
        expect(client.completePrescriptionSigning).not.toHaveBeenCalled();
    });

    it('cancels the pending request when the Doctor closes the dialog (AF-03)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);

        const dialog = await openAndSign(user);
        await within(dialog).findByText(/Vui lòng xác nhận ký/);
        await user.click(within(dialog).getByRole('button', { name: 'Hủy' }));

        expect(client.cancelPrescriptionSigning).toHaveBeenCalledWith('p1', 'r1');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('resumes a request still waiting when the tab is opened again (AF-06)', async () => {
        const client = api({ getCurrentPrescriptionSigning: vi.fn().mockResolvedValue(pending) });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        expect(await screen.findByRole('dialog', { name: 'Ký số và gửi đơn thuốc' })).toBeInTheDocument();
        expect(await screen.findByText(/Vui lòng xác nhận ký trên ứng dụng ký số của bạn/)).toBeInTheDocument();
        expect(client.requestPrescriptionSignature).not.toHaveBeenCalled();
    });

    it('cannot sign without a registered certificate (EF-10, MSG117)', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({ ...saved, signingCertificateRegistered: false }),
        })} />);

        expect(await screen.findByRole('button', { name: 'Ký và gửi đơn' })).toBeDisabled();
        expect(screen.getByText(SIGNING_MSG.NOT_REGISTERED)).toBeInTheDocument();
    });

    it('lists the errors of a rejected send (UC-91, MSG134)', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'REJECTED',
                lastSubmission: { ...accepted.lastSubmission, result: 'REJECTED', errors: ['Thiếu thông tin dia_chi.'] },
            }),
        })} />);

        const result = await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' });
        expect(result).toHaveTextContent(SIGNING_MSG.REJECTED);
        expect(result).toHaveTextContent('Thiếu thông tin dia_chi.');
        expect(screen.getByRole('button', { name: 'Ký và gửi đơn' })).toBeEnabled();
    });

    it('keeps the rejection errors visible while correcting and asks to save before resending (UC-92)', async () => {
        const user = userEvent.setup();
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'REJECTED',
                lastSubmission: { ...accepted.lastSubmission, result: 'REJECTED', errors: ['Sai liều dùng.'] },
            }),
        })} />);

        const result = await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' });
        expect(result).toHaveTextContent(SIGNING_MSG.CORRECT_ELSEWHERE);
        expect(within(result).getByRole('button', { name: 'Gửi lại' })).toBeEnabled();

        await user.click(within(result).getByRole('button', { name: 'Sửa đơn' }));
        expect(screen.getByLabelText('Thêm thuốc')).toHaveFocus();
        await user.type(screen.getByLabelText(/^Cách dùng/), ', sau ăn');

        expect(result).toHaveTextContent('Sai liều dùng.');
        expect(within(result).getByRole('button', { name: 'Gửi lại' })).toBeDisabled();
        expect(result).toHaveTextContent(SIGNING_MSG.SAVE_BEFORE_RESEND);
    });

    it('resends a rejected prescription without changing it (UC-92 AF-01)', async () => {
        const user = userEvent.setup();
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'REJECTED',
                lastSubmission: { ...accepted.lastSubmission, result: 'REJECTED', errors: ['Sai mã bác sĩ.'] },
            }),
        })} />);

        const result = await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' });
        await user.click(within(result).getByRole('button', { name: 'Gửi lại' }));

        expect(screen.getByRole('dialog', { name: 'Ký số và gửi đơn thuốc' })).toBeInTheDocument();
    });

    it('offers to send again after no answer (AF-02, MSG86)', async () => {
        const user = userEvent.setup();
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'SENT', sent: true,
                lastSubmission: { ...accepted.lastSubmission, result: 'NO_RESPONSE', respondedAt: null },
            }),
        })} />);

        const result = await screen.findByRole('alert', { name: 'Kết quả gửi đơn thuốc' });
        expect(result).toHaveTextContent(SIGNING_MSG.NO_ANSWER);
        await user.click(within(result).getByRole('button', { name: 'Gửi lại' }));

        expect(screen.getByRole('dialog', { name: 'Ký số và gửi đơn thuốc' })).toBeInTheDocument();
    });
});
