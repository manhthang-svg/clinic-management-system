import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { FollowUpPanel } from './FollowUpPanel';
import { FOLLOW_UP_MSG } from './followUpForm';

const empty = {
    visitId: 'v1', visitDate: '2026-11-03', minDate: '2026-11-04', maxDate: '2027-11-03', conclusionSaved: true,
    diagnoses: [{ code: 'I10', name: 'Tăng huyết áp vô căn' }], appointment: null,
    prescriptionAccepted: false, prescriptionFollowUpDate: null,
};

const appointment = {
    id: 'a1', version: 1, slipNumber: 'GH-DK0000000123', followUpDate: '2026-12-03',
    advice: 'Đo huyết áp mỗi sáng', status: 'SCHEDULED', printedAt: null,
};

const saved = { ...empty, appointment };

function api(overrides = {}) {
    return {
        getFollowUp: vi.fn().mockResolvedValue(empty),
        saveFollowUp: vi.fn().mockResolvedValue(saved),
        ...overrides,
    };
}

afterEach(() => vi.restoreAllMocks());

describe('FollowUpPanel (UC-61 Hẹn tái khám)', () => {
    it('schedules the follow-up with its advice and shows the slip number (UC-61)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<FollowUpPanel visitId="v1" api={client} />);

        const date = await screen.findByLabelText(/Ngày tái khám/);
        expect(date).toHaveAttribute('min', '2026-11-04');
        expect(date).toHaveAttribute('max', '2027-11-03');
        await user.type(date, '2026-12-03');
        await user.type(screen.getByLabelText('Lời dặn tái khám'), 'Đo huyết áp mỗi sáng');
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(client.saveFollowUp).toHaveBeenCalledWith('v1',
            { followUpDate: '2026-12-03', advice: 'Đo huyết áp mỗi sáng', version: null });
        expect(await screen.findByText('GH-DK0000000123')).toBeInTheDocument();
        // PO 23/11/2026: the slip is printed once the record is closed (UC-62), not from the open visit.
        expect(screen.queryByRole('button', { name: 'In giấy hẹn' })).not.toBeInTheDocument();
    });

    it('checks the date before saving (BR-61-02, MSG02, MSG33)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<FollowUpPanel visitId="v1" api={client} />);

        await user.click(await screen.findByRole('button', { name: 'Lưu' }));
        expect(screen.getByText(FOLLOW_UP_MSG.DATE_REQUIRED)).toBeInTheDocument();

        await user.type(screen.getByLabelText(/Ngày tái khám/), '2026-11-03');
        expect(screen.getByText(FOLLOW_UP_MSG.NOT_AFTER_VISIT)).toBeInTheDocument();
        expect(client.saveFollowUp).not.toHaveBeenCalled();
    });

    it('shows the server reason under the date (MSG125)', async () => {
        const user = userEvent.setup();
        const client = api({
            saveFollowUp: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 422,
                errorResponse: { code: 'FOLLOW_UP_APPOINTMENT_INVALID', details: { field: 'followUpDate', reason: 'TOO_FAR' } },
            })),
        });
        render(<FollowUpPanel visitId="v1" api={client} />);

        await user.type(await screen.findByLabelText(/Ngày tái khám/), '2026-12-03');
        await user.click(screen.getByRole('button', { name: 'Lưu' }));

        expect(await screen.findByText(FOLLOW_UP_MSG.TOO_FAR)).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent(FOLLOW_UP_MSG.CHECK_FIELDS);
    });

    it('needs the saved conclusion first (EF-03, MSG127)', async () => {
        render(<FollowUpPanel visitId="v1" api={api({
            getFollowUp: vi.fn().mockResolvedValue({ ...empty, conclusionSaved: false, diagnoses: [] }),
        })} />);

        expect(await screen.findByText(FOLLOW_UP_MSG.CONCLUSION_REQUIRED)).toBeInTheDocument();
        expect(screen.getByLabelText(/Ngày tái khám/)).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Lưu' })).not.toBeInTheDocument();
    });

    it('changes the saved appointment with its version and asks before discarding (AF-01, AF-02, MSG38)', async () => {
        const user = userEvent.setup();
        const client = api({ getFollowUp: vi.fn().mockResolvedValue(saved) });
        render(<FollowUpPanel visitId="v1" api={client} />);

        const advice = await screen.findByLabelText('Lời dặn tái khám');
        expect(advice).toHaveValue('Đo huyết áp mỗi sáng');
        await user.type(advice, ', ghi sổ');

        await user.click(screen.getByRole('button', { name: 'Hủy' }));
        const dialog = screen.getByRole('alertdialog', { name: 'Thay đổi chưa lưu' });
        expect(dialog).toHaveTextContent(FOLLOW_UP_MSG.DISCARD);
        await user.click(within(dialog).getByRole('button', { name: 'Loại bỏ' }));
        expect(advice).toHaveValue('Đo huyết áp mỗi sáng');

        await user.type(advice, ', ghi sổ');
        await user.click(screen.getByRole('button', { name: 'Lưu' }));
        expect(client.saveFollowUp).toHaveBeenCalledWith('v1',
            { followUpDate: '2026-12-03', advice: 'Đo huyết áp mỗi sáng, ghi sổ', version: 1 });
    });

    it('says the sent prescription keeps its own date (AF-03, MSG126)', async () => {
        render(<FollowUpPanel visitId="v1" api={api({
            getFollowUp: vi.fn().mockResolvedValue({ ...saved, prescriptionAccepted: true, prescriptionFollowUpDate: null }),
        })} />);

        expect(await screen.findByText(FOLLOW_UP_MSG.PRESCRIPTION_SENT(''))).toBeInTheDocument();
    });

    it('locks an appointment that reception already used (MSG128)', async () => {
        render(<FollowUpPanel visitId="v1" api={api({
            getFollowUp: vi.fn().mockResolvedValue({ ...saved, appointment: { ...appointment, status: 'ARRIVED' } }),
        })} />);

        expect(await screen.findByText(FOLLOW_UP_MSG.NOT_SCHEDULED)).toBeInTheDocument();
        expect(screen.getByLabelText(/Ngày tái khám/)).toBeDisabled();
    });
    describe('UC-75 email nhắc lịch tái khám', () => {
        const scheduled = { ...saved, patientHasEmail: true, reminderDaysBefore: 1 };

        it('tells the Doctor the reminder will be sent automatically', async () => {
            render(<FollowUpPanel visitId="v1" api={api({ getFollowUp: vi.fn().mockResolvedValue(scheduled) })} />);

            expect(await screen.findByText(FOLLOW_UP_MSG.REMINDER_PLANNED(1))).toBeInTheDocument();
        });

        it('shows when the reminder was sent, in clinic time', async () => {
            const sent = { ...scheduled, appointment: { ...appointment, reminderSentAt: '2026-12-02T01:00:00Z' } };
            render(<FollowUpPanel visitId="v1" api={api({ getFollowUp: vi.fn().mockResolvedValue(sent) })} />);

            expect(await screen.findByText(FOLLOW_UP_MSG.REMINDER_SENT('08:00 02/12/2026'))).toBeInTheDocument();
        });

        it('warns that a patient without email gets no reminder', async () => {
            const noEmail = { ...scheduled, patientHasEmail: false };
            render(<FollowUpPanel visitId="v1" api={api({ getFollowUp: vi.fn().mockResolvedValue(noEmail) })} />);

            expect(await screen.findByText(FOLLOW_UP_MSG.REMINDER_NO_EMAIL)).toBeInTheDocument();
        });

        it('says nothing when the reminder job is off or before the appointment is saved', async () => {
            const off = { ...scheduled, reminderDaysBefore: null };
            const { unmount } = render(<FollowUpPanel visitId="v1" api={api({ getFollowUp: vi.fn().mockResolvedValue(off) })} />);
            await screen.findByText('GH-DK0000000123');
            expect(screen.queryByText(/email nhắc lịch/)).not.toBeInTheDocument();
            unmount();

            render(<FollowUpPanel visitId="v1" api={api({ getFollowUp: vi.fn().mockResolvedValue({ ...empty, patientHasEmail: true, reminderDaysBefore: 1 }) })} />);
            await screen.findByLabelText(/Ngày tái khám/);
            expect(screen.queryByText(/email nhắc lịch/)).not.toBeInTheDocument();
        });
    });
});
