import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { ConfirmConclusionSection } from './ConfirmConclusionSection';
import { CLOSE_MSG } from './closeRecord';

const ready = {
    conclusionSaved: true, conclusionVersion: 4, pendingParaclinicalCount: 0, prescriptionStatus: 'ACCEPTED',
    followUpScheduled: true, canClose: true,
};
const closed = {
    visitId: 'v1', visitCode: 'DK0000000123', visitStatus: 'COMPLETED', closedAt: '2026-11-17T03:00:00Z',
    archiveNumber: 'LT-DK0000000123', retentionCategory: 'STANDARD', retentionYears: 10, expiresOn: '2036-12-31',
};

function api(overrides = {}) {
    return {
        getConclusionClosure: vi.fn().mockResolvedValue(ready),
        confirmConclusion: vi.fn().mockResolvedValue(closed),
        ...overrides,
    };
}

function apiError(status, code, details = {}) {
    return new ApiError('raw', { status, errorResponse: { code, message: 'raw', details } });
}

describe('ConfirmConclusionSection (UC-58)', () => {
    it('is disabled until the conclusion is saved, and while changes are unsaved', () => {
        const { rerender } = render(<ConfirmConclusionSection visitId="v1" saved={false} dirty={false} api={api()} />);
        expect(screen.getByRole('button', { name: 'Xác nhận kết luận' })).toBeDisabled();
        expect(screen.getByText(CLOSE_MSG.CONCLUSION_REQUIRED)).toBeInTheDocument();

        rerender(<ConfirmConclusionSection visitId="v1" saved dirty api={api()} />);
        expect(screen.getByRole('button', { name: 'Xác nhận kết luận' })).toBeDisabled();
        expect(screen.getByText(CLOSE_MSG.SAVE_FIRST)).toBeInTheDocument();
    });

    it('asks MSG27 with the retention category and closes the record (MSG130)', async () => {
        const user = userEvent.setup();
        const client = api();
        const onClosed = vi.fn();
        render(<ConfirmConclusionSection visitId="v1" saved dirty={false} api={client} onClosed={onClosed} />);

        await user.click(screen.getByRole('button', { name: 'Xác nhận kết luận' }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText(CLOSE_MSG.CONFIRM)).toBeInTheDocument();
        const category = within(dialog).getByLabelText('Loại hồ sơ lưu trữ');
        expect(category).toHaveValue('STANDARD');
        await user.selectOptions(category, 'TRAFFIC_ACCIDENT');
        await user.click(within(dialog).getByRole('button', { name: 'Xác nhận' }));

        expect(client.confirmConclusion).toHaveBeenCalledWith('v1', {
            conclusionVersion: 4, retentionCategory: 'TRAFFIC_ACCIDENT',
        });
        expect(onClosed).toHaveBeenCalledWith(closed);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('does nothing when the Doctor cancels MSG27 (AF-01)', async () => {
        const user = userEvent.setup();
        const client = api();
        render(<ConfirmConclusionSection visitId="v1" saved dirty={false} api={client} />);

        await user.click(screen.getByRole('button', { name: 'Xác nhận kết luận' }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Hủy' }));

        expect(client.confirmConclusion).not.toHaveBeenCalled();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('shows what blocks instead of the dialog (MSG25, MSG67) with a link to the prescription tab', async () => {
        const user = userEvent.setup();
        const onOpenPrescription = vi.fn();
        const client = api({
            getConclusionClosure: vi.fn().mockResolvedValue({
                ...ready, pendingParaclinicalCount: 2, prescriptionStatus: 'REJECTED', canClose: false,
            }),
        });
        render(<ConfirmConclusionSection visitId="v1" saved dirty={false} api={client} onOpenPrescription={onOpenPrescription} />);

        await user.click(screen.getByRole('button', { name: 'Xác nhận kết luận' }));

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent(CLOSE_MSG.pendingParaclinical(2));
        expect(alert).toHaveTextContent(CLOSE_MSG.PRESCRIPTION_NOT_ACCEPTED);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        await user.click(within(alert).getByRole('button', { name: 'Mở tab Kê đơn' }));
        expect(onOpenPrescription).toHaveBeenCalled();
    });

    it('maps a refusal of the confirmation to a Vietnamese message, never the raw one', async () => {
        const user = userEvent.setup();
        const client = api({
            confirmConclusion: vi.fn().mockRejectedValue(apiError(409, 'CONCURRENT_MODIFICATION')),
        });
        render(<ConfirmConclusionSection visitId="v1" saved dirty={false} api={client} />);

        await user.click(screen.getByRole('button', { name: 'Xác nhận kết luận' }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Xác nhận' }));

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent(CLOSE_MSG.CONCURRENT_MODIFICATION);
        expect(alert).not.toHaveTextContent('raw');
    });
});
