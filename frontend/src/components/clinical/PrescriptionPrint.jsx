import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { QRCodeSVG } from 'qrcode.react';
import { LoaderCircle, Printer } from 'lucide-react';

import { SIGNING_MSG, signatureReason } from './prescriptionSigning';
import {
    PRINT_MSG, dateTime, longDate, medicineLabel, printError, quantityText, shortDate,
} from './prescriptionPrintFormat';

function Row({ label, children }) {
    return <p><span className="font-semibold">{label}</span> {children || '……'}</p>;
}

function Box({ checked }) {
    return (
        <span aria-hidden="true" className="mx-1 inline-flex h-3.5 w-3.5 items-center justify-center border border-black align-middle text-[10px] font-bold leading-none">
            {checked ? 'X' : ''}
        </span>
    );
}

/** BR-59-13 / BR-59-10 step 7: the digital signature of the accepted send, verified again (BR-59-16). */
function SignatureBlock({ signature }) {
    if (!signature?.signedAt) return null;
    const valid = signature.signatureValid !== false;
    return (
        <div className="mt-1 rounded border border-black px-2 py-1 text-left text-[11px] leading-4">
            <div className="font-bold">
                {valid ? SIGNING_MSG.SIGNATURE_VALID : SIGNING_MSG.SIGNATURE_NOT_VALID(signatureReason(signature.signatureFailureReason))}
            </div>
            <div>Ký bởi: {signature.signerName}</div>
            <div>Thời gian ký: {dateTime(signature.signedAt)}</div>
            <div className="break-all">Số chứng thư: {signature.certificateSerial}</div>
            <div className="break-all">Tổ chức cấp: {signature.certificateIssuer}</div>
        </div>
    );
}

/**
 * UC-60 Print Prescription: the form of TT 26/2025 Phụ lục I (BR-59-13) with the code and its QR code (ASSUMPTION:
 * QR content = the code), the data sent to the national system, the digital signature and the mark of a printout
 * converted from the electronic prescription (Luật GDĐT Điều 12 khoản 2, ASSUMPTION wording).
 */
export function PrescriptionPrintForm({ form }) {
    const patient = form.patient ?? {};
    const facility = form.facility ?? {};
    const child = form.child;
    const identity = patient.citizenId || patient.passportNumber;
    return (
        <article
            aria-label="Đơn thuốc in"
            className="prescription-sheet space-y-2 rounded-2xl border border-slate-200 bg-white p-6 font-['Times_New_Roman',serif] text-[13px] leading-[1.45] text-black"
        >
            <header className="flex items-start justify-between gap-4">
                <div className="leading-5">
                    <div className="font-extrabold uppercase">{facility.name}</div>
                    {facility.address && <div className="text-[12px]">Địa chỉ: {facility.address}</div>}
                    {facility.phone && <div className="text-[12px]">Điện thoại: {facility.phone}</div>}
                </div>
                <div className="flex flex-col items-end gap-1 text-right">
                    <QRCodeSVG value={form.prescriptionCode} size={76} level="M" title={`Mã QR của đơn thuốc ${form.prescriptionCode}`} />
                    <div>Mã đơn thuốc: <strong className="font-mono">{form.prescriptionCode}</strong></div>
                    {form.copy && <div className="font-extrabold">BẢN SAO</div>}
                </div>
            </header>

            <h2 className="py-2 text-center text-[20px] font-extrabold uppercase tracking-wide">Đơn thuốc</h2>

            <div className="space-y-0.5">
                <Row label="Họ tên:"><span className="font-bold uppercase">{patient.fullName}</span></Row>
                <Row label="Số định danh cá nhân/ số căn cước/ số hộ chiếu:">{identity}</Row>
                <p className="flex flex-wrap gap-x-6">
                    <span>
                        <span className="font-semibold">Ngày sinh:</span> {shortDate(patient.birthDate) || '……'}
                        {child && ` (${child.ageMonths} tháng tuổi)`}
                    </span>
                    <span>
                        <span className="font-semibold">Cân nặng:</span>{' '}
                        {child?.weightKg != null ? `${Number(child.weightKg).toLocaleString('vi-VN')} kg` : '……'}
                    </span>
                    <span>
                        <span className="font-semibold">Giới tính:</span>
                        <Box checked={patient.gender === 'Nam'} />Nam
                        <Box checked={patient.gender === 'Nữ'} />Nữ
                    </span>
                </p>
                <Row label="Mã số bảo hiểm y tế:">{patient.insuranceNumber}</Row>
                <Row label="Nơi thường trú/nơi tạm trú/nơi ở hiện tại:">{patient.address}</Row>
                <Row label="Chẩn đoán:">
                    {(form.diagnoses ?? []).map((item) => `${item.code} – ${item.name}`).join('; ')}
                </Row>
            </div>

            <div>
                <p className="font-semibold">Thuốc điều trị:</p>
                <ol className="space-y-1 pl-1">
                    {(form.lines ?? []).map((line, index) => (
                        <li key={`${line.name}-${index}`}>
                            <div className="flex justify-between gap-4">
                                <span><strong>{index + 1}. {medicineLabel(line)}</strong></span>
                                <span className="shrink-0 tabular-nums">SL: <strong>{quantityText(line.quantity)}</strong> {line.unit}</span>
                            </div>
                            <div className="pl-4 italic">
                                {line.route ? `${line.route}: ` : ''}{line.dosePerTime} × {line.timesPerDay} lần/ngày × {line.days} ngày; {line.usage}
                                {line.note ? ` (${line.note})` : ''}
                            </div>
                        </li>
                    ))}
                </ol>
            </div>

            {form.notes && <Row label="Lưu ý:">{form.notes}</Row>}
            <Row label="Lời dặn:">{form.advice}</Row>
            {form.followUpDate && <Row label="Ngày tái khám:">{shortDate(form.followUpDate)}</Row>}

            <div className="flex justify-end pt-1">
                <div className="w-72 text-center">
                    <div className="italic">{longDate(form.prescribedAt)}</div>
                    <div className="font-bold">Bác sỹ/Y sỹ khám bệnh</div>
                    <div className="italic">(Đã ký số)</div>
                    <div className="font-bold">{form.doctorName}</div>
                    <SignatureBlock signature={form.signature} />
                </div>
            </div>

            <div className="text-[12px]">
                <p>- Khám bệnh lại xin mang theo đơn này.</p>
                <p>- Số điện thoại liên hệ: {patient.phone || '……'}</p>
                {child && <p>- Họ và tên người đưa trẻ đến khám bệnh, chữa bệnh: {child.accompanyingPerson || '……'}</p>}
            </div>

            <p className="border-t border-black pt-1 text-[11px] italic" data-testid="print-mark">
                {PRINT_MSG.MARK(form.prescriptionCode, facility.name)}
                {form.printedAt ? PRINT_MSG.PRINTED_BY(form.printedBy, dateTime(form.printedAt)) : ''}.
            </p>
        </article>
    );
}

