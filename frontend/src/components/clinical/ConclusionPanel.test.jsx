import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { ConclusionPanel } from './ConclusionPanel';
import { MSG, toPayload, validateConclusion, EMPTY_CONCLUSION } from './conclusionForm';

const emptyForm = {
    visitId: 'v1', medicalRecordId: 'r1', conclusionId: null, version: null,
    preliminaryDiagnosis: 'Viêm họng', preliminaryDiagnoses: [{ code: 'J02.9', name: 'Viêm họng cấp' }],
    diagnosis: null, mainDiagnosis: null, secondaryDiagnoses: [], treatmentPlan: null, treatmentResult: null,
    advice: null, summary: null, note: null, diagnosesLocked: false, doctorName: null, concludedAt: null,
};

const savedForm = {
    ...emptyForm, conclusionId: 'c1', version: 0, diagnosis: 'Viêm họng cấp do liên cầu',
    mainDiagnosis: { code: 'J02.0', name: 'Viêm họng do liên cầu' }, treatmentPlan: 'Kháng sinh 7 ngày',
    treatmentResult: 2, advice: 'Uống nhiều nước', doctorName: 'BS An', concludedAt: '2026-11-12T03:00:00Z',
};

function api(overrides = {}) {
    return {
        getConclusion: vi.fn().mockResolvedValue(emptyForm),
        saveConclusion: vi.fn().mockResolvedValue(savedForm),
        searchIcd10: vi.fn().mockResolvedValue([{ code: 'J02.0', name: 'Viêm họng do liên cầu' }]),
        ...overrides,
    };
}

async function fillRequired(user) {
    await user.type(screen.getByLabelText(/^Chẩn đoán/), 'Viêm họng cấp do liên cầu');
    await user.type(screen.getByLabelText(/^Bệnh chính/), 'J02');
    await user.click(await screen.findByRole('option', { name: /J02\.0/ }));
    await user.type(screen.getByLabelText(/^Hướng điều trị/), 'Kháng sinh 7 ngày');
    await user.click(screen.getByRole('radio', { name: 'Đỡ' }));
    await user.type(screen.getByLabelText(/^Lời dặn/), 'Uống nhiều nước');
}

describe('ConclusionPanel (UC-56)', () => {
    it('shows the preliminary diagnosis for reference and the empty form', async () => {
        render(<ConclusionPanel visitId="v1" api={api()} />);

        expect(await screen.findByText('Viêm họng')).toBeInTheDocument();
        expect(screen.getByText('J02.9')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Lưu kết luận' })).toBeEnabled();
        expect(screen.getAllByRole('radio')).toHaveLength(4);
    });

    it('marks the empty required fields and does not save (EF-04, MSG02)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<ConclusionPanel visitId="v1" api={client} />);
        await screen.findByText('Viêm họng');

        await user.click(screen.getByRole('button', { name: 'Lưu kết luận' }));

        expect(screen.getByRole('alert')).toHaveTextContent(MSG.CHECK_FIELDS);
        expect(screen.getByText('Trường Chẩn đoán là bắt buộc.')).toBeInTheDocument();
        expect(screen.getByText(MSG.MAIN_REQUIRED)).toBeInTheDocument();
        expect(client.saveConclusion).not.toHaveBeenCalled();
    });

    it('saves the conclusion with the chosen ICD-10 code (steps 4-7, MSG03)', async () => {
        const user = userEvent.setup();
        const client = api();
        const onSaved = vi.fn();
        render(<ConclusionPanel visitId="v1" api={client} onSaved={onSaved} />);
        await screen.findByText('Viêm họng');

        await fillRequired(user);
        await user.click(screen.getByRole('button', { name: 'Lưu kết luận' }));

        expect(client.saveConclusion).toHaveBeenCalledWith('v1', expect.objectContaining({
            diagnosis: 'Viêm họng cấp do liên cầu', mainIcd10Code: 'J02.0', secondaryIcd10Codes: [],
            treatmentPlan: 'Kháng sinh 7 ngày', treatmentResult: 2, advice: 'Uống nhiều nước',
            confirmDiscouragedMain: false, version: null,
        }));
        expect(await screen.findByText(/Đã lưu lúc/)).toHaveTextContent('BS An');
        expect(onSaved).toHaveBeenCalledWith(savedForm);
    });

    it('asks before keeping a discouraged main diagnosis and saves again when confirmed (AF-01, MSG66)', async () => {
        const user = userEvent.setup();
        const client = api({
            saveConclusion: vi.fn()
                .mockRejectedValueOnce(new ApiError('raw', {
                    status: 409,
                    errorResponse: { code: 'CONCLUSION_MAIN_DIAGNOSIS_DISCOURAGED', details: { icd10Code: 'J02.0' } },
                }))
                .mockResolvedValueOnce(savedForm),
        });
        render(<ConclusionPanel visitId="v1" api={client} />);
        await screen.findByText('Viêm họng');
        await fillRequired(user);

        await user.click(screen.getByRole('button', { name: 'Lưu kết luận' }));
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent(MSG.mainDiscouraged('J02.0'));
        await user.click(within(dialog).getByRole('button', { name: 'Giữ mã này' }));

        expect(client.saveConclusion).toHaveBeenLastCalledWith('v1', expect.objectContaining({ confirmDiscouragedMain: true }));
    });

    it('keeps the diagnoses read-only once sent with a prescription (AF-02, BR-47)', async () => {
        render(<ConclusionPanel visitId="v1" api={api({ getConclusion: vi.fn().mockResolvedValue({ ...savedForm, diagnosesLocked: true }) })} />);

        expect(await screen.findByText(MSG.DIAGNOSES_LOCKED)).toBeInTheDocument();
        expect(screen.getByText('J02.0')).toBeInTheDocument();
        expect(screen.queryByLabelText(/^Bệnh chính/)).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Xóa J02.0' })).not.toBeInTheDocument();
        expect(screen.getByLabelText(/^Lời dặn/)).toHaveValue('Uống nhiều nước');
    });

    it('shows a Vietnamese message, never the raw backend message', async () => {
        render(<ConclusionPanel visitId="v1" api={api({
            getConclusion: vi.fn().mockRejectedValue(new ApiError('Visit is not concludable', {
                status: 409, errorResponse: { code: 'VISIT_NOT_IN_EXAMINATION', message: 'Visit is not concludable' },
            })),
        })} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(MSG.NOT_CONCLUDABLE);
        expect(screen.queryByText('Visit is not concludable')).not.toBeInTheDocument();
    });
});

describe('conclusionForm', () => {
    it('refuses the main diagnosis repeated as a secondary one (MSG64)', () => {
        const form = {
            ...EMPTY_CONCLUSION, diagnosis: 'A', treatmentPlan: 'B', advice: 'C', treatmentResult: 1,
            main: { code: 'J02.0', name: 'x' }, secondary: [{ code: 'J02.0', name: 'x' }],
        };
        expect(validateConclusion(form)).toEqual({ secondary: MSG.MAIN_REQUIRED });
    });

    it('sends empty optional texts as null', () => {
        const payload = toPayload({ ...EMPTY_CONCLUSION, diagnosis: ' A ', main: { code: 'J02.0' }, summary: '  ' }, 3);
        expect(payload).toMatchObject({ diagnosis: 'A', mainIcd10Code: 'J02.0', summary: null, note: null, version: 3 });
    });
});
