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

        expect(await screen.findByText('Penicillin')).toBeInTheDocument();
        expect(screen.getByText(/bệnh chính/)).toHaveTextContent('J02.9');
        expect(screen.getByText('Chưa có thuốc nào trong đơn.')).toBeInTheDocument();
    });

    it('adds a medicine, works out the quantity and saves the prescription (steps 1-7, MSG03)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');

        await addAmoxicillin(user);
        await fillLine(user);
        expect(screen.getByLabelText(/^Số lượng/)).toHaveValue(21);
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

        expect(client.savePrescription).toHaveBeenCalledWith('v1', expect.objectContaining({
            lines: [{ medicineId: 'm1', quantity: 21, dosePerTime: '1 viên', timesPerDay: 3, days: 7, usage: 'Uống sau ăn', note: null }],
            version: null,
        }));
        expect(await screen.findByText('79001abc1234-c')).toBeInTheDocument();
    });

    it('asks for at least one medicine and for the required line fields (EF-01, MSG02)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');

        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
        expect(screen.getByRole('alert')).toHaveTextContent(MSG.NO_LINE);

        await addAmoxicillin(user);
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
        expect(screen.getByText('Trường Cách dùng là bắt buộc.')).toBeInTheDocument();
        expect(client.savePrescription).not.toHaveBeenCalled();
    });

    it('explains the 30-day limit at the days field only when more than 30 days are typed (EF-04)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');
        await addAmoxicillin(user);
        expect(screen.queryByText(MSG.LONG_TERM)).not.toBeInTheDocument();

        await fillLine(user, { days: '45' });
        expect(screen.getByText(MSG.LONG_TERM)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

        expect(screen.getByText(MSG.LONG_TERM)).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent(MSG.CHECK_FIELDS);
        expect(client.savePrescription).not.toHaveBeenCalled();
    });

    it('allows up to 90 days when a diagnosis is a long-term disease (AF-05)', async () => {
        const user = userEvent.setup();
        const client = api({ getPrescription: vi.fn().mockResolvedValue({ ...emptyPrescription, longTermAllowed: true }) });
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');
        await addAmoxicillin(user);

        await fillLine(user, { days: '60' });
        expect(screen.getByText(MSG.LONG_TERM_ALLOWED)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

        expect(client.savePrescription.mock.calls[0][1].lines[0].days).toBe(60);
    });

    it('lists each medicine on one grid row with its catalog columns', async () => {
        const user = userEvent.setup();
        render(<PrescriptionPanel visitId="v1" api={api()} />);
        await screen.findByText('Penicillin');
        await addAmoxicillin(user);

        const table = screen.getByRole('table', { name: 'Các thuốc trong đơn' });
        const headers = within(table).getAllByRole('columnheader').map((cell) => cell.textContent);
        expect(headers).toEqual(expect.arrayContaining(['Tên thuốc', 'Hoạt chất', 'ĐVT', 'Đường dùng', 'SL', 'Cách dùng']));
        const row = within(table).getByRole('row', { name: /Amoxicilin 500mg/ });
        expect(row).toHaveTextContent('Amoxicilin 500mg');
        expect(row).toHaveTextContent('Viên');
        expect(row).toHaveTextContent('Uống');
    });

    it('moves a medicine up and saves the new order', async () => {
        const user = userEvent.setup();
        const paracetamol = { ...amoxicillin, id: 'm2', code: 'PARA500', name: 'Paracetamol 500mg', activeIngredient: 'Paracetamol' };
        const client = api({ searchMedicines: vi.fn().mockResolvedValue([amoxicillin, paracetamol]) });
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');
        await addAmoxicillin(user);
        await user.type(screen.getByLabelText('Thêm thuốc'), 'para');
        await user.click(await screen.findByRole('option', { name: /Paracetamol 500mg/ }));

        await user.click(screen.getByRole('button', { name: 'Đưa Paracetamol 500mg lên' }));

        expect(screen.getByRole('button', { name: 'Đưa Paracetamol 500mg lên' })).toBeDisabled();
        const names = screen.getAllByRole('row').map((row) => row.textContent).filter((text) => /mg/.test(text));
        expect(names[0]).toContain('Paracetamol 500mg');
    });

    it('saves without an allergy check even when the patient has a recorded drug allergy', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');
        await addAmoxicillin(user);
        await fillLine(user);

        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

        expect(await screen.findByText('79001abc1234-c')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(client.savePrescription).toHaveBeenCalledTimes(1);
        expect(client.savePrescription.mock.calls[0][1]).not.toHaveProperty('confirmAllergy');
    });

    it('refuses a quantity that is not a whole number of the unit (BR-59-04)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');
        await addAmoxicillin(user);
        await fillLine(user);
        const quantity = screen.getByLabelText(/^Số lượng/);

        await user.clear(quantity);
        await user.type(quantity, '0.12');
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
        expect(screen.getByText(MSG.QUANTITY)).toBeInTheDocument();
        expect(client.savePrescription).not.toHaveBeenCalled();

        await user.clear(quantity);
        await user.type(quantity, '20');
        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));
        expect(client.savePrescription.mock.calls[0][1].lines[0].quantity).toBe(20);
    });

    it('shows MSG106 under the line when a medicine left the ordinary group (EF-03)', async () => {
        const user = userEvent.setup();
        const client = api({
            savePrescription: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409,
                errorResponse: { code: 'PRESCRIPTION_MEDICINE_UNAVAILABLE', details: { line: '0', reason: 'NOT_ORDINARY', medicineName: 'Amoxicilin 500mg' } },
            })),
        });
        render(<PrescriptionPanel visitId="v1" api={client} />);
        await screen.findByText('Penicillin');
        await addAmoxicillin(user);
        await fillLine(user);

        await user.click(screen.getByRole('button', { name: 'Lưu đơn thuốc' }));

        expect(await screen.findAllByText(MSG.MEDICINE_NOT_ORDINARY('Amoxicilin 500mg'))).toHaveLength(2);
    });

    it('disables the form without the link codes (EF-07, MSG116)', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({ getPrescription: vi.fn().mockResolvedValue({ ...emptyPrescription, prescriberLinkCodeMissing: true }) })} />);

        expect(await screen.findByText(MSG.LINK_CODE_MISSING(MSG.PRESCRIBER))).toBeInTheDocument();
        expect(screen.queryByLabelText('Thêm thuốc')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Lưu đơn thuốc' })).not.toBeInTheDocument();
    });

    it('shows the child data with the missing items and the follow-up date (BR-59-07)', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({
            getPrescription: vi.fn().mockResolvedValue({
                ...emptyPrescription, followUpDate: '2026-11-10',
                child: { ageMonths: 18, weightKg: null, accompanyingPerson: 'Trần Thị Mẹ' },
            }),
        })} />);

        const child = await screen.findByRole('region', { name: 'Thông tin trẻ dưới 72 tháng tuổi' });
        expect(child).toHaveTextContent('18 tháng');
        expect(child).toHaveTextContent('Trần Thị Mẹ');
        expect(child).toHaveTextContent(MSG.CHILD_DATA_MISSING('cân nặng'));
        expect(screen.getByText('10/11/2026')).toBeInTheDocument();
    });

    it('asks to save the conclusion first and offers no editing until then', async () => {
        render(<PrescriptionPanel visitId="v1" api={api({ getPrescription: vi.fn().mockResolvedValue({ ...emptyPrescription, conclusionSaved: false }) })} />);

        expect(await screen.findByText(MSG.CONCLUSION_REQUIRED)).toBeInTheDocument();
        expect(screen.queryByLabelText('Thêm thuốc')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Lưu đơn thuốc' })).not.toBeInTheDocument();
    });
});

