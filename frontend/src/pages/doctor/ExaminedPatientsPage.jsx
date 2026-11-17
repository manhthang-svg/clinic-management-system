import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { ChevronLeft, ChevronRight, ClipboardList, FileText, Search, Users } from 'lucide-react';
import { ApiError } from '../../apis/apiError';
import { examinedPatientApi } from '../../apis/examinedPatientApi';
import { PATIENT_GENDER_LABELS, VISIT_STATUS_LABELS } from '../../constants/visitData';
import { clinicalExaminationPath } from '../../routes/routes';
import { formatDayLong, formatNumber } from '../../components/statistics/dashboardPeriod';
import { KpiCard, LoadError, PeriodFilter, RefreshButton } from '../../components/statistics/StatisticsParts';
import { usePeriod, useStatisticsQuery } from '../../components/statistics/useStatistics';

const PAGE_SIZE = 20;

// doi loi tai danh sach thanh thong bao
function examinedListError(error) {
  if (!(error instanceof ApiError)) return 'Đã xảy ra lỗi. Vui lòng thử lại.';
  if (error.errorResponse?.code === 'REQUEST_VALIDATION_FAILED') return 'Khoảng thời gian không hợp lệ. Vui lòng chọn lại.';
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Chỉ bác sĩ mới xem được danh sách bệnh nhân đã khám.';
  if (error.status === undefined) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  return 'Đã xảy ra lỗi. Vui lòng thử lại.';
}

// tuoi tai ngay kham
function ageOn(dateOfBirth, day) {
  if (!dateOfBirth || !day) return null;
  const [by, bm, bd] = dateOfBirth.split('-').map(Number);
  const [y, m, d] = day.split('-').map(Number);
  return y - by - (m < bm || (m === bm && d < bd) ? 1 : 0);
}

