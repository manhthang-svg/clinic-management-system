import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Building2,
  Clock,
  ArrowRight,
  RefreshCw,
  MapPin,
  AlertTriangle,
  Microscope,
  CheckCircle2,
  SkipForward,
  ListOrdered,
  CalendarDays,
} from 'lucide-react';

import { ApiError } from '../../apis/apiError';
import defaultDutyScheduleApi from '../../apis/dutyScheduleApi';
import { technicianApi as defaultTechnicianApi } from '../../apis/technicianApi';
import { MetricCard } from '../../components/shared/MetricCard';

// tu tai lai moi 10 giay khi tab dang mo
const REFRESH_MS = 10_000;
// so ngay hien o lich truc sap toi (tinh ca hom nay)
const SCHEDULE_DAYS = 4;
const SHIFT_NAMES = { MORNING: 'Ca sáng', AFTERNOON: 'Ca chiều' };
const SHIFT_ORDER = ['MORNING', 'AFTERNOON'];
const WEEKDAYS = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

const MSG = {
  load: 'Không thể tải dữ liệu tổng quan. Vui lòng thử lại.',
  session: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  forbidden: 'Bạn không có quyền xem tổng quan kỹ thuật viên.',
  schedule: 'Không thể tải lịch trực.',
  noRoom: 'Chưa có phòng cận lâm sàng nào đang hoạt động.',
};

// doi loi tai thanh thong bao
function errorMessage(error) {
  if (error instanceof ApiError && error.status === 401) return MSG.session;
  if (error instanceof ApiError && error.status === 403) return MSG.forbidden;
  return MSG.load;
}

function toIsoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function addDays(date, days) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

