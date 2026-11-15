import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { PrintPrescriptionButton } from './PrescriptionPrint';
import { PRINT_MSG, medicineLabel, quantityText } from './prescriptionPrintFormat';

const form = {
    prescriptionId: 'p1', prescriptionCode: '79001abc1234-c', copy: false, printedAt: null, printedBy: null,
    facility: { name: 'Phòng khám METPH', address: '1 Lê Lợi', phone: '0281234567' },
    patient: {
        fullName: 'Nguyễn Văn An', birthDate: '1990-01-01', gender: 'Nam', address: '1 Lê Lợi, Bến Nghé',
        phone: '0901234567', citizenId: '079090000001', passportNumber: null, insuranceNumber: null,
    },
    child: null,
    diagnoses: [{ code: 'J02.9', name: 'Viêm họng cấp' }],
    lines: [
        {
            activeIngredient: 'Amoxicilin', name: 'Amoxicilin 500mg', strength: '500mg', route: 'Uống', unit: 'Viên',
            quantity: 21, dosePerTime: '1 viên', timesPerDay: 3, days: 7, usage: 'Uống sau ăn', note: null,
        },
        {
            activeIngredient: 'Paracetamol', name: 'Hapacol', strength: '500mg', route: 'Uống', unit: 'Viên',
            quantity: 6, dosePerTime: '1 viên', timesPerDay: 2, days: 3, usage: 'Khi sốt', note: null,
        },
    ],
    notes: null, advice: 'Uống nhiều nước', followUpDate: '2026-11-19', prescribedAt: '2026-11-12T03:00:00Z',
    doctorName: 'ThS. BS An',
    signature: {
        attemptNo: 1, result: 'ACCEPTED', signedAt: '2026-11-12T03:05:00Z', signerName: 'BS An',
        certificateSerial: 'abc123', certificateIssuer: 'CN=CA giả lập', signatureValid: true,
    },
};

const printed = { ...form, printedAt: '2026-11-12T04:00:00Z', printedBy: 'ThS. BS An' };

function api(overrides = {}) {
    return {
        getPrescriptionPrint: vi.fn().mockResolvedValue(form),
        preparePrescriptionPrint: vi.fn().mockResolvedValue(printed),
        ...overrides,
    };
}

afterEach(() => vi.restoreAllMocks());

describe('UC-60 In đơn thuốc', () => {
    it('names each medicine by its active ingredient and trade name, with a leading zero below 10 (BR-59-13)', () => {
        expect(medicineLabel(form.lines[1])).toBe('Paracetamol (Hapacol) 500mg');
        expect(medicineLabel(form.lines[0])).toBe('Amoxicilin (Amoxicilin 500mg)');
        expect(quantityText(6)).toBe('06');
        expect(quantityText(21)).toBe('21');
    });

    it('cannot print a prescription that was not accepted (AF-01, MSG112)', () => {
        const client = api();
        render(<PrintPrescriptionButton prescriptionId="p1" accepted={false} api={client} />);

        const button = screen.getByRole('button', { name: 'In đơn thuốc' });
        expect(button).toBeDisabled();
        expect(button).toHaveAccessibleDescription(PRINT_MSG.NOT_PRINTABLE);
        expect(client.getPrescriptionPrint).not.toHaveBeenCalled();
    });

    it('previews the form, then records the print and opens the print dialog', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const client = api();
        render(<PrintPrescriptionButton prescriptionId="p1" accepted api={client} />);

        await user.click(screen.getByRole('button', { name: 'In đơn thuốc' }));
        const dialog = screen.getByRole('dialog', { name: 'In đơn thuốc' });
        const sheet = await within(dialog).findByRole('article', { name: 'Đơn thuốc in' });

        expect(client.getPrescriptionPrint).toHaveBeenCalledWith('p1', expect.any(AbortSignal));
        expect(within(sheet).getByText('79001abc1234-c')).toBeInTheDocument();
        expect(within(sheet).getByTitle('Mã QR của đơn thuốc 79001abc1234-c')).toBeInTheDocument();
        expect(within(sheet).getByText(/Paracetamol \(Hapacol\) 500mg/)).toBeInTheDocument();
        expect(within(sheet).getByText('06')).toBeInTheDocument();
        expect(within(sheet).getByText(/J02\.9 – Viêm họng cấp/)).toBeInTheDocument();
        expect(within(sheet).getByText('Chữ ký số hợp lệ')).toBeInTheDocument();
        expect(within(sheet).getByTestId('print-mark'))
            .toHaveTextContent('Bản in từ đơn thuốc điện tử 79001abc1234-c lưu tại Phòng khám METPH.');
        expect(client.preparePrescriptionPrint).not.toHaveBeenCalled();

        await user.click(within(dialog).getByRole('button', { name: 'In' }));

        expect(client.preparePrescriptionPrint).toHaveBeenCalledWith('p1');
        expect(print).toHaveBeenCalledTimes(1);
        expect(within(dialog).getByTestId('print-mark'))
            .toHaveTextContent('lưu tại Phòng khám METPH; người in ThS. BS An, 11:00 12/11/2026.');
    });

    it('shows MSG112 when the server refuses the print', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const client = api({
            preparePrescriptionPrint: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409, errorResponse: { code: 'PRESCRIPTION_NOT_PRINTABLE', details: {} },
            })),
        });
        render(<PrintPrescriptionButton prescriptionId="p1" accepted api={client} />);

        await user.click(screen.getByRole('button', { name: 'In đơn thuốc' }));
        const dialog = screen.getByRole('dialog', { name: 'In đơn thuốc' });
        await within(dialog).findByRole('article', { name: 'Đơn thuốc in' });
        await user.click(within(dialog).getByRole('button', { name: 'In' }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent(PRINT_MSG.NOT_PRINTABLE);
        expect(print).not.toHaveBeenCalled();
    });
});
