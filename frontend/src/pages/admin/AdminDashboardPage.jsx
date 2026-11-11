import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Banknote, FlaskConical, RefreshCw, Stethoscope, Users } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { FormAlert } from '../../components/shared/FormFields';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { statisticsApi } from '../../apis/statisticsApi';
import {
  PRESETS,
  dashboardError,
  formatDayLong,
  formatDayShort,
  formatMoney,
  formatNumber,
  validatePeriod,
} from '../../components/statistics/dashboardPeriod';

const { ADMIN } = ROUTES;

const TEAL = '#127e7a';
const COLORS = {
  completed: TEAL,
  inProgress: '#0ea5e9',
  cancelled: '#f59e0b',
  abandoned: '#e11d48',
  insurance: TEAL,
  patient: '#f59e0b',
  selfPay: '#6366f1',
};

const CARD = 'bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-5';
const INPUT = 'px-3 py-2 text-sm border border-slate-200 rounded-xl bg-white focus:outline-none focus:ring-2 focus:ring-[#127e7a]/30 focus:border-[#127e7a]';

function KpiCard({ icon: Icon, label, value, hint }) {
  return (
    <div className={`${CARD} flex items-start gap-4`}>
      <div className="w-11 h-11 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
        <Icon className="w-5 h-5" aria-hidden="true" />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium text-slate-500">{label}</p>
        <p className="text-2xl font-extrabold text-[#1b2533] tracking-tight mt-0.5 truncate">{value}</p>
        {hint && <p className="text-xs text-slate-500 mt-1">{hint}</p>}
      </div>
    </div>
  );
}

function ChartCard({ title, description, children, className = '' }) {
  return (
    <section className={`${CARD} ${className}`} aria-label={title}>
      <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">{title}</h2>
      {description && <p className="text-xs text-slate-500 mt-0.5">{description}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}

function EmptyChart() {
  return (
    <div className="h-56 flex items-center justify-center text-sm text-slate-400 bg-slate-50 rounded-xl">
      Chưa có dữ liệu trong khoảng thời gian này.
    </div>
  );
}

function Legend({ items }) {
  return (
    <ul className="space-y-2 text-sm">
      {items.map((item) => (
        <li key={item.name} className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: item.color }} aria-hidden="true" />
          <span className="text-slate-600 flex-1">{item.name}</span>
          <span className="font-semibold text-slate-800 tabular-nums">{item.display ?? formatNumber(item.value)}</span>
        </li>
      ))}
    </ul>
  );
}

function DonutWithLegend({ items, label }) {
  const hasData = items.some((item) => item.value > 0);
  if (!hasData) return <EmptyChart />;
  return (
    <div className="grid grid-cols-1 sm:grid-cols-[180px_minmax(0,1fr)] gap-4 items-center">
      <div className="h-44" role="img" aria-label={label}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={items} dataKey="value" nameKey="name" innerRadius={50} outerRadius={78} paddingAngle={2} stroke="none">
              {items.map((item) => <Cell key={item.name} fill={item.color} />)}
            </Pie>
            <Tooltip formatter={(value, name, entry) => [entry.payload.display ?? formatNumber(value), name]} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <Legend items={items} />
    </div>
  );
}

/** UC-26 View Statistics Dashboard. */
export function AdminDashboardPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  api = statisticsApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const [period, setPeriod] = useState(() => PRESETS.find((p) => p.id === 'month').range(today));
  const [presetId, setPresetId] = useState('month');
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  // The request that last settled; loading is derived so the effect never sets state synchronously.
  const [settledKey, setSettledKey] = useState(null);

  const periodError = validatePeriod(period);
  const requestKey = `${period.from}|${period.to}|${reloadKey}`;
  const loading = !periodError && settledKey !== requestKey;

  useEffect(() => {
    if (periodError) return undefined;
    const controller = new AbortController();
    (async () => {
      try {
        const result = await api.getDashboard(period, controller.signal);
        setData(result);
        setError('');
      } catch (err) {
        if (err?.name === 'AbortError' || controller.signal.aborted) return;
        setError(dashboardError(err));
      }
      if (!controller.signal.aborted) setSettledKey(requestKey);
    })();
    return () => controller.abort();
  }, [api, period, periodError, requestKey]);

  const choosePreset = (preset) => {
    setPresetId(preset.id);
    setPeriod(preset.range(today));
  };

  const changeDate = (field) => (event) => {
    setPresetId('');
    setPeriod((current) => ({ ...current, [field]: event.target.value }));
  };

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
          <button
            type="button"
            onClick={() => setReloadKey((k) => k + 1)}
            disabled={loading || Boolean(periodError)}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            {loading ? 'Đang tải…' : 'Làm mới'}
          </button>
        </div>

        <section className={`${CARD} flex flex-wrap items-end gap-4`} aria-label="Khoảng thời gian">
          <div className="flex flex-wrap gap-2" role="group" aria-label="Chọn nhanh">
            {PRESETS.map((preset) => (
              <button
                key={preset.id}
                type="button"
                aria-pressed={presetId === preset.id}
                onClick={() => choosePreset(preset)}
                className={`px-3.5 py-2 text-sm rounded-xl border cursor-pointer transition-colors ${presetId === preset.id
                  ? 'bg-[#127e7a] border-[#127e7a] text-white font-semibold'
                  : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'}`}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-end gap-3 sm:ml-auto">
            <div className="flex flex-col gap-1">
              <label htmlFor="dashboard-from" className="text-xs font-medium text-slate-600">Từ ngày</label>
              <input id="dashboard-from" type="date" value={period.from} max={period.to || undefined} onChange={changeDate('from')} className={INPUT} />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="dashboard-to" className="text-xs font-medium text-slate-600">Đến ngày</label>
              <input id="dashboard-to" type="date" value={period.to} min={period.from || undefined} onChange={changeDate('to')} className={INPUT} />
            </div>
          </div>
          {periodError && <p role="alert" className="w-full text-xs text-rose-600">{periodError}</p>}
        </section>

        {error && !periodError && (
          <div className="space-y-3 max-w-xl">
            <FormAlert>{error}</FormAlert>
            <button
              type="button"
              onClick={() => setReloadKey((k) => k + 1)}
              className="px-4 py-2 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl cursor-pointer"
            >
              Thử lại
            </button>
          </div>
        )}

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
