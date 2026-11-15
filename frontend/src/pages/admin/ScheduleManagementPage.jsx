import { useState, useMemo, useEffect, useCallback } from 'react';
import { AdminLayout } from '../../layouts';
import { ConfirmModal } from '../../components/ui';
import { toast } from 'sonner';
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  RotateCcw,
  Copy,
  Clock,
  Eye,
  ShieldCheck,
} from 'lucide-react';
import {
  DutyWeeklyMatrix,
  DutyScheduleModal,
  DutyShiftConfigModal,
} from '../../components/schedules';
import { dutyScheduleApi } from '../../apis/dutyScheduleApi';
import { departmentApi } from '../../apis/departmentApi';
import { apiClient } from '../../services/session';
import { createAccountApi } from '../../apis/accountApi';
import { describeApiError } from '../../utils/describeApiError';
import { getVietnamTodayStr, isShiftPast } from '../../utils/vietnamTimeUtils';

/**
 * Tiện ích xác định ngày Thứ 2 đầu tuần của một mốc ngày bất kỳ.
 * Hỗ trợ nhận diện chuỗi ngày chuẩn ISO ("YYYY-MM-DD") hoặc đối tượng Date.
 * 
 * @param {string | Date} d Mốc ngày cần tính toán
 * @returns {Date} Đối tượng Date đại diện cho ngày Thứ 2 lúc 00:00:00
 */
function getMondayOfDate(d) {
  let date;
  if (typeof d === 'string' && d.includes('-')) {
    const [y, m, day] = d.split('-').map(Number);
    date = new Date(y, m - 1, day);
  } else {
    date = new Date(d);
  }
  const day = date.getDay();
  const diff = date.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(date.setDate(diff));
  monday.setHours(0, 0, 0, 0);
  return monday;
}

/**
 * Tiện ích chuyển đổi đối tượng Date sang chuỗi định dạng chuẩn ISO 'YYYY-MM-DD'.
 *
 * @param {Date} date Đối tượng ngày
 * @returns {string} Chuỗi ngày dạng 'YYYY-MM-DD'
 */
