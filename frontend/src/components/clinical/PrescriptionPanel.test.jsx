import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { PrescriptionPanel } from './PrescriptionPanel';
import { MSG, suggestedQuantity } from './prescriptionForm';

const amoxicillin = {
    id: 'm1', code: 'AMO500', name: 'Amoxicilin 500mg', activeIngredient: 'Amoxicilin', strength: '500mg',
    route: 'Uống', unit: 'Viên',
};

const emptyPrescription = {
    visitId: 'v1', prescriptionId: null, version: null, prescriptionCode: null, slipNumber: null,
    conclusionSaved: true, diagnosis: 'Viêm họng cấp', diagnoses: [{ code: 'J02.9', name: 'Viêm họng cấp' }],
    allergies: ['Penicillin'], longTermAllowed: false, lines: [], notes: null, advice: null, sent: false,
    doctorName: null, prescribedAt: null,
};

const savedPrescription = {
    ...emptyPrescription, prescriptionId: 'p1', version: 0, prescriptionCode: '79001abc1234-c', slipNumber: 'DT-DK1',
    lines: [{
        medicineId: 'm1', code: 'AMO500', name: 'Amoxicilin 500mg', activeIngredient: 'Amoxicilin', strength: '500mg',
        route: 'Uống', unit: 'Viên', quantity: 21, dosePerTime: '1 viên', timesPerDay: 3, days: 7,
        usage: 'Uống sau ăn', note: null,
    }],
    doctorName: 'BS An', prescribedAt: '2026-11-12T03:00:00Z',
};

function api(overrides = {}) {
    return {
        getPrescription: vi.fn().mockResolvedValue(emptyPrescription),
        savePrescription: vi.fn().mockResolvedValue(savedPrescription),
        searchMedicines: vi.fn().mockResolvedValue([amoxicillin]),
        ...overrides,
    };
}

async function addAmoxicillin(user) {
    await user.type(screen.getByLabelText('Thêm thuốc'), 'amox');
    await user.click(await screen.findByRole('option', { name: /Amoxicilin 500mg/ }));
}

async function fillLine(user, { days = '7' } = {}) {
    await user.type(screen.getByLabelText(/^Liều mỗi lần/), '1 viên');
    await user.type(screen.getByLabelText(/^Số lần\/ngày/), '3');
    await user.type(screen.getByLabelText(/^Số ngày/), days);
    await user.type(screen.getByLabelText(/^Cách dùng/), 'Uống sau ăn');
}

describe('PrescriptionPanel (UC-59)', () => {
    it('shows the conclusion diagnoses and the drug allergies in red (BR-23)', async () => {
        render(<PrescriptionPanel visitId="v1" api={api()} />);

        expect(await screen.findByText('Dị ứng: Penicillin')).toBeInTheDocument();
        expect(screen.getByText(/bệnh chính/)).toHaveTextContent('J02.9');
        expect(screen.getByText('Chưa có thuốc nào trong đơn.')).toBeInTheDocument();
    });

    it('adds a medicine, works out the quantity and saves the prescription (steps 1-7, MSG03)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Dị ứng: Penicillin');

        await addAmoxicillin(user);
        await fillLine(user);
        expect(screen.getByLabelText(/^Số lượng/)).toHaveValue(21);
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

        expect(client.savePrescription).toHaveBeenCalledWith('v1', expect.objectContaining({
            lines: [{ medicineId: 'm1', quantity: 21, dosePerTime: '1 viên', timesPerDay: 3, days: 7, usage: 'Uống sau ăn', note: null }],
            confirmAllergy: false, version: null,
        }));
        expect(await screen.findByText('79001abc1234-c')).toBeInTheDocument();
    });

    it('asks for at least one medicine and for the required line fields (EF-01, MSG02)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Dị ứng: Penicillin');

        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
        expect(screen.getByRole('alert')).toHaveTextContent(MSG.NO_LINE);

        await addAmoxicillin(user);
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
        expect(screen.getByText('Trường Cách dùng là bắt buộc.')).toBeInTheDocument();
        expect(client.savePrescription).not.toHaveBeenCalled();
    });

    it('keeps more than 30 days for long-term diseases only (TT 26/2025)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Dị ứng: Penicillin');

        await addAmoxicillin(user);
        await fillLine(user, { days: '45' });
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

        expect(screen.getAllByText(MSG.LONG_TERM).length).toBeGreaterThan(1);
        expect(client.savePrescription).not.toHaveBeenCalled();
    });

    it('warns about a drug allergy and saves when the Doctor confirms (AF-01, MSG26)', async () => {
        const user = userEvent.setup();
        const client = api({
            savePrescription: vi.fn()
                .mockRejectedValueOnce(new ApiError('raw', {
                    status: 409,
                    errorResponse: { code: 'PRESCRIPTION_ALLERGY_WARNING', details: { allergies: 'Penicillin (Amoxicilin 500mg)' } },
                }))
                .mockResolvedValueOnce(savedPrescription),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Dị ứng: Penicillin');
        await addAmoxicillin(user);
        await fillLine(user);

        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Penicillin (Amoxicilin 500mg)');
        await user.click(within(dialog).getByRole('button', { name: 'Vẫn kê thuốc này' }));

        expect(client.savePrescription).toHaveBeenLastCalledWith('v1', expect.objectContaining({ confirmAllergy: true }));
    });

    it('asks to save the conclusion first and offers no editing until then', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({ getPrescription: vi.fn().mockResolvedValue({ ...emptyPrescription, conclusionSaved: false }) })} />);

        expect(await screen.findByText(MSG.CONCLUSION_REQUIRED)).toBeInTheDocument();
        expect(screen.queryByLabelText('Thêm thuốc')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Lưu đơn thuốc' })).not.toBeInTheDocument();
    });
});

describe('suggestedQuantity', () => {
    it('multiplies a numeric dose by times a day and days, rounding up', () => {
        expect(suggestedQuantity({ dosePerTime: '1 viên', timesPerDay: '3', days: '7' })).toBe(21);
        expect(suggestedQuantity({ dosePerTime: '0,5 viên', timesPerDay: '3', days: '5' })).toBe(8);
        expect(suggestedQuantity({ dosePerTime: 'theo chỉ dẫn', timesPerDay: '1', days: '3' })).toBeNull();
    });
});
