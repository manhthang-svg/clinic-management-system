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
import { CalendarCheck, CheckCircle2, Repeat, Siren, Stethoscope } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { statisticsApi } from '../../apis/statisticsApi';
import { dashboardError, formatDayLong, formatDayShort, formatNumber } from '../../components/statistics/dashboardPeriod';
import {
  ChartCard,
  DonutWithLegend,
  EmptyChart,
  KpiCard,
  LoadError,
  PeriodFilter,
  RefreshButton,
  ReportTabs,
  SegmentedControl,
} from '../../components/statistics/StatisticsParts';
import { COLORS, OUTCOMES } from '../../components/statistics/statisticsTheme';
import { ReportActions } from '../../components/statistics/ReportExport';
import { usePeriod, useStatisticsQuery } from '../../components/statistics/useStatistics';
import { ListPagination } from '../../components/shared/ListPagination';
import { useServerPage } from '../../components/shared/useServerPage';

const { ADMIN } = ROUTES;

const GROUP_OPTIONS = [
  { value: 'DAY', label: 'Theo ngày' },
  { value: 'MONTH', label: 'Theo tháng' },
];

// hien thang dang MM/yyyy
function formatMonth(iso) {
  const [y, m] = iso.split('-');
  return `${m}/${y}`;
}

// ti le phan tram (1 so le)
function percent(part, total) {
  if (!total) return '0%';
  return `${Math.round((part / total) * 1000) / 10}%`.replace('.', ',');
}

