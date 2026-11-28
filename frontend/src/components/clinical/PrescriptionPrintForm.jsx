import { QRCodeSVG } from 'qrcode.react';

import { PRINT_MSG, dateTime, longDate, medicineLabel, quantityText, shortDate } from './prescriptionPrintFormat';

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

/**
 * UC-60 Print Prescription: the form of TT 26/2025 Phụ lục I (BR-59-13) with the code and its QR code (ASSUMPTION:
 * QR content = the code), the data sent to the national system and the mark of a printout
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
                    {/* TT 26/2025 Phụ lục I: signed by hand; the digital signature is out of scope (BR-47). */}
                    <div className="italic">(Ký, ghi rõ họ tên)</div>
                    <div className="h-14" aria-hidden="true" />
                    <div className="font-bold">{form.doctorName}</div>
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

export default PrescriptionPrintForm;
