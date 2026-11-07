import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { TransferRoomPanel } from './TransferRoomPanel';
import { TRANSFER_MESSAGES, transferError, validateTransfer } from './transferRoom';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const visit = { id: 'visit-1', sovaovien: 'DK0000000019', queueNumber: 4, lydovaovien: 'Đau khớp gối' };
const ngoai = {
    roomId: 'room-ngoai', roomCode: 'PK-NGOAI', roomName: 'Phòng khám Ngoại', location: 'Tầng 2',
    departmentName: 'Khoa Ngoại', doctorName: 'BS Lê Thị Bình',
};

function apiWith(overrides = {}) {
    return {
        getTransferRooms: vi.fn().mockResolvedValue([ngoai]),
        transferVisit: vi.fn().mockResolvedValue({
            visitId: 'visit-1', toRoomId: 'room-ngoai', toRoomCode: 'PK-NGOAI', toRoomName: 'Phòng khám Ngoại',
            toRoomLocation: 'Tầng 2', newQueueNumber: 7,
        }),
        getTicket: vi.fn(),
        prepareTicketPrint: vi.fn(),
        ...overrides,
    };
}

function renderPanel(api = apiWith(), props = {}) {
    render(<TransferRoomPanel visit={visit} currentRoomName="Phòng khám Nội" onCancel={vi.fn()} onDone={vi.fn()} api={api} {...props} />);
    return api;
}

async function fillForm(user, reason = 'Cần khám chuyên khoa Ngoại') {
    await user.selectOptions(await screen.findByLabelText(/Phòng khám chuyển đến/), 'room-ngoai');
    await user.type(screen.getByLabelText(/Lý do chuyển/), reason);
}

describe('validateTransfer', () => {
    it('requires a room and a trimmed reason of 5 to 500 characters (BR-97)', () => {
        expect(validateTransfer({ toRoomId: '', reason: ' ', notes: '' })).toEqual({
            toRoomId: TRANSFER_MESSAGES.roomRequired, reason: TRANSFER_MESSAGES.reasonRequired,
        });
        expect(validateTransfer({ toRoomId: 'r', reason: '  abcd ', notes: '' }).reason).toBe(TRANSFER_MESSAGES.reasonLength);
        expect(validateTransfer({ toRoomId: 'r', reason: 'abcde', notes: '' })).toEqual({});
    });
});

describe('transferError', () => {
    it('maps backend codes to the UC-48 messages, never the raw message', () => {
        const error = (code, status = 409) => new ApiError('raw', { status, errorResponse: { code } });
        expect(transferError(error('MEDICAL_RECORD_ALREADY_OPENED'))).toEqual({ message: TRANSFER_MESSAGES.recordOpened, ends: true });
        expect(transferError(error('TRANSFER_ROOM_NOT_ELIGIBLE'), 'PK Ngoại').message).toContain('PK Ngoại');
        expect(transferError(error('VISIT_NOT_TRANSFERABLE')).message).toBe(TRANSFER_MESSAGES.visitChanged);
        expect(transferError(error('DOCTOR_OFF_DUTY', 403)).message).toBe(TRANSFER_MESSAGES.outsideShift);
        expect(transferError(error('SOMETHING', 500)).message).toBe(TRANSFER_MESSAGES.systemError);
    });
});

describe('TransferRoomPanel', () => {
    it('shows the Doctor on duty of the chosen room and transfers the patient', async () => {
        const user = userEvent.setup();
        const api = renderPanel();

        await fillForm(user, '  Cần khám chuyên khoa Ngoại  ');
        expect(screen.getByText('BS Lê Thị Bình')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Xác nhận chuyển phòng' }));

        expect(api.transferVisit).toHaveBeenCalledWith('visit-1', {
            toRoomId: 'room-ngoai', reason: 'Cần khám chuyên khoa Ngoại', notes: null,
        });
        expect(await screen.findByRole('status')).toHaveTextContent('Đã chuyển bệnh nhân đến Phòng khám Ngoại. Số thứ tự mới: 7.');
        expect(screen.getByRole('button', { name: 'In lại phiếu khám' })).toBeInTheDocument();
    });

    it('keeps the confirm button disabled when no room can receive the patient (AF-03)', async () => {
        renderPanel(apiWith({ getTransferRooms: vi.fn().mockResolvedValue([]) }));

        expect(await screen.findByText(TRANSFER_MESSAGES.noEligibleRoom)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Xác nhận chuyển phòng' })).toBeDisabled();
    });

    it('rejects a short reason before calling the backend', async () => {
        const user = userEvent.setup();
        const api = renderPanel();

        await fillForm(user, 'abc');
        await user.click(screen.getByRole('button', { name: 'Xác nhận chuyển phòng' }));

        expect(screen.getByText(TRANSFER_MESSAGES.reasonLength)).toBeInTheDocument();
        expect(api.transferVisit).not.toHaveBeenCalled();
    });

    it('stops when the examination was saved in the meantime (EF-01)', async () => {
        const user = userEvent.setup();
        renderPanel(apiWith({
            transferVisit: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409, errorResponse: { code: 'MEDICAL_RECORD_ALREADY_OPENED' },
            })),
        }));

        await fillForm(user);
        await user.click(screen.getByRole('button', { name: 'Xác nhận chuyển phòng' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(TRANSFER_MESSAGES.recordOpened);
        expect(screen.getByRole('button', { name: 'Xác nhận chuyển phòng' })).toBeDisabled();
    });

    it('asks before discarding an unfinished form (MSG38)', async () => {
        const user = userEvent.setup();
        const onCancel = vi.fn();
        renderPanel(apiWith(), { onCancel });

        await fillForm(user);
        await user.click(screen.getByRole('button', { name: 'Hủy' }));

        expect(screen.getByText(TRANSFER_MESSAGES.unsavedChanges)).toBeInTheDocument();
        expect(onCancel).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', { name: 'Hủy chuyển phòng' }));
        expect(onCancel).toHaveBeenCalled();
    });
});
