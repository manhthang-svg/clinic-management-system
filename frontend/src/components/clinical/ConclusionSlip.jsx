import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { LoaderCircle, Printer } from 'lucide-react';

import { BUTTON } from './buttonStyles';
import { TREATMENT_RESULTS } from './conclusionForm';
import { shortDate } from './followUpForm';

const RESULT_LABELS = Object.fromEntries(TREATMENT_RESULTS);
const VISIT_TYPE_LABELS = { BHYT: 'BHYT', DV: 'Dịch vụ' };

/** dd/MM/yyyy HH:mm in clinic time. */
function dateTime(iso) {
    if (!iso) return '';
    const parts = new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    }).formatToParts(new Date(iso));
    const part = (type) => parts.find((item) => item.type === type)?.value;
    return `${part('day')}/${part('month')}/${part('year')} ${part('hour')}:${part('minute')}`;
}

function diagnosisText(item) {
    return item?.code ? `${item.code} – ${item.name}` : '';
}

function Line({ label, children }) {
    return <p><strong>{label}</strong> {children || '—'}</p>;
}

/**
 * BF-04 "In phiếu kết luận": the conclusion of the visit as the Doctor saved it (UC-56), on one A4 sheet, with the
 * print time on the top edge. **ASSUMPTION:** no TT 32/2023 form is required for an outpatient conclusion.
 */
export function ConclusionSlip({ slip }) {
    const patient = slip.patient ?? {};
    const facility = slip.facility ?? {};
    const secondary = (slip.secondaryDiagnoses ?? []).map(diagnosisText).filter(Boolean).join('; ');
    return (
        <div className="conclusion-print">
            <style>{'@media print { @page { size: A4 portrait; margin: 0; } .conclusion-print { padding: 12mm 14mm; } }'}</style>
            <article
                aria-label="Phiếu kết luận khám bệnh"
                className="conclusion-sheet flex flex-col gap-2 rounded-2xl border border-slate-200 bg-white p-6 font-['Times_New_Roman',serif] text-[14px] leading-[1.5] text-black"
            >
                <div className="-mt-[8mm] mb-1 text-[10px] leading-none text-slate-600">In lúc: {dateTime(slip.printedAt)}</div>
                <header className="flex items-start justify-between gap-4">
                    <div className="leading-6">
                        <div className="text-[16px] font-extrabold uppercase">{facility.name}</div>
                        {facility.address && <div className="text-[13px]">Địa chỉ: {facility.address}</div>}
                        {facility.phone && <div className="text-[13px]">SĐT: {facility.phone}</div>}
                    </div>
                    <div className="shrink-0 text-right text-[13px]">Mã lượt khám: <strong>{slip.visitCode}</strong></div>
                </header>

                <h2 className="my-3 text-center text-[22px] font-extrabold uppercase tracking-wide">Phiếu kết luận khám bệnh</h2>

                <div className="space-y-1">
                    <p className="flex flex-wrap gap-x-6">
                        <span className="flex-1"><strong>Họ và tên:</strong> <span className="font-bold uppercase">{patient.fullName}</span></span>
                        <span><strong>Giới tính:</strong> {patient.gender || '—'}</span>
                        <span><strong>Ngày sinh:</strong> {shortDate(patient.birthDate) || '—'}</span>
                    </p>
                    <p className="flex flex-wrap gap-x-6">
                        <span><strong>Mã BN:</strong> {patient.patientCode}</span>
                        <span><strong>Đối tượng:</strong> {VISIT_TYPE_LABELS[slip.visitType] ?? slip.visitType}</span>
                        {slip.insuranceCardNumber && <span><strong>Số thẻ BHYT:</strong> {slip.insuranceCardNumber}</span>}
                    </p>
                    <Line label="Địa chỉ:">{patient.address}</Line>
                    <Line label="Thời gian đăng ký khám:">{dateTime(slip.registeredAt)}</Line>
                </div>

                <div className="mt-2 space-y-1">
                    <Line label="Chẩn đoán:">{slip.diagnosis}</Line>
                    <Line label="Bệnh chính:">{diagnosisText(slip.mainDiagnosis)}</Line>
                    <Line label="Bệnh kèm theo:">{secondary}</Line>
                    <Line label="Hướng điều trị:">{slip.treatmentPlan}</Line>
                    <Line label="Kết quả điều trị:">{RESULT_LABELS[slip.treatmentResult]}</Line>
                    <p className="whitespace-pre-line"><strong>Lời dặn:</strong> {slip.advice || '—'}</p>
                </div>

                <div className="mt-4 flex justify-end">
                    <div className="w-72 text-center">
                        <div className="italic">Thời gian kết luận: {dateTime(slip.concludedAt)}</div>
                        <div className="font-bold">Bác sĩ khám bệnh</div>
                        <div className="italic text-[13px]">(Ký, ghi rõ họ tên)</div>
                        <div className="h-16" />
                        <div className="font-bold">{slip.doctorName}</div>
                    </div>
                </div>
            </article>
        </div>
    );
}

/** Never the raw backend message. */
function printError(error) {
    const code = error?.errorResponse?.code;
    if (code === 'CLINIC_FACILITY_NOT_CONFIGURED') {
        return 'Chưa có thông tin cơ sở khám bệnh, chữa bệnh. Vui lòng liên hệ quản trị viên.';
    }
    if (code === 'CLOSE_CONCLUSION_REQUIRED') return 'Vui lòng lưu kết luận trước khi in.';
    if (code === 'MEDICAL_RECORD_CLOSED') return 'Hồ sơ bệnh án đã đóng và không thể chỉnh sửa.'; // MSG68
    if (error?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'; // MSG12
    if (error?.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'; // MSG13
    return 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.'; // MSG80
}

/**
 * "In phiếu kết luận": loads the saved conclusion and opens the browser print dialog. Disabled until the conclusion
 * is saved and while it has unsaved changes. The slip is rendered into `document.body`, hidden on screen.
 */
export function PrintConclusionButton({ visitId, api, disabled = false }) {
    const [job, setJob] = useState(null);
    const [printing, setPrinting] = useState(false);
    const [error, setError] = useState('');

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
            const slip = await api.getConclusionPrint(visitId);
            setJob({ slip, id: Date.now() });
        } catch (err) {
            setError(printError(err));
        } finally {
            setPrinting(false);
        }
    };

    return (
        <div className="space-y-2">
            <button
                type="button"
                onClick={() => { void print(); }}
                disabled={disabled || printing}
                title={disabled ? 'Lưu kết luận trước khi in' : undefined}
                className={BUTTON.outline}
            >
                {printing
                    ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" />
                    : <Printer aria-hidden="true" className="h-4 w-4" />}
                {printing ? 'Đang chuẩn bị phiếu…' : 'In phiếu kết luận'}
            </button>
            {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
            {job && createPortal(
                <div className="hidden print:block">
                    <ConclusionSlip slip={job.slip} />
                </div>,
                document.body,
            )}
        </div>
    );
}

export default ConclusionSlip;