describe('suggestedQuantity', () => {
    it('multiplies a whole dose of the dispensing unit by times a day and days (BR-59-04)', () => {
        expect(suggestedQuantity({ dosePerTime: '1 viên', unit: 'Viên', timesPerDay: '3', days: '7' })).toBe(21);
        expect(suggestedQuantity({ dosePerTime: '2', unit: 'Viên', timesPerDay: '2', days: '5' })).toBe(20);
        expect(suggestedQuantity({ dosePerTime: '1 ống', unit: 'Ống', timesPerDay: '2', days: '3' })).toBe(6);
    });

    it('suggests nothing for another unit, a split dose, free text or a total over 9,999', () => {
        expect(suggestedQuantity({ dosePerTime: '500 mg', unit: 'Viên', timesPerDay: '3', days: '5' })).toBeNull();
        expect(suggestedQuantity({ dosePerTime: '5 ml', unit: 'Lọ', timesPerDay: '3', days: '5' })).toBeNull();
        expect(suggestedQuantity({ dosePerTime: '0,5 viên', unit: 'Viên', timesPerDay: '3', days: '5' })).toBeNull();
        expect(suggestedQuantity({ dosePerTime: '1/2 viên', unit: 'Viên', timesPerDay: '2', days: '5' })).toBeNull();
        expect(suggestedQuantity({ dosePerTime: 'theo chỉ dẫn', unit: 'Viên', timesPerDay: '1', days: '3' })).toBeNull();
        expect(suggestedQuantity({ dosePerTime: '50 viên', unit: 'Viên', timesPerDay: '24', days: '90' })).toBeNull();
    });
});
