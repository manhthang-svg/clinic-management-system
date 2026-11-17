import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { LoaderCircle, Printer } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import { VISIT_TYPE_LABELS } from '../../constants/visitData';
import { Barcode } from '../paraclinicalResult/Barcode';
import { ageText } from '../visits/visitFormat';
import { printErrorMessage } from './paraclinicalOrder';

/** Printed clinic name; address and phone still come from the facility settings (UC-77). */
export const SLIP_CLINIC_NAME = 'PHÒNG KHÁM ĐA KHOA METPH';

function roomLabel(sheet) {
    if (!sheet.room) return `${sheet.serviceGroupName || 'Cận lâm sàng'} – phòng sẽ được chỉ định sau khi thanh toán`;
    return `${sheet.room.roomName}${sheet.room.location ? ` – ${sheet.room.location}` : ''}`;
}

/** Age on the order date in years and months, e.g. "76 tuổi 8 tháng" or "9 tháng", like the examination ticket. */
function ageOf(patient, orderedAt) {
    if (patient.dateOfBirth) return ageText(patient.dateOfBirth, orderedAt ? new Date(orderedAt) : new Date());
    if (patient.age != null) return `${patient.age} tuổi`;
    return patient.birthYear ?? '—';
}

function longDate(iso) {
    if (!iso) return 'Ngày …… tháng …… năm ……';
    const parts = new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
    }).formatToParts(new Date(iso));
    const part = (type) => parts.find((item) => item.type === type)?.value;
    return `Ngày ${part('day')} tháng ${part('month')} năm ${part('year')}`;
}

function Field({ label, children, className = '' }) {
    return (
        <span className={className}>
            <strong>{label}</strong> {children}
        </span>
    );
}

/**
 * UC-50 Print Paraclinical Order: every service of the order on one sheet, each with its performing room and
 * location (BR-50-04), laid out like the clinic's printed paraclinical forms (header with barcode, priority boxes,
 * patient lines, bordered request table, treating Doctor's signature). No amounts (BR-50-06) and no queue number
 * (BR-50-02). TBD: printer integration (HW-01); it uses the browser print dialog.
 */
