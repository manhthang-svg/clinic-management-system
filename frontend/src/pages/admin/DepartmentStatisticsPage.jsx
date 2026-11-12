import { Fragment, useMemo, useState } from 'react';
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
import { Building2, DoorOpen, FlaskConical, Stethoscope } from 'lucide-react';
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
import { COLORS } from '../../components/statistics/statisticsTheme';
import { usePeriod, useStatisticsQuery } from '../../components/statistics/useStatistics';

const { ADMIN } = ROUTES;

const TYPE_OPTIONS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'CLINICAL', label: 'Lâm sàng' },
  { value: 'PARACLINICAL', label: 'Cận lâm sàng' },
];

const DEPARTMENT_TYPE_LABEL = { CLINICAL: 'Lâm sàng', PARACLINICAL: 'Cận lâm sàng' };

const workload = (item) => item.visits + item.services;

/** "9 lượt (6 hoàn thành)" for an examination room, "18 dịch vụ (14 đã làm)" for a paraclinical one. */
function workSummary(item, isParaclinical) {
  return isParaclinical
    ? `${formatNumber(item.services)} dịch vụ (${formatNumber(item.performedServices)} đã làm)`
    : `${formatNumber(item.visits)} lượt (${formatNumber(item.completedVisits)} hoàn thành)`;
}

function InactiveBadge() {
  return (
    <span className="ml-2 px-1.5 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 text-slate-500 border border-slate-200">
      Tạm ngưng
    </span>
  );
}

