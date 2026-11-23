import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ApiError from '../../apis/apiError';
import { ClosedRecordPrintActions, ClosedRecordPrintDialog } from './ClosedRecordPrint';
import { FOLLOW_UP_MSG } from './followUpForm';
import { PRINT_MSG } from './prescriptionPrintFormat';

const conclusionSlip = {
    visitId: 'v1', visitCode: 'DK0000000123', copy: false, printedAt: null, printedBy: null,
    facility: { name: 'Phòng khám METPH', address: '1 Lê Lợi', phone: '0281234567' },
    patient: { patientCode: 'BN001', fullName: 'Nguyễn Văn An', birthDate: '1960-05-01', gender: 'Nam', address: '12 Lê Lợi, Hà Nội' },
    visitDate: '2026-11-03', roomName: 'Phòng khám Nội 1',
    diagnosis: { code: 'I10', name: 'Tăng huyết áp vô căn' },
    secondaryDiagnoses: [{ code: 'E11', name: 'Đái tháo đường type 2' }],
    diagnosisText: 'Tăng huyết áp độ 1', treatmentPlan: 'Điều trị ngoại trú', treatmentResult: 'Đỡ',
    advice: 'Ăn nhạt', concludedAt: '2026-11-03T03:00:00Z', doctorName: 'ThS. BS An',
};

const followUpSlip = {
    appointmentId: 'a1', slipNumber: 'GH-DK0000000123', copy: false, printedAt: null, printedBy: null,
    facility: { name: 'Phòng khám METPH', address: '1 Lê Lợi', phone: '0281234567' },
    patient: { fullName: 'Nguyễn Văn An', gender: 'Nam', birthDate: '1960-05-01', address: '12 Lê Lợi, Hà Nội' },
    insurance: { cardNumber: 'HC4010123456789', validFrom: '2026-01-01', validTo: '2026-12-31' },
    visitDate: '2026-11-03', diagnosis: { code: 'I10', name: 'Tăng huyết áp vô căn' },
    secondaryDiagnoses: [{ code: 'E11', name: 'Đái tháo đường type 2' }],
    followUpDate: '2026-12-03', advice: 'Đo huyết áp mỗi sáng', doctorName: 'ThS. BS An',
};

function api(overrides = {}) {
    return {
        getConclusionPrint: vi.fn().mockResolvedValue(conclusionSlip),
        prepareConclusionPrint: vi.fn().mockResolvedValue({ ...conclusionSlip, printedAt: '2026-11-03T04:00:00Z' }),
        getFollowUpPrint: vi.fn().mockResolvedValue(followUpSlip),
        prepareFollowUpPrint: vi.fn().mockResolvedValue({ ...followUpSlip, printedAt: '2026-11-03T04:00:00Z' }),
        getPrescriptionPrint: vi.fn(),
        preparePrescriptionPrint: vi.fn(),
        ...overrides,
    };
}

const closure = {
    visitId: 'v1', visitCode: 'DK0000000123', visitStatus: 'COMPLETED', prescriptionId: 'p1', followUpAppointmentId: 'a1',
};

afterEach(() => vi.restoreAllMocks());

