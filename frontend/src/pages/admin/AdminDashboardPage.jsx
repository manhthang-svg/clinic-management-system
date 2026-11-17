import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Banknote, FlaskConical, Stethoscope, Users } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { statisticsApi } from '../../apis/statisticsApi';
import {
  formatDayLong,
  formatDayShort,
  formatMoney,
  formatNumber,
} from '../../components/statistics/dashboardPeriod';
import {
  ChartCard,
  DonutWithLegend,
  EmptyChart,
  KpiCard,
  LoadError,
  PeriodFilter,
  RefreshButton,
} from '../../components/statistics/StatisticsParts';
import { COLORS, TEAL } from '../../components/statistics/statisticsTheme';
import { usePeriod, useStatisticsQuery } from '../../components/statistics/useStatistics';

const { ADMIN } = ROUTES;

// UC-26: trang Tong quan cua admin
export function AdminDashboardPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  api = statisticsApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const { period, presetId, periodError, choosePreset, changeDate } = usePeriod(today);
  const params = useMemo(() => ({ from: period.from, to: period.to }), [period.from, period.to]);
  const { data, error, loading, reload } = useStatisticsQuery(api.getDashboard, params, !periodError);

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'dashboard',
  };

  const visits = data?.visits;
  const revenue = data?.revenue;

  const outcomeItems = visits ? [
    { name: 'Hoàn thành', value: visits.completed, color: COLORS.completed },
    { name: 'Đang xử lý', value: visits.inProgress, color: COLORS.inProgress },
    { name: 'Đã hủy', value: visits.cancelled, color: COLORS.cancelled },
    { name: 'Bỏ khám', value: visits.abandoned, color: COLORS.abandoned },
  ] : [];

  const patientTypeItems = visits ? [
    { name: 'BHYT', value: visits.insurance, color: COLORS.insurance },
    { name: 'Dịch vụ', value: visits.selfPay, color: COLORS.selfPay },
  ] : [];

  const revenueItems = revenue ? [
    {
      name: 'BHYT chi trả',
      value: Number(revenue.insuranceAmount),
      display: formatMoney(revenue.insuranceAmount),
      color: COLORS.insurance,
    },
    {
      name: 'Bệnh nhân trả',
      value: Number(revenue.patientAmount),
      display: formatMoney(revenue.patientAmount),
      color: COLORS.patient,
    },
  ] : [];

  const dailyVisits = data?.dailyVisits ?? [];
  const hasDailyVisits = dailyVisits.some((d) => d.total > 0);
  const topRooms = data?.topRooms ?? [];
  const topDiagnoses = data?.topDiagnoses ?? [];

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <AccountPageHeader
            title="Tổng quan"
            description="Số liệu hoạt động của phòng khám trong khoảng thời gian được chọn."
            crumbs={[{ label: 'Tổng quan' }]}
          />
          <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
        </div>

        <PeriodFilter
          idPrefix="dashboard"
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
              Số liệu từ {formatDayLong(data.from)} đến {formatDayLong(data.to)}.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <KpiCard
                icon={Stethoscope}
                label="Lượt khám"
                value={formatNumber(visits.total)}
                hint={`${formatNumber(visits.completed)} lượt đã hoàn thành`}
              />
              <KpiCard
                icon={Users}
                label="Bệnh nhân"
                value={formatNumber(data.distinctPatients)}
                hint="Mỗi bệnh nhân chỉ tính một lần"
              />
              <KpiCard
                icon={FlaskConical}
                label="Dịch vụ CLS đã thực hiện"
                value={formatNumber(data.paraclinicalServicesCompleted)}
              />
              <KpiCard
                icon={Banknote}
                label="Doanh thu đã thu"
                value={formatMoney(revenue.totalAmount)}
                hint={`${formatNumber(revenue.paymentCount)} khoản thu`}
              />
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
              <ChartCard title="Lượt khám theo ngày" description="Tính theo ngày tiếp nhận vào hàng chờ." className="xl:col-span-2">
                {hasDailyVisits ? (
                  <div className="h-64" role="img" aria-label="Biểu đồ lượt khám theo ngày">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={dailyVisits} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                        <defs>
                          <linearGradient id="visitsFill" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor={TEAL} stopOpacity={0.25} />
                            <stop offset="100%" stopColor={TEAL} stopOpacity={0} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                        <XAxis dataKey="date" tickFormatter={formatDayShort} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} minTickGap={16} />
                        <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
                        <Tooltip labelFormatter={formatDayLong} formatter={(value) => [formatNumber(value), 'Lượt khám']} />
                        <Area type="monotone" dataKey="total" stroke={TEAL} strokeWidth={2} fill="url(#visitsFill)" />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                ) : <EmptyChart />}
              </ChartCard>

              <ChartCard title="Kết quả lượt khám">
                <DonutWithLegend items={outcomeItems} label="Biểu đồ kết quả lượt khám" />
              </ChartCard>
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
              <ChartCard title="Doanh thu đã thu" description="Tách phần BHYT chi trả và phần bệnh nhân trả.">
                <DonutWithLegend items={revenueItems} label="Biểu đồ cơ cấu doanh thu" />
              </ChartCard>

              <ChartCard title="Đối tượng bệnh nhân">
                <DonutWithLegend items={patientTypeItems} label="Biểu đồ lượt khám theo đối tượng" />
              </ChartCard>

              <ChartCard title="Phòng khám đông nhất" description="5 phòng có nhiều lượt khám nhất.">
                {topRooms.length > 0 ? (
                  <div className="h-56" role="img" aria-label="Biểu đồ phòng có nhiều lượt khám nhất">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={topRooms} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
                        <XAxis type="number" allowDecimals={false} hide />
                        <YAxis type="category" dataKey="roomName" width={110} tick={{ fontSize: 12, fill: '#475569' }} tickLine={false} axisLine={false} />
                        <Tooltip formatter={(value) => [formatNumber(value), 'Lượt khám']} cursor={{ fill: '#f1f5f9' }} />
                        <Bar dataKey="total" fill={TEAL} radius={[0, 6, 6, 0]} barSize={18} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                ) : <EmptyChart />}
              </ChartCard>
            </div>

            <ChartCard title="Bệnh thường gặp" description="5 chẩn đoán chính (ICD-10) gặp nhiều nhất theo ngày kết luận.">
              {topDiagnoses.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                        <th scope="col" className="py-2 pr-4 font-medium">Mã ICD-10</th>
                        <th scope="col" className="py-2 pr-4 font-medium">Tên bệnh</th>
                        <th scope="col" className="py-2 font-medium text-right">Số ca</th>
                      </tr>
                    </thead>
                    <tbody>
                      {topDiagnoses.map((row) => (
                        <tr key={row.icdCode} className="border-b border-slate-50 last:border-0">
                          <td className="py-2.5 pr-4 font-mono text-slate-700">{row.icdCode}</td>
                          <td className="py-2.5 pr-4 text-slate-700">{row.icdName ?? '—'}</td>
                          <td className="py-2.5 text-right font-semibold tabular-nums text-slate-800">{formatNumber(row.total)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : <EmptyChart />}
            </ChartCard>
          </div>
        )}
      </div>
    </AdminLayout>
  );
}

export default AdminDashboardPage;
