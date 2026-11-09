import { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import {
  Search,
  Microscope,
  Clock,
  Building2,
  ChevronDown,
  RotateCcw,
  RefreshCw,
  Megaphone,
  User,
  ShieldCheck,
  MapPin,
  Play,
  SkipForward,
  Ban,
  ListOrdered,
  AlertTriangle,
  Loader2,
  Eye,
} from 'lucide-react';
import { toast } from 'sonner';
import { useNavigate } from 'react-router';

import { MetricCard } from '../../components/shared/MetricCard';
import technicianApi from '../../apis/technicianApi';

import {
  PatientClinicalDrawer,
  RejectOrderModal,
  ReturnToWaitingDialog,
} from '../../components/technician';
import { PERFORM_MESSAGES, performErrorMessage } from '../../components/technician/performOrder';

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
  // 1. Quản lý danh sách phòng máy thực tế từ backend
  const [rooms, setRooms] = useState([]);
  const [currentRoomId, setCurrentRoomId] = useState('');
  const [roomsLoading, setRoomsLoading] = useState(true);

  // 2. Dữ liệu hàng đợi và thống kê từ backend
  const [queueData, setQueueData] = useState({
    stats: null,
    items: [],
    canManage: null,
  });
  const [queueLoading, setQueueLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  // 3. Toolbar & Filters states
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [doctorRoomFilter, setDoctorRoomFilter] = useState('ALL');
  const [doctorRoomsList, setDoctorRoomsList] = useState([]);

  // 4. Drawer & Modal states
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [orderToReject, setOrderToReject] = useState(null);
  const [orderToReturn, setOrderToReturn] = useState(null);
  const navigate = useNavigate();

  // Debounce tìm kiếm (300ms)
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery.trim());
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Tải danh sách phòng máy khi khởi tạo
  useEffect(() => {
    let mounted = true;
    const fetchRooms = async () => {
      try {
        setRoomsLoading(true);
        const roomList = await technicianApi.getRooms();
        if (mounted) {
          const validRooms = Array.isArray(roomList) ? roomList : [];
          setRooms(validRooms);
          if (validRooms.length > 0) {
            const assignedRoom = validRooms.find((r) => r.isAssigned);
            const defaultRoom = assignedRoom || validRooms[0];
            setCurrentRoomId((prev) => (prev && validRooms.some((r) => r.id === prev) ? prev : defaultRoom.id));
          }
        }
      } catch (err) {
        if (mounted) {
          toast.error(err?.message || 'Không thể tải danh sách phòng máy cận lâm sàng.');
        }
      } finally {
        if (mounted) setRoomsLoading(false);
      }
    };

    fetchRooms();
    return () => {
      mounted = false;
    };
  }, []);

  // Lấy thông tin chi tiết phòng máy hiện tại
  const currentRoom = useMemo(() => {
    return rooms.find((r) => r.id === currentRoomId) || rooms[0] || null;
  }, [rooms, currentRoomId]);

  // Ref theo dõi cờ in-flight tránh gọi chồng chéo khi mạng trễ
  const inFlightRef = useRef(false);

  // Hàm tải dữ liệu hàng đợi từ backend
  const loadQueue = useCallback(
    async (roomId, options = {}) => {
      if (!roomId) return;
      if (inFlightRef.current && options.silent) return;
      const { signal, silent = false } = options;
      inFlightRef.current = true;
      if (!silent) {
        Promise.resolve().then(() => setQueueLoading(true));
      }
      try {
        const response = await technicianApi.getQueue(
          {
            roomId,
            search: debouncedSearch,
            status: statusFilter,
            doctorRoom: doctorRoomFilter,
          },
          signal
        );

        const items = Array.isArray(response?.items) ? response.items : [];
        const stats = response?.stats || { total: 0, waiting: 0, priority: 0, skipped: 0 };
        const canManage = typeof response?.canManage === 'boolean' ? response.canManage : false;

        setQueueData({ stats, items, canManage });

        // Tự động thu thập danh sách phòng khám gửi chỉ định vào bộ lọc
        if (items.length > 0) {
          setDoctorRoomsList((prev) => {
            const set = new Set(prev);
            items.forEach((item) => {
              if (item.doctorRoom) set.add(item.doctorRoom);
            });
            return Array.from(set);
          });
        }
      } catch (err) {
        if (err?.name === 'AbortError') return;
        if (!silent) {
          toast.error(err?.message || 'Không thể cập nhật danh sách hàng đợi.');
        }
      } finally {
        inFlightRef.current = false;
        if (!silent) setQueueLoading(false);
        setRefreshing(false);
      }
    },
    [debouncedSearch, statusFilter, doctorRoomFilter]
  );

  // Tải dữ liệu và đặt lịch tự động làm mới nền mỗi 10 giây (chỉ khi tab đang active)
  useEffect(() => {
    if (!currentRoomId) return;
    const controller = new AbortController();

    const firstTimer = window.setTimeout(() => {
      void loadQueue(currentRoomId, { signal: controller.signal });
    }, 0);

    const intervalTimer = window.setInterval(() => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        void loadQueue(currentRoomId, { signal: controller.signal, silent: true });
      }
    }, 10_000);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void loadQueue(currentRoomId, { silent: true });
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      controller.abort();
      window.clearTimeout(firstTimer);
      window.clearInterval(intervalTimer);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [currentRoomId, loadQueue]);

  // canManage: ưu tiên cờ backend, nếu chưa có thì lấy từ currentRoom.isAssigned
  const canManage = typeof queueData?.canManage === 'boolean'
    ? queueData.canManage
    : Boolean(currentRoom?.isAssigned);

  // Xử lý đổi phòng thủ công
  const handleRoomSelect = (roomId) => {
    setCurrentRoomId(roomId);
    const selectedRoom = rooms.find((r) => r.id === roomId);
    // Cập nhật ngay canManage theo phòng vừa chọn để UI phản hồi tức thì
    setQueueData((prev) => ({
      ...prev,
      canManage: selectedRoom ? selectedRoom.isAssigned : null,
    }));
    toast.info(`Đã chuyển phạm vi làm việc sang: ${selectedRoom?.code || ''} - ${selectedRoom?.name || ''}`);
  };

  const isInitialLoading = roomsLoading || !currentRoomId || queueData.stats === null;
  const stats = queueData.stats || { total: 0, waiting: 0, priority: 0, skipped: 0 };
  const orders = queueData.items || [];

  // Đặt lại bộ lọc
  const handleResetFilters = () => {
    setSearchQuery('');
    setDebouncedSearch('');
    setStatusFilter('ALL');
    setDoctorRoomFilter('ALL');
    toast.info('Đã đặt lại bộ lọc danh sách hàng đợi.');
  };

  // Làm mới thủ công
  const handleManualRefresh = () => {
    setRefreshing(true);
    loadQueue(currentRoomId);
  };

  // Thao tác: Mở Drawer xem chi tiết lâm sàng
  const handleOpenDetail = (order) => {
    setSelectedOrder(order);
    setIsDrawerOpen(true);
  };

  // Thao tác: Phát loa gọi số bệnh nhân
  const handleCallQueue = (order, e) => {
    e?.stopPropagation();
    playChimeSound();
    const numStr = order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber;
    const roomName = currentRoom?.name || 'phòng máy';
    toast.success(
      `🔔 Đang gọi số #${numStr}: Bệnh nhân ${order.patientName} vào ${roomName}!`,
      {
        duration: 4500,
        className: 'font-semibold text-xs',
      }
    );
  };

  // Thao tác: Tiếp nhận bệnh nhân vào phòng máy
  const handleStartOrder = async (order, e) => {
    e?.stopPropagation();
    try {
      setActionLoadingId(order.id);
      await technicianApi.acceptOrder(order.id);
      toast.success(`Đã tiếp nhận bệnh nhân ${order.patientName} vào phòng máy!`);
      if (selectedOrder?.id === order.id) {
        setIsDrawerOpen(false);
        setSelectedOrder(null);
      }
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(err?.message || 'Không thể tiếp nhận bệnh nhân.');
    } finally {
      setActionLoadingId(null);
    }
  };

  // Thao tác: Bỏ qua lượt bệnh nhân vắng mặt
  const handleSkipOrder = async (order, e) => {
    e?.stopPropagation();
    try {
      setActionLoadingId(order.id);
      await technicianApi.skipOrder(order.id);
      const numStr = order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber;
      toast.info(
        `Đã chuyển bệnh nhân ${order.patientName} (Số #${numStr}) sang trạng thái bỏ qua!`
      );
      if (selectedOrder?.id === order.id) {
        setSelectedOrder((prev) => (prev ? { ...prev, isSkipped: true, status: 'SKIPPED' } : null));
      }
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(err?.message || 'Không thể bỏ qua lượt bệnh nhân.');
    } finally {
      setActionLoadingId(null);
    }
  };

  // Thao tác: Gọi lại / Khôi phục bệnh nhân đã bỏ qua
  const handleUnskipOrder = async (order, e) => {
    e?.stopPropagation();
    try {
      setActionLoadingId(order.id);
      await technicianApi.unskipOrder(order.id);
      toast.success(`Đã khôi phục bệnh nhân ${order.patientName} về danh sách chờ gọi!`);
      if (selectedOrder?.id === order.id) {
        setSelectedOrder((prev) => (prev ? { ...prev, isSkipped: false, status: 'WAITING' } : null));
      }
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(err?.message || 'Không thể khôi phục lượt bệnh nhân.');
    } finally {
      setActionLoadingId(null);
    }
  };

  // UC-66: xác nhận đã thực hiện; openResult = true là AF-01 (nhập kết quả ngay, ví dụ điện tim).
  const openResult = (order) => navigate(`/technician/results/${order.id}`);
  const handleCompleteOrder = async (order, { openResultNow = false } = {}) => {
    try {
      setActionLoadingId(order.id);
      await technicianApi.completeOrder(order.id);
      if (selectedOrder?.id === order.id) {
        setIsDrawerOpen(false);
        setSelectedOrder(null);
      }
      if (openResultNow) {
        openResult(order);
        return;
      }
      toast.success(PERFORM_MESSAGES.performed, {
        description: `BN ${order.patientName} • ${order.serviceName}`,
        action: { label: 'Nhập kết quả', onClick: () => openResult(order) },
      });
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(performErrorMessage(err));
      await loadQueue(currentRoomId, { silent: true });
    } finally {
      setActionLoadingId(null);
    }
  };

  // UC-66 AF-02: trả chỉ định về hàng chờ (đúng số thứ tự cũ) kèm lý do.
  const handleConfirmReturn = async (order, reason) => {
    try {
      setActionLoadingId(order.id);
      await technicianApi.returnToWaiting(order.id, reason);
      setOrderToReturn(null);
      if (selectedOrder?.id === order.id) {
        setIsDrawerOpen(false);
        setSelectedOrder(null);
      }
      toast.info(PERFORM_MESSAGES.returned);
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(performErrorMessage(err));
    } finally {
      setActionLoadingId(null);
    }
  };

  // Thao tác: Mở modal từ chối
  const handleOpenRejectModal = (order) => {
    setOrderToReject(order);
    setRejectModalOpen(true);
  };

  // Thao tác: Xác nhận từ chối y lệnh kèm lý do
  const handleConfirmReject = async (orderId, reason) => {
    try {
      setActionLoadingId(orderId);
      await technicianApi.rejectOrder(orderId, reason);
      toast.error(`Đã từ chối y lệnh và hoàn trả về phòng khám bác sĩ.`);
      if (selectedOrder?.id === orderId) {
        setIsDrawerOpen(false);
        setSelectedOrder(null);
      }
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(err?.message || 'Không thể gửi lý do từ chối.');
    } finally {
      setActionLoadingId(null);
    }
  };

  if (isInitialLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-3">
        <Loader2 className="w-8 h-8 text-[#0d9488] animate-spin" />
        <p className="text-sm font-semibold text-slate-600">
          Đang nạp dữ liệu hàng đợi cận lâm sàng...
        </p>
      </div>
    );
  }

  if (rooms.length === 0) {
    return (
      <div className="p-8 text-center bg-white rounded-2xl border border-slate-200/80 shadow-2xs space-y-3">
        <Microscope className="w-10 h-10 text-slate-400 mx-auto" />
        <h3 className="text-base font-bold text-slate-800">
          Chưa có phòng máy cận lâm sàng nào hoạt động
        </h3>
        <p className="text-xs text-slate-500 max-w-md mx-auto">
          Hiện tại hệ thống chưa cấu hình hoặc kích hoạt phòng máy cận lâm sàng. Vui lòng liên hệ Quản trị viên để phân công phòng trực.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6 w-full pb-10">
      {/* 1. HEADER & BỘ CHỌN PHÒNG MÁY (ROOM SCOPE HEADER) */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs">
        <div className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-xl font-extrabold text-slate-900 tracking-tight">
              Danh sách chờ & Phân loại cận lâm sàng
            </h1>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80">
              Phòng máy: {currentRoom?.code || '---'}
            </span>
            {canManage ? (
              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200/90 shadow-2xs">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                Phòng bạn phụ trách
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-50 text-amber-800 border border-amber-200/90 shadow-2xs">
                <Eye className="w-3.5 h-3.5 text-amber-600" />
                Chế độ chỉ xem
              </span>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 font-medium">
            <span className="flex items-center gap-1.5 text-slate-800 font-bold">
              <Building2 className="w-4 h-4 text-[#0d9488]" />
              <span>{currentRoom?.name || 'Phòng máy'}</span>
            </span>
            <span>•</span>
            <span className="flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-slate-400" />
              <span>{currentRoom?.location || 'Đang cập nhật'}</span>
            </span>
            <span>•</span>
            <span className="text-slate-600">
              Phụ trách: <strong>{currentRoom?.activeTech || 'Chưa phân công'}</strong>
            </span>
          </div>
        </div>

        {/* Bộ chọn/Chuyển đổi phòng (Room Selector Dropdown) */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="text-right hidden sm:block">
            <span className="block text-[11px] font-semibold text-slate-400">
              Chuyển đổi phòng máy:
            </span>
            <span className="block text-xs font-bold text-slate-700">
              {currentRoom?.code} - {currentRoom?.name}
            </span>
          </div>

          <div className="relative">
            <select
              value={currentRoomId}
              onChange={(e) => handleRoomSelect(e.target.value)}
              className="h-11 px-4 pr-10 bg-teal-50/70 border border-teal-200/90 rounded-xl text-xs font-bold text-[#0d9488] focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer shadow-2xs hover:bg-teal-50 transition-all appearance-none"
            >
              {rooms.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.code} - {room.name} {room.isAssigned ? '✓ (Phụ trách)' : ''}
                </option>
              ))}
            </select>
            <ChevronDown className="w-4 h-4 text-[#0d9488] absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>

          <button
            type="button"
            onClick={handleManualRefresh}
            disabled={refreshing || queueLoading}
            className="h-11 w-11 flex items-center justify-center rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 hover:text-slate-900 shadow-2xs transition-all cursor-pointer disabled:opacity-50"
            title="Làm mới hàng đợi"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing || queueLoading ? 'animate-spin text-[#0d9488]' : ''}`} />
          </button>
        </div>
      </div>

      {/* Thông báo chế độ chỉ xem nếu không phụ trách phòng hiện tại */}
      {!canManage && (
        <div className="flex items-center gap-2.5 px-4 py-3 bg-amber-50/90 border border-amber-200/90 rounded-xl text-xs text-amber-900 font-medium shadow-2xs">
          <Eye className="w-4 h-4 text-amber-600 shrink-0" />
          <span>
            <strong>Chế độ chỉ xem:</strong> Bạn không được phân công quản lý phòng máy này trong ca trực. Bạn có thể tra cứu và xem hồ sơ cận lâm sàng nhưng không thể thao tác tiếp nhận, gọi số, hoàn tất hay từ chối y lệnh.
          </span>
        </div>
      )}

      {/* 2. 4 THẺ THỐNG KÊ THEO PHÒNG HIỆN TẠI (REUSING METRICCARD WITH ADMIN PALETTE) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title="Tổng chỉ định"
          value={stats.total}
          icon={ListOrdered}
          iconBg="bg-teal-50 text-[#0d9488]"
          valueColor="text-slate-900"
          subtitle={`Tổng số ca gửi đến ${currentRoom?.code || ''} trong ca trực`}
          dotColor="bg-teal-500"
          footerLabel="Phạm vi"
          footerValue={currentRoom?.code || '---'}
          footerValueColor="text-teal-700"
        />

        <MetricCard
          title="Chờ tiếp nhận"
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
          title="Đã bỏ qua"
          value={stats.skipped}
          icon={SkipForward}
          iconBg="bg-slate-100 text-slate-700"
          valueColor="text-slate-800"
          subtitle="Số ca tạm thời bỏ qua do chưa có mặt"
          dotColor="bg-slate-400"
          footerLabel="Trạng thái"
          footerValue="Vắng mặt"
          footerValueColor="text-slate-600"
        />
      </div>

      {/* 3. TOOLBAR LỌC & TÌM KIẾM */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-2xs flex flex-col md:flex-row items-center justify-between gap-3">
        {/* Input Tìm kiếm */}
        <div className="relative w-full md:flex-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm kiếm theo Mã bệnh nhân (Patient ID), Mã phiếu, Họ tên, số CCCD hoặc BHYT..."
            className="w-full h-10 pl-10 pr-4 bg-slate-50/70 border border-slate-200/80 rounded-xl text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0d9488] focus:bg-white transition-all"
          />
        </div>

        {/* Dropdowns lọc */}
        <div className="flex flex-wrap items-center gap-2.5 w-full md:w-auto">
          {/* Bộ lọc trạng thái */}
          <div className="relative min-w-[145px]">
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="w-full h-10 pl-3 pr-8 bg-white border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#0d9488] cursor-pointer appearance-none shadow-2xs"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="WAITING">Chờ tiếp nhận</option>
              <option value="SKIPPED">Đã bỏ qua (Vắng)</option>
              <option value="IN_PROGRESS">Đang thực hiện</option>
              <option value="CANCELLED">Đã từ chối</option>
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>

          {/* Bộ lọc phòng khám chỉ định */}
          <div className="relative min-w-[170px]">
            <select
              value={doctorRoomFilter}
              onChange={(e) => setDoctorRoomFilter(e.target.value)}
              className="w-full h-10 pl-3 pr-8 bg-white border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#0d9488] cursor-pointer appearance-none shadow-2xs"
            >
              <option value="ALL">Tất cả phòng gửi chỉ định</option>
              {doctorRoomsList.map((roomName) => (
                <option key={roomName} value={roomName}>
                  {roomName}
                </option>
              ))}
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>

          {/* Nút Đặt lại bộ lọc */}
          <button
            type="button"
            onClick={handleResetFilters}
            className="h-10 px-3.5 inline-flex items-center gap-1.5 text-xs font-bold text-slate-600 hover:text-slate-900 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-xl transition-all cursor-pointer shadow-2xs"
            title="Xóa nhanh tất cả bộ lọc"
          >
            <RotateCcw className="w-3.5 h-3.5 text-slate-500" />
            <span>Đặt lại</span>
          </button>
        </div>
      </div>

      {/* 4. BẢNG DANH SÁCH CHỜ CẬN LÂM SÀNG */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50/80 border-b border-slate-200/80 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                <th className="py-3 px-3.5 whitespace-nowrap">STT / Mã phiếu</th>
                <th className="py-3 px-2.5 whitespace-nowrap text-center">Ưu tiên</th>
                <th className="py-3 px-3 whitespace-nowrap">Bệnh nhân</th>
                <th className="py-3 px-2.5 whitespace-nowrap">Mã BN</th>
                <th className="py-3 px-2.5 whitespace-nowrap text-center">Đối tượng</th>
                <th className="py-3 px-3 min-w-[200px]">Dịch vụ</th>
                <th className="py-3 px-3 whitespace-nowrap">Bác sĩ & Phòng</th>
                <th className="py-3 px-2.5 whitespace-nowrap text-center">Thời gian</th>
                <th className="py-3 px-2.5 whitespace-nowrap text-center">Trạng thái</th>
                <th className="py-3 px-3.5 whitespace-nowrap text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {queueLoading && orders.length === 0 ? (
                <tr>
                  <td colSpan={10} className="py-12 text-center text-slate-400">
                    <Loader2 className="w-8 h-8 text-[#0d9488] animate-spin mx-auto mb-2" />
                    <p className="text-xs font-semibold">Đang cập nhật danh sách...</p>
                  </td>
                </tr>
              ) : orders.map((order) => {
                const isPriority = order.priority === 'PRIORITY';
                const isWaiting = order.status === 'WAITING';
                const isSkipped = order.isSkipped || order.status === 'SKIPPED';
                const isInProgress = order.status === 'IN_PROGRESS';
                const isCompleted = order.status === 'COMPLETED';
                const isCancelled = order.status === 'CANCELLED';
                const isRowBusy = actionLoadingId === order.id;

                return (
                  <tr
                    key={order.id}
                    onClick={() => handleOpenDetail(order)}
                    className={`hover:bg-slate-50/80 transition-colors cursor-pointer group ${
                      isSkipped ? 'bg-slate-50/40 text-slate-400' : ''
                    } ${isRowBusy ? 'opacity-50 pointer-events-none' : ''}`}
                  >
                    {/* STT & Mã phiếu */}
                    <td className="py-3 px-3.5 whitespace-nowrap">
                      <div className="flex items-center gap-1.5">
                        <span className="font-extrabold text-slate-900 text-xs tracking-tight">
                          #{order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber}
                        </span>
                      </div>
                      <span className="text-[10px] text-slate-400 block font-mono">
                        {order.orderCode}
                      </span>
                    </td>

                    {/* Mức ưu tiên */}
                    <td className="py-3 px-2.5 whitespace-nowrap text-center">
                      {isPriority ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
                          Ưu tiên
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-medium bg-slate-100 text-slate-600">
                          <span className="w-1.5 h-1.5 rounded-full bg-teal-500" />
                          Thường
                        </span>
                      )}
                    </td>

                    {/* Bệnh nhân (Họ tên, tuổi, giới tính) */}
                    <td className="py-3 px-3 whitespace-nowrap">
                      <div className="font-bold text-slate-900 group-hover:text-[#0d9488] transition-colors">
                        {order.patientName}
                      </div>
                      <div className="text-[11px] text-slate-400">
                        {order.age ? `${order.age}t` : ''} • {order.gender || '---'}
                      </div>
                    </td>

                    {/* Mã bệnh nhân (Patient ID) */}
                    <td className="py-3 px-2.5 whitespace-nowrap font-mono text-slate-600 font-semibold text-[11px]">
                      {order.patientId}
                    </td>

                    {/* Đối tượng BHYT / Viện phí */}
                    <td className="py-3 px-2.5 whitespace-nowrap text-center">
                      {order.insuranceType === 'BHYT' ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10.5px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200/80">
                          <ShieldCheck className="w-3 h-3 text-emerald-600 shrink-0" />
                          BHYT
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10.5px] font-semibold bg-slate-100 text-slate-700 border border-slate-200/70">
                          Viện phí
                        </span>
                      )}
                    </td>

                    {/* Dịch vụ thực hiện */}
                    <td className="py-3 px-3">
                      <div className="font-bold text-slate-900 line-clamp-1">
                        {order.serviceName}
                      </div>
                      <div className="text-[11px] text-slate-400 line-clamp-1 italic">
                        {order.clinicalDiagnosis || order.chiefComplaint || 'Không có ghi chú lâm sàng'}
                      </div>
                    </td>

                    {/* Bác sĩ & Phòng chỉ định */}
                    <td className="py-3 px-3 whitespace-nowrap">
                      <div className="font-semibold text-slate-800">
                        {order.doctorName || 'Chưa ghi nhận'}
                      </div>
                      <div className="text-[11px] text-slate-400">
                        {order.doctorRoom || 'Phòng khám'}
                      </div>
                    </td>

                    {/* Thời gian gửi chỉ định */}
                    <td className="py-3 px-2.5 whitespace-nowrap text-center text-slate-600 font-medium">
                      <div className="flex items-center justify-center gap-1 text-[11px]">
                        <Clock className="w-3 h-3 text-slate-400" />
                        <span>{order.orderTime || '--:--'}</span>
                      </div>
                      <span className="text-[10px] text-slate-400 block font-mono">
                        {order.orderCode}
                      </span>
                    </td>

                    {/* Trạng thái tiếp nhận */}
                    <td className="py-3 px-2.5 whitespace-nowrap text-center">
                      {isSkipped && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                          Đã bỏ qua
                        </span>
                      )}
                      {isWaiting && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-sky-50 text-sky-700 border border-sky-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-sky-500" />
                          Chờ tiếp nhận
                        </span>
                      )}
                      {isInProgress && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
                          Đang thực hiện
                        </span>
                      )}
                      {isCompleted && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                          Đã hoàn thành
                        </span>
                      )}
                      {isCancelled && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-rose-50 text-rose-700 border border-rose-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                          Đã từ chối
                        </span>
                      )}
                    </td>

                    {/* Thao tác */}
                    <td className="py-3 px-3.5 whitespace-nowrap text-right" onClick={(e) => e.stopPropagation()}>
                      {!canManage ? (
                        <button
                          type="button"
                          onClick={() => handleOpenDetail(order)}
                          className="h-7 inline-flex items-center gap-1.5 px-2.5 text-[10.5px] font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 border border-slate-200 rounded-lg shadow-2xs transition-all cursor-pointer shrink-0"
                          title="Xem chi tiết lâm sàng & y lệnh"
                        >
                          <Eye className="w-3 h-3 text-slate-500 shrink-0" />
                          <span>Chi tiết</span>
                        </button>
                      ) : (
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Nút Gọi số / Gọi lại */}
                          <button
                            type="button"
                            onClick={(e) => handleCallQueue(order, e)}
                            className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200/80 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0"
                            title={isSkipped ? 'Phát loa gọi lại bệnh nhân vào phòng' : 'Phát loa gọi số bệnh nhân vào phòng'}
                          >
                            <Megaphone className="w-3 h-3 text-amber-600 shrink-0" />
                            <span>{isSkipped ? 'Gọi lại' : 'Gọi số'}</span>
                          </button>

                          {/* Nút Tiếp nhận (nếu WAITING) */}
                          {isWaiting && (
                            <button
                              type="button"
                              onClick={(e) => handleStartOrder(order, e)}
                              className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0"
                              title="Tiếp nhận bệnh nhân"
                            >
                              <Play className="w-3 h-3 fill-current shrink-0" />
                              <span>Tiếp nhận</span>
                            </button>
                          )}

                          {/* Nút Bỏ qua (chỉ hiển thị khi WAITING) */}
                          {isWaiting && (
                            <button
                              type="button"
                              onClick={(e) => handleSkipOrder(order, e)}
                              className="h-7 inline-flex items-center gap-1 px-1.5 text-[10.5px] font-bold text-slate-700 bg-slate-100 hover:bg-slate-200 border border-slate-200/90 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0"
                              title="Bỏ qua lượt bệnh nhân vắng mặt"
                            >
                              <SkipForward className="w-3 h-3 text-slate-500 shrink-0" />
                              <span>Bỏ qua</span>
                            </button>
                          )}

                          {/* Nút Tiếp nhận lại (khi SKIPPED) */}
                          {isSkipped && (
                            <button
                              type="button"
                              onClick={(e) => handleUnskipOrder(order, e)}
                              className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-teal-800 bg-teal-50 hover:bg-teal-100 border border-teal-200/80 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0"
                              title="Khôi phục bệnh nhân về hàng đợi"
                            >
                              <Play className="w-3 h-3 fill-current shrink-0" />
                              <span>Nhận lại</span>
                            </button>
                          )}

                          {/* UC-66: Đã thực hiện / Nhập KQ ngay (AF-01) / Trả về hàng chờ (AF-02) khi IN_PROGRESS */}
                          {isInProgress && (
                            <>
                              <button
                                type="button"
                                disabled={actionLoadingId === order.id}
                                onClick={(e) => { e.stopPropagation(); handleCompleteOrder(order); }}
                                className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0 disabled:opacity-50"
                                title="Xác nhận đã thực hiện xong dịch vụ"
                              >
                                <span>Đã thực hiện</span>
                              </button>
                              <button
                                type="button"
                                disabled={actionLoadingId === order.id}
                                onClick={(e) => { e.stopPropagation(); handleCompleteOrder(order, { openResultNow: true }); }}
                                className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0 disabled:opacity-50"
                                title="Xác nhận đã thực hiện và nhập kết quả ngay"
                              >
                                <span>Nhập KQ</span>
                              </button>
                              <button
                                type="button"
                                disabled={actionLoadingId === order.id}
                                onClick={(e) => { e.stopPropagation(); setOrderToReturn(order); }}
                                className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0 disabled:opacity-50"
                                title="Không thực hiện được: trả về hàng chờ kèm lý do"
                              >
                                <RotateCcw className="w-3 h-3 shrink-0" />
                                <span>Trả về</span>
                              </button>
                            </>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}

              {!queueLoading && orders.length === 0 && (
                <tr>
                  <td colSpan={10} className="py-12 text-center text-slate-400 space-y-2">
                    <Microscope className="w-8 h-8 text-slate-300 mx-auto" />
                    <p className="text-sm font-semibold">
                      Không có chỉ định nào phù hợp với bộ lọc tại {currentRoom?.name}.
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

        {/* Footer thống kê số lượng đang hiển thị */}
        <div className="px-5 py-3.5 bg-slate-50/80 border-t border-slate-200/80 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-slate-500 font-medium">
          <div className="flex items-center gap-2">
            <span>
              Đang hiển thị: <strong className="text-slate-900">{orders.length}</strong> / {stats.total} ca cận lâm sàng
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

      {/* 5. NGĂN KÉO CHI TIẾT BỆNH NHÂN & Y LỆNH (PATIENT & CLINICAL DRAWER) */}
      <PatientClinicalDrawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        order={selectedOrder}
        canManage={canManage}
        onStartOrder={(order) => handleStartOrder(order)}
        onSkipOrder={(order) => handleSkipOrder(order)}
        onCompleteOrder={(order) => handleCompleteOrder(order)}
        onOpenRejectModal={(order) => handleOpenRejectModal(order)}
      />

      {/* UC-66 AF-02: TRẢ VỀ HÀNG CHỜ */}
      <ReturnToWaitingDialog
        key={orderToReturn?.id ?? 'none'}
        order={orderToReturn}
        onClose={() => setOrderToReturn(null)}
        onConfirm={handleConfirmReturn}
      />

      {/* 6. MODAL TỪ CHỐI / HỦY Y LỆNH */}
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
