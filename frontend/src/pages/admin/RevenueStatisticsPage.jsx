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
import { Banknote, HandCoins, ReceiptText, ShieldCheck } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { statisticsApi } from '../../apis/statisticsApi';
import {
  formatDayLong,
  formatDayShort,
  formatMoney,
  formatMoneyShort,
  formatMonth,
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
  ReportTabs,
  SegmentedControl,
} from '../../components/statistics/StatisticsParts';
import { COLORS } from '../../components/statistics/statisticsTheme';
import { usePeriod, useStatisticsQuery } from '../../components/statistics/useStatistics';

const { ADMIN } = ROUTES;

const GROUP_OPTIONS = [
  { value: 'DAY', label: 'Theo ngày' },
  { value: 'MONTH', label: 'Theo tháng' },
];

const PAYMENT_TYPES = {
  EXAMINATION_FEE: { label: 'Phí khám', color: COLORS.completed },
  PARACLINICAL_FEE: { label: 'Phí cận lâm sàng', color: COLORS.selfPay },
};

const PAYMENT_METHODS = {
  CASH: { label: 'Tiền mặt', color: COLORS.completed },
  BANK_TRANSFER: { label: 'Chuyển khoản', color: COLORS.inProgress },
  CARD: { label: 'Thẻ', color: COLORS.followUp },
};
const NO_METHOD = { label: 'Chưa ghi hình thức', color: '#94a3b8' };

function percent(part, total) {
  const whole = Number(total);
  if (!whole) return '0%';
  return `${(Math.round((Number(part) / whole) * 1000) / 10).toLocaleString('vi-VN')}%`;
}