describe('In phiếu sau khi đóng hồ sơ (UC-57, UC-60, UC-62)', () => {
    it('offers the three slips right after closing, then goes back to the list (UC-58)', async () => {
        const user = userEvent.setup();
        const onDone = vi.fn();
        render(<ClosedRecordPrintDialog closure={closure} api={api()} onDone={onDone} />);

        const dialog = screen.getByRole('dialog', { name: 'Đã đóng hồ sơ bệnh án' });
        expect(dialog).toHaveTextContent('DK0000000123');
        expect(within(dialog).getByRole('button', { name: 'In kết luận' })).toBeEnabled();
        expect(within(dialog).getByRole('button', { name: 'In đơn thuốc' })).toBeEnabled();
        expect(within(dialog).getByRole('button', { name: 'In giấy hẹn' })).toBeEnabled();

        await user.click(within(dialog).getByRole('button', { name: 'Về danh sách chờ' }));
        expect(onDone).toHaveBeenCalledTimes(1);
    });

    it('prints only the conclusion when the visit has no prescription or appointment', () => {
        render(<ClosedRecordPrintActions visitId="v1" api={api()} />);

        expect(screen.getByRole('button', { name: 'In kết luận' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'In đơn thuốc' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'In giấy hẹn' })).not.toBeInTheDocument();
    });

    it('previews the conclusion slip and prints it (UC-57)', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const client = api();
        render(<ClosedRecordPrintActions visitId="v1" api={client} />);

        await user.click(screen.getByRole('button', { name: 'In kết luận' }));
        const dialog = screen.getByRole('dialog', { name: 'In kết luận' });
        const sheet = await within(dialog).findByRole('article', { name: 'Phiếu kết luận khám bệnh' });

        expect(client.getConclusionPrint).toHaveBeenCalledWith('v1', expect.any(AbortSignal));
        expect(sheet).toHaveTextContent('Mã lượt khám: DK0000000123');
        expect(sheet).toHaveTextContent('Phòng khám: Phòng khám Nội 1');
        expect(sheet).toHaveTextContent('Chẩn đoán: I10 – Tăng huyết áp vô căn');
        expect(sheet).toHaveTextContent('Bệnh kèm theo: E11 – Đái tháo đường type 2');
        expect(sheet).toHaveTextContent('Hướng điều trị: Điều trị ngoại trú');
        expect(sheet).toHaveTextContent('Kết quả điều trị: Đỡ');
        expect(sheet).toHaveTextContent('ThS. BS An');
        expect(sheet).not.toHaveTextContent('BẢN SAO');
        expect(client.prepareConclusionPrint).not.toHaveBeenCalled();

        await user.click(within(dialog).getByRole('button', { name: 'In' }));

        expect(client.prepareConclusionPrint).toHaveBeenCalledWith('v1');
        expect(print).toHaveBeenCalledTimes(1);
    });

    it('marks a conclusion printed before as a copy (PO 23/11/2026)', async () => {
        const user = userEvent.setup();
        render(<ClosedRecordPrintActions visitId="v1" api={api({
            getConclusionPrint: vi.fn().mockResolvedValue({ ...conclusionSlip, copy: true }),
        })} />);

        await user.click(screen.getByRole('button', { name: 'In kết luận' }));

        expect(await screen.findByRole('article', { name: 'Phiếu kết luận khám bệnh' })).toHaveTextContent('BẢN SAO');
    });

    it('shows MSG61 while the record is still open', async () => {
        const user = userEvent.setup();
        render(<ClosedRecordPrintActions visitId="v1" api={api({
            getConclusionPrint: vi.fn().mockRejectedValue(new ApiError('raw', {
                status: 409, errorResponse: { code: 'PRINT_RECORD_NOT_CLOSED' },
            })),
        })} />);

        await user.click(screen.getByRole('button', { name: 'In kết luận' }));

        expect(await screen.findByRole('alert')).toHaveTextContent(PRINT_MSG.RECORD_NOT_CLOSED);
    });

    it('previews the Phiếu hẹn khám lại of TT 01/2025 Phụ lục V and prints it (UC-62)', async () => {
        const user = userEvent.setup();
        const print = vi.spyOn(window, 'print').mockImplementation(() => {});
        const client = api();
        render(<ClosedRecordPrintActions visitId="v1" appointmentId="a1" api={client} />);

        await user.click(screen.getByRole('button', { name: 'In giấy hẹn' }));
        const dialog = screen.getByRole('dialog', { name: 'In giấy hẹn' });
        const sheet = await within(dialog).findByRole('article', { name: 'Phiếu hẹn khám lại' });

        expect(client.getFollowUpPrint).toHaveBeenCalledWith('a1', expect.any(AbortSignal));
        expect(within(sheet).getByText('GH-DK0000000123')).toBeInTheDocument();
        expect(within(sheet).getByText('HC4010123456789', { exact: false })).toBeInTheDocument();
        expect(sheet).toHaveTextContent('Hạn sử dụng: Từ 01/01/2026 Đến 31/12/2026');
        expect(sheet).toHaveTextContent('Chẩn đoán: I10 – Tăng huyết áp vô căn');
        expect(sheet).toHaveTextContent('Bệnh kèm theo: E11 – Đái tháo đường type 2');
        expect(sheet).toHaveTextContent('Hẹn khám lại vào ngày 03 tháng 12 năm 2026');
        expect(sheet).toHaveTextContent('Phiếu hẹn khám lại chỉ có giá trị sử dụng 01 (một) lần.');
        expect(within(sheet).getByTestId('stamp-area')).toBeInTheDocument();
        expect(sheet).not.toHaveTextContent('BẢN SAO');
        expect(client.prepareFollowUpPrint).not.toHaveBeenCalled();

        await user.click(within(dialog).getByRole('button', { name: 'In' }));

        expect(client.prepareFollowUpPrint).toHaveBeenCalledWith('a1');
        expect(print).toHaveBeenCalledTimes(1);
    });

    it('marks a follow-up slip printed before as a copy', async () => {
        const user = userEvent.setup();
        render(<ClosedRecordPrintActions visitId="v1" appointmentId="a1" api={api({
            getFollowUpPrint: vi.fn().mockResolvedValue({ ...followUpSlip, copy: true }),
        })} />);

        await user.click(screen.getByRole('button', { name: 'In giấy hẹn' }));

        expect(await screen.findByRole('article', { name: 'Phiếu hẹn khám lại' })).toHaveTextContent('BẢN SAO');
    });

    it('does not print an appointment that is no longer scheduled (MSG128)', () => {
        render(<ClosedRecordPrintActions visitId="v1" appointmentId="a1" appointmentScheduled={false} api={api()} />);

        const button = screen.getByRole('button', { name: 'In giấy hẹn' });
        expect(button).toBeDisabled();
        expect(button).toHaveAccessibleDescription(FOLLOW_UP_MSG.NOT_SCHEDULED);
    });
});
