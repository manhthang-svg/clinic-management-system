import { useState, useMemo, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router';
import {
  Search,
  RotateCcw,
  Building2,
  ChevronDown,
  ListOrdered,
  Clock,
  FileEdit,
  AlertTriangle,
  Printer,
  Plus,
  MapPin,
  FileText,
  PrinterCheck,
  X,
  Eye,
  RefreshCw,
  Loader2,
} from 'lucide-react';
import { toast } from 'sonner';

import { MetricCard } from '../../components/shared/MetricCard';
import { technicianApi } from '../../apis/technicianApi';

/**
 * Màn hình Nhập kết quả & Quản lý kết luận CLS (ParaclinicalResultListPage)
 * Dữ liệu 100% kết nối backend qua technicianApi, không dùng mock data:
 * - Sub-Header thông tin phòng máy & bộ chuyển phòng phụ trách (tải từ backend)
 * - 4 thẻ thống kê tổng quan (Summary Metric Cards: Tổng chờ nhập, Chưa nhập, Đang lưu nháp, Quá hạn)
 * - Thanh tìm kiếm và bộ lọc nhanh (Filter Bar: Tìm kiếm, Trạng thái, Phòng gửi, Đặt lại, Làm mới)
 * - Bảng danh sách chờ kết quả 9 cột chuẩn đặc tả
 * - Hiển thị trạng thái rỗng ("chưa có") khi chưa có ca cận lâm sàng nào
 */
export function ParaclinicalResultListPage() {
  const navigate = useNavigate();

  // 1. Quản lý danh sách phòng máy cận lâm sàng từ backend (tối ưu hóa: đọc cache phòng & activeRoom để load song song)
  const [rooms, setRooms] = useState(() => (typeof technicianApi.getCachedRooms === 'function' ? technicianApi.getCachedRooms() : []) || []);
  const [roomsLoading, setRoomsLoading] = useState(() => !(technicianApi.getCachedRooms?.()?.length));
  const [currentRoomId, setCurrentRoomId] = useState(() => (typeof technicianApi.getActiveRoomId === 'function' ? technicianApi.getActiveRoomId() : null));

  // 2. Nguồn dữ liệu ca chờ nhập & bản nháp CLS từ backend
  const [resultsData, setResultsData] = useState({
    stats: null,
    items: [],
    canManage: null,
  });
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // 3. Trạng thái tìm kiếm & bộ lọc
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL' | 'PENDING' | 'DRAFT'
  const [doctorRoomFilter, setDoctorRoomFilter] = useState('ALL');
  const [doctorRoomsList, setDoctorRoomsList] = useState([]);

  // 4. Modal in phiếu chỉ định nhanh
  const [printModalOrder, setPrintModalOrder] = useState(null);

  // Debounce ô tìm kiếm 300ms
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchQuery);
    }, 300);
    return () => clearTimeout(handler);
  }, [searchQuery]);

  // Tải danh sách phòng máy khi khởi tạo trang
  useEffect(() => {
    let mounted = true;
    const fetchRooms = async () => {
      try {
        if (!technicianApi.getCachedRooms?.()?.length) {
          setRoomsLoading(true);
        }
        const roomList = await technicianApi.getRooms();
        if (mounted) {
          const validRooms = Array.isArray(roomList) ? roomList : [];
          setRooms(validRooms);
          if (validRooms.length > 0) {
            const assignedRoom = validRooms.find((r) => r.hasOwnWork) || validRooms.find((r) => r.isAssigned);
            const defaultRoom = assignedRoom || validRooms[0];
            setCurrentRoomId((prev) => {
              const selected = (prev && validRooms.some((r) => r.id === prev)) ? prev : defaultRoom.id;
              technicianApi.setActiveRoomId?.(selected);
              return selected;
            });
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

  // Chi tiết phòng máy hiện tại
  const currentRoom = useMemo(() => {
    return rooms.find((r) => r.id === currentRoomId) || rooms[0] || null;
  }, [rooms, currentRoomId]);

  // Quyền thao tác tại phòng hiện tại
  const canManage =
    typeof resultsData?.canManage === 'boolean'
      ? resultsData.canManage
      : Boolean(currentRoom?.isAssigned);

  // Ref theo dõi cờ in-flight tránh gọi request trùng lặp
  const inFlightRef = useRef(false);

  // Hàm tải dữ liệu danh sách ca chờ nhập kết quả từ backend
  const loadResults = useCallback(
    async (roomId, options = {}) => {
      if (!roomId) return;
      if (inFlightRef.current && options.silent) return;
      const { signal, silent = false } = options;
      inFlightRef.current = true;
      if (!silent) {
        Promise.resolve().then(() => setLoading(true));
      }
      try {
        const response = await technicianApi.getPendingResults(
          {
            roomId,
            search: debouncedSearch,
            status: statusFilter,
            doctorRoom: doctorRoomFilter,
          },
          signal
        );

        const items = Array.isArray(response?.items) ? response.items : [];
        const stats = response?.stats || { total: 0, pending: 0, draft: 0, overdue: 0 };
        const canManageFlag =
          typeof response?.canManage === 'boolean' ? response.canManage : false;

        setResultsData({ stats, items, canManage: canManageFlag });

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
        if (err?.name === 'AbortError' || err?.code === 'ERR_CANCELED') return;
        toast.error(err?.message || 'Không thể tải danh sách kết quả cận lâm sàng.');
      } finally {
        inFlightRef.current = false;
        if (!silent) setLoading(false);
        setRefreshing(false);
      }
    },
    [debouncedSearch, statusFilter, doctorRoomFilter]
  );

  // Kích hoạt tải dữ liệu khi phòng hoặc bộ lọc thay đổi
  useEffect(() => {
    if (!currentRoomId) return;
    const controller = new AbortController();

    const firstTimer = window.setTimeout(() => {
      void loadResults(currentRoomId, { signal: controller.signal });
    }, 0);

    return () => {
      controller.abort();
      window.clearTimeout(firstTimer);
    };
  }, [currentRoomId, loadResults]);

  // Xử lý chuyển phòng thủ công
  const handleRoomSelect = (roomId) => {
    setCurrentRoomId(roomId);
    technicianApi.setActiveRoomId?.(roomId);
    const selectedRoom = rooms.find((r) => r.id === roomId);
    setResultsData((prev) => ({
      ...prev,
      canManage: selectedRoom ? selectedRoom.isAssigned : null,
    }));
    if (selectedRoom) {
      toast.info(`Đã chuyển phạm vi làm việc sang: ${selectedRoom.code} – ${selectedRoom.name}`);
    }
  };

  // Đặt lại bộ lọc về mặc định
  const handleResetFilters = () => {
    setSearchQuery('');
    setDebouncedSearch('');
    setStatusFilter('ALL');
    setDoctorRoomFilter('ALL');
    toast.info('Đã đặt lại toàn bộ bộ lọc danh sách kết quả.');
  };

  // Làm mới thủ công
  const handleRefresh = () => {
    if (!currentRoomId) return;
    setRefreshing(true);
    loadResults(currentRoomId, { silent: false });
  };

  // Điều hướng đến màn hình nhập kết quả UC-67 (mở thẳng form, chuyển dịch vụ bằng Tab trên form)
  const handleOpenResultForm = (order) => {
    const ownServices = order.services.filter((service) => service.isPerformer);
    const targetService = ownServices.find((service) => service.status === 'DRAFT') || ownServices[0];
    if (!targetService) return;
    navigate(`/technician/results/${targetService.id}`, {
      state: {
        siblingServices: ownServices,
        patientId: order.patientId,
        baseOrderCode: order.baseOrderCode || order.orderCode,
      },
    });
  };

  // Thao tác in phiếu chỉ định
  const handlePrintOrder = (order, e) => {
    e?.stopPropagation();
    setPrintModalOrder(order);
  };

  const handleConfirmPrint = () => {
    if (printModalOrder) {
      toast.success(
        `Đã gửi lệnh in phiếu chỉ định ${printModalOrder.orderCode} cho BN ${printModalOrder.patientName}!`,
        { duration: 3500 }
      );
      setPrintModalOrder(null);
    }
  };

  const stats = resultsData.stats || { total: 0, pending: 0, draft: 0, overdue: 0 };
  const items = useMemo(() => resultsData.items || [], [resultsData.items]);
  const hasOwnResults = items.some((item) => item.isPerformer);

  // 5. Gom nhóm danh sách chờ kết quả: Mỗi bệnh nhân / đợt chỉ định chỉ hiển thị 1 dòng và 1 STT duy nhất
  const groupedResults = useMemo(() => {
    if (!items.length) return [];

    const groups = new Map();

    for (const item of items) {
      const baseOrderCode = item.orderCode?.replace(/-\d+$/, '') || item.orderCode || '';
      const groupKey = `${item.patientId || ''}_${baseOrderCode || item.id}`;

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
          existing.priorityLabel = item.priorityLabel || 'Ưu tiên';
        }
        if (existing.queueNumber == null && item.queueNumber != null) {
          existing.queueNumber = item.queueNumber;
        } else if (item.queueNumber != null && existing.queueNumber != null) {
          // Lấy STT nhỏ nhất của bệnh nhân được cấp khi check-in
          existing.queueNumber = Math.min(existing.queueNumber, item.queueNumber);
        }
      }
    }

    const list = Array.from(groups.values()).map((group) => {
      const svcs = group.services;
      const draftCount = svcs.filter((s) => s.status === 'DRAFT').length;
      const totalCount = svcs.length;
      const isAllDraft = draftCount === totalCount;
      const hasDraft = draftCount > 0;
      const anyOverdue = svcs.some((s) => (s.completedMinutesAgo ?? 0) >= 30);
      const completedAtTime = group.completedAtTime || svcs[0]?.completedAtTime || '';

      return {
        ...group,
        draftCount,
        totalCount,
        isAllDraft,
        hasDraft,
        anyOverdue,
        completedAtTime,
      };
    });

    // Sắp xếp: Ưu tiên trước, STT thấp lên trước (tăng dần 01, 04, 05...), sau đó theo thời gian hoàn thành
    list.sort((a, b) => {
      if (a.priority === 'PRIORITY' && b.priority !== 'PRIORITY') return -1;
      if (a.priority !== 'PRIORITY' && b.priority === 'PRIORITY') return 1;

      const numA = a.queueNumber != null ? Number(a.queueNumber) : 999999;
      const numB = b.queueNumber != null ? Number(b.queueNumber) : 999999;
      if (numA !== numB) return numA - numB;

      return (b.completedMinutesAgo ?? 0) - (a.completedMinutesAgo ?? 0);
    });

    return list;
  }, [items]);

  // Thống kê đồng bộ theo số bệnh nhân (và tổng số dịch vụ)
  const groupedStats = useMemo(() => {
    const totalPatients = groupedResults.length;
    const pendingPatients = groupedResults.filter((g) => !g.isAllDraft).length;
    const draftPatients = groupedResults.filter((g) => g.hasDraft).length;
    const overduePatients = groupedResults.filter((g) => g.anyOverdue).length;
    return {
      total: totalPatients,
      pending: pendingPatients,
      draft: draftPatients,
      overdue: overduePatients,
    };
  }, [groupedResults]);

  return (
    <div className="space-y-6 w-full pb-10">
      {/* 2. THANH THÔNG TIN PHÒNG MÁY (SUB-HEADER) */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs">
        {/* Tiêu đề phân hệ & Thông tin phòng máy */}
        <div className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-xl font-extrabold text-slate-900 tracking-tight">
              Nhập kết quả & Quản lý kết luận CLS
            </h1>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80">
              Phòng máy: {currentRoom ? currentRoom.code : '...'}
            </span>
            {!canManage && !roomsLoading && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 bg-amber-50 text-amber-700 border border-amber-200/80 rounded-full text-xs font-semibold">
                <Eye className="w-3.5 h-3.5 text-amber-600" />
                Chế độ chỉ xem
              </span>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 font-medium">
            <span className="flex items-center gap-1.5 text-slate-800 font-bold">
              <Building2 className="w-4 h-4 text-[#0d9488]" />
              <span>{currentRoom ? currentRoom.name : roomsLoading ? 'Đang tải...' : 'Chưa chọn phòng'}</span>
            </span>
            <span>•</span>
            <span className="flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-slate-400" />
              <span>{currentRoom ? currentRoom.location : '...'}</span>
            </span>
            <span>•</span>
            <span className="text-slate-600">
              Phụ trách: <strong>{currentRoom?.activeTech || 'Chưa có'}</strong>
            </span>
          </div>
        </div>

        {/* Dropdown chuyển đổi phòng phụ trách */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="text-right hidden sm:block">
            <span className="block text-[11px] font-semibold text-slate-400">
              Chuyển đổi phòng phụ trách:
            </span>
            <span className="block text-xs font-bold text-slate-700">
              {currentRoom ? `${currentRoom.code} – ${currentRoom.name}` : '...'}
            </span>
          </div>

          <div className="relative">
            <select
              value={currentRoomId || ''}
              onChange={(e) => handleRoomSelect(e.target.value)}
              disabled={roomsLoading || rooms.length === 0}
              className="h-11 px-4 pr-10 bg-teal-50/70 border border-teal-200/90 rounded-xl text-xs font-bold text-[#0d9488] focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer shadow-2xs hover:bg-teal-50 transition-all appearance-none disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {rooms.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.code} – {room.name} {room.isAssigned ? '(Đang phụ trách)' : ''}
                </option>
              ))}
            </select>
            <ChevronDown className="w-4 h-4 text-[#0d9488] absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </div>
      </div>

      {/* 3. KHUNG THẺ THỐNG KÊ NHANH (SUMMARY METRIC CARDS) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Thẻ 1 – TỔNG CHỜ NHẬP */}
        <MetricCard
          title="TỔNG CHỜ NHẬP"
          value={groupedStats.total}
          icon={ListOrdered}
          iconBg="bg-teal-50 text-[#0d9488]"
          valueColor="text-slate-900"
          subtitle={`Gồm ${items.length} dịch vụ đã làm`}
          dotColor="bg-teal-500"
          footerLabel="Phạm vi"
          footerValue={currentRoom ? currentRoom.code : '---'}
          footerValueColor="text-teal-700"
        />

        {/* Thẻ 2 – CHƯA NHẬP */}
        <MetricCard
          title="CHƯA NHẬP"
          value={groupedStats.pending}
          icon={Clock}
          iconBg="bg-blue-50 text-blue-600"
          valueColor="text-blue-700"
          subtitle="Ca mới chụp/siêu âm xong"
          dotColor="bg-blue-500"
          footerLabel="Tiến độ"
          footerValue={
            <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">
              Chờ xử lý
            </span>
          }
        />

        {/* Thẻ 3 – ĐANG LƯU NHÁP */}
        <MetricCard
          title="ĐANG LƯU NHÁP"
          value={groupedStats.draft}
          icon={FileEdit}
          iconBg="bg-slate-100 text-slate-700"
          valueColor="text-slate-800"
          subtitle="Đã có thông tin nhưng chưa nộp"
          dotColor="bg-slate-400"
          footerLabel="Tiến độ"
          footerValue={
            <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
              Đang soạn
            </span>
          }
        />

        {/* Thẻ 4 – QUÁ HẠN CHỜ */}
        <MetricCard
          title="QUÁ HẠN CHỜ"
          value={groupedStats.overdue}
          icon={AlertTriangle}
          iconBg="bg-rose-50 text-rose-600"
          valueColor="text-rose-700"
          subtitle="Ca chờ quá 30 phút"
          dotColor="bg-rose-500"
          footerLabel="Cảnh báo"
          footerValue={
            <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200 animate-pulse">
              Cần làm gấp
            </span>
          }
        />
      </div>

      {/* 4. THANH CÔNG CỤ TÌM KIẾM VÀ BỘ LỌC (FILTER BAR) */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-2xs">
        <div className="flex flex-col lg:flex-row items-stretch lg:items-center gap-3">
          {/* 4.1. Ô tìm kiếm chính */}
          <div className="relative flex-1 lg:max-w-2xl">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm kiếm theo Mã bệnh nhân, Mã phiếu, Họ tên, số CCCD hoặc BHYT..."
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

          {/* 4.2. Dropdown lọc trạng thái */}
          <div className="relative min-w-[170px]">
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="w-full h-10 px-3 pr-8 bg-slate-50 border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer appearance-none"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="PENDING">Chờ nhập kết quả</option>
              <option value="DRAFT">Đang lưu nháp</option>
            </select>
            <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>

          {/* 4.3. Dropdown lọc phòng gửi chỉ định */}
          <div className="relative min-w-[210px]">
            <select
              value={doctorRoomFilter}
              onChange={(e) => setDoctorRoomFilter(e.target.value)}
              className="w-full h-10 px-3 pr-8 bg-slate-50 border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer appearance-none"
            >
              <option value="ALL">Tất cả phòng gửi chỉ định</option>
              {doctorRoomsList.map((room) => (
                <option key={room} value={room}>
                  {room}
                </option>
              ))}
            </select>
            <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>

          {/* 4.4. Nút Làm mới (Refresh) */}
          <button
            type="button"
            onClick={handleRefresh}
            disabled={loading || refreshing}
            className="h-10 px-3.5 inline-flex items-center justify-center gap-1.5 border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-bold rounded-xl transition-all cursor-pointer shadow-2xs shrink-0 disabled:opacity-50"
            title="Làm mới danh sách kết quả"
          >
            <RefreshCw className={`w-3.5 h-3.5 text-slate-500 ${refreshing ? 'animate-spin' : ''}`} />
            <span className="hidden sm:inline">Làm mới</span>
          </button>

          {/* 4.5. Nút Đặt lại (Reset) */}
          <button
            type="button"
            onClick={handleResetFilters}
            className="h-10 px-4 inline-flex items-center justify-center gap-1.5 border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-bold rounded-xl transition-all cursor-pointer shadow-2xs shrink-0"
            title="Đặt lại toàn bộ bộ lọc"
          >
            <RotateCcw className="w-3.5 h-3.5 text-slate-500" />
            <span>Đặt lại</span>
          </button>
        </div>
      </div>

      {/* 5. BẢNG DANH SÁCH CHỜ KẾT QUẢ (PENDING RESULT TABLE) */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden w-full">
        <div className="overflow-x-auto w-full">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="bg-slate-50/90 text-[11px] font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200">
                <th className="py-2.5 px-2 text-left whitespace-nowrap">STT / Mã phiếu</th>
                <th className="py-2.5 px-1.5 text-center whitespace-nowrap">Ưu tiên</th>
                <th className="py-2.5 px-2 text-left whitespace-nowrap">Bệnh nhân</th>
                <th className="py-2.5 px-1.5 text-center whitespace-nowrap">Mã BN</th>
                <th className="py-2.5 px-1.5 text-center whitespace-nowrap">Đối tượng</th>
                <th className="py-2.5 px-2 text-left min-w-[140px] max-w-[200px]">Dịch vụ đã làm</th>
                <th className="py-2.5 px-1.5 text-center whitespace-nowrap">Thời gian</th>
                <th className="py-2.5 px-1.5 text-center whitespace-nowrap">Trạng thái</th>
                <th className="py-2.5 px-2 text-center whitespace-nowrap">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading && items.length === 0 ? (
                <tr>
                  <td colSpan={9} className="py-12 text-center text-slate-400">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <Loader2 className="w-7 h-7 text-teal-600 animate-spin" />
                      <p className="text-sm font-semibold text-slate-600">
                        Đang tải danh sách kết quả từ hệ thống...
                      </p>
                    </div>
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={9} className="py-12 text-center text-slate-400">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <FileText className="w-8 h-8 text-slate-300" />
                      <p className="text-sm font-semibold text-slate-600">
                        Chưa có ca cận lâm sàng nào cần nhập kết quả.
                      </p>
                      <p className="text-xs text-slate-400">
                        {searchQuery || statusFilter !== 'ALL' || doctorRoomFilter !== 'ALL'
                          ? 'Không tìm thấy ca phù hợp với bộ lọc hiện tại. Vui lòng bấm "Đặt lại" để tải lại.'
                          : 'Các ca chỉ định sau khi kỹ thuật viên thực hiện xong sẽ tự động hiển thị tại đây.'}
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                groupedResults.map((order) => {
                  const isOverdue = order.anyOverdue;
                  const queueNumStr = order.queueNumber != null
                    ? (order.queueNumber < 10 ? `0${order.queueNumber}` : `${order.queueNumber}`)
                    : '—';
                  const baseCode = order.baseOrderCode || order.orderCode;
                  const hasMultiple = order.services && order.services.length > 1;

                  return (
                    <tr
                      key={order.id}
                      className="hover:bg-slate-50/70 transition-colors group"
                    >
                      {/* Cột 1 – STT / Mã phiếu */}
                      <td className="py-2.5 px-3.5 text-left whitespace-nowrap">
                        <div className="font-mono font-bold text-slate-800 text-xs">
                          #{queueNumStr}
                        </div>
                        <div className="text-[10.5px] text-slate-400 font-mono mt-0.5">
                          {baseCode}
                        </div>
                      </td>

                      {/* Cột 2 – Mức độ ưu tiên */}
                      <td className="py-2.5 px-1.5 whitespace-nowrap text-center">
                        <div className="flex items-center justify-center">
                          {order.priority === 'PRIORITY' ? (
                            <span className="w-[74px] h-6 px-1.5 rounded-full text-[10.5px] font-semibold bg-amber-50 text-amber-700 border border-amber-200/80 inline-flex items-center justify-start gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-amber-500 shrink-0 animate-ping" />
                              <span>Ưu tiên</span>
                            </span>
                          ) : (
                            <span className="w-[74px] h-6 px-1.5 rounded-full text-[10.5px] font-semibold bg-teal-50 text-teal-700 border border-teal-200/80 inline-flex items-center justify-start gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-teal-500 shrink-0" />
                              <span>Thường</span>
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Cột 3 – Bệnh nhân */}
                      <td className="py-2.5 px-2 text-left whitespace-nowrap">
                        <div className="font-bold text-slate-900 text-xs group-hover:text-teal-700 transition-colors">
                          {order.patientName}
                        </div>
                        <div className="text-[10.5px] text-slate-500 font-medium mt-0.5">
                          {order.birthYear ? `${order.birthYear} • ` : ''}{order.age ? `${order.age}t` : ''} • {order.gender}
                        </div>
                      </td>

                      {/* Cột 4 – Mã BN */}
                      <td className="py-2.5 px-1.5 whitespace-nowrap text-center">
                        <span className="font-mono font-bold text-slate-700 text-xs">
                          {order.patientId}
                        </span>
                      </td>

                      {/* Cột 5 – Đối tượng */}
                      <td className="py-2.5 px-1.5 whitespace-nowrap text-center">
                        <div className="flex items-center justify-center">
                          {order.insuranceType === 'BHYT' ? (
                            <span className="w-[54px] h-5.5 rounded-md text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200/80 inline-flex items-center justify-center">
                              BHYT
                            </span>
                          ) : (
                            <span className="w-[54px] h-5.5 rounded-md text-[10px] font-bold bg-slate-100 text-slate-700 border border-slate-200/80 inline-flex items-center justify-center">
                              Viện phí
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Cột 6 – Dịch vụ đã làm */}
                      <td className="py-2.5 px-2 text-left min-w-[160px] max-w-[240px]">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <span className="font-bold text-slate-900 line-clamp-1" title={order.services?.[0]?.serviceName || order.serviceName}>
                            {order.services?.[0]?.serviceName || order.serviceName}
                          </span>
                          {hasMultiple && (
                            <span
                              className="inline-flex items-center px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-teal-50 text-teal-700 border border-teal-200/90 whitespace-nowrap shadow-2xs cursor-help"
                              title={order.services.slice(1).map((s) => s.serviceName).join(', ')}
                            >
                              +{order.services.length - 1} dịch vụ khác
                            </span>
                          )}
                        </div>
                        <div className="text-[10.5px] text-slate-500 italic mt-0.5 truncate" title={order.clinicalDiagnosis}>
                          {order.clinicalDiagnosis || 'Không có ghi chú lâm sàng'}
                        </div>
                      </td>

                      {/* Cột 7 – Thời gian thực hiện */}
                      <td className="py-2.5 px-1.5 whitespace-nowrap text-center">
                        <span
                          className={`font-mono font-bold text-xs ${
                            isOverdue ? 'text-rose-600 font-extrabold' : 'text-slate-800'
                          }`}
                        >
                          {order.completedAtTime}
                        </span>
                      </td>

                      {/* Cột 8 – Trạng thái */}
                      <td className="py-2.5 px-1.5 whitespace-nowrap text-center">
                        <div className="flex items-center justify-center">
                          {order.isAllDraft ? (
                            <span className="w-[122px] h-6.5 px-2 rounded-full text-[10.5px] font-semibold bg-sky-50 text-sky-700 border border-sky-200/80 inline-flex items-center justify-start gap-1.5">
                              <span className="w-1.5 h-1.5 rounded-full bg-sky-500 shrink-0" />
                              <span>Đang lưu nháp</span>
                            </span>
                          ) : order.hasDraft ? (
                            <span
                              className="w-[122px] h-6.5 px-2 rounded-full text-[10.5px] font-semibold bg-sky-50 text-sky-700 border border-sky-200/80 inline-flex items-center justify-start gap-1.5"
                              title={`${order.draftCount}/${order.totalCount} dịch vụ đã lưu nháp`}
                            >
                              <span className="w-1.5 h-1.5 rounded-full bg-sky-500 shrink-0" />
                              <span>Lưu nháp ({order.draftCount}/{order.totalCount})</span>
                            </span>
                          ) : (
                            <span className="w-[122px] h-6.5 px-2 rounded-full text-[10.5px] font-semibold bg-amber-50 text-amber-700 border border-amber-200/80 inline-flex items-center justify-start gap-1.5">
                              <span className="w-1.5 h-1.5 rounded-full bg-amber-500 shrink-0" />
                              <span>Chờ nhập kết quả</span>
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Cột 9 – Thao tác */}
                      <td className="py-2.5 px-2 whitespace-nowrap text-center">
                        <div className="flex items-center justify-center gap-1.5">
                          {!order.services.some((service) => service.isPerformer) ? (
                            <span className="w-[110px] text-[10.5px] text-slate-500">Chỉ xem danh sách</span>
                          ) : order.services.some((service) => service.isPerformer && service.status === 'DRAFT') ? (
                            <button
                              type="button"
                              onClick={() => handleOpenResultForm(order)}
                              className="w-[110px] h-7 px-2 rounded-xl font-bold text-[10.5px] bg-[#f1e6d8] hover:bg-[#e6d8c7] text-[#714b28] border border-[#e2d2be] active:scale-[0.98] inline-flex items-center justify-start gap-1.5 transition-all cursor-pointer shadow-2xs hover:shadow-xs shrink-0"
                              title="Tiếp tục nhập kết quả cho bệnh nhân này"
                            >
                              <Plus className="w-3.5 h-3.5 shrink-0" />
                              <span>Nhập tiếp</span>
                            </button>
                          ) : (
                            <button
                              type="button"
                              onClick={() => handleOpenResultForm(order)}
                              className="w-[110px] h-7 px-2 rounded-xl font-bold text-[10.5px] bg-[#dbe8fd] hover:bg-[#c9ddfc] text-[#2558c4] border border-[#b8d2fa] active:scale-[0.98] inline-flex items-center justify-start gap-1.5 transition-all cursor-pointer shadow-2xs hover:shadow-xs shrink-0"
                              title="Nhập kết quả cho bệnh nhân này"
                            >
                              <Plus className="w-3.5 h-3.5 shrink-0" />
                              <span>Nhập kết quả</span>
                            </button>
                          )}

                          {/* Nút phụ – In phiếu */}
                          <button
                            type="button"
                            onClick={(e) => handlePrintOrder(order, e)}
                            className="w-[74px] h-7 px-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 active:scale-[0.98] text-slate-700 font-semibold text-[10.5px] inline-flex items-center justify-center gap-1 transition-all cursor-pointer shadow-2xs shrink-0"
                            title="In lại phiếu chỉ định cận lâm sàng"
                          >
                            <Printer className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                            <span>In phiếu</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Footer tóm tắt số lượng */}
        <div className="p-4 bg-slate-50/70 border-t border-slate-200/80 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-slate-500">
          <div>
            Đang hiển thị:{' '}
            <strong className="text-slate-800">{groupedResults.length}</strong> bệnh nhân ({items.length} dịch vụ cận lâm sàng)
          </div>
          <div className="text-[11px] text-slate-400 italic">
            * Ca chờ từ 30 phút trở lên có mốc thời gian hiển thị màu đỏ để cảnh báo quá hạn. Các ca ưu tiên được tự động đẩy lên đầu danh sách.
          </div>
        </div>
      </div>

      {/* MODAL XÁC NHẬN IN PHIẾU CHỈ ĐỊNH */}
      {printModalOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-base">
                <PrinterCheck className="w-5 h-5 text-[#0d9488]" />
                <span>In phiếu chỉ định cận lâm sàng</span>
              </div>
              <button
                type="button"
                onClick={() => setPrintModalOrder(null)}
                className="text-slate-400 hover:text-slate-600 cursor-pointer p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="bg-slate-50 p-4 rounded-xl space-y-2 text-xs text-slate-600 border border-slate-200/70">
              <div className="flex justify-between">
                <span className="text-slate-500">Mã phiếu:</span>
                <span className="font-mono font-bold text-slate-800">{printModalOrder.baseOrderCode || printModalOrder.orderCode}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Bệnh nhân:</span>
                <span className="font-bold text-slate-800">
                  {printModalOrder.patientName} ({printModalOrder.patientId})
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Dịch vụ ({printModalOrder.services?.length || 1}):</span>
                <span className="font-medium text-slate-800 text-right max-w-[240px]">
                  {printModalOrder.services?.map((s) => s.serviceName).join(', ') || printModalOrder.serviceName}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Phòng khám gửi:</span>
                <span className="font-medium text-slate-800">{printModalOrder.doctorRoom}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Thời gian xong:</span>
                <span className="font-medium text-slate-800">{printModalOrder.completedAtTime}</span>
              </div>
            </div>

            <p className="text-xs text-slate-500">
              Bạn có muốn in phiếu chỉ định này qua máy in nhiệt/A4 đã kết nối không?
            </p>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setPrintModalOrder(null)}
                className="px-4 py-2 border border-slate-200 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50 cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleConfirmPrint}
                className="px-4 py-2 bg-[#0d9488] hover:bg-[#0f766e] text-white rounded-xl text-xs font-bold flex items-center gap-1.5 cursor-pointer shadow-2xs"
              >
                <Printer className="w-4 h-4" />
                <span>Xác nhận in phiếu</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default ParaclinicalResultListPage;
