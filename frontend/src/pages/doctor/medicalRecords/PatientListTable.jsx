import { Users, ChevronLeft, ChevronRight, FileText, ArrowRight } from 'lucide-react';
import { PATIENT_GENDER_LABELS } from '../../../constants/visitData';

function formatDate(val) {
  if (!val) return '—';
  try {
    const d = new Date(val);
    if (isNaN(d.getTime())) return val;
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
  } catch {
    return val;
  }
}

/**
 * UC-51 cấp 1: danh sách bệnh nhân khớp điều kiện (BR-51-05).
 */
export function PatientListTable({
  patients = [],
  pageInfo = {},
  page = 0,
  size = 20,
  onChangePage,
  onChangeSize,
  onSelectPatient,
  loading = false,
}) {
  const totalElements = pageInfo?.totalElements ?? 0;
  const totalPages = Math.max(pageInfo?.totalPages ?? 1, 1);

  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-teal-50 text-[#127e7a]">
            <Users className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-900">Danh sách Bệnh nhân</h2>
            <p className="text-xs text-slate-500">
              Tổng số: <span className="font-semibold text-slate-700">{totalElements}</span> bệnh nhân
            </p>
          </div>
        </div>

        {/* Chọn số dòng/trang: 10, 20, 50 */}
        <div className="flex items-center gap-2 text-xs text-slate-600">
          <span>Hiển thị:</span>
          <select
            value={size}
            onChange={(e) => onChangeSize(Number(e.target.value))}
            className="border border-slate-200 rounded-lg px-2 py-1 text-xs bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-[#127e7a]"
          >
            <option value={10}>10 dòng/trang</option>
            <option value={20}>20 dòng/trang</option>
            <option value={50}>50 dòng/trang</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="bg-slate-50/80 text-xs font-semibold text-slate-600 border-b border-slate-200/70">
              <th scope="col" className="py-3 px-4">Mã bệnh nhân</th>
              <th scope="col" className="py-3 px-4">Họ và tên</th>
              <th scope="col" className="py-3 px-4">Ngày sinh</th>
              <th scope="col" className="py-3 px-4">Giới tính</th>
              <th scope="col" className="py-3 px-4 text-center">Số lượt khám</th>
              <th scope="col" className="py-3 px-4">Ngày khám gần nhất</th>
              <th scope="col" className="py-3 px-4">Chẩn đoán gần nhất</th>
              <th scope="col" className="py-3 px-4 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && (
              <tr>
                <td colSpan={8} className="py-12 text-center text-slate-400">
                  <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-[#127e7a] border-t-transparent" />
                  <p className="mt-2 text-xs">Đang tải danh sách bệnh nhân…</p>
                </td>
              </tr>
            )}

            {!loading && patients.length === 0 && (
              <tr>
                <td colSpan={8} className="py-12 text-center">
                  <p className="text-sm font-medium text-slate-500">Không tìm thấy kết quả.</p>
                  <p className="text-xs text-slate-400 mt-1">Vui lòng thử điều chỉnh lại từ khóa hoặc các tiêu chí bộ lọc.</p>
                </td>
              </tr>
            )}

            {!loading && patients.map((patient) => {
              const diagText = patient.lastDiagnosisName
                ? `${patient.lastDiagnosisIcd ? `${patient.lastDiagnosisIcd} - ` : ''}${patient.lastDiagnosisName}`
                : '—';

              return (
                <tr
                  key={patient.patientId}
                  onClick={() => onSelectPatient(patient)}
                  className="hover:bg-teal-50/40 cursor-pointer transition-colors group"
                >
                  <td className="py-3 px-4 font-mono font-semibold text-[#127e7a] whitespace-nowrap">
                    {patient.patientCode}
                  </td>
                  <td className="py-3 px-4 font-semibold text-slate-800">
                    {patient.fullName}
                  </td>
                  <td className="py-3 px-4 text-slate-600 whitespace-nowrap">
                    {formatDate(patient.birthDate)}
                  </td>
                  <td className="py-3 px-4 text-slate-600 whitespace-nowrap">
                    {PATIENT_GENDER_LABELS[patient.gender] ?? '—'}
                  </td>
                  <td className="py-3 px-4 text-center whitespace-nowrap">
                    <span className="inline-flex items-center justify-center min-w-6 h-6 px-2 text-xs font-bold rounded-full bg-slate-100 text-slate-700">
                      {patient.totalVisits ?? 0}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-slate-600 whitespace-nowrap">
                    {formatDate(patient.lastVisitDate)}
                  </td>
                  <td className="py-3 px-4 text-slate-700 max-w-xs truncate" title={diagText}>
                    {diagText}
                  </td>
                  <td className="py-3 px-4 text-right whitespace-nowrap">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelectPatient(patient);
                      }}
                      className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold text-[#127e7a] bg-teal-50 hover:bg-[#127e7a] hover:text-white rounded-lg transition-colors cursor-pointer"
                    >
                      <span>Lịch sử khám</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-slate-100 text-xs text-slate-600">
          <div>
            Trang <span className="font-semibold text-slate-800">{page + 1}</span> / {totalPages}
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => onChangePage(page - 1)}
              className="p-1.5 rounded-lg border border-slate-200 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
              aria-label="Trang trước"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="px-3 py-1 font-semibold text-slate-700">{page + 1}</span>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => onChangePage(page + 1)}
              className="p-1.5 rounded-lg border border-slate-200 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
              aria-label="Trang sau"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default PatientListTable;