export function ParaclinicalOrderSlip({ slip }) {
    const patient = slip.patient ?? {};
    const facility = slip.facility ?? {};
    const visitType = VISIT_TYPE_LABELS[patient.visitType] || patient.visitType;
    const emergency = slip.mucdochidinh === 'Cấp cứu';
    // Services in room order; the room cell spans the services of that room.
    const rows = slip.sheets.flatMap((sheet) => sheet.services.map((service, index) => ({
        service, sheet, firstOfRoom: index === 0,
    })));

    return (
        <div className="paraclinical-order-print">
            {/* A4 portrait; only present while this slip is being printed. The page margin is 0 so the browser has no
                room for its own header and footer (date, title, URL); the slip's padding gives the white border. */}
            <style>{'@media print { @page { size: A4 portrait; margin: 0; } .paraclinical-order-print { padding: 12mm 14mm; } }'}</style>
            <article
                aria-label="Phiếu chỉ định cận lâm sàng"
                className="paraclinical-order-sheet flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-6 font-['Times_New_Roman',serif] text-[14px] leading-[1.5] text-black"
            >
                <header className="flex items-start justify-between gap-2">
                    <div className="flex min-w-0 flex-1 items-start gap-3">
                        <div className="flex h-14 w-14 shrink-0 items-center justify-center border-2 border-black text-[11px] font-bold">METPH</div>
                        <div className="leading-6">
                            <div className="text-[16px] font-extrabold">{SLIP_CLINIC_NAME}</div>
                            {facility.diachi && <div className="text-[13px]">Địa chỉ: {facility.diachi}</div>}
                            {facility.sodienthoai && <div className="text-[13px]">SĐT: {facility.sodienthoai}</div>}
                        </div>
                    </div>
                    {/* Pulled into the page margin by its quiet zone so the clinic address has more room on one line. */}
                    <div className="-mr-3 shrink-0 text-right">
                        <div className="pr-3 text-[15px] font-bold">Số: {slip.sophieu}</div>
                        <Barcode value={slip.sophieu} module={1.1} />
                    </div>
                </header>

                <div className="my-2 text-center">
                    <h2 className="text-[22px] font-extrabold uppercase tracking-wide">Phiếu chỉ định cận lâm sàng</h2>
                    <div className="mt-1 flex items-center justify-center gap-3 text-[14px] italic">
                        <span>Thường</span>
                        <span className="inline-flex h-6 w-6 items-center justify-center border border-black font-bold not-italic">{emergency ? '' : 'X'}</span>
                        <span className="text-red-700">Cấp cứu</span>
                        <span className="inline-flex h-6 w-6 items-center justify-center border border-red-700 font-bold not-italic">{emergency ? 'X' : ''}</span>
                    </div>
                </div>

                <div className="space-y-1">
                    <div className="flex flex-wrap gap-x-6">
                        <Field label="Họ và tên:" className="flex-1"><span className="font-bold uppercase">{patient.fullName}</span></Field>
                        <Field label="Giới tính:">{patient.gender || '—'}</Field>
                        <Field label="Tuổi:">{ageOf(patient, slip.orderedAt)}</Field>
                    </div>
                    <div className="flex flex-wrap gap-x-6">
                        <Field label="Đối tượng:">{visitType}</Field>
                        {patient.insuranceCardNumber && (
                            <Field label="Số thẻ BHYT:" className="flex-1">{patient.insuranceCardNumber}</Field>
                        )}
                        <Field label="Mã BN:">{patient.patientCode}</Field>
                    </div>
                    <div><Field label="Địa chỉ:">{patient.address || '—'}</Field></div>
                    <div className="flex flex-wrap gap-x-6">
                        <Field label="Phòng:" className="flex-1">{slip.orderingRoom || '—'}</Field>
                        {slip.orderingDepartment && <span>({slip.orderingDepartment})</span>}
                    </div>
                    <div><Field label="Chẩn đoán:">{slip.chandoansobo || '—'}</Field></div>
                </div>

                <table className="mt-2 w-full border-collapse border border-black">
                    <thead>
                        <tr>
                            <th scope="col" colSpan={4} className="border border-black px-2.5 py-1.5 text-left font-bold">Yêu cầu chỉ định</th>
                        </tr>
                        <tr>
                            <th scope="col" className="w-14 border border-black px-2 py-1.5">STT</th>
                            <th scope="col" className="border border-black px-2.5 py-1.5 text-left">Tên chỉ định</th>
                            <th scope="col" className="w-14 border border-black px-2 py-1.5">SL</th>
                            <th scope="col" className="w-[42%] border border-black px-2.5 py-1.5 text-left">Nơi thực hiện</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map(({ service, sheet, firstOfRoom }, index) => (
                            <tr key={service.machidinh} className="h-8 align-middle">
                                <td className="border border-black px-2 text-center">{index + 1}</td>
                                <td className="border border-black px-2.5">{service.tenchidinh}</td>
                                <td className="border border-black px-2 text-center tabular-nums">{Number(service.soluong ?? 1).toLocaleString('vi-VN')}</td>
                                {firstOfRoom && (
                                    <td rowSpan={sheet.services.length} className="border border-black px-2.5 font-semibold" data-testid="slip-room">
                                        {roomLabel(sheet)}
                                    </td>
                                )}
                            </tr>
                        ))}
                    </tbody>
                </table>

                {/* The signature sits lower on the page instead of right under a short table. */}
                <div className="mt-4 flex justify-end">
                    <div className="w-72 text-center">
                        <div className="italic">{longDate(slip.orderedAt)}</div>
                        <div className="font-bold">Bác sĩ điều trị</div>
                        <div className="italic text-[13px]">(Ký, ghi rõ họ tên)</div>
                        <div className="h-16" />
                        <div className="font-bold">{slip.doctorName}</div>
                    </div>
                </div>
            </article>
        </div>
    );
}

/**
 * "In phiếu chỉ định": loads the slip and opens the browser print dialog. The print is not logged and every print
 * is the same (no first print / reprint distinction).
 * The slip is rendered straight into `document.body`: inside the page it would sit in the scrolling `main` of the
 * layout, which the print view clips. It is hidden on screen and shown only when printing.
 */
export function PrintOrderButton({ orderId, api = visitApi, className = '' }) {
    const [job, setJob] = useState(null);
    const [printing, setPrinting] = useState(false);
    const [error, setError] = useState('');

    // Open the dialog only once the slip is in the DOM, then remove it so another order's print does not include it.
    useEffect(() => {
        if (!job) return undefined;
        const done = () => setJob(null);
        window.addEventListener('afterprint', done, { once: true });
        window.print();
        return () => window.removeEventListener('afterprint', done);
    }, [job]);

    const handlePrint = async () => {
        setPrinting(true);
        setError('');
        try {
            const slip = await api.prepareParaclinicalOrderPrint(orderId);
            setJob({ slip, id: Date.now() });
        } catch (err) {
            setError(printErrorMessage(err));
        } finally {
            setPrinting(false);
        }
    };

    return (
        <div className={`space-y-3 ${className}`}>
            <button
                type="button"
                onClick={handlePrint}
                disabled={printing}
                className="inline-flex min-h-10 items-center gap-2 rounded-lg border border-teal-700 px-4 py-2 text-xs font-semibold text-teal-800 hover:bg-teal-50 disabled:opacity-60"
            >
                {printing
                    ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />
                    : <Printer aria-hidden="true" className="h-4 w-4" />}
                {printing ? 'Đang chuẩn bị phiếu…' : 'In phiếu chỉ định'}
            </button>
            {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
            {job && createPortal(
                <div className="hidden print:block">
                    <ParaclinicalOrderSlip slip={job.slip} />
                </div>,
                document.body,
            )}
        </div>
    );
}

export default ParaclinicalOrderSlip;
