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
import { ClipboardCheck, FileClock, FlaskConical, ListChecks, Search } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { statisticsApi } from '../../apis/statisticsApi';
import { formatDayLong, formatNumber } from '../../components/statistics/dashboardPeriod';
import {
  ChartCard,
  DonutWithLegend,
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

/** Labels of the paraclinical service types (MedicalServiceType). */
const SERVICE_TYPES = {
  LAB: { label: 'Xét nghiệm', color: COLORS.completed },
  IMAGING: { label: 'Chẩn đoán hình ảnh', color: COLORS.inProgress },
  FUNCTIONAL: { label: 'Thăm dò chức năng', color: COLORS.followUp },
};

const ROOM_CHART_LIMIT = 10;

function percent(part, total) {
  if (!total) return '0%';
  return `${(Math.round((part / total) * 1000) / 10).toLocaleString('vi-VN')}%`;
}

function fold(text) {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
}

/** UC-30 View Paraclinical Service Statistics. */
export function ParaclinicalStatisticsPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  api = statisticsApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const { period, presetId, periodError, choosePreset, changeDate } = usePeriod(today);
  const params = useMemo(() => ({ from: period.from, to: period.to }), [period.from, period.to]);
  const { data, error, loading, reload } = useStatisticsQuery(api.getParaclinical, params, !periodError);
  const [query, setQuery] = useState('');

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'reports',
  };

  const typeItems = (data?.byType ?? []).map((item) => ({
    name: SERVICE_TYPES[item.type]?.label ?? item.type,
    value: item.performed,
    color: SERVICE_TYPES[item.type]?.color ?? COLORS.selfPay,
  }));
  const roomRows = (data?.byRoom ?? []).slice(0, ROOM_CHART_LIMIT).map((room) => ({
    name: room.roomName ?? room.roomCode,
    withResult: room.withResult,
    awaiting: room.performed - room.withResult,
  }));
  const services = data?.byService ?? [];
  const term = fold(query.trim());
  const shownServices = term
    ? services.filter((service) => fold(service.serviceName).includes(term) || fold(service.serviceCode).includes(term))
    : services;

  return (
    <AdminLayout {...layoutProps}>
      <div className="statistics-report-print px-6 pb-8 space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <AccountPageHeader
            title="Thống kê dịch vụ cận lâm sàng"
            description="Số dịch vụ cận lâm sàng đã thực hiện theo loại dịch vụ, theo dịch vụ và theo phòng."
            crumbs={[{ label: 'Báo cáo thống kê' }, { label: 'Cận lâm sàng' }]}
          />
          <div className="statistics-print-hide flex flex-wrap items-center gap-2">
            <ReportActions report="PARACLINICAL" period={period} disabled={Boolean(periodError) || !data} />
            <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
          </div>
        </div>

        <ReportTabs />

        <PeriodFilter
          idPrefix="paraclinical-stats"
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
              Số liệu từ {formatDayLong(data.from)} đến {formatDayLong(data.to)}, tính theo thời điểm thực hiện. Dịch vụ
              chưa làm, đã hủy hoặc bỏ không được tính.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <KpiCard icon={FlaskConical} label="Dịch vụ đã thực hiện" value={formatNumber(data.performed)} />
              <KpiCard
                icon={ClipboardCheck}
                label="Đã có kết quả"
                value={formatNumber(data.withResult)}
                hint={`${percent(data.withResult, data.performed)} số dịch vụ đã thực hiện`}
              />
              <KpiCard
                icon={FileClock}
                label="Chờ nhập kết quả"
                value={formatNumber(data.performed - data.withResult)}
                hint="Đã làm nhưng chưa gửi kết quả"
              />
              <KpiCard icon={ListChecks} label="Loại dịch vụ đã làm" value={formatNumber(services.length)} hint="Số dịch vụ khác nhau" />
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
              <ChartCard title="Theo loại dịch vụ">
                <DonutWithLegend items={typeItems} label="Biểu đồ dịch vụ đã thực hiện theo loại" />
              </ChartCard>

              <ChartCard
                title="Theo phòng thực hiện"
                description={(data.byRoom?.length ?? 0) > ROOM_CHART_LIMIT
                  ? `${ROOM_CHART_LIMIT} phòng làm nhiều nhất.`
                  : 'Đã có kết quả và chờ nhập kết quả.'}
              >
                {roomRows.length > 0 ? (
                  <div style={{ height: Math.max(200, roomRows.length * 44) }} role="img" aria-label="Biểu đồ dịch vụ theo phòng">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={roomRows} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
                        <YAxis type="category" dataKey="name" width={170} tick={{ fontSize: 12, fill: '#475569' }} tickLine={false} axisLine={false} />
                        <Tooltip formatter={(value, name) => [formatNumber(value), name]} cursor={{ fill: '#f1f5f9' }} />
                        <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                        <Bar dataKey="withResult" name="Đã có kết quả" stackId="room" fill={COLORS.completed} barSize={18} />
                        <Bar dataKey="awaiting" name="Chờ nhập kết quả" stackId="room" fill={COLORS.cancelled} radius={[0, 4, 4, 0]} barSize={18} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                ) : <EmptyChart />}
              </ChartCard>
            </div>

            <ChartCard title="Theo nhóm dịch vụ">
              {(data.byGroup ?? []).length > 0 ? (
                <ul className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
                  {data.byGroup.map((group) => (
                    <li key={group.groupId} className="flex items-center justify-between gap-3 px-4 py-3 rounded-xl bg-slate-50 border border-slate-100">
                      <span className="text-sm font-medium text-slate-700">{group.groupName ?? group.groupCode}</span>
                      <span className="text-sm tabular-nums text-slate-500">
                        <span className="font-semibold text-slate-800">{formatNumber(group.performed)}</span>
                        {' '}({formatNumber(group.withResult)} có KQ)
                      </span>
                    </li>
                  ))}
                </ul>
              ) : <EmptyChart />}
            </ChartCard>

            <ChartCard
              title="Chi tiết theo dịch vụ"
              description="Xếp theo số lần thực hiện, nhiều nhất trước."
              actions={(
                <div className="statistics-print-hide relative">
                  <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
                  <label htmlFor="paraclinical-stats-search" className="sr-only">Tìm dịch vụ</label>
                  <input
                    id="paraclinical-stats-search"
                    type="search"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Tìm theo tên hoặc mã"
                    className="pl-9 pr-3 py-2 text-sm border border-slate-200 rounded-xl w-60 focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a]"
                  />
                </div>
              )}
            >
              {shownServices.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-slate-500 border-b border-slate-100">
                        <th scope="col" className="py-2 pr-4 font-medium text-left">Dịch vụ</th>
                        <th scope="col" className="py-2 px-2 font-medium text-left">Mã</th>
                        <th scope="col" className="py-2 px-2 font-medium text-left">Loại</th>
                        <th scope="col" className="py-2 px-2 font-medium text-left">Nhóm</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Đã thực hiện</th>
                        <th scope="col" className="py-2 pl-2 font-medium text-right">Đã có kết quả</th>
                      </tr>
                    </thead>
                    <tbody className="tabular-nums">
                      {shownServices.map((service) => (
                        <tr key={service.serviceId} className="border-b border-slate-50 last:border-0">
                          <th scope="row" className="py-2.5 pr-4 text-left font-medium text-slate-700">{service.serviceName ?? '—'}</th>
                          <td className="py-2.5 px-2 font-mono text-xs text-slate-500">{service.serviceCode ?? '—'}</td>
                          <td className="py-2.5 px-2 text-slate-600">{SERVICE_TYPES[service.type]?.label ?? service.type ?? '—'}</td>
                          <td className="py-2.5 px-2 text-slate-600">{service.groupName ?? '—'}</td>
                          <td className="py-2.5 px-2 text-right font-semibold text-slate-800">{formatNumber(service.performed)}</td>
                          <td className="py-2.5 pl-2 text-right text-slate-600">{formatNumber(service.withResult)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : services.length === 0 ? <EmptyChart /> : (
                <p className="py-8 text-center text-sm text-slate-400">Không tìm thấy dịch vụ phù hợp.</p>
              )}
            </ChartCard>
          </div>
        )}
      </div>
    </AdminLayout>
  );
}

export default ParaclinicalStatisticsPage;
