import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Clock3, Search, Stethoscope, UserRound, Users } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { statisticsApi } from '../../apis/statisticsApi';
import { formatDayLong, formatNumber } from '../../components/statistics/dashboardPeriod';
import {
  ChartCard,
  EmptyChart,
  KpiCard,
  LoadError,
  PeriodFilter,
  RefreshButton,
  ReportTabs,
} from '../../components/statistics/StatisticsParts';
import { COLORS } from '../../components/statistics/statisticsTheme';
import { ReportActions } from '../../components/statistics/ReportExport';
import { usePeriod, useStatisticsQuery } from '../../components/statistics/useStatistics';

const { ADMIN } = ROUTES;

// cac cot ket qua cua 1 bac si (theo thu tu cot chong)
const DOCTOR_OUTCOMES = [
  { key: 'completed', label: 'Hoàn thành' },
  { key: 'inProgress', label: 'Đang xử lý' },
  { key: 'abandoned', label: 'Bỏ khám' },
  { key: 'cancelled', label: 'Đã hủy' },
];

const CHART_LIMIT = 10;

// trung binh 1 so le
function average(total, count) {
  if (!count) return '0';
  return (Math.round((total / count) * 10) / 10).toLocaleString('vi-VN');
}

// bo dau de tim ten khong can go dau
function fold(text) {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
}

