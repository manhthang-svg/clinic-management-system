import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Award, ClipboardList, FileCheck2, Hash } from 'lucide-react';
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
  SegmentedControl,
} from '../../components/statistics/StatisticsParts';
import { TEAL } from '../../components/statistics/statisticsTheme';
import { ReportActions } from '../../components/statistics/ReportExport';
import { usePeriod, useStatisticsQuery } from '../../components/statistics/useStatistics';

const { ADMIN } = ROUTES;

const LIMIT_OPTIONS = [
  { value: 10, label: 'Top 10' },
  { value: 20, label: 'Top 20' },
  { value: 50, label: 'Top 50' },
];

function percent(part, total) {
  if (!total) return '0%';
  return `${(Math.round((part / total) * 1000) / 10).toLocaleString('vi-VN')}%`;
}

/** A small ranked list: code, name and number of cases. */
function RankedList({ rows, codeKey, nameKey }) {
  if (rows.length === 0) return <EmptyChart />;
  return (
    <ol className="space-y-2">
      {rows.map((row, index) => (
        <li key={row[codeKey]} className="flex items-center gap-3 text-sm">
          <span className="w-6 text-right text-xs text-slate-400 tabular-nums">{index + 1}</span>
          <span className="font-mono text-xs font-semibold text-[#127e7a] w-14 shrink-0">{row[codeKey]}</span>
          <span className="flex-1 min-w-0 truncate text-slate-700" title={row[nameKey] ?? ''}>{row[nameKey] ?? '—'}</span>
          <span className="font-semibold text-slate-800 tabular-nums">{formatNumber(row.cases)}</span>
        </li>
      ))}
    </ol>
  );
}

/** UC-32 View Diagnosis Statistics (ICD-10, BR-31). */
export function DiagnosisStatisticsPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  api = statisticsApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const { period, presetId, periodError, choosePreset, changeDate } = usePeriod(today);
  const [limit, setLimit] = useState(10);
  const params = useMemo(
    () => ({ from: period.from, to: period.to, limit }),
    [period.from, period.to, limit],
  );
  const { data, error, loading, reload } = useStatisticsQuery(api.getDiagnoses, params, !periodError);

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'reports',
  };

  const main = data?.mainDiagnoses ?? [];
  const topDiagnosis = main[0];
  const chartRows = main.slice(0, 10).map((row) => ({ code: row.icdCode, name: row.icdName, cases: row.cases }));

  return (
    <AdminLayout {...layoutProps}>
      <div className="statistics-report-print px-6 pb-8 space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <AccountPageHeader
            title="Thống kê bệnh thường gặp"
            description="Các chẩn đoán gặp nhiều nhất theo mã ICD-10 trong khoảng thời gian được chọn."
            crumbs={[{ label: 'Báo cáo thống kê' }, { label: 'Bệnh thường gặp' }]}
          />
          <div className="statistics-print-hide flex flex-wrap items-center gap-2">
            <ReportActions report="DIAGNOSES" period={period} limit={limit} disabled={Boolean(periodError) || !data} />
            <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
          </div>
        </div>

        <ReportTabs />

        <PeriodFilter
          idPrefix="diagnosis-stats"
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
              Số liệu từ {formatDayLong(data.from)} đến {formatDayLong(data.to)}, theo thời điểm bác sĩ lưu kết luận.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <KpiCard icon={ClipboardList} label="Kết luận" value={formatNumber(data.conclusions)} />
              <KpiCard
                icon={FileCheck2}
                label="Có mã ICD-10"
                value={formatNumber(data.withIcd)}
                hint={`${percent(data.withIcd, data.conclusions)} số kết luận`}
              />
              <KpiCard icon={Hash} label="Mã bệnh khác nhau" value={formatNumber(data.distinctCodes)} />
              <KpiCard
                icon={Award}
                label="Gặp nhiều nhất"
                value={topDiagnosis ? topDiagnosis.icdCode : '—'}
                hint={topDiagnosis ? `${topDiagnosis.icdName} · ${formatNumber(topDiagnosis.cases)} ca` : 'Chưa có kết luận'}
              />
            </div>

            <ChartCard
              title="Bệnh chính thường gặp"
              description="10 mã ICD-10 có nhiều ca nhất."
              actions={<SegmentedControl label="Số bệnh hiển thị" options={LIMIT_OPTIONS} value={limit} onChange={setLimit} />}
            >
              {chartRows.length > 0 ? (
                <div style={{ height: Math.max(200, chartRows.length * 36) }} role="img" aria-label="Biểu đồ bệnh chính thường gặp">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartRows} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
                      <YAxis type="category" dataKey="code" width={64} tick={{ fontSize: 12, fill: '#475569' }} tickLine={false} axisLine={false} />
                      <Tooltip
                        labelFormatter={(code) => `${code} – ${chartRows.find((row) => row.code === code)?.name ?? ''}`}
                        formatter={(value) => [formatNumber(value), 'Số ca']}
                        cursor={{ fill: '#f1f5f9' }}
                      />
                      <Bar dataKey="cases" name="Số ca" fill={TEAL} radius={[0, 4, 4, 0]} barSize={16} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : <EmptyChart />}
            </ChartCard>

            <ChartCard title="Chi tiết bệnh chính" description={`Top ${formatNumber(data.limit)} mã ICD-10; tỉ lệ tính trên số kết luận có mã.`}>
              {main.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-slate-500 border-b border-slate-100">
                        <th scope="col" className="py-2 pr-2 font-medium text-right w-10">#</th>
                        <th scope="col" className="py-2 px-2 font-medium text-left">Mã ICD-10</th>
                        <th scope="col" className="py-2 px-2 font-medium text-left">Tên bệnh</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Số ca</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Bệnh nhân</th>
                        <th scope="col" className="py-2 pl-2 font-medium text-right">Tỉ lệ</th>
                      </tr>
                    </thead>
                    <tbody className="tabular-nums">
                      {main.map((row, index) => (
                        <tr key={row.icdCode} className="border-b border-slate-50 last:border-0">
                          <td className="py-2.5 pr-2 text-right text-xs text-slate-400">{index + 1}</td>
                          <th scope="row" className="py-2.5 px-2 text-left font-mono text-slate-700">{row.icdCode}</th>
                          <td className="py-2.5 px-2 text-slate-700">{row.icdName ?? '—'}</td>
                          <td className="py-2.5 px-2 text-right font-semibold text-slate-800">{formatNumber(row.cases)}</td>
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatNumber(row.patients)}</td>
                          <td className="py-2.5 pl-2 text-right text-slate-600">{percent(row.cases, data.withIcd)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : <EmptyChart />}
            </ChartCard>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
              <ChartCard title="Theo nhóm bệnh" description="Gộp theo 3 ký tự đầu của mã (J06.9, J06.0 → J06).">
                <RankedList rows={data.categories ?? []} codeKey="categoryCode" nameKey="categoryName" />
              </ChartCard>
              <ChartCard title="Bệnh kèm theo thường gặp">
                <RankedList rows={data.secondaryDiagnoses ?? []} codeKey="icdCode" nameKey="icdName" />
              </ChartCard>
            </div>
          </div>
        )}
      </div>
    </AdminLayout>
  );
}

export default DiagnosisStatisticsPage;