/**
 * UC-60: "In đơn thuốc" opens the preview of an accepted prescription (step 2); "In" records the print on the server
 * (PRINT audit row, first printed_at) and opens the browser print dialog (step 3; printer integration TBD, HW-02).
 * Not accepted: the button is disabled with MSG112 (AF-01). The printed copy is rendered into `document.body` and
 * shown only when printing, as for the paraclinical order slip.
 */
export function PrintPrescriptionButton({ prescriptionId, accepted, api }) {
    const titleId = useId();
    const hintId = useId();
    const [open, setOpen] = useState(false);
    const [preview, setPreview] = useState(null);
    const [printing, setPrinting] = useState(false);
    const [error, setError] = useState('');
    const [job, setJob] = useState(null);

    const loading = useRef(null);

    // Step 2: the preview; closing the dialog abandons a preview still loading.
    const openPreview = async () => {
        const controller = new AbortController();
        loading.current = controller;
        setOpen(true);
        setPreview(null);
        setError('');
        try {
            const result = await api.getPrescriptionPrint(prescriptionId, controller.signal);
            if (!controller.signal.aborted) setPreview(result);
        } catch (err) {
            if (!controller.signal.aborted) setError(printError(err));
        }
    };

    const close = () => {
        loading.current?.abort();
        setOpen(false);
        setPreview(null);
    };

    // Open the print dialog once the copy is in the DOM, then remove it.
    useEffect(() => {
        if (!job) return undefined;
        const done = () => setJob(null);
        window.addEventListener('afterprint', done, { once: true });
        window.print();
        return () => window.removeEventListener('afterprint', done);
    }, [job]);

    const print = async () => {
        setPrinting(true);
        setError('');
        try {
            const form = await api.preparePrescriptionPrint(prescriptionId);
            setPreview(form);
            setJob({ form, id: Date.now() });
        } catch (err) {
            setError(printError(err));
        } finally {
            setPrinting(false);
        }
    };

    return (
        <>
            <button
                type="button"
                onClick={() => { void openPreview(); }}
                disabled={!accepted}
                title={accepted ? undefined : PRINT_MSG.NOT_PRINTABLE}
                aria-describedby={accepted ? undefined : hintId}
                className="inline-flex min-h-10 items-center gap-2 rounded-lg border border-teal-700 bg-white px-4 py-2 text-sm font-semibold text-teal-800 hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
                <Printer aria-hidden="true" className="h-4 w-4" />In đơn thuốc
            </button>
            {!accepted && <span id={hintId} className="sr-only">{PRINT_MSG.NOT_PRINTABLE}</span>}
            {open && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
                    <div role="dialog" aria-modal="true" aria-labelledby={titleId} className="flex max-h-[92vh] w-full max-w-3xl flex-col rounded-2xl border border-slate-200 bg-slate-50 p-5 shadow-2xl">
                        <h2 id={titleId} className="flex items-center gap-2 text-lg font-bold text-slate-900">
                            <Printer aria-hidden="true" className="h-5 w-5 text-teal-700" />In đơn thuốc
                        </h2>
                        <div className="mt-3 min-h-0 flex-1 overflow-y-auto">
                            {!preview && !error && <p role="status" className="text-sm text-slate-600">Đang tải đơn thuốc…</p>}
                            {preview && <PrescriptionPrintForm form={preview} />}
                        </div>
                        {error && <p role="alert" className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800">{error}</p>}
                        <div className="mt-4 flex justify-end gap-3">
                            <button type="button" onClick={close} className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
                                Đóng
                            </button>
                            <button
                                type="button"
                                onClick={() => { void print(); }}
                                disabled={!preview || printing}
                                className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                {printing && <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />}
                                {printing ? 'Đang chuẩn bị đơn…' : 'In'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {job && createPortal(
                <div className="prescription-print hidden print:block">
                    <PrescriptionPrintForm form={job.form} />
                </div>,
                document.body,
            )}
        </>
    );
}

export default PrintPrescriptionButton;
