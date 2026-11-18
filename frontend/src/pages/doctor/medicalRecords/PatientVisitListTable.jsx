import { ArrowLeft, Clock, User, FileText } from 'lucide-react';
import { PATIENT_GENDER_LABELS, VISIT_TYPE_LABELS } from '../../../constants/visitData';
import { formatDate, visitStatusLabel } from './medicalRecordView';

// Badge tones are TBD (CLAUDE.md); ended visits are muted, completed ones green, the rest neutral.
function StatusBadge({ status, skipped }) {
  let tone = 'bg-sky-50 text-sky-700 border-sky-200';
  if (status === 'COMPLETED') {
    tone = 'bg-emerald-50 text-emerald-700 border-emerald-200';
  } else if (status === 'ABANDONED' || status === 'CANCELLED') {
    tone = 'bg-slate-100 text-slate-600 border-slate-200';
  }
  return (
    <span className={`inline-flex px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${tone}`}>
      {visitStatusLabel(status, skipped)}
    </span>
  );
}

/**
 * UC-51 cấp 2 / AF-01: bệnh nhân được chọn và mọi lượt khám, mới nhất trước (BR-51-06).
 */
export function PatientVisitListTable({
  patient,
  visits = [],
  loading = false,
  onBack,
  onViewDetail,
}) {
  return (
    <div className="space-y-4">
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <button
            type="button"
            onClick={onBack}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Quay lại danh sách bệnh nhân</span>
          </button>
          <span className="text-xs text-slate-400">
            Tổng cộng: <strong className="text-slate-700">{visits.length}</strong> lượt khám
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#127e7a]/10 text-[#127e7a]">
              <User className="w-5 h-5" />
            </div>
            <div>
              <span className="text-xs text-slate-400 block">Bệnh nhân</span>
              <span className="font-bold text-slate-900">{patient?.fullName || '—'}</span>
            </div>
          </div>
          <div>
            <span className="text-xs text-slate-400 block">Mã bệnh nhân</span>
            <span className="font-mono font-semibold text-[#127e7a]">{patient?.patientCode || '—'}</span>
          </div>
          <div>
            <span className="text-xs text-slate-400 block">Ngày sinh</span>
            <span className="text-slate-700 font-medium">{formatDate(patient?.birthDate)}</span>
          </div>
          <div>
            <span className="text-xs text-slate-400 block">Giới tính</span>
            <span className="text-slate-700 font-medium">{PATIENT_GENDER_LABELS[patient?.gender] ?? '—'}</span>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-3">
        <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
          <Clock className="w-4 h-4 text-[#127e7a]" />
          <span>Lịch sử các lượt khám (mới nhất lên đầu)</span>
        </h3>

        <div className="overflow-x-auto rounded-xl border border-slate-100">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="bg-slate-50/80 text-xs font-semibold text-slate-600 border-b border-slate-200/70">
                <th scope="col" className="py-3 px-4">Mã lượt khám</th>
                <th scope="col" className="py-3 px-4">Ngày khám</th>
                <th scope="col" className="py-3 px-4">Phòng khám</th>
                <th scope="col" className="py-3 px-4">Bác sĩ khám</th>
                <th scope="col" className="py-3 px-4">Đối tượng</th>
                <th scope="col" className="py-3 px-4">Chẩn đoán cuối cùng</th>
                <th scope="col" className="py-3 px-4">Trạng thái</th>
                <th scope="col" className="py-3 px-4 text-right">Chi tiết</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading && (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-400">
                    <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-[#127e7a] border-t-transparent" />
                    <p className="mt-2 text-xs">Đang tải lịch sử khám…</p>
                  </td>
                </tr>
              )}

              {!loading && visits.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-400">
                    Bệnh nhân này chưa có lượt khám nào.
                  </td>
                </tr>
              )}

              {!loading && visits.map((visit) => {
                const diagnosis = visit.finalDiagnosisName
                  ? `${visit.finalDiagnosisIcd ? `${visit.finalDiagnosisIcd} - ` : ''}${visit.finalDiagnosisName}`
                  : '—';

                return (
                  <tr key={visit.visitId} className="hover:bg-slate-50/60 transition-colors">
                    <td className="py-3 px-4 font-mono font-semibold text-slate-800 whitespace-nowrap">
                      {visit.registrationCode}
                    </td>
                    <td className="py-3 px-4 text-slate-600 whitespace-nowrap">
                      {formatDate(visit.visitDate)}
                    </td>
                    <td className="py-3 px-4 text-slate-700 whitespace-nowrap">
                      {visit.roomName || '—'}
                    </td>
                    <td className="py-3 px-4 font-medium text-slate-800">
                      {visit.doctorName || '—'}
                    </td>
                    <td className="py-3 px-4 text-slate-600 whitespace-nowrap">
                      <span className={`px-2 py-0.5 rounded-md text-xs font-semibold ${
                        visit.patientType === 'BHYT'
                          ? 'bg-blue-50 text-blue-700 border border-blue-200'
                          : 'bg-amber-50 text-amber-700 border border-amber-200'
                      }`}>
                        {VISIT_TYPE_LABELS[visit.patientType] ?? '—'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-700 max-w-xs truncate" title={diagnosis}>
                      {diagnosis}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap">
                      <StatusBadge status={visit.status} skipped={visit.skipped} />
                    </td>
                    <td className="py-3 px-4 text-right whitespace-nowrap">
                      {visit.medicalRecordId ? (
                        <button
                          type="button"
                          onClick={() => onViewDetail(visit)}
                          className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold text-[#127e7a] border border-[#127e7a]/40 hover:bg-teal-50 rounded-lg transition-colors cursor-pointer"
                        >
                          <FileText className="w-3.5 h-3.5" />
                          <span>Xem chi tiết</span>
                        </button>
                      ) : (
                        // BR-51-06: a visit without a medical record cannot be opened in UC-52.
                        <span className="text-xs text-slate-400">Chưa có hồ sơ</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default PatientVisitListTable;