// UC-27: trang thong ke luot kham
export function VisitStatisticsPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  api = statisticsApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const { period, presetId, periodError, choosePreset, changeDate } = usePeriod(today);
  const [groupBy, setGroupBy] = useState('DAY');
  const params = useMemo(
    () => ({ from: period.from, to: period.to, groupBy }),
    [period.from, period.to, groupBy],
  );
  const { data, error, loading, reload: reloadSummary } = useStatisticsQuery(api.getVisits, params, !periodError);
  // bang chi tiet: loc + phan trang o server, chi nhan 20 dong dang xem
  const table = useServerPage(api.getVisitRows, params, { enabled: !periodError, toMessage: dashboardError });
  const reload = () => {
    reloadSummary();
    table.reload();
  };

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'reports',
  };

  const summary = data?.summary;
  const byMonth = data?.groupBy === 'MONTH';
  const formatPeriod = byMonth ? formatMonth : formatDayLong;
  const formatAxis = byMonth ? formatMonth : formatDayShort;

  const series = (data?.series ?? []).map((item) => ({ period: item.period, ...item.counts }));
  const tableRows = table.rows.map((item) => ({ period: item.period, ...item.counts }));

  const patientTypeItems = summary ? [
    { name: 'BHYT', value: summary.insurance, color: COLORS.insurance },
    { name: 'Dịch vụ', value: summary.selfPay, color: COLORS.selfPay },
  ] : [];

  const visitKindItems = summary ? [
    { name: 'Khám mới', value: summary.total - summary.followUp, color: COLORS.completed },
    { name: 'Tái khám', value: summary.followUp, color: COLORS.followUp },
  ] : [];

  return (
    <AdminLayout {...layoutProps}>
      <div className="statistics-report-print px-6 pb-8 space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <AccountPageHeader
            title="Thống kê lượt khám"
            description="Số lượt khám theo ngày hoặc tháng, theo kết quả và loại lượt khám."
            crumbs={[{ label: 'Báo cáo thống kê' }, { label: 'Lượt khám' }]}
          />
          <div className="statistics-print-hide flex flex-wrap items-center gap-2">
            <ReportActions period={period} groupBy={groupBy} disabled={Boolean(periodError) || !data} />
            <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
          </div>
        </div>

        <ReportTabs />

        <PeriodFilter
          idPrefix="visit-stats"
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
              Số liệu từ {formatDayLong(data.from)} đến {formatDayLong(data.to)}, tính theo ngày tiếp nhận vào hàng chờ.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <KpiCard icon={Stethoscope} label="Tổng lượt khám" value={formatNumber(summary.total)} />
              <KpiCard
                icon={CheckCircle2}
                label="Hoàn thành"
                value={formatNumber(summary.completed)}
                hint={`${percent(summary.completed, summary.total)} tổng lượt khám`}
              />
              <KpiCard
                icon={Repeat}
                label="Tái khám"
                value={formatNumber(summary.followUp)}
                hint="Lượt khám theo lịch hẹn tái khám"
              />
              <KpiCard
                icon={Siren}
                label="Ưu tiên"
                value={formatNumber(summary.priority)}
                hint={`${formatNumber(summary.cancelled + summary.abandoned)} lượt đã hủy hoặc bỏ khám`}
              />
            </div>

            <ChartCard
              title={byMonth ? 'Lượt khám theo tháng' : 'Lượt khám theo ngày'}
              description="Mỗi cột chia theo kết quả lượt khám."
              actions={(
                <SegmentedControl label="Nhóm theo" options={GROUP_OPTIONS} value={groupBy} onChange={setGroupBy} />
              )}
            >
              {summary.total > 0 ? (
                <div className="h-72" role="img" aria-label="Biểu đồ lượt khám theo kết quả">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={series} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                      <XAxis dataKey="period" tickFormatter={formatAxis} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} minTickGap={12} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
                      <Tooltip labelFormatter={formatPeriod} formatter={(value, name) => [formatNumber(value), name]} cursor={{ fill: '#f1f5f9' }} />
                      <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                      {OUTCOMES.map((outcome, index) => (
                        <Bar
                          key={outcome.key}
                          dataKey={outcome.key}
                          name={outcome.label}
                          stackId="outcome"
                          fill={COLORS[outcome.key]}
                          radius={index === OUTCOMES.length - 1 ? [4, 4, 0, 0] : 0}
                          maxBarSize={36}
                        />
                      ))}
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : <EmptyChart />}
            </ChartCard>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
              <ChartCard title="Đối tượng bệnh nhân">
                <DonutWithLegend items={patientTypeItems} label="Biểu đồ lượt khám theo đối tượng" />
              </ChartCard>
              <ChartCard title="Khám mới và tái khám">
                <DonutWithLegend items={visitKindItems} label="Biểu đồ khám mới và tái khám" />
              </ChartCard>
            </div>

            <ChartCard
              title="Chi tiết"
              description={byMonth ? 'Các tháng có lượt khám.' : 'Các ngày có lượt khám.'}
            >
              {table.total > 0 ? (
                <div className="overflow-x-auto max-h-[480px] overflow-y-auto">
                  <table className="w-full text-sm">
                    <thead className="sticky top-0 bg-white">
                      <tr className="text-xs text-slate-500 border-b border-slate-100">
                        <th scope="col" className="py-2 pr-4 font-medium text-left">{byMonth ? 'Tháng' : 'Ngày'}</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Tổng</th>
                        {OUTCOMES.map((outcome) => (
                          <th key={outcome.key} scope="col" className="py-2 px-2 font-medium text-right">{outcome.label}</th>
                        ))}
                        <th scope="col" className="py-2 px-2 font-medium text-right">BHYT</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Dịch vụ</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Tái khám</th>
                        <th scope="col" className="py-2 pl-2 font-medium text-right">Ưu tiên</th>
                      </tr>
                    </thead>
                    <tbody className="tabular-nums">
                      {tableRows.map((row) => (
                        <tr key={row.period} className="border-b border-slate-50 last:border-0">
                          <th scope="row" className="py-2.5 pr-4 text-left font-medium text-slate-700">
                            <span className="inline-flex items-center gap-1.5">
                              <CalendarCheck className="w-3.5 h-3.5 text-slate-400" aria-hidden="true" />
                              {formatPeriod(row.period)}
                            </span>
                          </th>
                          <td className="py-2.5 px-2 text-right font-semibold text-slate-800">{formatNumber(row.total)}</td>
                          {OUTCOMES.map((outcome) => (
                            <td key={outcome.key} className="py-2.5 px-2 text-right text-slate-600">{formatNumber(row[outcome.key])}</td>
                          ))}
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatNumber(row.insurance)}</td>
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatNumber(row.selfPay)}</td>
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatNumber(row.followUp)}</td>
                          <td className="py-2.5 pl-2 text-right text-slate-600">{formatNumber(row.priority)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <ListPagination paged={table} />
                </div>
              ) : <EmptyChart />}
            </ChartCard>
          </div>
        )}
      </div>
    </AdminLayout>
  );
}

export default VisitStatisticsPage;
