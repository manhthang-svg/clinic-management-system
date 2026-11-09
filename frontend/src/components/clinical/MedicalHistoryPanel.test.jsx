import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import MedicalHistoryPanel from './MedicalHistoryPanel';
import { MESSAGES, toRequest, validateDraft } from './medicalHistory';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const saved = {
    visitId: 'visit-1',
    patientId: 'patient-1',
    editable: true,
    readOnlyReason: null,
    history: [{ category: 'CHRONIC_DISEASE', content: 'Tăng huyết áp' }],
    allergies: [{ drug: 'Penicillin', reaction: 'Phát ban', severity: 'SEVERE' }],
    noKnownDrugAllergy: false,
    updatedAt: '2026-11-05T02:00:00.123Z',
    updatedByName: 'BS Nguyễn Văn An',
};

function apiWith(overrides = {}) {
    return {
        getMedicalHistory: vi.fn().mockResolvedValue(saved),
        updateMedicalHistory: vi.fn().mockResolvedValue(saved),
        ...overrides,
    };
}

describe('MedicalHistoryPanel', () => {
    it('shows history, red allergy labels and who updated them last', async () => {
        const onLoaded = vi.fn();
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith()} onLoaded={onLoaded} />);

        expect(await screen.findByText('Dị ứng: Penicillin (Nặng)')).toBeInTheDocument();
        expect(screen.getByText('Tăng huyết áp')).toBeInTheDocument();
        expect(screen.getByText(/BS Nguyễn Văn An/)).toBeInTheDocument();
        expect(onLoaded).toHaveBeenCalledWith(saved);
    });

    it('saves the edited lists with the last updated time it loaded', async () => {
        const user = userEvent.setup();
        const visitApi = apiWith();
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={visitApi} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));

        await user.click(screen.getByRole('button', { name: 'Thêm dị ứng' }));
        await user.type(screen.getByLabelText('Thuốc hoặc nhóm thuốc 2'), 'Aspirin');
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(visitApi.updateMedicalHistory).toHaveBeenCalledWith('visit-1', {
            history: [{ category: 'CHRONIC_DISEASE', content: 'Tăng huyết áp' }],
            allergies: [
                { drug: 'Penicillin', reaction: 'Phát ban', severity: 'SEVERE' },
                { drug: 'Aspirin', reaction: null, severity: null },
            ],
            noKnownDrugAllergy: false,
            lastUpdatedAt: '2026-11-05T02:00:00.123Z',
        });
        expect(await screen.findByRole('button', { name: 'Cập nhật' })).toBeInTheDocument();
    });

    it('requires a drug for every allergy and does not call the API', async () => {
        const user = userEvent.setup();
        const visitApi = apiWith();
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={visitApi} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));

        await user.click(screen.getByRole('button', { name: 'Thêm dị ứng' }));
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(screen.getByText('Thuốc hoặc nhóm thuốc là bắt buộc.')).toBeInTheDocument();
        expect(visitApi.updateMedicalHistory).not.toHaveBeenCalled();
    });

    it('asks before discarding unsaved changes', async () => {
        const user = userEvent.setup();
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith()} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));

        await user.click(screen.getByRole('button', { name: 'Xóa tiền sử 1' }));
        await user.click(screen.getByRole('button', { name: 'Hủy' }));

        const dialog = screen.getByRole('alertdialog', { name: 'Loại bỏ thay đổi' });
        expect(dialog).toHaveTextContent(MESSAGES.discard);
        await user.click(within(dialog).getByRole('button', { name: 'Loại bỏ' }));
        expect(screen.getByText('Tăng huyết áp')).toBeInTheDocument();
    });

    it('hides the allergy rows when "no known drug allergy" is ticked', async () => {
        const user = userEvent.setup();
        const visitApi = apiWith();
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={visitApi} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));

        await user.click(screen.getByLabelText('Không có tiền sử dị ứng thuốc'));
        expect(screen.queryByLabelText('Thuốc hoặc nhóm thuốc 1')).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(visitApi.updateMedicalHistory.mock.calls[0][1]).toMatchObject({ allergies: [], noKnownDrugAllergy: true });
    });

    it('is read-only when the visit is not examined in the doctor room', async () => {
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({
            getMedicalHistory: vi.fn().mockResolvedValue({ ...saved, editable: false, readOnlyReason: 'NOT_IN_EXAMINATION' }),
        })} />);

        expect(await screen.findByText(MESSAGES.readOnly)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Cập nhật' })).not.toBeInTheDocument();
    });

    it('shows a loading state until the data arrives', async () => {
        let resolve;
        const visitApi = apiWith({ getMedicalHistory: vi.fn(() => new Promise((done) => { resolve = done; })) });
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={visitApi} />);

        expect(screen.getByText('Đang tải tiền sử và dị ứng…')).toBeInTheDocument();
        resolve(saved);
        expect(await screen.findByText('Tăng huyết áp')).toBeInTheDocument();
        expect(screen.queryByText('Đang tải tiền sử và dị ứng…')).not.toBeInTheDocument();
    });

    it('shows empty states when nothing has been recorded yet', async () => {
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({
            getMedicalHistory: vi.fn().mockResolvedValue({
                ...saved, history: [], allergies: [], updatedAt: null, updatedByName: null,
            }),
        })} />);

        expect(await screen.findByText('Chưa ghi nhận tiền sử')).toBeInTheDocument();
        expect(screen.getByText('Chưa ghi nhận dị ứng thuốc')).toBeInTheDocument();
        expect(screen.queryByText(/Cập nhật lần cuối/)).not.toBeInTheDocument();
    });

    it('shows a load error and loads again on retry', async () => {
        const user = userEvent.setup();
        const getMedicalHistory = vi.fn()
            .mockRejectedValueOnce(new ApiError('raw', { status: 500, errorResponse: { code: 'INTERNAL_ERROR' } }))
            .mockResolvedValueOnce(saved);
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({ getMedicalHistory })} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(MESSAGES.loadError);
        await user.click(screen.getByRole('button', { name: 'Thử lại' }));

        expect(await screen.findByText('Tăng huyết áp')).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(getMedicalHistory).toHaveBeenCalledTimes(2);
    });

    it('hides the update button from roles the backend rejects', async () => {
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({
            getMedicalHistory: vi.fn().mockRejectedValue(new ApiError('raw', { status: 403, errorResponse: { code: 'ACCESS_DENIED' } })),
        })} />);

        expect(await screen.findByRole('alert')).toHaveTextContent(MESSAGES.readOnly);
        expect(screen.queryByRole('button', { name: 'Cập nhật' })).not.toBeInTheDocument();
    });

    it('shows the closed-record notice and no update button', async () => {
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({
            getMedicalHistory: vi.fn().mockResolvedValue({ ...saved, editable: false, readOnlyReason: 'MEDICAL_RECORD_CLOSED' }),
        })} />);

        expect(await screen.findByText(MESSAGES.recordClosed)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Cập nhật' })).not.toBeInTheDocument();
    });

    it('disables save and shows progress while saving', async () => {
        const user = userEvent.setup();
        let resolve;
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({
            updateMedicalHistory: vi.fn(() => new Promise((done) => { resolve = done; })),
        })} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(screen.getByRole('button', { name: 'Đang lưu…' })).toBeDisabled();
        resolve(saved);
        expect(await screen.findByRole('button', { name: 'Cập nhật' })).toBeInTheDocument();
    });

    it('leaves edit mode without asking when nothing changed', async () => {
        const user = userEvent.setup();
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith()} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));
        await user.click(screen.getByRole('button', { name: 'Hủy' }));

        expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Cập nhật' })).toBeInTheDocument();
    });

    it('reloads the latest data after another doctor saved first', async () => {
        const user = userEvent.setup();
        const latest = { ...saved, allergies: [{ drug: 'Aspirin', reaction: null, severity: null }], updatedAt: '2026-11-05T03:00:00.000Z' };
        const getMedicalHistory = vi.fn().mockResolvedValueOnce(saved).mockResolvedValueOnce(latest);
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({
            getMedicalHistory,
            updateMedicalHistory: vi.fn().mockRejectedValue(new ApiError('raw', { status: 409, errorResponse: { code: 'CONCURRENT_MODIFICATION' } })),
        })} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(MESSAGES.changedByOthers);
        await user.click(screen.getByRole('button', { name: 'Tải lại' }));

        expect(await screen.findByText('Dị ứng: Aspirin')).toBeInTheDocument();
        expect(getMedicalHistory).toHaveBeenCalledTimes(2);
    });

    it.each([
        ['UNAUTHORIZED', 401, MESSAGES.sessionExpired],
        ['REQUEST_VALIDATION_FAILED', 400, MESSAGES.invalid],
        ['MEDICAL_RECORD_CLOSED', 409, MESSAGES.recordClosed],
        ['CONCURRENT_MODIFICATION', 409, MESSAGES.changedByOthers],
        ['FORBIDDEN', 403, MESSAGES.readOnly],
        ['INTERNAL_ERROR', 500, MESSAGES.systemError],
    ])('maps save failure %s to a Vietnamese message', async (code, status, message) => {
        const user = userEvent.setup();
        render(<MedicalHistoryPanel visitId="visit-1" visitApi={apiWith({
            updateMedicalHistory: vi.fn().mockRejectedValue(new ApiError('raw', { status, errorResponse: { code } })),
        })} />);
        await user.click(await screen.findByRole('button', { name: 'Cập nhật' }));
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(message);
        expect(screen.queryByText('raw')).not.toBeInTheDocument();
    });
});

describe('medicalHistory helpers', () => {
    it('limits the whole history to 2000 characters', () => {
        const history = Array.from({ length: 9 }, () => ({ category: 'OTHER', content: 'x'.repeat(250) }));
        expect(validateDraft({ history, allergies: [], noKnownDrugAllergy: false }).history)
            .toBe('Vượt quá độ dài tối đa 2000 ký tự.');
    });

    it('trims values and drops allergies when none are known', () => {
        expect(toRequest({
            history: [{ category: 'SURGERY', content: '  Mổ ruột thừa ' }],
            allergies: [{ drug: 'Aspirin', reaction: '', severity: '' }],
            noKnownDrugAllergy: true,
        }, null)).toEqual({
            history: [{ category: 'SURGERY', content: 'Mổ ruột thừa' }],
            allergies: [],
            noKnownDrugAllergy: true,
            lastUpdatedAt: null,
        });
    });
});
