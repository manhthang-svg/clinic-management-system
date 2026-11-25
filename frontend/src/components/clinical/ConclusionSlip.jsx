import { shortDate } from './followUpForm';
import { longDate } from './prescriptionPrintFormat';

function Line({ label, children }) {
    return <p><span>{label}</span> {children || '……………………'}</p>;
}

function diagnosisText(item) {
    return item ? `${item.code} – ${item.name}` : '';
}

/**
 * UC-57 Print Conclusion: the conclusion slip of a closed record with the content of Report 3 UC-57 step 2 (facility,
 * patient, visit code and date, examination room, diagnoses with ICD-10 codes, treatment plan and outcome, advice, the
 * Doctor's academic title and name). No legal form is named for it, so the layout follows the follow-up slip
 * (ASSUMPTION, docs/uc-57-print-conclusion.md). A reprint carries "Bản sao" (PO 23/11/2026).
 */
export function ConclusionSlip({ slip }) {
    const patient = slip.patient ?? {};
    const facility = slip.facility ?? {};
    const secondary = (slip.secondaryDiagnoses ?? []).map(diagnosisText).join('; ');
    return (
        <article
            aria-label="Phiếu kết luận khám bệnh"
            className="conclusion-sheet space-y-1.5 rounded-2xl border border-slate-200 bg-white p-6 font-['Times_New_Roman',serif] text-[13px] leading-[1.5] text-black"
        >
            <header className="grid grid-cols-2 gap-4">
                <div className="space-y-0.5">
                    <div className="font-bold uppercase">{facility.name}</div>
                    {facility.address && <div className="text-[12px]">Địa chỉ: {facility.address}</div>}
                    {facility.phone && <div className="text-[12px]">Điện thoại: {facility.phone}</div>}
                    <div>Mã lượt khám: <strong className="font-mono">{slip.visitCode}</strong></div>
                    {slip.copy && <div className="font-extrabold">BẢN SAO</div>}
                </div>
                <div className="text-center">
                    <div className="font-bold uppercase">Cộng hòa xã hội chủ nghĩa Việt Nam</div>
                    <div className="font-bold">Độc lập - Tự do - Hạnh phúc</div>
                </div>
            </header>

            <h2 className="py-3 text-center text-[20px] font-extrabold uppercase tracking-wide">Phiếu kết luận khám bệnh</h2>

            <p className="flex flex-wrap justify-between gap-x-6">
                <span>Họ tên người bệnh: <strong className="uppercase">{patient.fullName}</strong></span>
                <span>Giới: {patient.gender || '……'}</span>
            </p>
            <p className="flex flex-wrap justify-between gap-x-6">
                <span>Mã người bệnh: {patient.patientCode || '……'}</span>
                <span>Sinh ngày: {shortDate(patient.birthDate) || '……/……/………'}</span>
            </p>
            <Line label="Địa chỉ:">{patient.address}</Line>
            <p className="flex flex-wrap justify-between gap-x-6">
                <span>Ngày khám bệnh: {shortDate(slip.visitDate)}</span>
                <span>Phòng khám: {slip.roomName || '……'}</span>
            </p>
            <Line label="Chẩn đoán:">{diagnosisText(slip.diagnosis)}</Line>
            {slip.diagnosisText && <p className="whitespace-pre-line pl-4 italic">{slip.diagnosisText}</p>}
            <Line label="Bệnh kèm theo:">{secondary}</Line>
            <Line label="Hướng điều trị:">{slip.treatmentPlan}</Line>
            {/* Free text since V63; may span several lines or be empty (dotted line). */}
            <p className="whitespace-pre-line"><span>Kết quả điều trị:</span> {slip.treatmentResult || '……………………'}</p>
            {slip.advice && <p className="whitespace-pre-line">Lời dặn: {slip.advice}</p>}

            <div className="flex justify-end pt-2">
                <div className="w-72 text-center">
                    <div className="italic">………, {longDate(slip.printedAt ?? new Date().toISOString()).replace('Ngày', 'ngày')}</div>
                    <div className="font-bold">Bác sĩ khám bệnh</div>
                    <div className="italic">(ký, ghi rõ họ tên)</div>
                    <div className="h-14" />
                    <div className="font-bold">{slip.doctorName}</div>
                </div>
            </div>
        </article>
    );
}

export default ConclusionSlip;
