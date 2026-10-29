import { useState, useMemo } from 'react';
import {
  Search,
  Filter,
  Microscope,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Building2,
  ChevronDown,
  RotateCcw,
  RefreshCw,
  Megaphone,
  User,
  ShieldCheck,
  MapPin,
  Play,
  Ban,
  ArrowRight,
  ListOrdered,
  FileCheck,
} from 'lucide-react';
import { toast } from 'sonner';

import { MetricCard } from '../../components/shared/MetricCard';
import {
  TECHNICIAN_ROOM_LIST,
  INITIAL_PARACLINICAL_ORDERS,
} from '../../constants/technicianQueueData';

import {
  PatientClinicalDrawer,
  RejectOrderModal,
} from '../../components/technician';

/**
 * Âm thanh chuông báo gọi số bệnh nhân (Web Audio API Synthesizer)
 */
function playChimeSound() {
  try {
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) return;
    const ctx = new AudioContext();
    const now = ctx.currentTime;

    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(587.33, now); // D5
    gain1.gain.setValueAtTime(0.15, now);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.5);
    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    osc1.start(now);
    osc1.stop(now + 0.5);

    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'sine';
    osc2.frequency.setValueAtTime(880, now + 0.15); // A5
    gain2.gain.setValueAtTime(0.18, now + 0.15);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.7);
    osc2.connect(gain2);
    gain2.connect(ctx.destination);
    osc2.start(now + 0.15);
    osc2.stop(now + 0.7);
  } catch {
    // AudioContext blocked
  }
}

