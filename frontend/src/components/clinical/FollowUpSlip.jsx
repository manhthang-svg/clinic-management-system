import { dayMonthYear, shortDate } from './followUpForm';
import { longDate } from './prescriptionPrintFormat';

function Line({ label, children }) {
    return <p><span>{label}</span> {children || '……………………'}</p>;
}

function diagnosisText(item) {
    return item ? `${item.code} – ${item.name}` : '';
}

/**
 * UC-62 Print Follow-up Appointment: the Phiếu hẹn khám lại of TT 01/2025/TT-BYT Phụ lục V (BR-61-06), with room at
 * the top left for the clinic's stamp and room for the Doctor's signature, both required on paper (Điều 11 khoản 2).
 * "Cơ quan chủ quản" and the place are TBD and left as dots; the inpatient dates are left blank.
 */
export function FollowUpSlip({ slip }) {
    const patient = slip.patient ?? {};
    const facility = slip.facility ?? {};
    const insurance = slip.insurance;
    const secondary = (slip.secondaryDiagnoses ?? []).map(diagnosisText).join('; ');
    return (
        <article
            aria-label="Phiếu hẹn khám lại"
            className="follow-up-sheet space-y-1.5 rounded-2xl border border-slate-200 bg-white p-6 font-['Times_New_Roman',serif] text-[13px] leading-[1.5] text-black"
        >
            <header className="grid grid-cols-2 gap-4">
                <div className="space-y-0.5">
                    <div className="text-[11px] italic text-slate-500" data-testid="stamp-area">(Đóng dấu treo của cơ sở KCB)</div>
                    <div className="uppercase">Cơ quan chủ quản ……………</div>
                    <div className="font-bold uppercase">{facility.name}</div>
                    <div>Số: <strong className="font-mono">{slip.slipNumber}</strong></div>
                    {slip.copy && <div className="font-extrabold">BẢN SAO</div>}
                </div>
                <div className="text-center">
                    <div className="font-bold uppercase">Cộng hòa xã hội chủ nghĩa Việt Nam</div>
                    <div className="font-bold">Độc lập - Tự do - Hạnh phúc</div>
                </div>
            </header>

            <h2 className="py-3 text-center text-[20px] font-extrabold uppercase tracking-wide">Phiếu hẹn khám lại</h2>

            <p className="flex flex-wrap justify-between gap-x-6">
                <span>Họ tên người bệnh: <strong className="uppercase">{patient.fullName}</strong></span>
                <span>Giới: {patient.gender || '……'}</span>
            </p>
            <Line label="Sinh ngày:">{shortDate(patient.birthDate)}</Line>
            <Line label="Địa chỉ:">{patient.address}</Line>
            <Line label="Số thẻ BHYT:">{insurance?.cardNumber}</Line>
            <p>
                Hạn sử dụng: Từ {insurance?.validFrom ? shortDate(insurance.validFrom) : '……/……/………'} Đến{' '}
                {insurance?.validTo ? shortDate(insurance.validTo) : '……/……/………'}
            </p>
            <Line label="Ngày khám bệnh:">{shortDate(slip.visitDate)}</Line>
            <p>Ngày vào viện: ……/……/……… &nbsp;&nbsp; Ngày ra viện: ……/……/………</p>
            <Line label="Chẩn đoán:">{diagnosisText(slip.diagnosis)}</Line>
            <Line label="Bệnh kèm theo:">{secondary}</Line>
            <p>
                Hẹn khám lại vào <strong>{dayMonthYear(slip.followUpDate)}</strong>,
                hoặc đến bất kỳ thời gian nào trước ngày được hẹn khám lại nếu có dấu hiệu (triệu chứng) bất thường.
            </p>
            <p className="italic">
                Phiếu hẹn khám lại chỉ có giá trị sử dụng 01 (một) lần. Trường hợp không đúng hẹn cần liên hệ với cơ sở
                khám bệnh chữa bệnh để được giải quyết.
            </p>
            {slip.advice && <p className="whitespace-pre-line">Lời dặn: {slip.advice}</p>}

            <div className="flex justify-end pt-2">
                <div className="w-72 text-center">
                    <div className="italic">………, {longDate(slip.printedAt ?? new Date().toISOString()).replace('Ngày', 'ngày')}</div>
                    <div className="font-bold">Bác sĩ, Y sĩ khám bệnh</div>
                    <div className="italic">(ký tên)</div>
                    <div className="h-14" />
                    <div className="font-bold">{slip.doctorName}</div>
                </div>
            </div>
        </article>
    );
}

export default FollowUpSlip;
