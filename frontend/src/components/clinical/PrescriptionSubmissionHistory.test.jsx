import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { PrescriptionPanel } from './PrescriptionPanel';
import { MSG } from './prescriptionForm';

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

const empty = {
    ...saved, prescriptionId: null, version: null, prescriptionCode: null, slipNumber: null, lines: [], status: null,
    doctorName: null, prescribedAt: null,
};

const rejectedSend = {
    attemptNo: 1, result: 'REJECTED', errors: ['Sai mã liên thông bác sĩ.'], submittedAt: '2026-11-12T03:05:00Z',
    respondedAt: '2026-11-12T03:05:01Z', submittedBy: 'BS An',
};

const acceptedSend = {
    attemptNo: 2, result: 'ACCEPTED', errors: [], submittedAt: '2026-11-12T04:00:00Z',
    respondedAt: '2026-11-12T04:00:01Z', submittedBy: 'BS An',
};

function api(overrides = {}) {
    return {
        getPrescription: vi.fn().mockResolvedValue(saved),
        savePrescription: vi.fn(),
        searchMedicines: vi.fn().mockResolvedValue([]),
        deletePrescription: vi.fn().mockResolvedValue(undefined),
        getPrescriptionSubmissions: vi.fn().mockResolvedValue([acceptedSend, rejectedSend]),
        ...overrides,
    };
}

describe('PrescriptionPanel (UC-59 AF-03 Xóa đơn thuốc)', () => {
    it('deletes a pending prescription after the MSG109 confirmation and starts again empty', async () => {
        const user = userEvent.setup();
        const client = api({ getPrescription: vi.fn().mockResolvedValueOnce(saved).mockResolvedValue(empty) });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        await user.click(await screen.findByRole('button', { name: 'Xóa đơn' }));
        const dialog = screen.getByRole('alertdialog', { name: 'Xóa đơn thuốc' });
        expect(dialog).toHaveTextContent(MSG.DELETE_CONFIRM('79001abc1234-c'));
        await user.click(within(dialog).getByRole('button', { name: 'Xóa đơn' }));

        expect(client.deletePrescription).toHaveBeenCalledWith('p1');
        expect(await screen.findByText('Chưa có thuốc nào trong đơn.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Xóa đơn' })).not.toBeInTheDocument();
    });

    it('keeps the prescription when the Doctor cancels the confirmation', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);

        await user.click(await screen.findByRole('button', { name: 'Xóa đơn' }));
        await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Hủy' }));

        expect(client.deletePrescription).not.toHaveBeenCalled();
        expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    });

    it('shows MSG115 when the prescription was sent meanwhile', async () => {
        const user = userEvent.setup();
        const client = api({
            deletePrescription: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409, errorResponse: { code: 'PRESCRIPTION_ALREADY_SENT', details: {} },
            })),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        await user.click(await screen.findByRole('button', { name: 'Xóa đơn' }));
        await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Xóa đơn' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.SENT);
    });

    it('does not offer to delete a prescription that was sent', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({ ...saved, status: 'REJECTED', lastSubmission: rejectedSend }),
        })} />);

        expect(await screen.findByRole('button', { name: 'Gửi đơn' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Xóa đơn' })).not.toBeInTheDocument();
    });
});

describe('PrescriptionPanel (UC-91 Kết quả gửi đơn thuốc)', () => {
    it('lists every send newest first with its result, errors and sender', async () => {
        const user = userEvent.setup();
        const client = api({
            getPrescription: vi.fn().mockResolvedValue({
                ...saved, status: 'ACCEPTED', sent: true, lastSubmission: acceptedSend,
            }),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);

        await user.click(await screen.findByRole('button', { name: 'Kết quả gửi' }));

        const table = await screen.findByRole('table', { name: 'Lịch sử gửi đơn thuốc' });
        const rows = within(table).getAllByRole('row').slice(1);
        expect(client.getPrescriptionSubmissions).toHaveBeenCalledWith('p1', expect.anything());
        expect(rows).toHaveLength(2);
        expect(rows[0]).toHaveTextContent('Chấp nhận');
        expect(rows[1]).toHaveTextContent('Bị từ chối');
        expect(rows[1]).toHaveTextContent('Sai mã liên thông bác sĩ.');
        expect(rows[1]).toHaveTextContent('BS An');
    });

    it('has no submission history before the first send', async () => {
        render(<PrescriptionPanel visitId="v1" api={api()} />);

        expect(await screen.findByRole('button', { name: 'Gửi đơn' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Kết quả gửi' })).not.toBeInTheDocument();
    });
});