/** UC-31 View Revenue Statistics. Every amount separates the insurance part from the patient part (BR-38). */
export function RevenueStatisticsPage({
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
  const { data, error, loading, reload } = useStatisticsQuery(api.getRevenue, params, !periodError);

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

  const series = (data?.series ?? []).map((item) => ({
    period: item.period,
    payments: item.amounts.payments,
    total: Number(item.amounts.totalAmount),
    insurance: Number(item.amounts.insuranceAmount),
    patient: Number(item.amounts.patientAmount),
  }));
  const activeRows = series.filter((row) => row.payments > 0);

  const typeItems = (data?.byType ?? []).map((item) => ({
    name: PAYMENT_TYPES[item.type]?.label ?? item.type,
    value: Number(item.amounts.totalAmount),
    display: formatMoney(item.amounts.totalAmount),
    color: PAYMENT_TYPES[item.type]?.color ?? COLORS.priority,
  }));
  // Money that reached the clinic's till: what the patients paid, by payment method.
  const methodItems = (data?.byMethod ?? []).map((item) => {
    const method = PAYMENT_METHODS[item.method] ?? NO_METHOD;
    return {
      name: method.label,
      value: Number(item.amounts.patientAmount),
      display: formatMoney(item.amounts.patientAmount),
      color: method.color,
    };
  });

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <AccountPageHeader
            title="Thống kê doanh thu"
            description="Các khoản đã thu, tách phần BHYT chi trả và phần bệnh nhân trả."
            crumbs={[{ label: 'Báo cáo thống kê' }, { label: 'Doanh thu' }]}
          />
          <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
        </div>

        <ReportTabs />

        <PeriodFilter
          idPrefix="revenue-stats"
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
              Số liệu từ {formatDayLong(data.from)} đến {formatDayLong(data.to)}, theo ngày xác nhận đã thu. Chỉ tính
              khoản đã thu; khoản chờ thu, đã hủy hoặc miễn phí không được tính.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <KpiCard icon={Banknote} label="Doanh thu" value={formatMoney(summary.totalAmount)} hint="BHYT chi trả + bệnh nhân trả" />
              <KpiCard
                icon={ShieldCheck}
                label="BHYT chi trả"
                value={formatMoney(summary.insuranceAmount)}
                hint={`${percent(summary.insuranceAmount, summary.totalAmount)} doanh thu`}
              />
              <KpiCard
                icon={HandCoins}
                label="Bệnh nhân trả"
                value={formatMoney(summary.patientAmount)}
                hint={`${percent(summary.patientAmount, summary.totalAmount)} doanh thu`}
              />
              <KpiCard icon={ReceiptText} label="Khoản đã thu" value={formatNumber(summary.payments)} />
            </div>

            <ChartCard
              title={byMonth ? 'Doanh thu theo tháng' : 'Doanh thu theo ngày'}
              description="Mỗi cột chia phần BHYT chi trả và phần bệnh nhân trả."
              actions={<SegmentedControl label="Nhóm theo" options={GROUP_OPTIONS} value={groupBy} onChange={setGroupBy} />}
            >
              {summary.payments > 0 ? (
                <div className="h-72" role="img" aria-label="Biểu đồ doanh thu theo thời gian">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={series} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                      <XAxis dataKey="period" tickFormatter={formatAxis} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} minTickGap={12} />
                      <YAxis tickFormatter={formatMoneyShort} width={64} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
                      <Tooltip labelFormatter={formatPeriod} formatter={(value, name) => [formatMoney(value), name]} cursor={{ fill: '#f1f5f9' }} />
                      <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                      <Bar dataKey="insurance" name="BHYT chi trả" stackId="money" fill={COLORS.insurance} maxBarSize={36} />
                      <Bar dataKey="patient" name="Bệnh nhân trả" stackId="money" fill={COLORS.patient} radius={[4, 4, 0, 0]} maxBarSize={36} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : <EmptyChart />}
            </ChartCard>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
              <ChartCard title="Theo loại khoản thu">
                <DonutWithLegend items={typeItems} label="Biểu đồ doanh thu theo loại khoản thu" />
              </ChartCard>
              <ChartCard title="Theo hình thức thanh toán" description="Số tiền bệnh nhân đã trả.">
                <DonutWithLegend items={methodItems} label="Biểu đồ tiền bệnh nhân trả theo hình thức" />
              </ChartCard>
            </div>

            <ChartCard title="Chi tiết" description={byMonth ? 'Các tháng có khoản thu.' : 'Các ngày có khoản thu.'}>
              {activeRows.length > 0 ? (
                <div className="overflow-x-auto max-h-[480px] overflow-y-auto">
                  <table className="w-full text-sm">
                    <thead className="sticky top-0 bg-white">
                      <tr className="text-xs text-slate-500 border-b border-slate-100">
                        <th scope="col" className="py-2 pr-4 font-medium text-left">{byMonth ? 'Tháng' : 'Ngày'}</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Khoản thu</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">BHYT chi trả</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Bệnh nhân trả</th>
                        <th scope="col" className="py-2 pl-2 font-medium text-right">Doanh thu</th>
                      </tr>
                    </thead>
                    <tbody className="tabular-nums">
                      {activeRows.map((row) => (
                        <tr key={row.period} className="border-b border-slate-50 last:border-0">
                          <th scope="row" className="py-2.5 pr-4 text-left font-medium text-slate-700">{formatPeriod(row.period)}</th>
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatNumber(row.payments)}</td>
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatMoney(row.insurance)}</td>
                          <td className="py-2.5 px-2 text-right text-slate-600">{formatMoney(row.patient)}</td>
                          <td className="py-2.5 pl-2 text-right font-semibold text-slate-800">{formatMoney(row.total)}</td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="border-t border-slate-200 text-slate-800">
                        <th scope="row" className="py-2.5 pr-4 text-left font-semibold">Tổng</th>
                        <td className="py-2.5 px-2 text-right font-semibold">{formatNumber(summary.payments)}</td>
                        <td className="py-2.5 px-2 text-right font-semibold">{formatMoney(summary.insuranceAmount)}</td>
                        <td className="py-2.5 px-2 text-right font-semibold">{formatMoney(summary.patientAmount)}</td>
                        <td className="py-2.5 pl-2 text-right font-bold">{formatMoney(summary.totalAmount)}</td>
                      </tr>
                    </tfoot>
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

export default RevenueStatisticsPage;
