import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { LoaderCircle, Printer } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import { VISIT_TYPE_LABELS } from '../../constants/visitData';
import { Barcode } from '../paraclinicalResult/Barcode';
import { printErrorMessage } from './paraclinicalOrder';

/** Printed clinic name; address and phone still come from the facility settings (UC-77). */
export const SLIP_CLINIC_NAME = 'PHÒNG KHÁM ĐA KHOA METPH';

function roomLabel(sheet) {
    if (!sheet.room) return `${sheet.serviceGroupName || 'Cận lâm sàng'} – phòng sẽ được chỉ định sau khi thanh toán`;
    return `${sheet.room.roomName}${sheet.room.location ? ` – ${sheet.room.location}` : ''}`;
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
            <article
                aria-label="Phiếu chỉ định cận lâm sàng"
                className="paraclinical-order-sheet space-y-2 rounded-2xl border border-slate-200 bg-white p-6 font-['Times_New_Roman',serif] text-[13px] leading-[1.45] text-black"
            >
                <header className="flex items-start justify-between gap-4">
                    <div className="flex items-start gap-3">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center border-2 border-black text-[10px] font-bold">METPH</div>
                        <div className="leading-5">
                            <div className="font-extrabold">{SLIP_CLINIC_NAME}</div>
                            {facility.diachi && <div className="text-[12px]">Địa chỉ: {facility.diachi}</div>}
                            {facility.sodienthoai && <div className="text-[12px]">SĐT: {facility.sodienthoai}</div>}
                        </div>
                    </div>
                    <div className="text-right">
                        <div className="font-bold">Số: {slip.sophieu}</div>
                        <Barcode value={slip.sophieu} />
                        {slip.reprint && <div className="font-extrabold text-red-700">BẢN IN LẠI</div>}
                    </div>
                </header>

                <div className="my-3 text-center">
                    <h2 className="text-[20px] font-extrabold uppercase tracking-wide">Phiếu chỉ định cận lâm sàng</h2>
                    <div className="mt-1 flex items-center justify-center gap-3 text-[12px] italic">
                        <span>Thường</span>
                        <span className="inline-flex h-5 w-5 items-center justify-center border border-black font-bold not-italic">{emergency ? '' : 'X'}</span>
                        <span className="text-red-700">Cấp cứu</span>
                        <span className="inline-flex h-5 w-5 items-center justify-center border border-red-700 font-bold not-italic">{emergency ? 'X' : ''}</span>
                    </div>
                </div>

                <div className="space-y-0.5">
                    <div className="flex flex-wrap gap-x-6">
                        <Field label="Họ và tên:" className="flex-1"><span className="font-bold uppercase">{patient.fullName}</span></Field>
                        <Field label="Tuổi:">{patient.age != null ? `${patient.age} tuổi` : (patient.birthYear ?? '—')}</Field>
                        <span>{patient.gender}</span>
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

                <table className="w-full border-collapse border border-black">
                    <thead>
                        <tr>
                            <th scope="col" colSpan={4} className="border border-black px-2 text-left font-bold">Yêu cầu chỉ định</th>
                        </tr>
                        <tr>
                            <th scope="col" className="w-10 border border-black px-1.5">STT</th>
                            <th scope="col" className="border border-black px-1.5 text-left">Tên chỉ định</th>
                            <th scope="col" className="w-12 border border-black px-1.5">SL</th>
                            <th scope="col" className="border border-black px-1.5 text-left">Nơi thực hiện</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map(({ service, sheet, firstOfRoom }, index) => (
                            <tr key={service.machidinh} className="align-top">
                                <td className="border border-black px-1.5 text-center">{index + 1}</td>
                                <td className="border border-black px-1.5">{service.tenchidinh}</td>
                                <td className="border border-black px-1.5 text-center tabular-nums">{Number(service.soluong ?? 1).toLocaleString('vi-VN')}</td>
                                {firstOfRoom && (
                                    <td rowSpan={sheet.services.length} className="border border-black px-1.5 font-semibold" data-testid="slip-room">
                                        {roomLabel(sheet)}
                                    </td>
                                )}
                            </tr>
                        ))}
                    </tbody>
                </table>

                <div className="flex justify-end pt-1">
                    <div className="w-64 text-center">
                        <div className="italic">{longDate(slip.orderedAt)}</div>
                        <div className="font-bold">Bác sĩ điều trị</div>
                        <div className="h-14" />
                        <div className="font-bold">{slip.doctorName}</div>
                    </div>
                </div>
            </article>
        </div>
    );
}

/**
 * "In phiếu chỉ định": loads the slip (the server records the print, BR-50-08) and opens the browser print dialog.
 * The slip is rendered straight into `document.body`: inside the page it would sit in the scrolling `main` of the
 * layout, which the print view clips. It is hidden on screen and shown only when printing.
 */
export function PrintOrderButton({ orderId, api = visitApi, className = '' }) {
    const [job, setJob] = useState(null);
    const [printed, setPrinted] = useState(null);
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
            setPrinted(slip);
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
                {printing ? 'Đang chuẩn bị phiếu…' : printed ? 'In lại phiếu chỉ định' : 'In phiếu chỉ định'}
            </button>
            {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
            {printed && !error && (
                <p role="status" className="text-xs text-slate-600">
                    Đã mở hộp thoại in phiếu {printed.sophieu}.
                </p>
            )}
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
