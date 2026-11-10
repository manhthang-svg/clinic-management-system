import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { LoaderCircle, Printer } from 'lucide-react';

import visitApi from '../../apis/visitApi';
import { VISIT_TYPE_LABELS } from '../../constants/visitData';
import { formatDateTime } from '../visits/visitFormat';
import { printErrorMessage } from './paraclinicalOrder';

/**
 * UC-50 Print Paraclinical Order: one sheet per performing room with the room and its location (BR-50-04),
 * modelled on the request part of the TT 32/2023/TT-BYT forms 08/BV2 to 26/BV2. No amounts (BR-50-06) and no queue
 * number (BR-50-02). TBD: printer integration (HW-01); it uses the browser print dialog.
 */
export function ParaclinicalOrderSlip({ slip }) {
    const patient = slip.patient ?? {};
    const facility = slip.facility ?? {};
    const visitType = VISIT_TYPE_LABELS[patient.visitType] || patient.visitType;

    return (
        <div className="paraclinical-order-print space-y-4">
            {slip.sheets.map((sheet) => (
                <article
                    key={sheet.index}
                    aria-label={`Phiếu chỉ định tờ ${sheet.index}/${sheet.total}`}
                    className="paraclinical-order-sheet rounded-2xl border border-slate-200 bg-white p-6 text-xs text-slate-900"
                >
                    <header className="flex items-start justify-between gap-4">
                        <div>
                            <p className="font-bold uppercase">{facility.tenCskcb}</p>
                            {facility.diachi && <p>{facility.diachi}</p>}
                            {facility.sodienthoai && <p>Điện thoại: {facility.sodienthoai}</p>}
                        </div>
                        <div className="text-right">
                            <p>Số: <strong className="font-mono">{slip.sophieu}</strong></p>
                            <p>Tờ {sheet.index}/{sheet.total}</p>
                            {slip.reprint && <p className="font-extrabold text-rose-700">BẢN IN LẠI</p>}
                        </div>
                    </header>

                    <h2 className="mt-3 text-center text-base font-extrabold uppercase">Phiếu chỉ định cận lâm sàng</h2>
                    {slip.mucdochidinh && <p className="text-center">Mức độ: <strong>{slip.mucdochidinh}</strong></p>}

                    <dl className="mt-4 grid grid-cols-[auto_1fr_auto_1fr] gap-x-3 gap-y-1">
                        <dt className="text-slate-500">Họ tên</dt>
                        <dd className="font-semibold uppercase">{patient.fullName}</dd>
                        <dt className="text-slate-500">Năm sinh</dt>
                        <dd>{patient.birthYear ?? '—'}{patient.age != null ? ` (${patient.age} tuổi)` : ''} – {patient.gender}</dd>
                        <dt className="text-slate-500">Mã bệnh nhân</dt>
                        <dd className="font-mono">{patient.patientCode}</dd>
                        <dt className="text-slate-500">Mã lượt khám</dt>
                        <dd className="font-mono">{patient.visitCode}</dd>
                        <dt className="text-slate-500">Địa chỉ</dt>
                        <dd className="col-span-3">{patient.address || '—'}</dd>
                        <dt className="text-slate-500">Đối tượng</dt>
                        <dd>{visitType}</dd>
                        <dt className="text-slate-500">Số thẻ BHYT</dt>
                        <dd className="font-mono">{patient.insuranceCardNumber || '—'}</dd>
                        <dt className="text-slate-500">Khoa / phòng</dt>
                        <dd className="col-span-3">{[slip.orderingDepartment, slip.orderingRoom].filter(Boolean).join(' – ') || '—'}</dd>
                        <dt className="text-slate-500">Chẩn đoán</dt>
                        <dd className="col-span-3">{slip.chandoansobo || '—'}</dd>
                    </dl>

                    <table className="mt-4 w-full border-collapse">
                        <thead>
                            <tr className="border-b border-slate-300 text-left">
                                <th scope="col" className="w-10 py-1 pr-2">STT</th>
                                <th scope="col" className="py-1 pr-2">Mã chỉ định</th>
                                <th scope="col" className="py-1 pr-2">Yêu cầu</th>
                                <th scope="col" className="w-16 py-1 text-right">Số lượng</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sheet.services.map((service, index) => (
                                <tr key={service.machidinh} className="border-b border-slate-100 align-top">
                                    <td className="py-1 pr-2">{index + 1}</td>
                                    <td className="py-1 pr-2 font-mono">{service.machidinh}</td>
                                    <td className="py-1 pr-2">{service.tenchidinh}</td>
                                    <td className="py-1 text-right tabular-nums">{Number(service.soluong ?? 1).toLocaleString('vi-VN')}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    <p className="mt-3 rounded-lg border border-slate-300 px-3 py-2 text-sm" data-testid="slip-room">
                        <span className="font-semibold">Nơi thực hiện: </span>
                        {sheet.room
                            ? `${sheet.room.roomName}${sheet.room.location ? ` – ${sheet.room.location}` : ''}`
                            : `${sheet.serviceGroupName || 'Cận lâm sàng'} – phòng sẽ được chỉ định sau khi thanh toán`}
                    </p>

                    <footer className="mt-6 flex justify-end text-center">
                        <div>
                            <p>Ngày giờ chỉ định: {formatDateTime(slip.orderedAt)}</p>
                            <p className="mt-1 font-semibold">Bác sĩ chỉ định</p>
                            <p className="mt-12">{slip.doctorName}</p>
                        </div>
                    </footer>
                </article>
            ))}
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
                    Đã mở hộp thoại in phiếu {printed.sophieu} ({printed.sheets.length} tờ).
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