// gio "HH:mm" -> so phut tu 0h
function minutesOf(time) {
  if (!time) return null;
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

function hhmm(time) {
  return time ? time.slice(0, 5) : '';
}

// khung gio cua ca, vd "13:00 - 17:30"
function shiftTime(shift, config) {
  if (!config) return '';
  const start = shift === 'MORNING' ? config.morningStartTime : config.afternoonStartTime;
  const end = shift === 'MORNING' ? config.morningEndTime : config.afternoonEndTime;
  return start && end ? `${hhmm(start)} – ${hhmm(end)}` : '';
}

// ca dang dien ra luc nay, khong co thi null
function shiftAt(now, config) {
  if (!config) return null;
  const minutes = now.getHours() * 60 + now.getMinutes();
  return SHIFT_ORDER.find((shift) => {
    const start = minutesOf(shift === 'MORNING' ? config.morningStartTime : config.afternoonStartTime);
    const end = minutesOf(shift === 'MORNING' ? config.morningEndTime : config.afternoonEndTime);
    return start !== null && end !== null && minutes >= start && minutes < end;
  }) ?? null;
}

// nhan ngay: Hom nay, Ngay mai, Thu ...
function dayLabel(date, index) {
  const dm = `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}`;
  if (index === 0) return `Hôm nay, ${dm}`;
  if (index === 1) return `Ngày mai, ${dm}`;
  return `${WEEKDAYS[date.getDay()]}, ${dm}`;
}

// tao the lich truc tung ngay cua KTV dang dang nhap
function buildScheduleDays(schedule, accountId, now) {
  const items = (schedule?.items ?? []).filter((item) => item.staffAccountId === accountId);
  const current = shiftAt(now, schedule?.shiftConfig);
  return Array.from({ length: SCHEDULE_DAYS }, (_, index) => {
    const date = addDays(now, index);
    const iso = toIsoDate(date);
    const shifts = items
      .filter((item) => item.workDate === iso)
      .sort((a, b) => SHIFT_ORDER.indexOf(a.shift) - SHIFT_ORDER.indexOf(b.shift))
      .map((item) => ({
        key: item.id ?? `${iso}-${item.shift}`,
        name: SHIFT_NAMES[item.shift] ?? item.shift,
        time: shiftTime(item.shift, schedule?.shiftConfig),
        room: item.roomCode ? `${item.roomCode} – ${item.roomName ?? ''}` : 'Không phân công phòng',
        isCurrent: index === 0 && item.shift === current,
      }));
    const status = shifts.length === 0 ? 'OFF' : shifts.some((s) => s.isCurrent) ? 'CURRENT' : 'UPCOMING';
    const statusLabel = { OFF: 'Nghỉ ca', CURRENT: 'Đang diễn ra', UPCOMING: 'Có lịch trực' }[status];
    return { key: iso, label: dayLabel(date, index), shifts, status, statusLabel };
  });
}

function queueNumberLabel(number) {
  if (number === null || number === undefined) return '—';
  return number < 10 ? `0${number}` : String(number);
}

function InsuranceBadge({ type }) {
  const insured = type === 'BHYT';
  return (
    <span
      className={`px-1.5 py-0.5 rounded text-[10px] font-semibold shrink-0 ${
        insured ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-slate-100 text-slate-600 border border-slate-200'
      }`}
    >
      {insured ? 'BHYT' : 'Dịch vụ'}
    </span>
  );
}

function PatientMeta({ item }) {
  return (
    <>
      <span className="font-mono text-[11px] text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded shrink-0">
        {item.patientId}
      </span>
      {(item.age !== null && item.age !== undefined) || item.gender ? (
        <span className="text-[11px] text-slate-400 shrink-0">
          {[item.age !== null && item.age !== undefined ? `${item.age}t` : null, item.gender].filter(Boolean).join(' • ')}
        </span>
      ) : null}
      <InsuranceBadge type={item.insuranceType} />
    </>
  );
}

function DoctorLine({ item }) {
  return (
    <div className="flex items-center justify-between text-[11px] text-slate-500 gap-2 pt-0.5">
      <span className="truncate">
        <strong className="text-slate-600 font-semibold">BS:</strong> {item.doctorName || '—'}
        {item.doctorRoom ? ` (${item.doctorRoom})` : ''}
      </span>
      {item.clinicalDiagnosis && (
        <span className="text-slate-400 italic truncate max-w-[45%] text-right hidden sm:inline-block" title={item.clinicalDiagnosis}>
          CĐ: {item.clinicalDiagnosis}
        </span>
      )}
    </div>
  );
}

// dashboard KTV: so lieu hom nay, benh nhan sap toi luot, ket qua cho nhap, lich truc
export function TechnicianDashboardPage({
  currentUser,
  api = defaultTechnicianApi,
  scheduleApi = defaultDutyScheduleApi,
  today = new Date(),
}) {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState(null);
  const [error, setError] = useState('');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [lastRefreshedAt, setLastRefreshedAt] = useState(null);
  const [schedule, setSchedule] = useState(null);
  const [scheduleError, setScheduleError] = useState('');

  const load = useCallback(async (signal) => {
    const storedRoomId = api.getActiveRoomId?.() || undefined;
    try {
      let result;
      try {
        result = await api.getDashboard({ roomId: storedRoomId }, signal);
      } catch (failure) {
        // phong da luu khong con thi bo, lay phong mac dinh
        if (!storedRoomId || failure?.errorResponse?.code !== 'ROOM_NOT_FOUND') throw failure;
        api.setActiveRoomId?.('');
        result = await api.getDashboard({}, signal);
      }
      if (signal?.aborted) return;
      if (result?.room?.id) api.setActiveRoomId?.(result.room.id);
      setDashboard(result);
      setError('');
      setLastRefreshedAt(new Date());
    } catch (failure) {
      if (failure?.name === 'AbortError' || signal?.aborted) return;
      setError(errorMessage(failure));
    }
  }, [api]);

  useEffect(() => {
    const controller = new AbortController();
    // tai lan dau o tick sau, roi tu tai lai moi 10 giay
    const first = setTimeout(() => {
      void load(controller.signal);
    }, 0);
    const timer = setInterval(() => {
      if (typeof document === 'undefined' || document.visibilityState === 'visible') {
        void load(controller.signal);
      }
    }, REFRESH_MS);
    return () => {
      controller.abort();
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [load]);

  const todayIso = toIsoDate(today);
  useEffect(() => {
    const controller = new AbortController();
    const start = new Date(`${todayIso}T00:00:00`);
    scheduleApi.getWeeklySchedule(todayIso, toIsoDate(addDays(start, SCHEDULE_DAYS - 1)), controller.signal)
      .then((result) => {
        setSchedule(result);
        setScheduleError('');
      })
      .catch((failure) => {
        if (failure?.name !== 'AbortError' && !controller.signal.aborted) setScheduleError(MSG.schedule);
      });
    return () => controller.abort();
  }, [scheduleApi, todayIso]);

  const handleManualRefresh = async () => {
    setIsRefreshing(true);
    await load();
    setIsRefreshing(false);
  };

  const scheduleDays = useMemo(
    () => buildScheduleDays(schedule, currentUser?.id, today),
    // chi tinh lai khi doi ngay
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [schedule, currentUser?.id, todayIso],
  );
  const currentShift = shiftAt(today, schedule?.shiftConfig);

  const room = dashboard?.room;
  const stats = dashboard?.stats;
  const canManage = Boolean(dashboard?.canManage);
  const hasOwnWork = Boolean(room?.hasOwnWork);
  const resultsToEnter = (stats?.pendingResult ?? 0) + (stats?.draft ?? 0);

  // Gom nhom benh nhan sap toi luot: 1 benh nhan nhieu dich vu chi hien 1 dong duy nhat (tuong tu TechnicianQueuePage)
  const groupedNextQueue = useMemo(() => {
    const rawItems = dashboard?.nextQueue ?? [];
    if (!rawItems.length) return [];
    const groups = new Map();

    for (const item of rawItems) {
      const baseOrderCode = item.orderCode?.replace(/-\d+$/, '') || item.orderCode || '';
      const groupKey = item.patientId ? `${item.patientId}_${baseOrderCode}` : (item.orderId || item.id);

      if (!groups.has(groupKey)) {
        groups.set(groupKey, {
          ...item,
          baseOrderCode,
          roomServices: [item],
        });
      } else {
        const existing = groups.get(groupKey);
        existing.roomServices.push(item);
        if (item.priority === 'PRIORITY') {
          existing.priority = 'PRIORITY';
          existing.priorityLabel = item.priorityLabel || existing.priorityLabel || 'Ưu tiên';
        }
        if (existing.queueNumber == null && item.queueNumber != null) {
          existing.queueNumber = item.queueNumber;
        } else if (item.queueNumber != null && existing.queueNumber != null) {
          existing.queueNumber = Math.min(existing.queueNumber, item.queueNumber);
        }
        if (!existing.isCheckedIn && item.isCheckedIn) {
          existing.isCheckedIn = true;
        }
      }
    }

    return Array.from(groups.values());
  }, [dashboard?.nextQueue]);

  // Gom nhom ket qua cho nhap / ban nhap: 1 benh nhan nhieu dich vu chi hien 1 dong duy nhat (tuong tu ParaclinicalResultListPage)
  const groupedPendingResults = useMemo(() => {
    const rawItems = dashboard?.pendingResults ?? [];
    if (!rawItems.length) return [];
    const groups = new Map();

    for (const item of rawItems) {
      const baseOrderCode = item.orderCode?.replace(/-\d+$/, '') || item.orderCode || '';
      const groupKey = item.patientId ? `${item.patientId}_${baseOrderCode}` : (item.orderId || item.id);

      if (!groups.has(groupKey)) {
        groups.set(groupKey, {
          ...item,
          baseOrderCode,
          services: [item],
        });
      } else {
        const existing = groups.get(groupKey);
        existing.services.push(item);
        if (item.priority === 'PRIORITY') {
          existing.priority = 'PRIORITY';
          existing.priorityLabel = item.priorityLabel || existing.priorityLabel || 'Ưu tiên';
        }
        if (existing.queueNumber == null && item.queueNumber != null) {
          existing.queueNumber = item.queueNumber;
        } else if (item.queueNumber != null && existing.queueNumber != null) {
          existing.queueNumber = Math.min(existing.queueNumber, item.queueNumber);
        }
      }
    }

    return Array.from(groups.values()).map((group) => {
      const svcs = group.services;
      const draftCount = svcs.filter((s) => s.status === 'DRAFT').length;
      const totalCount = svcs.length;
      const isAllDraft = draftCount === totalCount;
      const hasDraft = draftCount > 0;
      const anyOverdue = svcs.some((s) => (s.completedMinutesAgo ?? 0) >= 30);
      const maxCompletedMinutesAgo = Math.max(...svcs.map((s) => s.completedMinutesAgo ?? 0));
      const completedAtTime = group.completedAtTime || svcs[0]?.completedAtTime || '';

      return {
        ...group,
        draftCount,
        totalCount,
        isAllDraft,
        hasDraft,
        anyOverdue,
        completedMinutesAgo: maxCompletedMinutesAgo,
        completedAtTime,
      };
    });
  }, [dashboard?.pendingResults]);

  // Thong ke dong bo theo so BENH NHAN (va tong so dich vu)
  const waitingPatientCount = stats?.waiting === 0 ? 0 : (groupedNextQueue.length || stats?.waiting || 0);
  const pendingPatientCount = resultsToEnter === 0 ? 0 : (groupedPendingResults.length || resultsToEnter);
  const overduePatientCount = resultsToEnter === 0 ? 0 : (
    groupedPendingResults.length > 0
      ? groupedPendingResults.filter((p) => p.anyOverdue || (p.completedMinutesAgo >= 30)).length
      : (stats?.overdue ?? 0)
  );

  if (!dashboard && !error) {
    return <p className="text-sm text-slate-500">Đang tải dữ liệu tổng quan…</p>;
  }

  return (
    <div className="space-y-6 w-full pb-10">
      {error && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700">
          <span>{error}</span>
          <button type="button" onClick={handleManualRefresh} className="font-semibold underline cursor-pointer">Thử lại</button>
        </div>
      )}

      {dashboard && !room && (
        <p className="px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-600">{MSG.noRoom}</p>
      )}

      {room && (
        <>
          {/* 1. Phong lam viec va trang thai ca truc (BR-05, BR-19, BR-55) */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col lg:flex-row lg:items-center justify-between gap-5">
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2.5">
                <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-teal-50 text-[#0d9488] border border-teal-200/80">
                  Phòng: {room.code}
                </span>
                {canManage ? (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 shadow-2xs">
                    <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                    Đang trong ca trực
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-50 text-amber-800 border border-amber-200 shadow-2xs">
                    <span className="w-2 h-2 rounded-full bg-amber-500" />
                    {hasOwnWork ? 'Ngoài ca trực · Còn chỉ định' : 'Ngoài ca trực · Chỉ xem'}
                  </span>
                )}
              </div>

              <div>
                <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">{room.name}</h1>
                <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 mt-1 font-medium">
                  {room.location && (
                    <>
                      <span className="flex items-center gap-1 text-slate-700 font-semibold">
                        <MapPin className="w-3.5 h-3.5 text-slate-400" />
                        {room.location}
                      </span>
                      <span>•</span>
                    </>
                  )}
                  {schedule?.shiftConfig && (
                    <>
                      <span className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-slate-400" />
                        Ca hiện tại:{' '}
                        <strong className="text-slate-700">
                          {currentShift
                            ? `${SHIFT_NAMES[currentShift]} (${shiftTime(currentShift, schedule.shiftConfig)})`
                            : 'Ngoài giờ ca trực'}
                        </strong>
                      </span>
                      <span>•</span>
                    </>
                  )}
                  <span>
                    Phụ trách: <strong className="text-slate-700">{room.activeTech || 'Chưa có'}</strong>
                  </span>
                </div>
              </div>
            </div>

            <button
              type="button"
              onClick={handleManualRefresh}
              disabled={isRefreshing}
              aria-label="Làm mới dữ liệu"
              className="h-11 w-11 shrink-0 flex items-center justify-center rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 hover:text-slate-900 shadow-2xs transition-all cursor-pointer disabled:opacity-50"
              title={`Làm mới dữ liệu (tự động mỗi 10 giây${lastRefreshedAt ? ` • lần cuối ${lastRefreshedAt.toLocaleTimeString('vi-VN')}` : ''})`}
            >
              <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-[#0d9488]' : ''}`} />
            </button>
          </div>

          {!canManage && (
            <div className="flex items-center gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 font-medium shadow-2xs">
              <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
              <span>
                {hasOwnWork
                  ? 'Bạn có thể hoàn tất và gửi kết quả cho các dịch vụ mình đã tiếp nhận.'
                  : 'Bạn có thể tra cứu các dịch vụ mình đã xử lý.'}
              </span>
            </div>
          )}

          {/* 2. Bon the chi so trong ngay; bam vao the de mo man hinh tuong ung */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <button type="button" onClick={() => navigate('/technician/queue')} className="text-left cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0">
              <MetricCard
                title="Đang chờ thực hiện"
                value={String(waitingPatientCount)}
                icon={Clock}
                iconBg="bg-teal-50 text-[#0d9488]"
                valueColor="text-teal-900"
                subtitle={
                  stats.waiting === 0
                    ? 'Đã thanh toán, chờ gọi vào phòng'
                    : waitingPatientCount !== stats.waiting
                      ? `Gồm ${stats.waiting} dịch vụ chờ gọi`
                      : 'Đã thanh toán, chờ gọi vào phòng'
                }
                dotColor="bg-teal-500"
                footerLabel="Mức ưu tiên"
                footerValue={`${stats.priority} ca ưu tiên`}
                footerValueColor={stats.priority > 0 ? 'text-amber-700 font-bold' : 'text-slate-700'}
              />
            </button>

            <button type="button" onClick={() => navigate('/technician/queue', { state: { tab: 'skipped' } })} className="text-left cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0">
              <MetricCard
                title="Đã bỏ qua hôm nay"
                value={String(stats.skipped)}
                icon={SkipForward}
                iconBg="bg-amber-50 text-amber-700"
                valueColor="text-amber-900"
                subtitle="Vắng mặt khi gọi lượt"
                dotColor="bg-amber-500"
                footerLabel="Chờ gọi lại"
                footerValue={`${stats.skipped} bệnh nhân`}
                footerValueColor="text-slate-700"
              />
            </button>

            <button type="button" onClick={() => navigate('/technician/results')} className="text-left cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0">
              <MetricCard
                title="Chờ nhập kết quả"
                value={String(pendingPatientCount)}
                icon={Microscope}
                iconBg="bg-sky-50 text-sky-700"
                valueColor="text-sky-900"
                subtitle={
                  resultsToEnter === 0
                    ? 'Chưa có kết quả chờ nhập'
                    : groupedPendingResults.length > 0 && resultsToEnter > groupedPendingResults.length
                      ? `Gồm ${resultsToEnter} dịch vụ (Chưa nhập ${stats.pendingResult} • Bản nháp ${stats.draft})`
                      : `Chưa nhập ${stats.pendingResult} • Bản nháp ${stats.draft}`
                }
                dotColor="bg-sky-500"
                footerLabel="Cần xử lý sớm"
                footerValue={`${overduePatientCount} ca >30 phút`}
                footerValueColor={overduePatientCount > 0 ? 'text-rose-600 font-bold' : 'text-slate-700'}
              />
            </button>

            <button type="button" onClick={() => navigate('/technician/results')} className="text-left cursor-pointer transition-transform hover:-translate-y-0.5 active:translate-y-0">
              <MetricCard
                title="Đã hoàn thành hôm nay"
                value={String(stats.completedToday)}
                icon={CheckCircle2}
                iconBg="bg-emerald-50 text-emerald-700"
                valueColor="text-emerald-900"
                subtitle="Kết quả đã gửi bác sĩ"
                dotColor="bg-emerald-500"
              />
            </button>
          </div>

          {/* 3. Benh nhan sap toi luot va ket qua dang cho nhap */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <section aria-labelledby="next-queue-title" className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col justify-between space-y-4">
              <div className="space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-lg bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
                      <ListOrdered className="w-4 h-4" />
                    </div>
                    <div>
                      <h2 id="next-queue-title" className="text-sm font-extrabold text-slate-900">Bệnh nhân sắp tới lượt</h2>
                      <p className="text-[11px] text-slate-500">5 bệnh nhân đứng đầu hàng đợi phòng {room.code}</p>
                    </div>
                  </div>
                  <button type="button" onClick={() => navigate('/technician/queue')} className="inline-flex items-center gap-1 text-xs font-bold text-[#0d9488] hover:text-[#0f766e] transition-colors cursor-pointer group">
                    <span>Xem tất cả ({waitingPatientCount})</span>
                    <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                  </button>
                </div>

                {groupedNextQueue.length === 0 ? (
                  <p className="py-6 text-center text-xs text-slate-400">Không có bệnh nhân nào đang chờ.</p>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {groupedNextQueue.slice(0, 5).map((item) => {
                      const hasMultiple = item.roomServices && item.roomServices.length > 1;
                      const mainService = item.roomServices?.[0] || item;
                      return (
                        <li key={item.id}>
                          <button
                            type="button"
                            onClick={() => navigate('/technician/queue')}
                            className="w-full text-left py-3 px-2.5 rounded-xl hover:bg-slate-50/90 transition-all cursor-pointer group space-y-1.5"
                            title="Mở màn hình hàng đợi"
                          >
                            <div className="flex items-center justify-between gap-2">
                              <div className="flex items-center gap-2 min-w-0 flex-wrap">
                                <span className="font-extrabold text-teal-800 bg-teal-50 px-2 py-0.5 rounded-md border border-teal-200/80 text-xs shrink-0 font-mono">
                                  #{queueNumberLabel(item.queueNumber)}
                                </span>
                                <span className="text-xs font-bold text-slate-900 group-hover:text-[#0d9488] transition-colors truncate">
                                  {item.patientName}
                                </span>
                                <PatientMeta item={item} />
                                {item.priority === 'PRIORITY' && (
                                  <span className="px-1.5 py-0.5 rounded text-[10px] font-extrabold bg-amber-50 text-amber-700 border border-amber-200 shrink-0" title={item.priorityLabel}>
                                    Ưu tiên
                                  </span>
                                )}
                              </div>
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80 shrink-0">
                                <span className="w-1.5 h-1.5 rounded-full bg-teal-500 animate-pulse" />
                                {item.isCheckedIn ? 'Đã có mặt' : 'Đang chờ gọi'}
                              </span>
                            </div>
                            <div className="flex items-center justify-between text-xs gap-2">
                              <div className="flex items-center gap-1.5 min-w-0 flex-wrap">
                                <span className="text-slate-800 font-medium truncate">{mainService.serviceName}</span>
                                {hasMultiple && (
                                  <span
                                    className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-teal-50 text-teal-700 border border-teal-200/90 whitespace-nowrap shadow-2xs cursor-help"
                                    title={item.roomServices.slice(1).map((s) => s.serviceName).join(', ')}
                                  >
                                    +{item.roomServices.length - 1} dịch vụ khác
                                  </span>
                                )}
                              </div>
                              {item.orderTime && (
                                <span className="text-[11px] text-slate-400 shrink-0 flex items-center gap-1">
                                  <Clock className="w-3 h-3 text-slate-400" />
                                  {item.orderTime}
                                </span>
                              )}
                            </div>
                            <DoctorLine item={item} />
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
              <p className="pt-2 border-t border-slate-100 text-[11px] text-slate-400">
                * Ưu tiên trước, sau đó theo số thứ tự tăng dần.
              </p>
            </section>

            <section aria-labelledby="pending-results-title" className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col justify-between space-y-4">
              <div className="space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-lg bg-sky-50 text-sky-700 flex items-center justify-center font-bold">
                      <Microscope className="w-4 h-4" />
                    </div>
                    <div>
                      <h2 id="pending-results-title" className="text-sm font-extrabold text-slate-900">Kết quả đang chờ nhập / Bản nháp</h2>
                      <p className="text-[11px] text-slate-500">Dịch vụ đã thực hiện, chưa gửi kết quả</p>
                    </div>
                  </div>
                  <button type="button" onClick={() => navigate('/technician/results')} className="inline-flex items-center gap-1 text-xs font-bold text-[#0d9488] hover:text-[#0f766e] transition-colors cursor-pointer group">
                    <span>Xem tất cả ({pendingPatientCount})</span>
                    <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                  </button>
                </div>

                {groupedPendingResults.length === 0 ? (
                  <p className="py-6 text-center text-xs text-slate-400">Không có kết quả nào đang chờ nhập.</p>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {groupedPendingResults.slice(0, 5).map((item) => {
                      const hasMultiple = item.services && item.services.length > 1;
                      const mainService = item.services?.[0] || item;
                      const isOverdue = item.anyOverdue || (item.completedMinutesAgo >= 30);
                      return (
                        <li key={item.id}>
                          <button
                            type="button"
                            onClick={() => navigate('/technician/results')}
                            className="w-full text-left py-3 px-2.5 rounded-xl hover:bg-slate-50/90 transition-all cursor-pointer group space-y-1.5"
                            title="Mở danh sách nhập kết quả"
                          >
                            <div className="flex items-center justify-between gap-2">
                              <div className="flex items-center gap-2 min-w-0 flex-wrap">
                                <span className="text-xs font-bold text-slate-900 group-hover:text-[#0d9488] transition-colors truncate">
                                  {item.patientName}
                                </span>
                                <PatientMeta item={item} />
                                {item.isAllDraft ? (
                                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-sky-50 text-sky-700 border border-sky-200 shrink-0">Bản nháp</span>
                                ) : item.hasDraft ? (
                                  <span
                                    className="px-2 py-0.5 rounded text-[10px] font-bold bg-sky-50 text-sky-700 border border-sky-200 shrink-0"
                                    title={`${item.draftCount}/${item.totalCount} dịch vụ đã lưu nháp`}
                                  >
                                    {`Bản nháp (${item.draftCount}/${item.totalCount})`}
                                  </span>
                                ) : (
                                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 shrink-0">Chưa nhập</span>
                                )}
                                {isOverdue && (
                                  <span className="px-1.5 py-0.5 rounded text-[10px] font-extrabold bg-rose-50 text-rose-700 border border-rose-200 shrink-0">&gt;30p</span>
                                )}
                              </div>
                              <div className="text-right shrink-0">
                                {item.completedAtTime && (
                                  <span className="text-[11px] font-semibold text-slate-700">Xong lúc {item.completedAtTime}</span>
                                )}
                                <span className="text-[10px] text-slate-400 block">Chờ gửi {item.completedMinutesAgo}p</span>
                              </div>
                            </div>
                            <div className="flex items-center justify-between text-xs gap-2">
                              <div className="flex items-center gap-1.5 min-w-0 flex-wrap">
                                <span className="text-slate-800 font-medium truncate">{mainService.serviceName}</span>
                                {hasMultiple && (
                                  <span
                                    className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-teal-50 text-teal-700 border border-teal-200/90 whitespace-nowrap shadow-2xs cursor-help"
                                    title={item.services.slice(1).map((s) => s.serviceName).join(', ')}
                                  >
                                    +{item.services.length - 1} dịch vụ khác
                                  </span>
                                )}
                              </div>
                              <span className="font-mono text-[11px] text-slate-400 shrink-0">{item.baseOrderCode || item.orderCode}</span>
                            </div>
                            <DoctorLine item={item} />
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
              <p className="pt-2 border-t border-slate-100 text-[11px] text-slate-400">
                * Ưu tiên trước, sau đó theo số thứ tự tăng dần.
              </p>
            </section>
          </div>
        </>
      )}

      {/* 4. Lich truc ca nhan sap toi (BR-55) */}
      <section aria-labelledby="my-shifts-title" className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
              <CalendarDays className="w-4 h-4" />
            </div>
            <div>
              <h2 id="my-shifts-title" className="text-sm font-extrabold text-slate-900">Lịch trực cá nhân sắp tới</h2>
              <p className="text-[11px] text-slate-500">Ca trực của bạn trong {SCHEDULE_DAYS} ngày tới (do Quản trị viên phân công)</p>
            </div>
          </div>
          <button type="button" onClick={() => navigate('/technician/schedules')} className="inline-flex items-center gap-1 text-xs font-bold text-[#0d9488] hover:text-[#0f766e] transition-colors cursor-pointer group">
            <span>Xem toàn bộ lịch trực</span>
            <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
          </button>
        </div>

        {scheduleError ? (
          <p className="text-xs text-rose-600">{scheduleError}</p>
        ) : !schedule ? (
          <p className="text-xs text-slate-500">Đang tải lịch trực…</p>
        ) : (
          <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
            {scheduleDays.map((day) => {
              const isCurrent = day.status === 'CURRENT';
              const isOff = day.status === 'OFF';
              return (
                <li
                  key={day.key}
                  className={`p-3.5 rounded-xl border transition-all ${
                    isCurrent
                      ? 'bg-teal-50/60 border-teal-200/90 shadow-2xs ring-1 ring-teal-500/20'
                      : isOff
                        ? 'bg-slate-50/60 border-slate-200 text-slate-400'
                        : 'bg-white border-slate-200/80'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className={`text-xs font-bold ${isCurrent ? 'text-teal-900' : 'text-slate-800'}`}>{day.label}</span>
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] font-extrabold ${
                        isCurrent
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : isOff
                            ? 'bg-slate-200/70 text-slate-600'
                            : 'bg-teal-50 text-teal-700 border border-teal-200'
                      }`}
                    >
                      {day.statusLabel}
                    </span>
                  </div>
                  {isOff ? (
                    <p className="text-sm font-extrabold text-slate-400">Không có ca trực</p>
                  ) : (
                    <div className="space-y-2.5">
                      {day.shifts.map((shift) => (
                        <div key={shift.key} className="space-y-1">
                          <div className="text-sm font-extrabold text-slate-900">{shift.name}</div>
                          {shift.time && (
                            <div className="text-[11px] text-slate-500 flex items-center gap-1">
                              <Clock className="w-3 h-3 text-slate-400" />
                              <span>{shift.time}</span>
                            </div>
                          )}
                          <div className="text-[11px] font-semibold text-slate-700 flex items-center gap-1">
                            <Building2 className="w-3 h-3 text-slate-400" />
                            <span>{shift.room}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

export default TechnicianDashboardPage;
