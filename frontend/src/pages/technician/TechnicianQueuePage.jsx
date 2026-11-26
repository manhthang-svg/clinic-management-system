import { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import {
  Search,
  Microscope,
  Clock,
  Building2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  RefreshCw,
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
  UserCheck,
} from 'lucide-react';
import { toast } from 'sonner';
import { useNavigate } from 'react-router';

import { MetricCard } from '../../components/shared/MetricCard';
import technicianApi from '../../apis/technicianApi';

import {
  CheckInTicketModal,
  PatientClinicalDrawer,
  ReacceptSkippedModal,
  RejectOrderModal,
  ReturnToWaitingDialog,
} from '../../components/technician';
import { PERFORM_MESSAGES, performErrorMessage } from '../../components/technician/performOrder';
import { groupStatusOf, isServiceCheckedIn, serviceForAction } from '../../components/technician/queueGroups';

const MSG01 = 'Không tìm thấy kết quả.';
const MSG92 = 'Chưa có dữ liệu để hiển thị.';
const MSG48 = 'Bạn không được phân công phòng khám này hoặc đang ngoài ca trực.';
const SKIPPED_PAGE_SIZES = [10, 20, 50];

/**
 * Xóa dấu tiếng Việt và chuyển thường để tìm kiếm không phân biệt hoa/thường và dấu
 */
function removeDiacritics(str) {
  if (!str) return '';
  return str
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

export function TechnicianQueuePage() {
  // 1. Quản lý danh sách phòng máy thực tế từ backend (tối ưu hóa: đọc cache phòng & activeRoom để load song song)
  const [rooms, setRooms] = useState(() => (typeof technicianApi.getCachedRooms === 'function' ? technicianApi.getCachedRooms() : []) || []);
  const [currentRoomId, setCurrentRoomId] = useState(() => (typeof technicianApi.getActiveRoomId === 'function' ? technicianApi.getActiveRoomId() : ''));
  const [roomsLoading, setRoomsLoading] = useState(() => !(technicianApi.getCachedRooms?.()?.length));

  // 2. Dữ liệu hàng đợi và thống kê từ backend
  const [queueData, setQueueData] = useState({
    stats: null,
    items: [],
    canManage: null,
    hasOwnWork: false,
  });
  const [queueLoading, setQueueLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  // Quản lý 3 Tab: 'waiting_unconfirmed' (Chờ tiếp nhận), 'active_queue' (Hàng đợi vào phòng) & 'skipped' (Đã bỏ qua)
  const [activeTab, setActiveTab] = useState('waiting_unconfirmed');
  const tabInitializedRef = useRef(false);

  // 3. Toolbar & Filters states
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [doctorRoomFilter, setDoctorRoomFilter] = useState('ALL');
  const [doctorRoomsList, setDoctorRoomsList] = useState([]);

  // Bộ lọc & phân trang dành riêng cho Tab 3 (Đã bỏ qua)
  const [skippedInsuranceFilter, setSkippedInsuranceFilter] = useState('ALL');
  const [skippedPage, setSkippedPage] = useState(1);
  const [skippedPageSize, setSkippedPageSize] = useState(10);

  // 4. Drawer & Modal states
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [orderToReject, setOrderToReject] = useState(null);
  const [orderToReturn, setOrderToReturn] = useState(null);
  const [ticketModalData, setTicketModalData] = useState(null);
  const [isTicketModalOpen, setIsTicketModalOpen] = useState(false);
  const [reacceptModalOpen, setReacceptModalOpen] = useState(false);
  const [orderToReaccept, setOrderToReaccept] = useState(null);
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

  // Lấy thông tin chi tiết phòng máy hiện tại
  const currentRoom = useMemo(() => {
    return rooms.find((r) => r.id === currentRoomId) || rooms[0] || null;
  }, [rooms, currentRoomId]);

  // Ref theo dõi cờ in-flight tránh gọi chồng chéo khi mạng trễ
  const inFlightRef = useRef(false);

  // Giữ cache cái hàm (giữ nguyên địa chỉ bộ nhớ của hàm đó :RAM) 
  // nếu các biến phụ thuộc bên trong nó không thay đổi.
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
            search: activeTab === 'skipped' ? undefined : debouncedSearch,
            status: activeTab === 'active_queue' ? statusFilter : 'ALL',
            doctorRoom: doctorRoomFilter,
          },
          signal
        );

        const items = Array.isArray(response?.items) ? response.items : [];
        const stats = response?.stats || { total: 0, waiting: 0, priority: 0, skipped: 0 };
        const canManage = typeof response?.canManage === 'boolean' ? response.canManage : false;

        const hasOwnWork = response?.hasOwnWork ?? items.some((item) => item.canComplete || item.canManageOrder);
        setQueueData({ stats, items, canManage, hasOwnWork });

        // Khởi tạo tab linh hoạt khi mới tải dữ liệu: nếu không có ca chưa đến nhưng có ca trong active queue thì mở active queue
        if (!tabInitializedRef.current && items.length > 0) {
          tabInitializedRef.current = true;
          const hasUnconfirmed = items.some((i) => !isServiceCheckedIn(i));
          const hasActive = items.some((i) => isServiceCheckedIn(i));
          if (!hasUnconfirmed && hasActive) {
            setActiveTab('active_queue');
          }
        }

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
    [debouncedSearch, statusFilter, doctorRoomFilter, activeTab]
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
  const hasOwnWork = queueData.hasOwnWork;
  const manageableService = (order) => serviceForAction(order, (service) => service.canManageOrder ?? canManage);
  const completableService = (order) => (order.roomServices ?? [order]).find((service) => service.canComplete);

  // Xử lý đổi phòng thủ công
  const handleRoomSelect = (roomId) => {
    setCurrentRoomId(roomId);
    technicianApi.setActiveRoomId?.(roomId);
    const selectedRoom = rooms.find((r) => r.id === roomId);
    // Cập nhật ngay canManage theo phòng vừa chọn để UI phản hồi tức thì
    setQueueData((prev) => ({
      ...prev,
      canManage: selectedRoom ? selectedRoom.isAssigned : null,
      hasOwnWork: Boolean(selectedRoom?.hasOwnWork),
    }));
    toast.info(`Đã chuyển phạm vi làm việc sang: ${selectedRoom?.code || ''} - ${selectedRoom?.name || ''}`);
  };

  const isInitialLoading = !currentRoomId || queueData.stats === null;
  const stats = queueData.stats || { total: 0, waiting: 0, priority: 0, skipped: 0 };
  // Chỉ hiển thị những ca đã thanh toán (loại bỏ PENDING_PAYMENT)
  const orders = useMemo(() => {
    return (queueData.items || []).filter((item) => item.status !== 'PENDING_PAYMENT');
  }, [queueData.items]);

  // Gom nhóm danh sách chờ: Mỗi bệnh nhân / lượt khám hiển thị 1 dòng duy nhất trong danh sách chờ của phòng
  const groupedOrders = useMemo(() => {
    const groups = new Map();

    for (const item of orders) {
      const isItemCheckedIn = isServiceCheckedIn(item);
      // Nhóm theo lượt chỉ định của bệnh nhân tại phòng
      const groupKey = item.orderId || item.patientId || item.id;
      if (!groups.has(groupKey)) {
        groups.set(groupKey, {
          ...item,
          isCheckedIn: isItemCheckedIn,
          roomServices: [item],
          skippedAt: item.skippedAt || null,
          skippedAtTime: item.skippedAtTime || null,
        });
      } else {
        const existing = groups.get(groupKey);
        existing.roomServices.push(item);
        if (item.priority === 'PRIORITY') {
          existing.priority = 'PRIORITY';
          existing.priorityLabel = item.priorityLabel || 'Ưu tiên';
        }
        if (existing.queueNumber == null && item.queueNumber != null) {
          existing.queueNumber = item.queueNumber;
        }
        if (!existing.isCheckedIn && isItemCheckedIn) {
          existing.isCheckedIn = true;
          existing.checkedInAtTime = item.checkedInAtTime;
        }
        if (item.skippedAt && !existing.skippedAt) {
          existing.skippedAt = item.skippedAt;
        }
        if (item.skippedAtTime && !existing.skippedAtTime) {
          existing.skippedAtTime = item.skippedAtTime;
        }
      }
    }

    return Array.from(groups.values()).map((group) => {
      const status = groupStatusOf(group.roomServices);
      const isSkipped = status === 'SKIPPED' || group.roomServices.some((s) => Boolean(s.isSkipped) || s.status === 'SKIPPED');
      return {
        ...group,
        status,
        isCheckedIn: group.roomServices.some(isServiceCheckedIn),
        isSkipped,
      };
    });
  }, [orders]);

  // Tab 1: Bệnh nhân chưa có mặt (chưa check-in, chưa có STT, chưa bị bỏ qua) - Sắp xếp theo ưu tiên và thời gian chỉ định
  // Giữ cache kết quả dữ liệu do hàm đó tính toán ra nếu các biến phụ thuộc không thay đổi.
  const unconfirmedList = useMemo(() => {
    return groupedOrders
      .filter((o) => !o.isCheckedIn && !o.isSkipped && o.status !== 'SKIPPED')
      .sort((a, b) => {
        if (a.priority === 'PRIORITY' && b.priority !== 'PRIORITY') return -1;
        if (a.priority !== 'PRIORITY' && b.priority === 'PRIORITY') return 1;
        return (a.orderTime || '').localeCompare(b.orderTime || '');
      });
  }, [groupedOrders]);

  // Tab 2: Bệnh nhân đã có mặt ngoài sảnh và đã được cấp số thứ tự - SẮP XẾP SỐ THỨ TỰ TĂNG DẦN
  // Tự động loại bỏ các ca đã thực hiện xong (PENDING_RESULT, COMPLETED) vì đã chuyển sang màn Nhập kết quả
  const activeQueueList = useMemo(() => {
    return groupedOrders
      .filter(
        (o) =>
          o.isCheckedIn &&
          !o.isSkipped &&
          o.status !== 'SKIPPED' &&
          o.status !== 'PENDING_RESULT' &&
          o.status !== 'COMPLETED'
      )
      .sort((a, b) => {
        if (a.priority === 'PRIORITY' && b.priority !== 'PRIORITY') return -1;
        if (a.priority !== 'PRIORITY' && b.priority === 'PRIORITY') return 1;
        const qA = a.queueNumber != null ? a.queueNumber : Infinity;
        const qB = b.queueNumber != null ? b.queueNumber : Infinity;
        if (qA !== qB) return qA - qB;
        return (a.orderTime || '').localeCompare(b.orderTime || '');
      });
  }, [groupedOrders]);

  // Helper lấy timestamp bỏ qua để sắp xếp (cũ nhất lên trước)
  const getSkipTimestamp = (item) => {
    if (item.skippedAt) {
      const t = new Date(item.skippedAt).getTime();
      if (!isNaN(t)) return t;
    }
    return null;
  };

  // Tab 3: Bệnh nhân / chỉ định bị bỏ qua - Sắp xếp mặc định theo thời điểm bỏ qua (cũ nhất lên trước)
  const skippedList = useMemo(() => {
    return groupedOrders
      .filter((o) => o.isSkipped || o.status === 'SKIPPED')
      .sort((a, b) => {
        const timeA = getSkipTimestamp(a);
        const timeB = getSkipTimestamp(b);
        if (timeA !== null && timeB !== null) {
          if (timeA !== timeB) return timeA - timeB;
        } else if (a.skippedAtTime && b.skippedAtTime) {
          const cmp = a.skippedAtTime.localeCompare(b.skippedAtTime);
          if (cmp !== 0) return cmp;
        }
        const qA = a.queueNumber != null ? a.queueNumber : Infinity;
        const qB = b.queueNumber != null ? b.queueNumber : Infinity;
        if (qA !== qB) return qA - qB;
        return (a.orderTime || '').localeCompare(b.orderTime || '');
      });
  }, [groupedOrders]);

  // Lọc tìm kiếm & phân loại cho danh sách bệnh nhân bỏ qua (Tab 3)
  const filteredSkippedList = useMemo(() => {
    return skippedList.filter((item) => {
      // 1. Ô tìm kiếm tự do theo Tên BN, Mã BN, Số thẻ BHYT (không phân biệt hoa/thường và dấu tiếng Việt)
      if (searchQuery.trim()) {
        const q = removeDiacritics(searchQuery.trim());
        const matchName = removeDiacritics(item.patientName || '').includes(q);
        const matchId = removeDiacritics(item.patientId || '').includes(q);
        const matchInsurance = removeDiacritics(
          item.insuranceNumber || item.insuranceCode || item.healthInsuranceNumber || ''
        ).includes(q);
        const matchCccd = removeDiacritics(item.cccd || item.citizenId || '').includes(q);
        const matchOrderCode = removeDiacritics(item.orderCode || '').includes(q);
        const matchQueueNumber = item.queueNumber != null && String(item.queueNumber).includes(q.replace('#', ''));

        if (!matchName && !matchId && !matchInsurance && !matchCccd && !matchOrderCode && !matchQueueNumber) {
          return false;
        }
      }

      // 2. Bộ lọc loại khám: Tất cả (ALL), BHYT, Dịch vụ (SERVICE)
      if (skippedInsuranceFilter === 'BHYT') {
        if (item.insuranceType !== 'BHYT') return false;
      } else if (skippedInsuranceFilter === 'SERVICE') {
        if (item.insuranceType === 'BHYT') return false;
      }

      // 3. Lọc theo phòng gửi chỉ định
      if (doctorRoomFilter !== 'ALL' && item.doctorRoom !== doctorRoomFilter) {
        return false;
      }

      return true;
    });
  }, [skippedList, searchQuery, skippedInsuranceFilter, doctorRoomFilter]);

  // Phân trang cho Tab 3 (Đã bỏ qua)
  const totalSkippedRecords = filteredSkippedList.length;
  const totalSkippedPages = Math.max(1, Math.ceil(totalSkippedRecords / skippedPageSize));
  const validSkippedPage = Math.min(skippedPage, totalSkippedPages);

  const pagedSkippedOrders = useMemo(() => {
    const start = (validSkippedPage - 1) * skippedPageSize;
    return filteredSkippedList.slice(start, start + skippedPageSize);
  }, [filteredSkippedList, validSkippedPage, skippedPageSize]);

  const skippedStartIndex = totalSkippedRecords === 0 ? 0 : (validSkippedPage - 1) * skippedPageSize + 1;
  const skippedEndIndex = Math.min(validSkippedPage * skippedPageSize, totalSkippedRecords);

  // Thống kê chính xác theo lượt bệnh nhân đồng bộ với 3 Tab
  const patientStats = useMemo(() => {
    const totalPatients = groupedOrders.length;
    const totalServices = orders.length;
    const activeQueueCount = activeQueueList.length;
    const priority = groupedOrders.filter((o) => o.priority === 'PRIORITY' && o.status !== 'CANCELLED').length;
    const skipped = skippedList.length;
    const unconfirmed = unconfirmedList.length;

    return {
      totalPatients,
      totalServices,
      activeQueueCount,
      priority,
      skipped,
      unconfirmed,
    };
  }, [groupedOrders, orders.length, activeQueueList.length, skippedList.length, unconfirmedList.length]);

  // Danh sách hiển thị theo tab hiện tại
  const displayedOrders = useMemo(() => {
    if (activeTab === 'waiting_unconfirmed') return unconfirmedList;
    if (activeTab === 'active_queue') return activeQueueList;
    return pagedSkippedOrders;
  }, [activeTab, unconfirmedList, activeQueueList, pagedSkippedOrders]);

  // Đặt lại bộ lọc & tìm kiếm về mặc định
  const handleResetFilters = () => {
    setSearchQuery('');
    setDebouncedSearch('');
    setStatusFilter('ALL');
    setDoctorRoomFilter('ALL');
    setSkippedInsuranceFilter('ALL');
    setSkippedPage(1);
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

  // Thao tác: KTV xác nhận bệnh nhân có mặt tại phòng (Check-in cấp STT cho toàn bộ lượt khám)
  const handleCheckInOrder = async (order, e) => {
    e?.stopPropagation();
    const targetService = manageableService(order);
    if (!targetService) return;
    try {
      setActionLoadingId(order.id);
      const updated = await technicianApi.checkInOrder(targetService.id);
      const numStr = updated?.queueNumber
        ? (updated.queueNumber < 10 ? `0${updated.queueNumber}` : updated.queueNumber)
        : '';
      toast.success(
        `Đã xác nhận bệnh nhân ${order.patientName} có mặt! Cấp số thứ tự #${numStr}`,
        { duration: 4500 }
      );
      if (selectedOrder?.id === order.id || order.roomServices?.some((s) => s.id === selectedOrder?.id)) {
        setSelectedOrder(updated);
      }
      await loadQueue(currentRoomId, { silent: true });
      // Bệnh nhân được cấp STT => tự động chuyển sang Tab 2 (Hàng đợi vào phòng)
      setActiveTab('active_queue');

      // Mở Pop-up phiếu số thứ tự (Ticket Modal)
      setTicketModalData({
        queueNumber: updated?.queueNumber,
        order,
        room: currentRoom,
        issuedAt: new Date(),
      });
      setIsTicketModalOpen(true);
    } catch (err) {
      if (err?.errorResponse?.code === 'PARACLINICAL_ORDER_NOT_PAID') {
        toast.error('Bệnh nhân chưa thanh toán dịch vụ này tại quầy thu ngân.');
      } else {
        toast.error(err?.message || 'Không thể xác nhận có mặt.');
      }
    } finally {
      setActionLoadingId(null);
    }
  };

  // UC-66: tiếp nhận trước, xác nhận đã thực hiện khi kỹ thuật hoàn tất
  const handleStartOrder = async (order, e) => {
    e?.stopPropagation();
    const targetService = manageableService(order);
    if (!targetService) return;
    try {
      setActionLoadingId(order.id);
      await technicianApi.acceptOrder(targetService.id);
      toast.success(`Đã tiếp nhận ${order.patientName}. Ca đang thực hiện.`);
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
    const targetService = manageableService(order);
    if (!targetService) return;
    try {
      setActionLoadingId(order.id);
      await technicianApi.skipOrder(targetService.id);
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
    const targetService = manageableService(order);
    if (!targetService) return;
    try {
      setActionLoadingId(order.id);
      await technicianApi.unskipOrder(targetService.id);
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

  // Thao tác: Mở modal Tiếp nhận lại bệnh nhân bị bỏ qua (UC-46)
  const handleOpenReacceptModal = (order) => {
    setOrderToReaccept(order);
    setReacceptModalOpen(true);
  };

  // Thao tác: Xác nhận tiếp nhận lại bệnh nhân bị bỏ qua (UC-46)
  const handleConfirmReaccept = async (order, note) => {
    const targetService = manageableService(order);
    if (!targetService) return;
    try {
      setActionLoadingId(order.id);
      await technicianApi.unskipOrder(targetService.id);
      const numStr = order.queueNumber != null
        ? (order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber)
        : '';
      toast.success(
        `Đã tiếp nhận lại bệnh nhân ${order.patientName}${numStr ? ` (Số #${numStr})` : ''} vào hàng đợi!${note ? ` Ghi chú: ${note}` : ''}`,
        { duration: 4000 }
      );
      if (selectedOrder?.id === order.id) {
        setSelectedOrder((prev) => (prev ? { ...prev, isSkipped: false, status: 'WAITING' } : null));
      }
      setReacceptModalOpen(false);
      setOrderToReaccept(null);
      await loadQueue(currentRoomId, { silent: true });
      // Tự động chuyển về Tab 2 (Hàng đợi vào phòng)
      setActiveTab('active_queue');
    } catch (err) {
      toast.error(err?.message || 'Không thể tiếp nhận lại bệnh nhân.');
      throw err;
    } finally {
      setActionLoadingId(null);
    }
  };

  // Điều hướng sang màn hình form nhập kết quả cận lâm sàng của chỉ định
  const openResult = (order) => navigate(`/technician/results/${order.id}`);

  // UC-66: xac nhan da thuc hien xong, chuyen sang cho nhap ket qua
  const handleCompleteOrder = async (order) => {
    const service = completableService(order);
    if (!service) return;

    try {
      setActionLoadingId(order.id);
      await technicianApi.completeOrder(service.id);
      if (selectedOrder?.id === order.id) {
        setIsDrawerOpen(false);
        setSelectedOrder(null);
      }
      toast.success(
        `Đã hoàn tất thực hiện kỹ thuật cho bệnh nhân ${order.patientName}!`,
        {
          description: `${service.serviceName} • Chuyển sang chờ nhập kết quả`,
          action: {
            label: 'Nhập KQ ngay',
            onClick: () => openResult(service),
          },
          duration: 5000,
        }
      );
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(performErrorMessage(err));
      await loadQueue(currentRoomId, { silent: true });
    } finally {
      setActionLoadingId(null);
    }
  };

  // UC-66: tra dich vu dang lam ve hang cho kem ly do
  const handleConfirmReturn = async (order, reason) => {
    try {
      setActionLoadingId(order.id);
      await technicianApi.returnToWaiting(order.id, reason);
      setOrderToReturn(null);
      if (selectedOrder?.id === order.id) {
        setIsDrawerOpen(false);
        setSelectedOrder(null);
      }
      toast.info(`Đã trả bệnh nhân ${order.patientName} về hàng chờ gọi.`);
      await loadQueue(currentRoomId, { silent: true });
    } catch (err) {
      toast.error(performErrorMessage(err));
    } finally {
      setActionLoadingId(null);
    }
  };

  // Mở modal nhập lý do từ chối thực hiện y lệnh cận lâm sàng
  const handleOpenRejectModal = (order) => {
    const targetService = manageableService(order);
    if (!targetService) return;
    setOrderToReject(targetService);
    setRejectModalOpen(true);
  };

  // Xác nhận từ chối thực hiện y lệnh do chống chỉ định và gửi phản hồi về bác sĩ
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
            ) : hasOwnWork ? (
              <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-amber-50 text-amber-800 border border-amber-200">
                Còn chỉ định thuộc ca của bạn
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
              Phụ trách: <strong>{currentRoom?.activeTech || 'Chưa có'}</strong>
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
            {hasOwnWork
              ? 'Bạn có thể hoàn tất và gửi kết quả cho các dịch vụ mình đã tiếp nhận.'
              : 'Bạn có thể tra cứu các dịch vụ mình đã xử lý.'}
          </span>
        </div>
      )}

      {/* 2. 4 THẺ THỐNG KÊ THEO PHÒNG HIỆN TẠI (REUSING METRICCARD WITH ADMIN PALETTE) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title="Tổng bệnh nhân"
          value={patientStats.totalPatients}
          icon={ListOrdered}
          iconBg="bg-teal-50 text-[#0d9488]"
          valueColor="text-slate-900"
          subtitle={`Gồm ${patientStats.totalServices} dịch vụ tại ${currentRoom?.code || ''}`}
          dotColor="bg-teal-500"
          footerLabel="Tổng dịch vụ"
          footerValue={`${patientStats.totalServices} dịch vụ`}
          footerValueColor="text-teal-700"
        />

        <MetricCard
          title="Hàng đợi vào phòng"
          value={patientStats.activeQueueCount}
          icon={Clock}
          iconBg="bg-sky-50 text-sky-700"
          valueColor="text-sky-800"
          subtitle="Bệnh nhân đã có STT đang chờ gọi"
          dotColor="bg-sky-500"
          footerLabel="Chờ tiếp nhận"
          footerValue={`${patientStats.unconfirmed} ca chưa đến`}
          footerValueColor={patientStats.unconfirmed > 0 ? 'text-amber-700 font-bold' : 'text-slate-600'}
        />

        <MetricCard
          title="Ưu tiên"
          value={patientStats.priority}
          icon={AlertTriangle}
          iconBg="bg-amber-50 text-amber-700"
          valueColor="text-amber-800"
          subtitle="Người cao tuổi, trẻ nhỏ, kiểm tra khẩn"
          dotColor="bg-amber-500"
          footerLabel="Mức ưu tiên"
          footerValue="Xử lý trước"
          footerValueColor="text-amber-700"
        />

        <div
          onClick={() => {
            setActiveTab('skipped');
            setSkippedPage(1);
          }}
          className="cursor-pointer"
        >
          <MetricCard
            title="Đã bỏ qua"
            value={patientStats.skipped}
            icon={SkipForward}
            iconBg="bg-slate-100 text-slate-700"
            valueColor="text-slate-800"
            subtitle="Bệnh nhân tạm thời bỏ qua do vắng mặt"
            dotColor="bg-slate-400"
            footerLabel="Trạng thái"
            footerValue="Vắng mặt"
            footerValueColor="text-slate-600"
          />
        </div>
      </div>

      {/* 3. BẢNG DANH SÁCH CHỜ CẬN LÂM SÀNG VỚI 3 TABS PHÂN TÁCH */}
      <section className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
        {/* Tabs phân tách ở ngay trên bảng dữ liệu */}
        <div className="flex items-center gap-2 border-b border-slate-200/80 pb-0 overflow-x-auto">
          {/* Tab 1: Chờ tiếp nhận / Chưa đến (Chưa có STT) */}
          <button
            type="button"
            onClick={() => setActiveTab('waiting_unconfirmed')}
            className={`pb-3 px-4 text-xs sm:text-sm font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
              activeTab === 'waiting_unconfirmed'
                ? 'border-[#0d9488] text-[#0d9488]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Clock className="w-4 h-4" />
            <span>Chờ tiếp nhận / Chưa đến</span>
            <span
              className={`ml-1 px-2 py-0.5 text-[11px] rounded-full font-semibold ${
                activeTab === 'waiting_unconfirmed'
                  ? 'bg-amber-50 text-amber-700 border border-amber-200/60'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {unconfirmedList.length}
            </span>
          </button>

          {/* Tab 2: Hàng đợi vào phòng (Đã có STT - Active Queue) */}
          <button
            type="button"
            onClick={() => setActiveTab('active_queue')}
            className={`pb-3 px-4 text-xs sm:text-sm font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
              activeTab === 'active_queue'
                ? 'border-[#0d9488] text-[#0d9488]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <ListOrdered className="w-4 h-4" />
            <span>Hàng đợi vào phòng</span>
            <span
              className={`ml-1 px-2 py-0.5 text-[11px] rounded-full font-semibold ${
                activeTab === 'active_queue'
                  ? 'bg-teal-50 text-[#0d9488] border border-teal-200/60'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {activeQueueList.length}
            </span>
          </button>

          {/* Tab 3: Đã bỏ qua (Vắng mặt - UC-46) */}
          <button
            type="button"
            onClick={() => {
              setActiveTab('skipped');
              setSkippedPage(1);
            }}
            className={`pb-3 px-4 text-xs sm:text-sm font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer whitespace-nowrap ${
              activeTab === 'skipped'
                ? 'border-[#0d9488] text-[#0d9488]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <SkipForward className="w-4 h-4" />
            <span>Đã bỏ qua</span>
            <span
              className={`ml-1 px-2 py-0.5 text-[11px] rounded-full font-semibold ${
                activeTab === 'skipped'
                  ? 'bg-rose-50 text-rose-700 border border-rose-200/60'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {skippedList.length}
            </span>
          </button>
        </div>

        {/* Toolbar lọc & tìm kiếm */}
        <div className="flex flex-col md:flex-row items-center justify-between gap-3 pt-1">
          {/* Input Tìm kiếm */}
          <div className="relative w-full md:flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                if (activeTab === 'skipped') setSkippedPage(1);
              }}
              placeholder={
                activeTab === 'skipped'
                  ? 'Tìm theo Tên bệnh nhân, Mã BN, Số thẻ BHYT, CCCD...'
                  : activeTab === 'waiting_unconfirmed'
                  ? 'Tìm theo Mã bệnh nhân (Patient ID), Mã phiếu, Họ tên, CCCD để xác nhận có mặt...'
                  : 'Tìm theo Mã bệnh nhân (Patient ID), STT, Mã phiếu, Họ tên, CCCD...'
              }
              className="w-full h-10 pl-10 pr-4 bg-slate-50/70 border border-slate-200/80 rounded-xl text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0d9488] focus:bg-white transition-all"
            />
          </div>

          {/* Dropdowns lọc */}
          <div className="flex flex-wrap items-center gap-2.5 w-full md:w-auto">
            {/* Tab 3: Bộ lọc loại khám (Lọc loại khám: Tất cả loại khám, BHYT, Dịch vụ) */}
            {activeTab === 'skipped' && (
              <div className="relative min-w-[145px]">
                <select
                  value={skippedInsuranceFilter}
                  onChange={(e) => {
                    setSkippedInsuranceFilter(e.target.value);
                    setSkippedPage(1);
                  }}
                  className="w-full h-10 pl-3 pr-8 bg-white border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#0d9488] cursor-pointer appearance-none shadow-2xs"
                >
                  <option value="ALL">Tất cả loại khám</option>
                  <option value="BHYT">BHYT</option>
                  <option value="SERVICE">Dịch vụ</option>
                </select>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>
            )}

            {/* Bộ lọc trạng thái (chỉ áp dụng ở Tab 2 vì Tab 1 toàn bộ là ca chưa đến, Tab 3 là ca đã bỏ qua) */}
            {activeTab === 'active_queue' && (
              <div className="relative min-w-[145px]">
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="w-full h-10 pl-3 pr-8 bg-white border border-slate-200/80 rounded-xl text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#0d9488] cursor-pointer appearance-none shadow-2xs"
                >
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="WAITING">Đang chờ gọi</option>
                  <option value="CANCELLED">Đã từ chối</option>
                </select>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>
            )}

            {/* Bộ lọc phòng khám chỉ định */}
            <div className="relative min-w-[170px]">
              <select
                value={doctorRoomFilter}
                onChange={(e) => {
                  setDoctorRoomFilter(e.target.value);
                  if (activeTab === 'skipped') setSkippedPage(1);
                }}
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

            {/* Nút Đặt lại / Xóa bộ lọc */}
            <button
              type="button"
              onClick={handleResetFilters}
              className="h-10 px-3.5 inline-flex items-center gap-1.5 text-xs font-bold text-slate-600 hover:text-slate-900 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-xl transition-all cursor-pointer shadow-2xs"
              title="Xóa nhanh tất cả bộ lọc và quay về trang 1"
            >
              <RotateCcw className="w-3.5 h-3.5 text-slate-500" />
              <span>{activeTab === 'skipped' ? 'Xóa bộ lọc' : 'Đặt lại'}</span>
            </button>
          </div>
        </div>

        {/* Bảng danh sách */}
        <div className="rounded-xl border border-slate-200/80 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50/80 border-b border-slate-200/80 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-3.5 whitespace-nowrap">STT / Hàng đợi</th>
                  <th className="py-3 px-2.5 whitespace-nowrap text-center">Ưu tiên</th>
                  <th className="py-3 px-3 whitespace-nowrap">Bệnh nhân</th>
                  <th className="py-3 px-2.5 whitespace-nowrap">Mã BN</th>
                  <th className="py-3 px-2.5 whitespace-nowrap text-center">Loại khám</th>
                  <th className="py-3 px-3 min-w-[200px]">Dịch vụ</th>
                  <th className="py-3 px-3 whitespace-nowrap">Bác sĩ & Phòng</th>
                  <th className="py-3 px-2.5 whitespace-nowrap text-center">
                    {activeTab === 'skipped' ? 'Thời gian (Gọi / Bỏ qua)' : 'Thời gian'}
                  </th>
                  <th className="py-3 px-2.5 whitespace-nowrap text-center">Trạng thái</th>
                  <th className="py-3 px-3.5 whitespace-nowrap text-center">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {queueLoading && displayedOrders.length === 0 ? (
                  <tr>
                    <td colSpan={10} className="py-12 text-center text-slate-400">
                      <Loader2 className="w-8 h-8 text-[#0d9488] animate-spin mx-auto mb-2" />
                      <p className="text-xs font-semibold">Đang cập nhật danh sách...</p>
                    </td>
                  </tr>
                ) : displayedOrders.map((order) => {
                  const isPriority = order.priority === 'PRIORITY';
                  const isWaiting = order.status === 'WAITING';
                  const isSkipped = order.isSkipped || order.status === 'SKIPPED';
                  const isInProgress = order.status === 'IN_PROGRESS';
                  const orderService = manageableService(order);
                  const canManageOrder = Boolean(orderService);
                  const canComplete = isInProgress && Boolean(completableService(order));
                  const isPendingResult = order.status === 'PENDING_RESULT';
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
                          {activeTab === 'waiting_unconfirmed' ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200/80 select-none">
                              Chờ có mặt
                            </span>
                          ) : activeTab === 'skipped' ? (
                            <span className="font-extrabold text-slate-700 bg-slate-100 px-2 py-0.5 rounded-md border border-slate-200 text-xs tracking-tight">
                              #{order.queueNumber != null ? (order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber) : '---'}
                            </span>
                          ) : (
                            <span className="font-extrabold text-teal-800 bg-teal-50 px-2 py-0.5 rounded-md border border-teal-200/80 text-xs tracking-tight">
                              #{order.queueNumber < 10 ? `0${order.queueNumber}` : order.queueNumber}
                            </span>
                          )}
                        </div>
                        <span className="text-[10px] text-slate-400 block font-mono">
                          {order.orderCode?.replace(/-\d+$/, '') || order.orderCode}
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
                            Dịch vụ
                          </span>
                        )}
                      </td>

                      {/* Dịch vụ thực hiện */}
                      <td className="py-3 px-3">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <span className="font-bold text-slate-900 line-clamp-1">
                            {order.roomServices?.[0]?.serviceName || order.serviceName}
                          </span>
                          {order.roomServices && order.roomServices.length > 1 && (
                            <span
                              className="inline-flex items-center px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-teal-50 text-teal-700 border border-teal-200/90 whitespace-nowrap shadow-2xs"
                              title={order.roomServices.slice(1).map((s) => s.serviceName).join(', ')}
                            >
                              +{order.roomServices.length - 1} dịch vụ khác
                            </span>
                          )}
                        </div>
                        <div className="text-[11px] text-slate-400 line-clamp-1 italic mt-0.5">
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

                      {/* Thời gian */}
                      <td className="py-3 px-2.5 whitespace-nowrap text-center text-slate-600 font-medium">
                        {activeTab === 'skipped' ? (
                          <div className="flex flex-col items-center justify-center gap-0.5 text-[11px]">
                            <div className="flex items-center gap-1 text-slate-500 font-medium" title="Thời gian đăng ký / gọi">
                              <Clock className="w-3 h-3 text-slate-400" />
                              <span>{order.orderTime || order.checkedInAtTime || '--:--'}</span>
                            </div>
                            <div className="flex items-center gap-1 text-rose-600 font-bold" title="Thời điểm hệ thống ghi nhận bỏ qua">
                              <span>Bỏ qua: {order.skippedAtTime || '--:--'}</span>
                            </div>
                          </div>
                        ) : (
                          <div className="flex items-center justify-center gap-1 text-[11px]">
                            <Clock className="w-3 h-3 text-slate-400" />
                            <span>{order.orderTime || '--:--'}</span>
                          </div>
                        )}
                      </td>

                      {/* Trạng thái tiếp nhận */}
                      <td className="py-3 px-2.5 whitespace-nowrap text-center">
                        {activeTab === 'waiting_unconfirmed' ? (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-semibold bg-slate-100 text-slate-600 border border-slate-200/80">
                            <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                            Chưa đến
                          </span>
                        ) : activeTab === 'skipped' ? (
                          <div className="inline-flex flex-col items-center">
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10.5px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                              <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                              Đã bỏ qua
                            </span>
                            <span className="text-[10px] text-slate-400 mt-0.5 font-medium">Chờ thực hiện CLS</span>
                          </div>
                        ) : (
                          <>
                            {isSkipped && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                                Đã bỏ qua
                              </span>
                            )}
                            {isWaiting && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-sky-50 text-sky-700 border border-sky-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-sky-500" />
                                Đang chờ gọi
                              </span>
                            )}
                            {isInProgress && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
                                Đang thực hiện
                              </span>
                            )}
                            {isPendingResult && (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10.5px] font-bold bg-violet-50 text-violet-700 border border-violet-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-violet-500" />
                                Chờ nhập kết quả
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
                          </>
                        )}
                      </td>

                      {/* Thao tác */}
                      <td className="py-3 px-3.5 whitespace-nowrap text-center" onClick={(e) => e.stopPropagation()}>
                        {activeTab === 'skipped' ? (
                          /* Thao tác Tab 3: Tiếp nhận lại (Re-accept UC-46) */
                          <div className="flex items-center justify-center">
                            <button
                              type="button"
                              disabled={!canManageOrder || isRowBusy}
                              onClick={(e) => {
                                e.stopPropagation();
                                handleOpenReacceptModal(order);
                              }}
                              className={`h-7 inline-flex items-center gap-1 px-2.5 text-[10.5px] font-bold rounded-lg shadow-2xs transition-all shrink-0 ${
                                !canManageOrder
                                  ? 'opacity-50 cursor-not-allowed bg-slate-100 text-slate-400 border border-slate-200'
                                  : 'text-white bg-[#0d9488] hover:bg-[#0f766e] cursor-pointer active:scale-95'
                              }`}
                              title={!canManageOrder ? MSG48 : 'Tiếp nhận lại bệnh nhân quay lại hàng đợi phục vụ'}
                            >
                              <RotateCcw className="w-3 h-3 shrink-0" />
                              <span>Tiếp nhận lại</span>
                            </button>
                          </div>
                        ) : !canManageOrder && !canComplete ? (
                          <div className="flex items-center justify-center">
                            <button
                              type="button"
                              onClick={() => handleOpenDetail(order)}
                              className="h-7 inline-flex items-center gap-1.5 px-2.5 text-[10.5px] font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 border border-slate-200 rounded-lg shadow-2xs transition-all cursor-pointer shrink-0"
                              title="Xem chi tiết lâm sàng & y lệnh"
                            >
                              <Eye className="w-3 h-3 text-slate-500 shrink-0" />
                              <span>Chi tiết</span>
                            </button>
                          </div>
                        ) : activeTab === 'waiting_unconfirmed' ? (
                          /* Thao tác Tab 1: Xác nhận có mặt */
                          <div className="flex items-center justify-center">
                            <button
                              type="button"
                              onClick={(e) => handleCheckInOrder(order, e)}
                              className="h-7 inline-flex items-center gap-1 px-2.5 text-[10.5px] font-bold text-white bg-teal-600 hover:bg-teal-700 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0"
                              title="Bệnh nhân đã đến cửa phòng: Bấm xác nhận có mặt để cấp số thứ tự và chuyển sang hàng đợi"
                            >
                              <UserCheck className="w-3.5 h-3.5 shrink-0" />
                              <span>Xác nhận có mặt</span>
                            </button>
                          </div>
                        ) : (
                          /* Thao tác Tab 2: Tiếp nhận, Bỏ qua / Nhận lại / Đã thực hiện */
                          <div className="flex items-center justify-center gap-1.5">
                            {/* Nút Tiếp nhận (khi WAITING) */}
                            {isWaiting && (
                              <button
                                type="button"
                                onClick={(e) => handleStartOrder(order, e)}
                                className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0"
                                title="Tiếp nhận bệnh nhân vào phòng máy"
                              >
                                <Play className="w-3 h-3 fill-current shrink-0" />
                                <span>Tiếp nhận</span>
                              </button>
                            )}

                            {/* Nút Bỏ qua (khi WAITING) */}
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

                            {/* Nút Nhận lại (khi SKIPPED) */}
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

                            {/* Đã thực hiện xong: chỉ còn nhập kết quả (UC-67) */}
                            {isPendingResult && (
                              <button
                                type="button"
                                onClick={(e) => { e.stopPropagation(); openResult(serviceForAction(order)); }}
                                className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-violet-700 bg-violet-50 hover:bg-violet-100 border border-violet-200 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0"
                                title="Mở phiếu nhập kết quả"
                              >
                                <span>Nhập KQ</span>
                              </button>
                            )}

                            {canComplete && (
                              <button
                                type="button"
                                disabled={isRowBusy}
                                onClick={(e) => { e.stopPropagation(); handleCompleteOrder(order); }}
                                className="h-7 inline-flex items-center gap-1 px-2.5 text-[10.5px] font-bold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0 disabled:opacity-50"
                                title="Xác nhận đã thực hiện xong dịch vụ"
                              >
                                <span>Đã thực hiện</span>
                              </button>
                            )}
                            {canManageOrder && isInProgress && (
                              <button
                                type="button"
                                disabled={isRowBusy}
                                onClick={(e) => { e.stopPropagation(); setOrderToReturn({ ...order, id: orderService.id }); }}
                                className="h-7 inline-flex items-center gap-1 px-2 text-[10.5px] font-bold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200 rounded-lg shadow-2xs transition-all cursor-pointer active:scale-95 shrink-0 disabled:opacity-50"
                                title="Không thực hiện được: trả về hàng chờ kèm lý do"
                              >
                                <RotateCcw className="w-3 h-3 shrink-0" />
                                <span>Trả về</span>
                              </button>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}

                {!queueLoading && displayedOrders.length === 0 && (
                  <tr>
                    <td colSpan={10} className="py-12 text-center text-slate-400 space-y-2">
                      {activeTab === 'skipped' ? (
                        skippedList.length === 0 ? (
                          <>
                            <SkipForward className="w-8 h-8 text-slate-300 mx-auto" />
                            <p className="text-sm font-semibold text-slate-600">
                              {MSG92}
                            </p>
                            <p className="text-xs text-slate-400">
                              Phòng máy hôm nay chưa ghi nhận bệnh nhân nào bị bỏ qua do vắng mặt.
                            </p>
                          </>
                        ) : (
                          <>
                            <Search className="w-8 h-8 text-slate-300 mx-auto" />
                            <p className="text-sm font-semibold text-slate-600">
                              {MSG01}
                            </p>
                            <p className="text-xs text-slate-400">
                              Không có bệnh nhân bị bỏ qua nào khớp với từ khóa tìm kiếm hoặc bộ lọc hiện tại.
                            </p>
                            <button
                              type="button"
                              onClick={handleResetFilters}
                              className="text-xs text-[#0d9488] font-bold hover:underline cursor-pointer block mx-auto mt-2"
                            >
                              Xóa bộ lọc
                            </button>
                          </>
                        )
                      ) : activeTab === 'waiting_unconfirmed' ? (
                        <>
                          <Clock className="w-8 h-8 text-slate-300 mx-auto" />
                          <p className="text-sm font-semibold">
                            {searchQuery
                              ? 'Không tìm thấy bệnh nhân nào phù hợp với từ khóa tìm kiếm.'
                              : `Hiện không có bệnh nhân nào đang chờ tiếp nhận / chưa đến tại ${currentRoom?.name || 'phòng'}.`}
                          </p>
                          <p className="text-xs text-slate-400">
                            Tất cả các ca chỉ định đến phòng máy này đã được xác nhận có mặt.
                          </p>
                        </>
                      ) : (
                        <>
                          <ListOrdered className="w-8 h-8 text-slate-300 mx-auto" />
                          <p className="text-sm font-semibold">
                            {searchQuery || statusFilter !== 'ALL'
                              ? 'Không có chỉ định nào phù hợp với bộ lọc trong hàng đợi.'
                              : 'Hàng đợi vào phòng hiện đang trống.'}
                          </p>
                          <p className="text-xs text-slate-400">
                            Chưa có bệnh nhân nào có mặt ngoài cửa phòng. Chuyển sang tab &quot;Chờ tiếp nhận / Chưa đến&quot; để bấm xác nhận khi bệnh nhân tới.
                          </p>
                          {unconfirmedList.length > 0 && (
                            <button
                              type="button"
                              onClick={() => setActiveTab('waiting_unconfirmed')}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-teal-700 bg-teal-50 hover:bg-teal-100 border border-teal-200 rounded-lg transition-all cursor-pointer shadow-2xs mt-1"
                            >
                              <Clock className="w-3.5 h-3.5" />
                              <span>Xem danh sách chờ tiếp nhận ({unconfirmedList.length})</span>
                            </button>
                          )}
                        </>
                      )}
                      {searchQuery && (
                        <button
                          type="button"
                          onClick={handleResetFilters}
                          className="text-xs text-[#0d9488] font-bold hover:underline cursor-pointer block mx-auto mt-2"
                        >
                          Đặt lại bộ lọc tìm kiếm
                        </button>
                      )}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Phân trang dành riêng cho Tab 3 (Đã bỏ qua) */}
          {activeTab === 'skipped' && totalSkippedRecords > 0 && (
            <div className="px-5 py-3 bg-white border-t border-slate-200/80 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
              <div className="text-slate-600 font-medium">
                Hiển thị <strong className="text-slate-900">{skippedStartIndex}–{skippedEndIndex}</strong> trên{' '}
                <strong className="text-slate-900">{totalSkippedRecords}</strong> bệnh nhân bỏ qua
              </div>

              <div className="flex items-center gap-3">
                <div className="flex items-center gap-1.5 text-slate-500">
                  <span>Số dòng:</span>
                  <select
                    value={skippedPageSize}
                    onChange={(e) => {
                      setSkippedPageSize(Number(e.target.value));
                      setSkippedPage(1);
                    }}
                    className="h-8 px-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-700 focus:outline-none focus:ring-1 focus:ring-[#0d9488] cursor-pointer"
                  >
                    {SKIPPED_PAGE_SIZES.map((size) => (
                      <option key={size} value={size}>
                        {size} / trang
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setSkippedPage((prev) => Math.max(1, prev - 1))}
                    disabled={validSkippedPage <= 1}
                    className="h-8 px-2.5 inline-flex items-center gap-1 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer transition-colors"
                    title="Trang trước"
                  >
                    <ChevronLeft className="w-3.5 h-3.5" />
                    <span className="hidden sm:inline">Trước</span>
                  </button>

                  <span className="px-3 py-1 font-bold text-teal-800 bg-teal-50 border border-teal-200 rounded-lg text-xs">
                    Trang {validSkippedPage} / {totalSkippedPages}
                  </span>

                  <button
                    type="button"
                    onClick={() => setSkippedPage((prev) => Math.min(totalSkippedPages, prev + 1))}
                    disabled={validSkippedPage >= totalSkippedPages}
                    className="h-8 px-2.5 inline-flex items-center gap-1 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer transition-colors"
                    title="Trang sau"
                  >
                    <span className="hidden sm:inline">Sau</span>
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Footer thống kê số lượng đang hiển thị */}
          <div className="px-5 py-3.5 bg-slate-50/80 border-t border-slate-200/80 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-slate-500 font-medium">
            <div className="flex items-center gap-2">
              <span>
                Đang hiển thị:{' '}
                <strong className="text-slate-900">{displayedOrders.length}</strong> bệnh nhân
                {activeTab === 'waiting_unconfirmed'
                  ? ' (chưa đến phòng)'
                  : activeTab === 'skipped'
                  ? ' (đã bỏ qua)'
                  : ' (trong hàng đợi)'}
              </span>
              {(searchQuery ||
                (activeTab === 'active_queue' && statusFilter !== 'ALL') ||
                (activeTab === 'skipped' && skippedInsuranceFilter !== 'ALL') ||
                doctorRoomFilter !== 'ALL') && (
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
      </section>

      {/* 5. NGĂN KÉO CHI TIẾT BỆNH NHÂN & Y LỆNH (PATIENT & CLINICAL DRAWER) */}
      <PatientClinicalDrawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        order={selectedOrder}
        canManage={Boolean(selectedOrder && manageableService(selectedOrder))}
        canComplete={Boolean(selectedOrder && completableService(selectedOrder))}
        onCheckInOrder={(order) => handleCheckInOrder(order)}
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

      {/* 7. POP-UP PHIẾU SỐ THỨ TỰ SAU KHI XÁC NHẬN CÓ MẶT */}
      <CheckInTicketModal
        isOpen={isTicketModalOpen}
        onClose={() => setIsTicketModalOpen(false)}
        ticketData={ticketModalData}
      />

      {/* 8. MODAL TIẾP NHẬN LẠI BỆNH NHÂN BỎ QUA (UC-46) */}
      <ReacceptSkippedModal
        isOpen={reacceptModalOpen}
        onClose={() => {
          setReacceptModalOpen(false);
          setOrderToReaccept(null);
        }}
        order={orderToReaccept}
        room={currentRoom}
        onConfirmReaccept={handleConfirmReaccept}
      />
    </div>
  );
}

export default TechnicianQueuePage;