// UC-28: trang thong ke theo bac si
export function DoctorStatisticsPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  api = statisticsApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const { period, presetId, periodError, choosePreset, changeDate } = usePeriod(today);
  const params = useMemo(() => ({ from: period.from, to: period.to }), [period.from, period.to]);
  const { data, error, loading, reload } = useStatisticsQuery(api.getDoctors, params, !periodError);
  const [query, setQuery] = useState('');

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'reports',
  };

  const doctors = data?.doctors ?? [];
  const working = doctors.filter((doctor) => doctor.visits > 0);
  const chartRows = working.slice(0, CHART_LIMIT);
  const shownRows = query.trim()
    ? doctors.filter((doctor) => fold(doctor.doctorName).includes(fold(query.trim())))
    : doctors;

  return (
    <AdminLayout {...layoutProps}>
      <div className="statistics-report-print px-6 pb-8 space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <AccountPageHeader
            title="Thống kê theo bác sĩ"
            description="Số lượt khám và số bệnh nhân mà từng bác sĩ đã khám."
            crumbs={[{ label: 'Báo cáo thống kê' }, { label: 'Bác sĩ' }]}
          />
          <div className="statistics-print-hide flex flex-wrap items-center gap-2">
            <ReportActions report="DOCTORS" period={period} disabled={Boolean(periodError) || !data} />
            <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
          </div>
        </div>

        <ReportTabs />

        <PeriodFilter
          idPrefix="doctor-stats"
          period={period}
          presetId={presetId}
          periodError={periodError}
          onPreset={choosePreset}
          onDateChange={changeDate}
        />

        {error && !periodError && <LoadError message={error} onRetry={reload} />}

        {!data && loading && !error && (
          <p className="text-sm text-slate-500">Đang tải số liệu thống kê…</p>
        )}

        {data && (
          <div className={`space-y-6 transition-opacity ${loading ? 'opacity-60' : ''}`} aria-busy={loading}>
            <p className="text-xs text-slate-500">
              Số liệu từ {formatDayLong(data.from)} đến {formatDayLong(data.to)}. Lượt khám tính cho bác sĩ đã bắt đầu
              khám; lượt chuyển phòng tính cho bác sĩ ở phòng mới.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <KpiCard
                icon={UserRound}
                label="Bác sĩ có lượt khám"
                value={formatNumber(working.length)}
                hint={`Trên ${formatNumber(doctors.length)} bác sĩ`}
              />
              <KpiCard icon={Stethoscope} label="Lượt đã khám" value={formatNumber(data.examinedVisits)} />
              <KpiCard
                icon={Users}
                label="Trung bình mỗi bác sĩ"
                value={average(data.examinedVisits, working.length)}
                hint="Lượt khám / bác sĩ có lượt khám"
              />
              <KpiCard
                icon={Clock3}
                label="Lượt chưa có bác sĩ"
                value={formatNumber(data.visitsWithoutDoctor)}
                hint="Đang chờ hoặc đã hủy trước khi khám"
              />
            </div>

            <ChartCard
              title="Lượt khám theo bác sĩ"
              description={working.length > CHART_LIMIT
                ? `${CHART_LIMIT} bác sĩ có nhiều lượt khám nhất, chia theo kết quả.`
                : 'Mỗi cột chia theo kết quả lượt khám.'}
            >
              {chartRows.length > 0 ? (
                <div style={{ height: Math.max(220, chartRows.length * 44) }} role="img" aria-label="Biểu đồ lượt khám theo bác sĩ">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartRows} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
                      <YAxis type="category" dataKey="doctorName" width={170} tick={{ fontSize: 12, fill: '#475569' }} tickLine={false} axisLine={false} />
                      <Tooltip formatter={(value, name) => [formatNumber(value), name]} cursor={{ fill: '#f1f5f9' }} />
                      <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                      {DOCTOR_OUTCOMES.map((outcome, index) => (
                        <Bar
                          key={outcome.key}
                          dataKey={outcome.key}
                          name={outcome.label}
                          stackId="outcome"
                          fill={COLORS[outcome.key]}
                          radius={index === DOCTOR_OUTCOMES.length - 1 ? [0, 4, 4, 0] : 0}
                          barSize={18}
                        />
                      ))}
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : <EmptyChart />}
            </ChartCard>

            <ChartCard
              title="Chi tiết theo bác sĩ"
              description="Mọi bác sĩ đang hoạt động; bác sĩ đã khóa chỉ hiện khi có khám trong khoảng này."
              actions={(
                <div className="statistics-print-hide relative">
                  <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
                  <label htmlFor="doctor-stats-search" className="sr-only">Tìm bác sĩ</label>
                  <input
                    id="doctor-stats-search"
                    type="search"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Tìm bác sĩ"
                    className="pl-9 pr-3 py-2 text-sm border border-slate-200 rounded-xl w-56 focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a]"
                  />
                </div>
              )}
            >
              {shownRows.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-slate-500 border-b border-slate-100">
                        <th scope="col" className="py-2 pr-4 font-medium text-left">Bác sĩ</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Lượt khám</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Bệnh nhân</th>
                        {DOCTOR_OUTCOMES.map((outcome) => (
                          <th key={outcome.key} scope="col" className="py-2 px-2 font-medium text-right">{outcome.label}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="tabular-nums">
                      {shownRows.map((doctor) => (
                        <tr key={doctor.doctorId} className="border-b border-slate-50 last:border-0">
                          <th scope="row" className="py-2.5 pr-4 text-left font-medium text-slate-700">
                            {doctor.doctorName}
                            {!doctor.active && (
                              <span className="ml-2 px-1.5 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 text-slate-500 border border-slate-200">
                                Đã khóa
                              </span>
                            )}
                          </th>
                          <td className="py-2.5 px-2 text-right font-semibold text-slate-800">{formatNumber(doctor.visits)}</td>
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatNumber(doctor.patients)}</td>
                          {DOCTOR_OUTCOMES.map((outcome) => (
                            <td key={outcome.key} className="py-2.5 px-2 text-right text-slate-600">{formatNumber(doctor[outcome.key])}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="py-8 text-center text-sm text-slate-400">
                  {doctors.length === 0 ? 'Chưa có bác sĩ nào.' : 'Không tìm thấy bác sĩ phù hợp.'}
                </p>
              )}
            </ChartCard>
          </div>
        )}
      </div>
    </AdminLayout>
  );
}

export default DoctorStatisticsPage;