function timeOf(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

function StatusBadge({ status }) {
  const tone = status === 'COMPLETED'
    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
    : status === 'CANCELLED' || status === 'ABANDONED'
      ? 'bg-slate-100 text-slate-600 border-slate-200'
      : 'bg-sky-50 text-sky-700 border-sky-200';
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold border ${tone}`}>
      {VISIT_STATUS_LABELS[status] ?? status}
    </span>
  );
}

// UC-34: trang benh nhan da kham cua bac si dang dang nhap
export function ExaminedPatientsPage({ api = examinedPatientApi, today = new Date() }) {
  const navigate = useNavigate();
  const { period, presetId, periodError, choosePreset, changeDate } = usePeriod(today);
  const [searchText, setSearchText] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const params = useMemo(
    () => ({ from: period.from, to: period.to, keyword, page, size: PAGE_SIZE }),
    [period.from, period.to, keyword, page],
  );
  const { data, error, loading, reload } = useStatisticsQuery(api.list, params, !periodError, examinedListError);

  // doi khoang ngay / tu khoa thi ve trang dau
  const onPreset = (preset) => {
    setPage(0);
    choosePreset(preset);
  };
  const onDateChange = (field) => (event) => {
    setPage(0);
    changeDate(field)(event);
  };
  const search = (event) => {
    event.preventDefault();
    setPage(0);
    setKeyword(searchText.trim());
  };

  const visits = data?.visits?.content ?? [];
  const pageInfo = data?.visits?.page;
  const totalPages = Math.max(pageInfo?.totalPages ?? 1, 1);

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl! leading-8! font-extrabold! text-[#1b2533] tracking-tight">Bệnh nhân đã khám</h1>
          <p className="text-sm text-slate-500 mt-1">Các lượt khám do bạn thực hiện trong khoảng thời gian được chọn.</p>
        </div>
        <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
      </div>

      <PeriodFilter
        idPrefix="examined"
        period={period}
        presetId={presetId}
        periodError={periodError}
        onPreset={onPreset}
        onDateChange={onDateChange}
      />

      {error && !periodError && <LoadError message={error} onRetry={reload} />}

      {!data && loading && !error && <p className="text-sm text-slate-500">Đang tải danh sách…</p>}

      {data && (
        <div className={`space-y-6 transition-opacity ${loading ? 'opacity-60' : ''}`} aria-busy={loading}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <KpiCard
              icon={ClipboardList}
              label="Lượt đã khám"
              value={formatNumber(data.examinedVisits)}
              hint={`Từ ${formatDayLong(data.from)} đến ${formatDayLong(data.to)}`}
            />
            <KpiCard icon={Users} label="Bệnh nhân" value={formatNumber(data.distinctPatients)} hint="Mỗi bệnh nhân chỉ tính một lần" />
          </div>

          <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5 space-y-4" aria-label="Danh sách lượt khám">
            <form onSubmit={search} noValidate role="search" className="flex flex-wrap items-center gap-2">
              <label htmlFor="examined-search" className="sr-only">Tìm bệnh nhân</label>
              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
                <input
                  id="examined-search"
                  type="search"
                  value={searchText}
                  onChange={(event) => setSearchText(event.target.value)}
                  placeholder="Họ tên hoặc mã bệnh nhân"
                  className="pl-9 pr-3 py-2 text-sm border border-slate-200 rounded-xl w-72 focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a]"
                />
              </div>
              <button type="submit" className="px-4 py-2 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer">
                Tìm
              </button>
              {keyword && (
                <span className="text-xs text-slate-500">
                  Kết quả cho “{keyword}”: {formatNumber(pageInfo?.totalElements ?? 0)} lượt
                </span>
              )}
            </form>

            {visits.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-xs text-slate-500 border-b border-slate-100 text-left">
                      <th scope="col" className="py-2 pr-3 font-medium">Ngày khám</th>
                      <th scope="col" className="py-2 px-3 font-medium">Bệnh nhân</th>
                      <th scope="col" className="py-2 px-3 font-medium">Giới tính / Tuổi</th>
                      <th scope="col" className="py-2 px-3 font-medium">Đối tượng</th>
                      <th scope="col" className="py-2 px-3 font-medium">Chẩn đoán</th>
                      <th scope="col" className="py-2 px-3 font-medium">Trạng thái</th>
                      <th scope="col" className="py-2 pl-3 font-medium"><span className="sr-only">Thao tác</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {visits.map((visit) => {
                      const age = ageOn(visit.dateOfBirth, visit.queueDate);
                      return (
                        <tr key={visit.visitId} className="border-b border-slate-50 last:border-0 align-top">
                          <td className="py-3 pr-3 text-slate-600 whitespace-nowrap">
                            {formatDayLong(visit.queueDate)}
                            {visit.examinedAt && <div className="text-xs text-slate-400">{timeOf(visit.examinedAt)}</div>}
                          </td>
                          <th scope="row" className="py-3 px-3 text-left font-semibold text-slate-800">
                            {visit.patientName}
                            <div className="text-xs font-normal text-slate-400">{visit.patientCode}{visit.roomName ? ` · ${visit.roomName}` : ''}</div>
                          </th>
                          <td className="py-3 px-3 text-slate-600 whitespace-nowrap">
                            {PATIENT_GENDER_LABELS[visit.gender] ?? '—'}{age !== null ? ` · ${age} tuổi` : ''}
                          </td>
                          <td className="py-3 px-3 text-slate-600">{visit.patientType === 'BHYT' ? 'BHYT' : 'Dịch vụ'}</td>
                          <td className="py-3 px-3 text-slate-700">
                            {visit.icdCode
                              ? <><span className="font-mono text-xs font-semibold text-[#127e7a]">{visit.icdCode}</span> {visit.icdName}</>
                              : <span className="text-slate-400">Chưa kết luận</span>}
                          </td>
                          <td className="py-3 px-3"><StatusBadge status={visit.status} /></td>
                          <td className="py-3 pl-3 text-right">
                            <button
                              type="button"
                              onClick={() => navigate(clinicalExaminationPath(visit.visitId))}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-[#127e7a] border border-[#127e7a]/40 rounded-lg hover:bg-teal-50 cursor-pointer"
                              aria-label={`Xem hồ sơ ${visit.patientName}`}
                            >
                              <FileText className="w-3.5 h-3.5" aria-hidden="true" />
                              Xem hồ sơ
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="py-10 text-center text-sm text-slate-400">
                {keyword ? 'Không tìm thấy bệnh nhân phù hợp.' : 'Bạn chưa khám bệnh nhân nào trong khoảng thời gian này.'}
              </p>
            )}

            {totalPages > 1 && (
              <nav aria-label="Phân trang" className="flex items-center justify-end gap-2 text-sm">
                <button
                  type="button"
                  onClick={() => setPage((current) => current - 1)}
                  disabled={page === 0 || loading}
                  className="inline-flex items-center gap-1 px-3 py-1.5 border border-slate-200 rounded-lg cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <ChevronLeft className="w-4 h-4" aria-hidden="true" /> Trước
                </button>
                <span className="text-slate-600">Trang {page + 1} / {totalPages}</span>
                <button
                  type="button"
                  onClick={() => setPage((current) => current + 1)}
                  disabled={page + 1 >= totalPages || loading}
                  className="inline-flex items-center gap-1 px-3 py-1.5 border border-slate-200 rounded-lg cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Sau <ChevronRight className="w-4 h-4" aria-hidden="true" />
                </button>
              </nav>
            )}
          </section>
        </div>
      )}
    </div>
  );
}

export default ExaminedPatientsPage;