export function TechnicianQueuePage() {
  // 1. Quản lý phòng máy hiện tại (Mặc định: P.105 - Phòng Siêu âm & X-quang 01)
  const [currentRoomId, setCurrentRoomId] = useState(TECHNICIAN_ROOM_LIST[0].id);

  // 2. Danh sách toàn bộ orders cận lâm sàng
  const [orders, setOrders] = useState(INITIAL_PARACLINICAL_ORDERS);

  // 3. Toolbar & Filters states
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL' | 'WAITING' | 'IN_PROGRESS' | 'COMPLETED'
  const [doctorRoomFilter, setDoctorRoomFilter] = useState('ALL');

  // 4. Drawer & Modal states
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [orderToReject, setOrderToReject] = useState(null);

  // Lấy thông tin chi tiết phòng máy hiện tại
  const currentRoom = useMemo(() => {
    return (
      TECHNICIAN_ROOM_LIST.find((r) => r.id === currentRoomId) ||
      TECHNICIAN_ROOM_LIST[0]
    );
  }, [currentRoomId]);

  // Lọc danh sách y lệnh theo phòng máy hiện tại (Scope phòng)
  const roomOrders = useMemo(() => {
    return orders.filter((o) => o.roomId === currentRoomId);
  }, [orders, currentRoomId]);

  // 4 THẺ THỐNG KÊ THEO PHÒNG HIỆN TẠI
  const stats = useMemo(() => {
    const total = roomOrders.length;
    const waiting = roomOrders.filter((o) => o.status === 'WAITING').length;
    const priority = roomOrders.filter(
      (o) => o.priority === 'PRIORITY' && o.status !== 'COMPLETED' && o.status !== 'CANCELLED'
    ).length;
    const completed = roomOrders.filter((o) => o.status === 'COMPLETED').length;

    return { total, waiting, priority, completed };
  }, [roomOrders]);

  // Danh sách các phòng khám gửi chỉ định (phục vụ dropdown lọc)
  const doctorRoomsList = useMemo(() => {
    const set = new Set();
    roomOrders.forEach((o) => {
      if (o.doctorRoom) set.add(o.doctorRoom);
    });
    return Array.from(set);
  }, [roomOrders]);

  // Danh sách chỉ định sau khi áp dụng các bộ lọc tìm kiếm
  const filteredOrders = useMemo(() => {
    return roomOrders.filter((order) => {
      // Tìm kiếm theo Mã BN, Mã phiếu, Họ tên, CCCD/BHYT
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchName = order.patientName.toLowerCase().includes(q);
        const matchCode = order.orderCode.toLowerCase().includes(q);
        const matchId = order.patientId.toLowerCase().includes(q);
        const matchCccd = order.cccd?.toLowerCase().includes(q) || false;
        const matchInsurance = order.insuranceNumber?.toLowerCase().includes(q) || false;

        if (!matchName && !matchCode && !matchId && !matchCccd && !matchInsurance) {
          return false;
        }
      }

      // Bộ lọc trạng thái
      if (statusFilter !== 'ALL' && order.status !== statusFilter) {
        return false;
      }

      // Bộ lọc Bác sĩ / Phòng gửi chỉ định
      if (doctorRoomFilter !== 'ALL' && order.doctorRoom !== doctorRoomFilter) {
        return false;
      }

      return true;
    }).sort((a, b) => {
      // Ưu tiên ca "Ưu tiên" lên trước, sau đó theo số thứ tự (queueNumber)
      if (a.priority === 'PRIORITY' && b.priority !== 'PRIORITY') return -1;
      if (b.priority === 'PRIORITY' && a.priority !== 'PRIORITY') return 1;
      return a.queueNumber - b.queueNumber;
    });
  }, [roomOrders, searchQuery, statusFilter, doctorRoomFilter]);

  // Đặt lại bộ lọc
  const handleResetFilters = () => {
    setSearchQuery('');
    setStatusFilter('ALL');
    setDoctorRoomFilter('ALL');
    toast.info('Đã đặt lại bộ lọc danh sách hàng đợi.');
  };

  // Thao tác: Mở Drawer xem chi tiết
  const handleOpenDetail = (order) => {
    setSelectedOrder(order);
    setIsDrawerOpen(true);
  };

  // Thao tác: Gọi số bệnh nhân vào phòng máy
  const handleCallQueue = (order, e) => {
    e?.stopPropagation();
    playChimeSound();
    toast.success(
      `🔔 Đang gọi số #${order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber}: Bệnh nhân ${order.patientName} vào ${currentRoom.name}!`,
      {
        duration: 4500,
        className: 'font-semibold text-xs',
      }
    );
  };

  // Thao tác: Tiếp nhận & Thực hiện
  const handleStartOrder = (order, e) => {
    e?.stopPropagation();
    setOrders((prev) =>
      prev.map((o) => (o.id === order.id ? { ...o, status: 'IN_PROGRESS' } : o))
    );
    // Cập nhật cả selectedOrder trong Drawer nếu đang mở
    if (selectedOrder?.id === order.id) {
      setSelectedOrder((prev) => ({ ...prev, status: 'IN_PROGRESS' }));
    }
    toast.success(`Đã tiếp nhận thực hiện y lệnh cho BN ${order.patientName}!`);
  };

  // Thao tác: Hoàn thành kỹ thuật
  const handleCompleteOrder = (order) => {
    setOrders((prev) =>
      prev.map((o) => (o.id === order.id ? { ...o, status: 'COMPLETED' } : o))
    );
    if (selectedOrder?.id === order.id) {
      setSelectedOrder((prev) => ({ ...prev, status: 'COMPLETED' }));
    }
    toast.success(`Đã hoàn tất kỹ thuật cận lâm sàng cho BN ${order.patientName}!`);
  };

  // Thao tác: Mở modal từ chối / hủy
  const handleOpenRejectModal = (order) => {
    setOrderToReject(order);
    setRejectModalOpen(true);
  };

  // Thao tác: Xác nhận từ chối y lệnh
  const handleConfirmReject = (orderId, reason) => {
    setOrders((prev) =>
      prev.map((o) => (o.id === orderId ? { ...o, status: 'CANCELLED', rejectReason: reason } : o))
    );
    if (selectedOrder?.id === orderId) {
      setSelectedOrder((prev) => ({ ...prev, status: 'CANCELLED', rejectReason: reason }));
    }
    toast.error(`Đã từ chối y lệnh và hoàn trả về phòng khám bác sĩ. Lý do: ${reason}`);
  };

  return (
    <div className="space-y-6 w-full pb-10">
      {/* 1. HEADER & BỘ CHỌN PHÒNG MÁY (ROOM SCOPE HEADER) */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs">
        {/* Tên màn hình & Phạm vi phòng máy */}
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-xl font-extrabold text-slate-900 tracking-tight">
              Danh sách chờ & Phân loại cận lâm sàng
            </h1>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80">
              Phòng máy: {currentRoom.code}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 font-medium">
            <span className="flex items-center gap-1.5 text-slate-800 font-bold">
              <Building2 className="w-4 h-4 text-[#0d9488]" />
              <span>{currentRoom.name}</span>
            </span>
            <span>•</span>
            <span className="flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-slate-400" />
              <span>{currentRoom.location}</span>
            </span>
            <span>•</span>
            <span className="text-slate-600">
              Phụ trách: <strong>{currentRoom.activeTech}</strong>
            </span>
          </div>
        </div>

        {/* Bộ chọn/Chuyển đổi phòng (Room Selector Dropdown) */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="text-right hidden sm:block">
            <span className="block text-[11px] font-semibold text-slate-400">
              Chuyển đổi phòng phụ trách:
            </span>
            <span className="block text-xs font-bold text-slate-700">
              {currentRoom.code} - {currentRoom.name}
            </span>
          </div>

          <div className="relative">
            <select
              value={currentRoomId}
              onChange={(e) => {
                setCurrentRoomId(e.target.value);
                toast.info(`Đã chuyển phạm vi làm việc sang: ${e.target.options[e.target.selectedIndex].text}`);
              }}
              className="h-11 px-4 pr-10 bg-teal-50/70 border border-teal-200/90 rounded-xl text-xs font-bold text-[#0d9488] focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer shadow-2xs hover:bg-teal-50 transition-all appearance-none"
            >
              {TECHNICIAN_ROOM_LIST.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.code} - {room.name}
                </option>
              ))}
            </select>
            <ChevronDown className="w-4 h-4 text-[#0d9488] absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </div>
      </div>

      {/* 4 THẺ THỐNG KÊ THEO PHÒNG HIỆN TẠI (REUSING METRICCARD WITH ADMIN PALETTE) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title="Tổng chỉ định"
          value={stats.total}
          icon={ListOrdered}
          iconBg="bg-teal-50 text-[#0d9488]"
          valueColor="text-slate-900"
          subtitle={`Tổng số ca gửi đến ${currentRoom.code} trong ca trực`}
          dotColor="bg-teal-500"
          footerLabel="Phạm vi"
          footerValue={currentRoom.code}
          footerValueColor="text-teal-700"
        />

        <MetricCard
          title="Chờ thực hiện"
          value={stats.waiting}
          icon={Clock}
          iconBg="bg-sky-50 text-sky-700"
          valueColor="text-sky-800"
          subtitle="Số ca đang đợi gọi vào phòng máy"
          dotColor="bg-sky-500"
          footerLabel="Trạng thái"
          footerValue="Đang chờ gọi"
          footerValueColor="text-sky-700"
        />

        <MetricCard
          title="Ưu tiên"
          value={stats.priority}
          icon={AlertTriangle}
          iconBg="bg-amber-50 text-amber-700"
          valueColor="text-amber-800"
          subtitle="Người cao tuổi, trẻ nhỏ, kiểm tra khẩn"
          dotColor="bg-amber-500"
          footerLabel="Mức ưu tiên"
          footerValue="Xử lý trước"
          footerValueColor="text-amber-700"
        />

        <MetricCard
          title="Đã hoàn thành"
          value={stats.completed}
          icon={CheckCircle2}
          iconBg="bg-emerald-50 text-emerald-700"
          valueColor="text-emerald-800"
          subtitle="Đã thực hiện xong kỹ thuật / có kết quả"
          dotColor="bg-emerald-500"
          footerLabel="Tiến độ hoàn tất"
          footerValue={`${stats.total > 0 ? Math.round((stats.completed / stats.total) * 100) : 0}%`}
          footerValueColor="text-emerald-700"
        />
      </div>

      {/* 2. THANH CÔNG CỤ & BỘ LỌC (TOOLBAR & FILTERS) */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-2xs">
        <div className="flex flex-col lg:flex-row items-stretch lg:items-center gap-3">
          {/* Ô tìm kiếm nhanh */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm kiếm theo Mã bệnh nhân (Patient ID), Mã phiếu, Họ tên, số CCCD hoặc BHYT..."
              className="w-full h-10 pl-10 pr-8 bg-slate-50 border border-slate-200/80 rounded-xl text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:bg-white transition-all"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-xs font-bold"
              >
                ✕
              </button>
            )}
          </div>

          {/* Bộ lọc trạng thái */}
          <div className="w-full lg:w-48">
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="w-full h-10 px-3 bg-slate-50 border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="WAITING">Chờ tiếp nhận</option>
              <option value="IN_PROGRESS">Đang thực hiện</option>
              <option value="COMPLETED">Đã hoàn thành</option>
            </select>
          </div>

          {/* Bộ lọc Bác sĩ / Phòng gửi chỉ định */}
          <div className="w-full lg:w-64">
            <select
              value={doctorRoomFilter}
              onChange={(e) => setDoctorRoomFilter(e.target.value)}
              className="w-full h-10 px-3 bg-slate-50 border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer"
            >
              <option value="ALL">Tất cả phòng gửi chỉ định</option>
              {doctorRoomsList.map((room) => (
                <option key={room} value={room}>
                  {room}
                </option>
              ))}
            </select>
          </div>

          {/* Nút đặt lại bộ lọc */}
          <button
            type="button"
            onClick={handleResetFilters}
            className="h-10 px-3.5 bg-slate-100 hover:bg-slate-200/70 text-slate-700 rounded-xl text-xs font-semibold transition-colors flex items-center justify-center gap-1.5 cursor-pointer shrink-0"
            title="Đặt lại bộ lọc"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Đặt lại</span>
          </button>
        </div>
      </div>

      {/* 3. BẢNG DANH SÁCH HÀNG ĐỢI (WAITING PARACLINICAL TABLE - MASTER VIEW) */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden w-full">
        <div className="overflow-x-auto xl:overflow-x-visible">
          <table className="w-full text-left text-xs table-auto">
            <thead>
              <tr className="bg-slate-50/80 text-slate-500 font-semibold border-b border-slate-200/80">
                <th className="py-3.5 px-3 w-14 text-center">STT</th>
                <th className="py-3.5 px-3 whitespace-nowrap">Mức độ ưu tiên</th>
                <th className="py-3.5 px-3">Mã BN & Bệnh nhân</th>
                <th className="py-3.5 px-3 text-center whitespace-nowrap">Đối tượng</th>
                <th className="py-3.5 px-3">Dịch vụ chỉ định</th>
                <th className="py-3.5 px-3">Bác sĩ & Phòng gửi</th>
                <th className="py-3.5 px-3 whitespace-nowrap">Thời gian</th>
                <th className="py-3.5 px-3 whitespace-nowrap">Trạng thái</th>
                <th className="py-3.5 px-4 text-right whitespace-nowrap">Thao tác nhanh</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700 font-medium">
              {filteredOrders.map((order) => {
                const isWaiting = order.status === 'WAITING';
                const isInProgress = order.status === 'IN_PROGRESS';
                const isCompleted = order.status === 'COMPLETED';
                const isCancelled = order.status === 'CANCELLED';

                return (
                  <tr
                    key={order.id}
                    onClick={() => handleOpenDetail(order)}
                    className={`hover:bg-slate-50/80 transition-colors cursor-pointer group ${
                      selectedOrder?.id === order.id && isDrawerOpen ? 'bg-teal-50/40' : ''
                    } ${order.priority === 'PRIORITY' ? 'bg-amber-50/15' : ''}`}
                  >
                    {/* 1. STT / Số thứ tự */}
                    <td className="py-3.5 px-3 text-center">
                      <span className="font-mono font-black text-xs text-[#0d9488] bg-teal-50 px-2 py-0.5 rounded-md border border-teal-200/60 inline-block min-w-[32px]">
                        #{order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber}
                      </span>
                    </td>

                    {/* 2. Mức độ ưu tiên */}
                    <td className="py-3.5 px-3 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold border ${
                          order.priority === 'PRIORITY'
                            ? 'bg-amber-50 text-amber-700 border-amber-200'
                            : 'bg-teal-50 text-teal-700 border-teal-200'
                        }`}
                      >
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            order.priority === 'PRIORITY' ? 'bg-amber-500' : 'bg-teal-500'
                          }`}
                        />
                        <span>{order.priorityLabel}</span>
                      </span>
                    </td>

                    {/* 3. Mã BN & Họ tên bệnh nhân */}
                    <td className="py-3.5 px-3">
                      <div className="font-bold text-slate-900 group-hover:text-[#0d9488] transition-colors text-[13px] whitespace-nowrap">
                        {order.patientName}
                      </div>
                      <div className="text-[11px] font-mono font-semibold text-slate-500 mt-0.5 whitespace-nowrap">
                        {order.patientId}
                      </div>
                    </td>

                    {/* 4. Đối tượng */}
                    <td className="py-3.5 px-3 text-center whitespace-nowrap">
                      {order.insuranceType === 'BHYT' ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          <ShieldCheck className="w-3 h-3" />
                          BHYT
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
                          Viện phí
                        </span>
                      )}
                    </td>

                    {/* 5. Dịch vụ chỉ định */}
                    <td className="py-3.5 px-3 max-w-[240px]">
                      <div className="font-bold text-slate-900 truncate" title={order.serviceName}>
                        {order.serviceName}
                      </div>
                      <div className="text-[11px] text-slate-400 truncate mt-0.5" title={order.clinicalDiagnosis}>
                        {order.clinicalDiagnosis}
                      </div>
                    </td>

                    {/* 6. Bác sĩ & Phòng chỉ định */}
                    <td className="py-3.5 px-3">
                      <div className="font-semibold text-slate-800 whitespace-nowrap">{order.doctorName}</div>
                      <div className="text-[11px] text-slate-400 whitespace-nowrap">{order.doctorRoom}</div>
                    </td>

                    {/* 7. Thời gian chỉ định */}
                    <td className="py-3.5 px-3 whitespace-nowrap">
                      <div className="flex items-center gap-1 text-slate-700 font-semibold">
                        <Clock className="w-3 h-3 text-slate-400" />
                        <span>{order.orderTime}</span>
                      </div>
                      <div className="text-[10px] text-slate-400 font-mono">Phiếu: {order.orderCode}</div>
                    </td>

                    {/* 8. Trạng thái */}
                    <td className="py-3.5 px-3 whitespace-nowrap">
                      {isWaiting && (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-sky-50 text-sky-700 border border-sky-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-sky-500" />
                          Chờ tiếp nhận
                        </span>
                      )}
                      {isInProgress && (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-purple-50 text-purple-700 border border-purple-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-purple-500 animate-pulse" />
                          Đang thực hiện
                        </span>
                      )}
                      {isCompleted && (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                          Đã có kết quả
                        </span>
                      )}
                      {isCancelled && (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                          <Ban className="w-3 h-3 text-rose-600" />
                          Đã từ chối
                        </span>
                      )}
                    </td>

                    {/* 9. Thao tác nhanh (Bỏ icon con mắt theo yêu cầu) */}
                    <td className="py-3.5 px-4 text-right whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1.5">
                        {/* Nút Gọi số */}
                        <button
                          type="button"
                          onClick={(e) => handleCallQueue(order, e)}
                          className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-bold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200/90 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95"
                          title="Phát loa gọi số bệnh nhân vào phòng"
                        >
                          <Megaphone className="w-3.5 h-3.5 text-amber-600" />
                          <span>Gọi số</span>
                        </button>

                        {/* Nút Tiếp nhận (nếu WAITING) */}
                        {isWaiting && (
                          <button
                            type="button"
                            onClick={(e) => handleStartOrder(order, e)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-bold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95"
                            title="Tiếp nhận bệnh nhân"
                          >
                            <Play className="w-3 h-3 fill-current" />
                            <span>Tiếp nhận</span>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}

              {filteredOrders.length === 0 && (
                <tr>
                  <td colSpan={9} className="py-12 text-center text-slate-400 space-y-2">
                    <Microscope className="w-8 h-8 text-slate-300 mx-auto" />
                    <p className="text-sm font-semibold">
                      Không có chỉ định nào phù hợp với bộ lọc tại {currentRoom.name}.
                    </p>
                    <button
                      type="button"
                      onClick={handleResetFilters}
                      className="text-xs text-[#0d9488] font-bold hover:underline cursor-pointer"
                    >
                      Đặt lại bộ lọc tìm kiếm
                    </button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Chuyển dòng "Đang hiển thị: ... ca cận lâm sàng" xuống cuối bảng theo yêu cầu */}
        <div className="px-5 py-3.5 bg-slate-50/80 border-t border-slate-200/80 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-slate-500 font-medium">
          <div className="flex items-center gap-2">
            <span>
              Đang hiển thị: <strong className="text-slate-900">{filteredOrders.length}</strong> / {roomOrders.length} ca cận lâm sàng
            </span>
            {(searchQuery || statusFilter !== 'ALL' || doctorRoomFilter !== 'ALL') && (
              <span className="text-amber-700 bg-amber-50 px-2 py-0.5 rounded-md font-semibold border border-amber-200/60 text-[11px]">
                Đang áp dụng bộ lọc tùy chỉnh
              </span>
            )}
          </div>

          <div className="text-[11px] text-slate-400">
            * Nhấn trực tiếp vào dòng bệnh nhân để mở Drawer chi tiết lâm sàng & y lệnh
          </div>
        </div>
      </div>

      {/* 4. NGĂN KÉO CHI TIẾT BỆNH NHÂN & Y LỆNH (PATIENT & CLINICAL DRAWER) */}
      <PatientClinicalDrawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        order={selectedOrder}
        onStartOrder={(order) => handleStartOrder(order)}
        onCompleteOrder={(order) => handleCompleteOrder(order)}
        onOpenRejectModal={(order) => handleOpenRejectModal(order)}
      />

      {/* MODAL TỪ CHỐI / HỦY Y LỆNH */}
      <RejectOrderModal
        isOpen={rejectModalOpen}
        onClose={() => setRejectModalOpen(false)}
        order={orderToReject}
        onConfirmReject={handleConfirmReject}
      />
    </div>
  );
}

export default TechnicianQueuePage;