/** UC-29 View Department and Room Statistics. */
export function DepartmentStatisticsPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  api = statisticsApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const { period, presetId, periodError, choosePreset, changeDate } = usePeriod(today);
  const params = useMemo(() => ({ from: period.from, to: period.to }), [period.from, period.to]);
  const { data, error, loading, reload } = useStatisticsQuery(api.getDepartments, params, !periodError);
  const [type, setType] = useState('ALL');

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'reports',
  };

  const departments = data?.departments ?? [];
  const rooms = departments.flatMap((department) => department.rooms);
  const busyDepartments = departments.filter((department) => workload(department) > 0);
  const busyRooms = rooms.filter((room) => workload(room) > 0);
  const shown = type === 'ALL' ? departments : departments.filter((department) => department.type === type);
  const chartRows = busyDepartments.map((department) => ({
    name: department.departmentName,
    visits: department.visits,
    services: department.services,
  }));

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <AccountPageHeader
            title="Thống kê theo khoa / phòng"
            description="Khối lượng công việc của từng khoa và từng phòng trong khoảng thời gian được chọn."
            crumbs={[{ label: 'Báo cáo thống kê' }, { label: 'Khoa / Phòng' }]}
          />
          <RefreshButton loading={loading} disabled={Boolean(periodError)} onClick={reload} />
        </div>

        <ReportTabs />

        <PeriodFilter
          idPrefix="department-stats"
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
              Số liệu từ {formatDayLong(data.from)} đến {formatDayLong(data.to)}. Phòng khám tính lượt khám theo ngày
              tiếp nhận; phòng cận lâm sàng tính dịch vụ được chuyển về phòng.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              <KpiCard icon={Stethoscope} label="Lượt khám" value={formatNumber(data.visits)} hint="Tại các phòng khám" />
              <KpiCard icon={FlaskConical} label="Dịch vụ cận lâm sàng" value={formatNumber(data.services)} hint="Được chuyển về phòng" />
              <KpiCard
                icon={Building2}
                label="Khoa có hoạt động"
                value={formatNumber(busyDepartments.length)}
                hint={`Trên ${formatNumber(departments.length)} khoa`}
              />
              <KpiCard
                icon={DoorOpen}
                label="Phòng có hoạt động"
                value={formatNumber(busyRooms.length)}
                hint={`Trên ${formatNumber(rooms.length)} phòng`}
              />
            </div>

            <ChartCard title="Khối lượng theo khoa" description="Lượt khám của khoa lâm sàng và dịch vụ của khoa cận lâm sàng.">
              {chartRows.length > 0 ? (
                <div style={{ height: Math.max(220, chartRows.length * 48) }} role="img" aria-label="Biểu đồ khối lượng theo khoa">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartRows} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} tickLine={false} axisLine={false} />
                      <YAxis type="category" dataKey="name" width={170} tick={{ fontSize: 12, fill: '#475569' }} tickLine={false} axisLine={false} />
                      <Tooltip formatter={(value, name) => [formatNumber(value), name]} cursor={{ fill: '#f1f5f9' }} />
                      <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                      <Bar dataKey="visits" name="Lượt khám" stackId="work" fill={COLORS.completed} barSize={18} />
                      <Bar dataKey="services" name="Dịch vụ CLS" stackId="work" fill={COLORS.selfPay} radius={[0, 4, 4, 0]} barSize={18} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : <EmptyChart />}
            </ChartCard>

            <ChartCard
              title="Chi tiết theo khoa và phòng"
              description="Khoa và phòng tạm ngưng chỉ hiện khi có công việc trong khoảng này."
              actions={<SegmentedControl label="Loại khoa" options={TYPE_OPTIONS} value={type} onChange={setType} />}
            >
              {shown.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-slate-500 border-b border-slate-100">
                        <th scope="col" className="py-2 pr-4 font-medium text-left">Khoa / Phòng</th>
                        <th scope="col" className="py-2 px-2 font-medium text-left">Loại</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Khối lượng</th>
                        <th scope="col" className="py-2 px-2 font-medium text-right">Đang chờ / đang làm</th>
                        <th scope="col" className="py-2 pl-2 font-medium text-right">Hủy / bỏ</th>
                      </tr>
                    </thead>
                    <tbody className="tabular-nums">
                      {shown.map((department) => {
                        const isParaclinical = department.type === 'PARACLINICAL';
                        return (
                          <Fragment key={department.departmentId}>
                            <tr className="bg-slate-50/80 border-b border-slate-100">
                              <th scope="row" className="py-2.5 pr-4 text-left font-semibold text-slate-800">
                                {department.departmentName}
                                <span className="ml-2 text-xs font-normal text-slate-400">{department.departmentCode}</span>
                                {!department.active && <InactiveBadge />}
                              </th>
                              <td className="py-2.5 px-2 text-slate-600">{DEPARTMENT_TYPE_LABEL[department.type] ?? department.type}</td>
                              <td className="py-2.5 px-2 text-right font-semibold text-slate-800">{workSummary(department, isParaclinical)}</td>
                              <td className="py-2.5 px-2" />
                              <td className="py-2.5 pl-2" />
                            </tr>
                            {department.rooms.length === 0 && (
                              <tr className="border-b border-slate-50">
                                <td colSpan={5} className="py-2.5 pl-6 text-xs text-slate-400">Khoa chưa có phòng.</td>
                              </tr>
                            )}
                            {department.rooms.map((room) => {
                              const roomIsParaclinical = room.type === 'PARACLINICAL';
                              return (
                                <tr key={room.roomId} className="border-b border-slate-50 last:border-0">
                                  <th scope="row" className="py-2 pr-4 pl-6 text-left font-normal text-slate-700">
                                    {room.roomName}
                                    <span className="ml-2 text-xs text-slate-400">{room.roomCode}</span>
                                    {!room.active && <InactiveBadge />}
                                  </th>
                                  <td className="py-2 px-2 text-slate-500">{roomIsParaclinical ? 'Phòng CLS' : 'Phòng khám'}</td>
                                  <td className="py-2 px-2 text-right text-slate-700">{workSummary(room, roomIsParaclinical)}</td>
                                  <td className="py-2 px-2 text-right text-slate-600">
                                    {formatNumber(roomIsParaclinical ? room.waitingServices : room.inProgressVisits)}
                                  </td>
                                  <td className="py-2 pl-2 text-right text-slate-600">
                                    {roomIsParaclinical ? formatNumber(room.cancelledServices) : '—'}
                                  </td>
                                </tr>
                              );
                            })}
                          </Fragment>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="py-8 text-center text-sm text-slate-400">Không có khoa nào thuộc loại này.</p>
              )}
            </ChartCard>
          </div>
        )}
      </div>
    </AdminLayout>
  );
}

export default DepartmentStatisticsPage;
