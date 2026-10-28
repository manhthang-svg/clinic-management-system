import { UserRound } from 'lucide-react';
import { INTAKE_TYPE_LABELS } from '../../constants/visitData';
import { formatDate, genderLabel, identityNumber } from './visitFormat';

function Info({ label, children }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="text-sm font-semibold text-slate-800 mt-0.5 break-words">{children}</dd>
    </div>
  );
}

/** UC-40 step 3: who is being registered and the intake type. */
export function VisitPatientCard({ patient, intakeType }) {
  const followUp = intakeType === 'FOLLOW_UP';

  return (
    <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 flex flex-col lg:flex-row lg:items-center gap-5">
      <div className="flex items-center gap-4 min-w-0 lg:w-80 shrink-0">
        <div className="w-14 h-14 rounded-2xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
          <UserRound className="w-7 h-7" />
        </div>
        <div className="min-w-0">
          <div className="text-xs font-semibold text-slate-400 font-mono">{patient.mabenhnhan}</div>
          <div className="text-xl font-extrabold text-[#1b2533] break-words">{patient.hoten}</div>
        </div>
      </div>

      <dl className="grid grid-cols-2 md:grid-cols-4 gap-4 flex-1">
        <Info label="Ngày sinh">{formatDate(patient.ngaysinh)}</Info>
        <Info label="Giới tính">{genderLabel(patient)}</Info>
        <Info label="Số định danh"><span className="font-mono">{identityNumber(patient)}</span></Info>
        <Info label="Loại tiếp nhận">
          <span
            className={`inline-flex px-2.5 py-0.5 rounded-lg text-xs font-semibold border ${
              followUp ? 'bg-[#f1edfd] text-[#7c5cdb] border-[#e2d9fb]' : 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]'
            }`}
          >
            {INTAKE_TYPE_LABELS[intakeType]}
          </span>
        </Info>
      </dl>
    </section>
  );
}