function formatDateISO(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Màn hình Quản lý lịch trực nhân viên (ScheduleManagementPage).
 * 
 * Chức năng chính:
 * 1. Hiển thị ma trận tuần (Thứ 2 -> Chủ Nhật) theo Khoa và Phòng khám.
 * 2. Hỗ trợ lọc ca làm việc: [Tất cả ca], [Ca Sáng], [Ca Chiều].
 * 3. Cho phép Admin phân công ca trực, đổi nhân viên, chỉnh sửa ghi chú và xóa ca trực.
 * 4. Tính năng "Sao chép tuần trước" giúp tạo nhanh lịch cho tuần mới chỉ với 1 click.
 * 5. Tùy chỉnh linh hoạt khung giờ bắt đầu và kết thúc của các ca trực trong tuần.
 * 6. Tích hợp chặt chẽ quy tắc: Ngăn chặn xóa hoặc đổi ca trực khi phòng đang có bệnh nhân.
 */
export function ScheduleManagementPage({
  currentAdmin,
  currentUser,
  onLogout,
  onChangePassword,
  onOpenProfile,
  readOnly = false,
}) {
  const effectiveUser = currentAdmin || currentUser;
  const isAdmin = !readOnly && effectiveUser?.role === 'ADMIN';

  // Current Week Monday (theo chuẩn múi giờ Việt Nam)
  const [currentMonday, setCurrentMonday] = useState(() => getMondayOfDate(getVietnamTodayStr()));

  // Bộ lọc Ca làm việc: 'ALL' (Tất cả ca) | 'MORNING' (Ca Sáng) | 'AFTERNOON' (Ca Chiều)
  const [shiftFilter, setShiftFilter] = useState('ALL');

  // Cấu hình khung giờ ca trực của tuần (Sáng 00:00 - 11:59, Chiều 12:00 - 23:59)
  const [shiftConfig, setShiftConfig] = useState({
    morningStartTime: '08:00',
    morningEndTime: '11:30',
    afternoonStartTime: '13:00',
    afternoonEndTime: '17:30',
  });
  const [shiftConfigModalOpen, setShiftConfigModalOpen] = useState(false);

  // Schedules state
  const [scheduleItems, setScheduleItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [metadataLoading, setMetadataLoading] = useState(true);

  // Available staff & rooms (tải thật từ API, không dùng mock)
  const [staffList, setStaffList] = useState([]);
  const [roomList, setRoomList] = useState([]);

  // Modal states (Admin CRUD)
  const [modalOpen, setModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [modalDefaultDate, setModalDefaultDate] = useState(null);

  // Delete confirm state
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [itemToDelete, setItemToDelete] = useState(null);

  // Copy previous week state
  const [copying, setCopying] = useState(false);
  const [copyConfirmOpen, setCopyConfirmOpen] = useState(false);

  // Calculate 7 days of the week
  const days = useMemo(() => {
    const dayLabels = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ Nhật'];
    return [0, 1, 2, 3, 4, 5, 6].map(offset => {
      const d = new Date(currentMonday);
      d.setDate(d.getDate() + offset);
      const dateStr = formatDateISO(d);
      const dayNum = String(d.getDate()).padStart(2, '0');
      const m = String(d.getMonth() + 1).padStart(2, '0');
      return {
        date: d,
        dateStr,
        label: dayLabels[offset],
        displayDate: `${dayNum}/${m}`,
      };
    });
  }, [currentMonday]);

  const startDateStr = days[0].dateStr;
  const endDateStr = days[6].dateStr;

  // =========================================================================
  // METADATA LOADING (PHÒNG BAN & NHÂN VIÊN)
  // =========================================================================
  /**
   * Tải dữ liệu metadata danh mục Khoa/Phòng và danh sách Bác sĩ / Kỹ thuật viên.
   * - Hỗ trợ tất cả role xem lịch trực (phòng ban được hiển thị cho mọi người dùng).
   * - Phân quyền Admin: Chỉ Admin mới tải toàn bộ tài khoản để phục vụ phân công ca trực trong modal.
   * - Tự động duyệt hết các trang API của `accountApi.list({ page, size: 50 })`.
   * - Lọc nhân viên có vai trò DOCTOR hoặc TECHNICIAN ở trạng thái ACTIVE (không lấy tài khoản bị khóa).
   * - Tự động liên kết nhân viên với phòng khám cố định (theo roomId, roomCode, roomName hoặc headPerson).
   */
  useEffect(() => {
    let isMounted = true;
    async function loadMetadata() {
      try {
        // Tải danh mục khoa/phòng thật và gán roomList ngay lập tức mà không phải chờ duyệt hết danh sách tài khoản
        const deptRes = await departmentApi.getAllItems().catch((err) => {
          console.warn('Không thể tải danh mục khoa phòng qua departmentApi:', err);
          return [];
        });

        const rawRooms = (deptRes?.data || deptRes || []).filter((it) => it.unitType === 'ROOM');

        if (isMounted) {
          setRoomList(rawRooms);
          setMetadataLoading(false);
        }

        // Tải tài khoản nhân viên (chỉ Admin mới tải để gán phân công trong modal)
        if (isAdmin && isMounted) {
          try {
            const accountApi = createAccountApi(apiClient);
            const allAccounts = [];
            for (let page = 0; ; page += 1) {
              const accRes = await accountApi.list({ page, size: 50 });
              const items = accRes?.content || accRes?.data?.content || accRes?.items || [];
              allAccounts.push(...items);
              const totalPages = accRes?.page?.totalPages ?? accRes?.data?.page?.totalPages ?? 1;
              if (page + 1 >= totalPages || items.length === 0) break;
            }

            if (!isMounted) return;

            // Lọc nhân viên có vai trò DOCTOR hoặc TECHNICIAN và tài khoản đang hoạt động (không lấy tài khoản bị khóa)
            const filteredAccounts = allAccounts.filter(
              (a) => (a.role === 'DOCTOR' || a.role === 'TECHNICIAN') && (a.status === 'ACTIVE' || !a.status)
            );
            const dutyStaff = filteredAccounts.map((a) => {
              const matchedRoom = rawRooms.find((r) => {
                if (a.roomId && r.id && String(a.roomId).toLowerCase() === String(r.id).toLowerCase()) return true;
                if (a.roomCode && r.code && a.roomCode.trim().toUpperCase() === r.code.trim().toUpperCase()) return true;
                if (a.roomName && r.name && a.roomName.trim().toLowerCase() === r.name.trim().toLowerCase()) return true;
                if (!a.roomId && r.headPerson && typeof r.headPerson === 'string' && a.fullName) {
                  const staffNames = r.headPerson.split(',').map((sn) => sn.trim().toLowerCase());
                  if (staffNames.some((sn) => sn === a.fullName.trim().toLowerCase())) {
                    return true;
                  }
                }
                return false;
              });
              const titlePrefix = a.academicTitle ? `${a.academicTitle.trim()} ` : '';
              const individualFullName = `${titlePrefix}${a.fullName || a.name || a.username || ''}`.trim();
              return {
                id: a.id,
                fullName: individualFullName,
                role: a.role,
                username: a.username,
                roomId: a.roomId || matchedRoom?.id || null,
                roomCode: a.roomCode || matchedRoom?.code || matchedRoom?.roomCode || null,
                roomName: a.roomName || matchedRoom?.name || matchedRoom?.roomName || null,
                departmentName: a.departmentName || matchedRoom?.departmentName || null,
              };
            });

            setStaffList(dutyStaff);
          } catch (err) {
            console.warn('Không thể tải danh sách tài khoản (chỉ admin):', err);
          }
        }
      } catch (e) {
        console.warn('Lỗi xử lý metadata phòng ban:', e);
        if (isMounted) {
          setMetadataLoading(false);
        }
      }
    }
    void loadMetadata();
    return () => { isMounted = false; };
  }, [isAdmin]);

  // =========================================================================
  // LẤY DỮ LIỆU LỊCH TRỰC TUẦN (DATA FETCHING)
  // =========================================================================
  /**
   * Tải danh sách phân công lịch trực trong tuần (từ Thứ 2 đến Chủ Nhật) và cấu hình ca trực.
   * 
   * @param {boolean} isSilent 
   *        - false: Bật trạng thái loading toàn trang (thường dùng khi chuyển tuần hoặc lần đầu vào trang).
   *        - true: Chế độ ngầm (silent mode) không bật loading skeleton, giúp cập nhật dữ liệu nền
   *                sau khi thêm/sửa/xóa mà không làm giật lag hay chớp nháy giao diện.
   */
  const fetchSchedules = useCallback(async (isSilent = false) => {
    if (!isSilent) {
      setLoading(true);
    }
    try {
      const res = await dutyScheduleApi.getWeeklySchedule(startDateStr, endDateStr);
      const items = res?.items || [];
      setScheduleItems(items);
      if (res?.shiftConfig) {
        setShiftConfig(res.shiftConfig);
      }
    } catch (e) {
      console.error('Failed to load duty schedules:', e);
      toast.error(describeApiError(e, 'Không thể tải lịch trực tuần này'));
    } finally {
      setLoading(false);
    }
  }, [startDateStr, endDateStr]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchSchedules(false);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [fetchSchedules]);

  // =========================================================================
  // ĐIỀU HƯỚNG TUẦN (WEEK NAVIGATION)
  // =========================================================================
  /**
   * Lùi về tuần trước (-7 ngày tính từ Thứ 2 hiện tại).
   */
  const handlePrevWeek = () => {
    const prev = new Date(currentMonday);
    prev.setDate(prev.getDate() - 7);
    setCurrentMonday(prev);
  };

  /**
   * Tiến tới tuần sau (+7 ngày tính từ Thứ 2 hiện tại).
   */
  const handleNextWeek = () => {
    const next = new Date(currentMonday);
    next.setDate(next.getDate() + 7);
    setCurrentMonday(next);
  };

  /**
   * Nhảy về tuần hiện tại (chứa ngày hôm nay theo múi giờ Việt Nam).
   */
  const handleCurrentWeek = () => {
    setCurrentMonday(getMondayOfDate(getVietnamTodayStr()));
  };

  const todayStr = getVietnamTodayStr();

  // =========================================================================
  // XỬ LÝ PHÂN CÔNG & XEM CHI TIẾT CA TRỰC (CRUD MODALS)
  // =========================================================================
  /**
   * Mở modal thêm phân công ca trực mới tại một ô cụ thể (Phòng khám, Ngày trực, Ca trực).
   * 
   * Quy trình nghiệp vụ:
   * 1. Kiểm tra ca quá khứ: Nếu ca đã kết thúc hoặc rơi vào ngày quá khứ -> chặn và cảnh báo toast.
   * 2. Kiểm tra trạng thái phòng: Nếu phòng khám tạm ngưng hoạt động (INACTIVE) -> chặn phân công.
   * 3. Phân quyền:
   *    - Nhân viên thường (readOnly / non-Admin): Chỉ mở modal ở chế độ xem chi tiết.
   *    - Admin: Tiến hành phân loại phòng để chuẩn bị danh sách nhân sự phù hợp:
   *      + Phòng Khám bệnh -> Yêu cầu vai trò Bác sĩ (DOCTOR).
   *      + Phòng Cận lâm sàng / CDHA / Xét nghiệm -> Yêu cầu vai trò Kỹ thuật viên (TECHNICIAN).
   *    - Lọc nghiêm ngặt nhân sự: Chỉ nhân sự được chỉ định vào đúng phòng này (hoặc phụ trách phòng này)
   *      mới xuất hiện trong danh sách lựa chọn.
   * 
   * @param {string | number} roomId ID phòng khám cần phân công
   * @param {string} dateStr Ngày trực dạng YYYY-MM-DD
   * @param {'MORNING' | 'AFTERNOON'} shift Ca trực (Sáng hoặc Chiều)
   */
  const handleOpenAddForSlot = (roomId, dateStr, shift) => {
    if (isShiftPast(dateStr, shift, shiftConfig)) {
      toast.error('Ca trực trong quá khứ chưa phân công không thể thao tác');
      return;
    }

    const targetRoom = roomList.find((r) => String(r.id).toLowerCase() === String(roomId).toLowerCase());
    const isTargetRoomInactive =
      targetRoom?.status === 'INACTIVE' ||
      targetRoom?.status === 'inactive' ||
      targetRoom?.isActive === false;

    if (isTargetRoomInactive) {
      toast.error('Phòng khám đang tạm ngừng hoạt động, không thể phân công ca trực');
      return;
    }

    if (!isAdmin) {
      // Role khác chỉ được phép xem chi tiết ca trực ô này (nếu chưa trong quá khứ)
      setEditingItem({
        id: null,
        workDate: dateStr,
        shift,
        roomId,
        roomName: targetRoom?.name || targetRoom?.roomName || '',
        roomCode: targetRoom?.code || targetRoom?.roomCode || '',
        staffAccountId: '',
        notes: '',
      });
      setModalDefaultDate(dateStr);
      setModalOpen(true);
      return;
    }

    const busyAccountIds = new Set(
      scheduleItems
        .filter((s) => s.workDate === dateStr && s.shift === shift)
        .map((s) => s.staffAccountId)
    );

    const roomCode = (targetRoom?.code || targetRoom?.roomCode || '').toUpperCase();
    const deptName = (targetRoom?.departmentName || '').toLowerCase();
    const roomType = (targetRoom?.type || '').toUpperCase();

    const isParaclinical =
      roomType === 'PARACLINICAL' ||
      deptName.includes('hình ảnh') ||
      deptName.includes('chẩn đoán') ||
      deptName.includes('xét nghiệm') ||
      deptName.includes('cận lâm sàng') ||
      roomCode.startsWith('CDHA') ||
      roomCode.startsWith('XN') ||
      roomCode.startsWith('CLS');

    const requiredRole = isParaclinical ? 'TECHNICIAN' : 'DOCTOR';

    const targetRoomId = roomId ? String(roomId).toLowerCase() : '';
    const staffOfRoom = staffList.filter((s) => {
      if (s.role !== requiredRole) return false;
      const sRoomId = s.roomId ? String(s.roomId).toLowerCase() : '';
      const sCode = (s.roomCode || '').trim().toUpperCase();
      const sName = (s.roomName || '').trim().toLowerCase();
      // Ràng buộc nghiêm ngặt: 1 Bác sĩ / KTV chỉ thuộc về 1 phòng.
      // Nếu nhân viên đã có roomId và roomId đó khác targetRoomId -> tuyệt đối không gán vào phòng này
      if (sRoomId && targetRoomId && sRoomId !== targetRoomId) {
        return false;
      }
      const matchesRoomId = Boolean(sRoomId && targetRoomId && sRoomId === targetRoomId);
      const matchesCode = Boolean(roomCode && sCode && sCode === roomCode);
      const matchesName = Boolean(targetRoom?.name && sName && sName === targetRoom.name.trim().toLowerCase());
      let matchesHeadPerson = false;
      if (!sRoomId && targetRoom?.headPerson && typeof targetRoom.headPerson === 'string' && s.fullName) {
        const names = targetRoom.headPerson.split(',').map((sn) => sn.trim().toLowerCase());
        matchesHeadPerson = names.some((sn) => sn === s.fullName.trim().toLowerCase());
      }
      return matchesRoomId || matchesCode || matchesName || matchesHeadPerson;
    });
    setEditingItem({
      id: null,
      workDate: dateStr,
      shift,
      roomId,
      roomName: targetRoom?.name || targetRoom?.roomName || '',
      roomCode: targetRoom?.code || targetRoom?.roomCode || '',
      staffAccountId: '',
      notes: '',
    });
    setModalDefaultDate(dateStr);
    setModalOpen(true);
  };

  /**
   * Mở modal chỉnh sửa ca trực (Admin) hoặc xem chi tiết ca trực đã phân công (Role khác).
   * 
   * @param {Object} item Thông tin ca trực được click từ ma trận lịch
   */
  const handleOpenEdit = (item) => {
    setEditingItem({
      ...item,
      roomId: item.roomId || '',
    });
    setModalDefaultDate(item.workDate);
    setModalOpen(true);
  };

  /**
   * Lưu phân công ca trực (Thêm mới hoặc Cập nhật ca trực đã có).
   * 
   * Quy trình:
   * 1. Kiểm tra quyền Admin và đảm bảo ca trực không phải ca đã qua giờ/kết thúc.
   * 2. Gọi API `dutyScheduleApi.saveDuty(formData)`.
   * 3. Thực hiện Optimistic Update: Cập nhật ngay bản ghi vừa lưu vào mảng `scheduleItems` trên giao diện
   *    giúp người dùng thấy kết quả ngay tức khắc không có độ trễ.
   * 4. Gọi `fetchSchedules(true)` chạy ngầm (silent) để đồng bộ hoàn chỉnh dữ liệu từ máy chủ.
   * 5. Tự động xử lý lỗi nghiệp vụ BR-113: Nếu phòng khám đang có bệnh nhân chờ hoặc khám dở,
   *    backend từ chối và hiển thị thông báo lỗi rõ ràng qua `describeApiError`.
   * 
   * @param {Object} formData Dữ liệu ca trực từ DutyScheduleModal (id, workDate, shift, roomId, staffAccountId, notes)
   */
  const handleSaveDuty = async (formData) => {
    if (!isAdmin) return;
    if (isShiftPast(formData.workDate, formData.shift, shiftConfig)) {
      toast.error('Không thể phân công hoặc chỉnh sửa ca trực đã kết thúc hoặc trong quá khứ');
      return;
    }
    try {
      const savedRes = await dutyScheduleApi.saveDuty(formData);
      toast.success(formData.id ? 'Đã cập nhật ca trực thành công' : 'Đã phân công ca trực thành công');
      setModalOpen(false);
      setEditingItem(null);

      // Optimistic update of local state to immediately show changes without screen flash
      if (savedRes && savedRes.id) {
        setScheduleItems((prev) => {
          const index = prev.findIndex((it) => it.id === savedRes.id);
          if (index >= 0) {
            const next = [...prev];
            next[index] = savedRes;
            return next;
          }
          return [...prev, savedRes];
        });
      }

      // Silent background sync
      void fetchSchedules(true);
    } catch (e) {
      console.error('Lỗi lưu lịch trực:', e);
      toast.error(describeApiError(e, 'Không thể lưu ca trực'));
    }
  };

  // =========================================================================
  // XỬ LÝ XOÁ CA TRỰC (DELETE HANDLERS - ADMIN ONLY)
  // =========================================================================
  /**
   * Bước 1: Kiểm tra hợp lệ và mở hộp thoại xác nhận xoá ca trực.
   * - Chặn ngay nếu ca trực đã qua giờ hoặc nằm trong quá khứ.
   * 
   * @param {Object} item Ca trực cần xoá
   */
  const handleConfirmDelete = (item) => {
    if (!isAdmin) return;
    if (isShiftPast(item.workDate, item.shift, shiftConfig)) {
      toast.error('Không thể xoá ca trực đã kết thúc hoặc trong quá khứ');
      return;
    }
    setItemToDelete(item);
    setDeleteConfirmOpen(false); // reset if needed
    setDeleteConfirmOpen(true);
  };

  /**
   * Bước 2: Thực thi lệnh xoá ca trực trên Backend sau khi người dùng xác nhận.
   * - Áp dụng cơ chế Optimistic UI: Tạm thời loại bỏ item khỏi danh sách hiển thị trên client.
   * - Gọi API `dutyScheduleApi.deleteDuty(targetId)`.
   * - Nếu thành công: Hiển thị toast thành công và gọi đồng bộ ngầm `fetchSchedules(true)`.
   * - Nếu thất bại (VD: vi phạm BR-113 do phòng đang có bệnh nhân chờ/khám dở):
   *   Hiển thị thông báo lỗi chi tiết và gọi `fetchSchedules(true)` để khôi phục lại ca trực trên UI.
   */
  const executeDeleteDuty = async () => {
    if (!isAdmin || !itemToDelete) return;
    if (isShiftPast(itemToDelete.workDate, itemToDelete.shift, shiftConfig)) {
      toast.error('Không thể xoá ca trực đã kết thúc hoặc trong quá khứ');
      setDeleteConfirmOpen(false);
      setItemToDelete(null);
      return;
    }
    const targetId = itemToDelete.id;
    try {
      // Optimistically remove from state immediately
      setScheduleItems((prev) => prev.filter((it) => it.id !== targetId));
      setDeleteConfirmOpen(false);
      setItemToDelete(null);

      await dutyScheduleApi.deleteDuty(targetId);
      toast.success('Đã xoá ca trực thành công');

      // Silent background sync
      void fetchSchedules(true);
    } catch (e) {
      console.error('Lỗi xoá ca trực:', e);
      toast.error(describeApiError(e, 'Không thể xoá ca trực'));
      // Re-fetch to restore state if delete failed
      void fetchSchedules(true);
    }
  };

  // =========================================================================
  // SAO CHÉP LỊCH TRỰC TỪ TUẦN TRƯỚC (COPY PREVIOUS WEEK - ADMIN ONLY)
  // =========================================================================
  const prevMondayDate = useMemo(() => {
    const d = new Date(currentMonday);
    d.setDate(d.getDate() - 7);
    return d;
  }, [currentMonday]);

  const prevSundayDate = useMemo(() => {
    const d = new Date(prevMondayDate);
    d.setDate(d.getDate() + 6);
    return d;
  }, [prevMondayDate]);

  const prevWeekDisplayStr = useMemo(() => {
    const d1 = String(prevMondayDate.getDate()).padStart(2, '0');
    const m1 = String(prevMondayDate.getMonth() + 1).padStart(2, '0');
    const d2 = String(prevSundayDate.getDate()).padStart(2, '0');
    const m2 = String(prevSundayDate.getMonth() + 1).padStart(2, '0');
    return `${d1}/${m1} - ${d2}/${m2}`;
  }, [prevMondayDate, prevSundayDate]);

  /**
   * Mở modal xác nhận sao chép ca trực từ tuần trước sang tuần này.
   * - Chặn thao tác nếu tuần hiện tại đang xem đã trôi qua hoàn toàn trong quá khứ.
   */
  const handleRequestCopy = () => {
    if (!isAdmin) return;
    if (endDateStr < todayStr) {
      toast.error('Không thể sao chép lịch trực cho tuần đã qua trong quá khứ');
      return;
    }
    setCopyConfirmOpen(true);
  };

  /**
   * Thực thi sao chép toàn bộ phân công của tuần trước sang tuần hiện tại:
   * 1. Tải danh sách ca trực của 7 ngày tuần trước qua `dutyScheduleApi.getWeeklySchedule`.
   * 2. Tự động dịch chuyển ngày của từng ca trực: `itemDate + 7 ngày`.
   * 3. Lọc bỏ các ca không hợp lệ:
   *    - Ca rơi vào ngày/giờ đã qua trong tuần này (`isShiftPast`).
   *    - Ca thuộc phòng khám đang tạm ngừng hoạt động (`INACTIVE`).
   * 4. Gọi API `dutyScheduleApi.batchSaveWeeklySchedule` để lưu hàng loạt các ca trực mới.
   * 5. Làm mới lại danh sách lịch trực hiển thị trên giao diện.
   */
  const executeCopyPreviousWeek = async () => {
    if (!isAdmin) return;
    setCopyConfirmOpen(false);

    const prevStartStr = formatDateISO(prevMondayDate);
    const prevEndStr = formatDateISO(prevSundayDate);

    try {
      setCopying(true);
      const prevRes = await dutyScheduleApi.getWeeklySchedule(prevStartStr, prevEndStr);
      const prevItems = prevRes?.items || [];

      if (prevItems.length === 0) {
        toast.warning('Tuần trước không có ca trực nào để sao chép.');
        return;
      }

      // Convert items to current week (+7 days)
      const newItems = prevItems
        .map(item => {
          const itemDate = new Date(item.workDate);
          itemDate.setDate(itemDate.getDate() + 7);
          const targetDateStr = formatDateISO(itemDate);

          return {
            workDate: targetDateStr,
            shift: item.shift,
            staffAccountId: item.staffAccountId,
            roomId: item.roomId,
            notes: item.notes || '',
          };
        })
        .filter(item => {
          if (isShiftPast(item.workDate, item.shift, shiftConfig)) return false;
          const targetRoom = roomList.find((r) => r.id === item.roomId);
          const isTargetInactive =
            targetRoom?.status === 'INACTIVE' ||
            targetRoom?.status === 'inactive' ||
            targetRoom?.isActive === false;
          return !isTargetInactive;
        });

      if (newItems.length === 0) {
        toast.warning('Tất cả các ngày tương ứng trong tuần này đều đã qua hoặc đã kết thúc, không thể sao chép.');
        return;
      }

      await dutyScheduleApi.batchSaveWeeklySchedule({
        startDate: startDateStr,
        endDate: endDateStr,
        items: newItems,
      });

      toast.success(`Đã sao chép thành công ${newItems.length} ca trực từ tuần trước sang tuần này`);
      void fetchSchedules(true);
    } catch (e) {
      console.error('Lỗi sao chép lịch trực từ tuần trước:', e);
      toast.error(describeApiError(e, 'Không thể sao chép lịch trực từ tuần trước'));
    } finally {
      setCopying(false);
    }
  };

  // =========================================================================
  // CẤU HÌNH KHUNG GIỜ CA TRỰC (SHIFT CONFIG - ADMIN ONLY)
  // =========================================================================
  /**
   * Lưu cấu hình khung giờ làm việc ca sáng và ca chiều trong tuần.
   * 
   * @param {Object} newConfig Cấu hình mới gồm { morningStartTime, morningEndTime, afternoonStartTime, afternoonEndTime }
   */
  const handleSaveShiftConfig = async (newConfig) => {
    try {
      const updated = await dutyScheduleApi.updateShiftConfig(newConfig);
      if (updated) {
        setShiftConfig(updated);
      }
      toast.success('Đã cập nhật khung giờ ca trực cho tuần thành công');
      void fetchSchedules(true);
    } catch (e) {
      console.error('Lỗi cập nhật khung giờ ca trực:', e);
      toast.error(describeApiError(e, 'Không thể cập nhật khung giờ ca trực'));
      throw e;
    }
  };

  const pageContent = (
    <div className="p-3.5 sm:p-5 max-w-full space-y-4">
      {/* Page Header (Chuẩn Image 2: Tiêu đề bên trái, Điều hướng tuần & Sao chép bên phải) */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 pb-1">
        <div className="flex items-start gap-3">
          <div className="p-2.5 rounded-2xl bg-teal-50 text-teal-600 border border-teal-100 shadow-2xs shrink-0">
            <CalendarDays className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <h1 className="text-xl sm:text-2xl font-black text-slate-800 tracking-tight">
                Lịch trực nhân viên
              </h1>
              {isAdmin ? (
                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-teal-700 border border-teal-200">
                  <ShieldCheck className="w-3.5 h-3.5" />
                  Quản trị viên (Toàn quyền thay đổi)
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-slate-100 text-slate-600 border border-slate-200">
                  <Eye className="w-3.5 h-3.5" />
                  Chế độ xem
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500 mt-1">
              {isAdmin
                ? `Quản lý, phân công và thiết lập lịch làm việc theo tuần (Ca Sáng: ${shiftConfig.morningStartTime} - ${shiftConfig.morningEndTime} & Ca Chiều: ${shiftConfig.afternoonStartTime} - ${shiftConfig.afternoonEndTime})`
                : 'Xem lịch phân công làm việc theo tuần của các khoa phòng (Ca Sáng & Ca Chiều)'}
            </p>
          </div>
        </div>

        {/* Nút điều hướng tuần & Nút sao chép tuần trước & Nút chỉnh sửa ca trực (Góc trên bên phải) */}
        <div className="flex items-center gap-2.5 self-start lg:self-center shrink-0 flex-wrap">
          <div className="flex items-center bg-white rounded-full p-1 border border-slate-200/90 shadow-2xs">
            <button
              type="button"
              onClick={handlePrevWeek}
              className="p-1.5 rounded-full text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-all cursor-pointer"
              title="Tuần trước"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={handleCurrentWeek}
              className="px-3 py-1 rounded-full text-xs font-semibold text-slate-700 hover:text-teal-700 hover:bg-teal-50 transition-all flex items-center gap-1 cursor-pointer"
              title="Về tuần hiện tại"
            >
              <RotateCcw className="w-3.5 h-3.5 text-slate-400" />
              <span>Tuần này</span>
            </button>
            <button
              type="button"
              onClick={handleNextWeek}
              className="p-1.5 rounded-full text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-all cursor-pointer"
              title="Tuần sau"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          {isAdmin && (
            <button
              type="button"
              onClick={handleRequestCopy}
              disabled={copying || endDateStr < todayStr}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full border border-teal-300 bg-white hover:bg-teal-50 text-teal-700 text-xs font-semibold transition-all shadow-2xs hover:shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              title="Sao chép toàn bộ phân công ca trực từ tuần trước sang tuần này"
            >
              <Copy className="w-3.5 h-3.5 text-teal-600" />
              <span>{copying ? 'Đang sao chép...' : 'Sao chép tuần trước'}</span>
            </button>
          )}

          {isAdmin && (
            <button
              type="button"
              onClick={() => setShiftConfigModalOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full border border-teal-300 bg-white hover:bg-teal-50 text-teal-700 text-xs font-semibold transition-all shadow-2xs hover:shadow-xs cursor-pointer"
              title="Chỉnh sửa khung giờ ca trực (Ca Sáng & Ca Chiều) cho tuần này"
            >
              <Clock className="w-3.5 h-3.5 text-teal-600" />
              <span>Chỉnh sửa ca trực</span>
            </button>
          )}
        </div>
      </div>

      {/* Card Bộ Lọc Ca làm việc */}
      <div className="bg-white rounded-2xl border border-slate-200/90 p-3.5 sm:p-4 shadow-sm">
        <div className="flex items-center gap-3 flex-wrap text-xs">
          <span className="font-semibold text-slate-700">Ca làm việc:</span>
          <div className="flex items-center gap-2 flex-wrap">
            <button
              type="button"
              onClick={() => setShiftFilter('ALL')}
              className={`px-3.5 py-1 rounded-full text-xs font-semibold transition-all cursor-pointer ${
                shiftFilter === 'ALL'
                  ? 'bg-[#e8f6f3] text-teal-800 border border-teal-500 font-bold shadow-2xs'
                  : 'bg-white text-slate-600 border border-slate-200/90 hover:bg-slate-50 hover:text-slate-900'
              }`}
            >
              [Tất cả ca]
            </button>
            <button
              type="button"
              onClick={() => setShiftFilter('MORNING')}
              className={`px-3.5 py-1 rounded-full text-xs font-semibold transition-all cursor-pointer ${
                shiftFilter === 'MORNING'
                  ? 'bg-amber-50 text-amber-800 border border-amber-500 font-bold shadow-2xs'
                  : 'bg-white text-slate-600 border border-slate-200/90 hover:bg-slate-50 hover:text-slate-900'
              }`}
            >
              {`Ca Sáng (${shiftConfig.morningStartTime} - ${shiftConfig.morningEndTime})`}
            </button>
            <button
              type="button"
              onClick={() => setShiftFilter('AFTERNOON')}
              className={`px-3.5 py-1 rounded-full text-xs font-semibold transition-all cursor-pointer ${
                shiftFilter === 'AFTERNOON'
                  ? 'bg-teal-50 text-teal-800 border border-teal-500 font-bold shadow-2xs'
                  : 'bg-white text-slate-600 border border-slate-200/90 hover:bg-slate-50 hover:text-slate-900'
              }`}
            >
              {`Ca Chiều (${shiftConfig.afternoonStartTime} - ${shiftConfig.afternoonEndTime})`}
            </button>
          </div>
        </div>
      </div>

      {/* Weekly Matrix Schedule Grid */}
      <div className="relative min-h-[350px]">
        {loading && (
          <div className="absolute inset-0 bg-white/75 backdrop-blur-[1px] z-20 flex items-center justify-center rounded-2xl transition-all">
            <div className="flex items-center gap-2.5 px-5 py-2.5 bg-white shadow-md rounded-xl border border-slate-200 text-slate-700 text-xs font-semibold">
              <div className="w-4 h-4 border-2 border-teal-600 border-t-transparent rounded-full animate-spin" />
              <span>Đang tải lịch trực...</span>
            </div>
          </div>
        )}
        {metadataLoading && roomList.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200/90 shadow-sm p-16 flex flex-col items-center justify-center min-h-[350px]">
            <div className="w-8 h-8 border-2 border-teal-600 border-t-transparent rounded-full animate-spin mb-3" />
            <span className="text-slate-600 font-medium text-sm">Đang tải danh mục phòng và lịch trực...</span>
          </div>
        ) : (
          <DutyWeeklyMatrix
            days={days}
            roomList={roomList}
            staffList={staffList}
            scheduleItems={scheduleItems}
            shiftFilter={shiftFilter}
            shiftConfig={shiftConfig}
            canEdit={isAdmin}
            onAddDutySlot={handleOpenAddForSlot}
            onEditDuty={handleOpenEdit}
            onDeleteDuty={handleConfirmDelete}
          />
        )}
      </div>

      {/* Modal Phân công / Chỉnh sửa (Admin) hoặc Xem chi tiết (Role khác) */}
      <DutyScheduleModal
        isOpen={modalOpen}
        readOnly={!isAdmin}
        onClose={() => {
          setModalOpen(false);
          setEditingItem(null);
        }}
        onSave={handleSaveDuty}
        onDelete={handleConfirmDelete}
        initialData={editingItem}
        weekStartDate={modalDefaultDate || startDateStr}
        staffList={staffList}
        roomList={roomList}
        existingSchedules={scheduleItems}
        shiftConfig={shiftConfig}
      />

      {/* Modal Chỉnh sửa khung giờ ca trực (Admin only) */}
      {isAdmin && (
        <DutyShiftConfigModal
          isOpen={shiftConfigModalOpen}
          onClose={() => setShiftConfigModalOpen(false)}
          weekStartDate={startDateStr}
          weekEndDate={endDateStr}
          initialConfig={shiftConfig}
          onSave={handleSaveShiftConfig}
        />
      )}

      {/* Modal Xác nhận Xoá (Admin only) */}
      {isAdmin && (
        <ConfirmModal
          isOpen={deleteConfirmOpen}
          title="Xác nhận xoá ca trực"
          description={`Bạn có chắc chắn muốn huỷ phân công ca trực của ${itemToDelete?.staffName || 'nhân viên này'} vào ngày ${itemToDelete?.workDate || ''}?${
            itemToDelete?.workDate === todayStr
              ? '\n\nLưu ý: Nếu phòng khám đang có bệnh nhân chờ hoặc đang có lượt khám/dịch vụ dở dang trong ca trực hôm nay, hệ thống sẽ từ chối xoá.'
              : ''
          }`}
          confirmText="Xoá ca trực"
          cancelText="Hủy"
          variant="destructive"
          onConfirm={executeDeleteDuty}
          onClose={() => {
            setDeleteConfirmOpen(false);
            setItemToDelete(null);
          }}
        />
      )}

      {/* Modal Xác nhận Sao chép tuần trước (Admin only) */}
      {isAdmin && (
        <ConfirmModal
          isOpen={copyConfirmOpen}
          title="Sao chép lịch trực tuần trước"
          description={`Hệ thống sẽ sao chép toàn bộ phân công ca trực từ tuần trước (${prevWeekDisplayStr}) sang tuần này (${days[0].displayDate} - ${days[6].displayDate}/${days[6].date.getFullYear()}).\n\nLưu ý: Các ca trực đã phân công trước đó trong tuần này (từ hôm nay trở đi) sẽ bị thay thế hoàn toàn. Các ca trực thuộc ngày đã qua trong quá khứ hoặc ca đang có bệnh nhân khám hôm nay sẽ được giữ nguyên không thay đổi.`}
          confirmText="Sao chép ngay"
          cancelText="Hủy"
          variant="mint"
          onConfirm={executeCopyPreviousWeek}
          onClose={() => setCopyConfirmOpen(false)}
        />
      )}
    </div>
  );

  // If user is inside Admin route with currentAdmin prop, wrap with AdminLayout
  if (currentAdmin) {
    return (
      <AdminLayout
        activeKey="schedules"
        currentAdmin={currentAdmin}
        onLogout={onLogout}
        onChangePassword={onChangePassword}
        onOpenProfile={onOpenProfile}
      >
        {pageContent}
      </AdminLayout>
    );
  }

  // Otherwise return standard container
  return pageContent;
}

export default ScheduleManagementPage;
