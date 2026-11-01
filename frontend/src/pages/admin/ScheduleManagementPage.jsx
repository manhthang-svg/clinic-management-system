import { useState, useMemo, useEffect, useCallback } from 'react';
import { AdminLayout } from '../../layouts';
import { ConfirmModal } from '../../components/ui';
import { toast } from 'sonner';
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  Copy,
  Eye,
  ShieldCheck,
} from 'lucide-react';
import {
  DutyWeeklyMatrix,
  DutyScheduleModal,
} from '../../components/schedules';
import { dutyScheduleApi } from '../../apis/dutyScheduleApi';
import { departmentApi } from '../../apis/departmentApi';
import { apiClient } from '../../services/session';
import { createAccountApi } from '../../apis/accountApi';

// Fallback staff list matching database V1000__demo_master_data.sql (Only DOCTOR and TECHNICIAN are assigned to room duty schedules)
const FALLBACK_STAFF = [
  { id: '0192a000-0005-7000-8000-000000000001', fullName: 'BSCKII Trần Văn An', role: 'DOCTOR' },
  { id: '0192a000-0005-7000-8000-000000000002', fullName: 'ThS.BS Lê Thị Bình', role: 'DOCTOR' },
  { id: '0192a000-0005-7000-8000-000000000003', fullName: 'BSCKI Phạm Minh Châu', role: 'DOCTOR' },
  { id: '0192a000-0005-7000-8000-000000000004', fullName: 'KTV Nguyễn Văn Bảo', role: 'TECHNICIAN' },
  { id: '0192a000-0005-7000-8000-000000000005', fullName: 'KTV Trần Thị Cúc', role: 'TECHNICIAN' },
  { id: '0192a000-0005-7000-8000-000000000006', fullName: 'KTV Lê Thị Đào', role: 'TECHNICIAN' },
];

// Fallback rooms list matching database V1000__demo_master_data.sql (16 standard rooms)
const FALLBACK_ROOMS = [
  // Khoa Nội
  {
    id: '0192a000-0004-7000-8000-000000000001',
    code: 'PK-NOI-CXK',
    name: 'Phòng khám Cơ xương khớp',
    departmentId: '0192a000-0001-7000-8000-000000000001',
    departmentName: 'Khoa Nội',
    location: 'Tòa A - Tầng 1 - P.101',
    unitType: 'ROOM',
    type: 'EXAMINATION',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-000000000002',
    code: 'PK-NOI-NT',
    name: 'Phòng khám Nội tiết',
    departmentId: '0192a000-0001-7000-8000-000000000001',
    departmentName: 'Khoa Nội',
    location: 'Tòa A - Tầng 1 - P.102',
    unitType: 'ROOM',
    type: 'EXAMINATION',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-000000000003',
    code: 'PK-NOI-TH',
    name: 'Phòng khám Tiêu hóa',
    departmentId: '0192a000-0001-7000-8000-000000000001',
    departmentName: 'Khoa Nội',
    location: 'Tòa A - Tầng 1 - P.103',
    unitType: 'ROOM',
    type: 'EXAMINATION',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-000000000004',
    code: 'PK-NOI-TQ',
    name: 'Phòng khám Nội tổng quát',
    departmentId: '0192a000-0001-7000-8000-000000000001',
    departmentName: 'Khoa Nội',
    location: 'Tòa A - Tầng 1 - P.104',
    unitType: 'ROOM',
    type: 'EXAMINATION',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-000000000006',
    code: 'PK-NOI-TQ-2',
    name: 'Phòng khám Nội tổng quát 2',
    departmentId: '0192a000-0001-7000-8000-000000000001',
    departmentName: 'Khoa Nội',
    location: 'Tòa A - Tầng 1 - P.105',
    unitType: 'ROOM',
    type: 'EXAMINATION',
    status: 'INACTIVE',
  },
  // Khoa Ngoại
  {
    id: '0192a000-0004-7000-8000-000000000005',
    code: 'PK-NGOAI',
    name: 'Phòng khám Ngoại',
    departmentId: '0192a000-0001-7000-8000-000000000002',
    departmentName: 'Khoa Ngoại',
    location: 'Tòa A - Tầng 2 - P.201',
    unitType: 'ROOM',
    type: 'EXAMINATION',
    status: 'ACTIVE',
  },
  // Khoa Xét nghiệm
  {
    id: '0192a000-0004-7000-8000-000000000007',
    code: 'XN-HH',
    name: 'Phòng Xét nghiệm Huyết học',
    departmentId: '0192a000-0001-7000-8000-000000000003',
    departmentName: 'Khoa Xét nghiệm',
    location: 'Tòa B - Tầng 1 - P.B101',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-000000000008',
    code: 'XN-SH',
    name: 'Phòng Xét nghiệm Sinh hóa',
    departmentId: '0192a000-0001-7000-8000-000000000003',
    departmentName: 'Khoa Xét nghiệm',
    location: 'Tòa B - Tầng 1 - P.B102',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-000000000009',
    code: 'XN-VS',
    name: 'Phòng Xét nghiệm Vi sinh',
    departmentId: '0192a000-0001-7000-8000-000000000003',
    departmentName: 'Khoa Xét nghiệm',
    location: 'Tòa B - Tầng 1 - P.B103',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  // Khoa Chẩn đoán hình ảnh
  {
    id: '0192a000-0004-7000-8000-00000000000a',
    code: 'CDHA-SA-1',
    name: 'Phòng Siêu âm 1',
    departmentId: '0192a000-0001-7000-8000-000000000004',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    location: 'Tòa B - Tầng 2 - P.B201',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-00000000000b',
    code: 'CDHA-SA-2',
    name: 'Phòng Siêu âm 2',
    departmentId: '0192a000-0001-7000-8000-000000000004',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    location: 'Tòa B - Tầng 2 - P.B202',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-00000000000c',
    code: 'CDHA-XQ',
    name: 'Phòng X-quang',
    departmentId: '0192a000-0001-7000-8000-000000000004',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    location: 'Tòa B - Tầng 2 - P.B203',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-00000000000d',
    code: 'CDHA-CT',
    name: 'Phòng CT',
    departmentId: '0192a000-0001-7000-8000-000000000004',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    location: 'Tòa B - Tầng 2 - P.B204',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-00000000000e',
    code: 'CDHA-MRI',
    name: 'Phòng MRI',
    departmentId: '0192a000-0001-7000-8000-000000000004',
    departmentName: 'Khoa Chẩn đoán hình ảnh',
    location: 'Tòa B - Tầng 2 - P.B205',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  // Khoa Thăm dò chức năng
  {
    id: '0192a000-0004-7000-8000-00000000000f',
    code: 'TDCN-NS',
    name: 'Phòng Nội soi',
    departmentId: '0192a000-0001-7000-8000-000000000005',
    departmentName: 'Khoa Thăm dò chức năng',
    location: 'Tòa B - Tầng 3 - P.B301',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
  {
    id: '0192a000-0004-7000-8000-000000000010',
    code: 'TDCN-DT',
    name: 'Phòng Điện tim',
    departmentId: '0192a000-0001-7000-8000-000000000005',
    departmentName: 'Khoa Thăm dò chức năng',
    location: 'Tòa B - Tầng 3 - P.B302',
    unitType: 'ROOM',
    type: 'PARACLINICAL',
    status: 'ACTIVE',
  },
];

function getMondayOfDate(d) {
  const date = new Date(d);
  const day = date.getDay();
  const diff = date.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(date.setDate(diff));
  monday.setHours(0, 0, 0, 0);
  return monday;
}

function formatDateISO(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function ScheduleManagementPage({
  currentAdmin,
  currentUser,
  onLogout,
  onChangePassword,
  readOnly = false,
}) {
  const effectiveUser = currentAdmin || currentUser;
  const isAdmin = !readOnly && effectiveUser?.role === 'ADMIN';

  // Current Week Monday
  const [currentMonday, setCurrentMonday] = useState(() => getMondayOfDate(new Date()));

  // Schedules state
  const [scheduleItems, setScheduleItems] = useState([]);
  const [loading, setLoading] = useState(false);

  // Available staff & rooms
  const [staffList, setStaffList] = useState(FALLBACK_STAFF);
  const [roomList, setRoomList] = useState(FALLBACK_ROOMS);


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

  // Load Staff & Rooms metadata
  useEffect(() => {
    let isMounted = true;
    async function loadMetadata() {
      try {
        const accountApi = createAccountApi(apiClient);
        const accRes = await accountApi.list({ size: 100 });
        const accounts = accRes?.data?.content || accRes?.content || accRes?.items || [];
        if (accounts.length > 0 && isMounted) {
          // Lọc nhân viên có vai trò DOCTOR hoặc TECHNICIAN cho lịch trực phòng
          const dutyStaff = accounts
            .filter(a => a.role === 'DOCTOR' || a.role === 'TECHNICIAN')
            .map(a => ({
              id: a.id,
              fullName: (a.academicTitle ? a.academicTitle + ' ' : '') + a.fullName,
              role: a.role,
              username: a.username,
            }));
          setStaffList(dutyStaff.length > 0 ? dutyStaff : accounts.map(a => ({
            id: a.id,
            fullName: (a.academicTitle ? a.academicTitle + ' ' : '') + a.fullName,
            role: a.role,
            username: a.username,
          })));
        }
      } catch (e) {
        console.warn('Could not fetch staff accounts list, using fallback:', e);
      }

      try {
        const deptRes = await departmentApi.getAllItems();
        const items = deptRes?.data || deptRes || [];
        // CHỈ LẤY PHÒNG (unitType === 'ROOM'), TUYỆT ĐỐI LOẠI BỎ CÁC KHOA (unitType === 'DEPARTMENT')
        const rooms = items.filter(it => it.unitType === 'ROOM');
        if (rooms.length > 0 && isMounted) {
          setRoomList(rooms);
        }
      } catch (e) {
        console.warn('Could not fetch room items list:', e);
      }
    }
    loadMetadata();
    return () => { isMounted = false; };
  }, []);

  // Fetch Schedules for current week (supports silent mode to prevent screen flickering/layout jumps)
  const fetchSchedules = useCallback(async (isSilent = false) => {
    if (!isSilent) {
      setLoading(true);
    }
    try {
      const res = await dutyScheduleApi.getWeeklySchedule(startDateStr, endDateStr);
      setScheduleItems(res?.items || []);
    } catch (e) {
      console.error('Failed to load duty schedules:', e);
      toast.error('Không thể tải lịch trực tuần này.');
    } finally {
      setLoading(false);
    }
  }, [startDateStr, endDateStr]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      // First load or week switch: only show full loading skeleton if we don't have items yet
      void fetchSchedules(false);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [fetchSchedules]);

  // Week Navigation Handlers
  const handlePrevWeek = () => {
    const prev = new Date(currentMonday);
    prev.setDate(prev.getDate() - 7);
    setCurrentMonday(prev);
  };

  const handleNextWeek = () => {
    const next = new Date(currentMonday);
    next.setDate(next.getDate() + 7);
    setCurrentMonday(next);
  };

  const handleCurrentWeek = () => {
    setCurrentMonday(getMondayOfDate(new Date()));
  };

  const todayStr = new Date().toISOString().split('T')[0];

  // Open Add Duty Modal for cell (Admin only)
  const handleOpenAddForCell = (dateStr, shift) => {
    if (!isAdmin) return;
    if (dateStr < todayStr) {
      toast.error('Không thể phân công lịch trực cho ngày trong quá khứ');
      return;
    }
    setEditingItem({
      id: null,
      workDate: dateStr,
      shift,
      staffAccountId: staffList[0]?.id || '',
      roomId: '',
      notes: '',
    });
    setModalDefaultDate(dateStr);
    setModalOpen(true);
  };

  // Open Edit Duty Modal (Admin only)
  const handleOpenEdit = (item) => {
    if (!isAdmin) return;
    if (item.workDate < todayStr) {
      toast.error('Không thể chỉnh sửa lịch trực trong quá khứ');
      return;
    }
    setEditingItem(item);
    setModalDefaultDate(item.workDate);
    setModalOpen(true);
  };

  // Save Duty Handler (Admin only)
  const handleSaveDuty = async (formData) => {
    if (!isAdmin) return;
    if (formData.workDate < todayStr) {
      toast.error('Không thể phân công hoặc chỉnh sửa lịch trực trong quá khứ');
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
      console.error('Failed to save duty:', e);
      toast.error(e?.message || 'Không thể lưu lịch trực');
    }
  };

  // Delete Duty Handlers (Admin only)
  const handleConfirmDelete = (item) => {
    if (!isAdmin) return;
    if (item.workDate < todayStr) {
      toast.error('Không thể xoá lịch trực trong quá khứ');
      return;
    }
    setItemToDelete(item);
    setDeleteConfirmOpen(false); // reset if needed
    setDeleteConfirmOpen(true);
  };

  const executeDeleteDuty = async () => {
    if (!isAdmin || !itemToDelete) return;
    if (itemToDelete.workDate < todayStr) {
      toast.error('Không thể xoá lịch trực trong quá khứ');
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
      console.error('Failed to delete duty:', e);
      toast.error('Không thể xoá ca trực');
      // Re-fetch to restore state if delete failed
      void fetchSchedules(true);
    }
  };

  // Copy previous week handlers (Admin only)
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

  const handleRequestCopy = () => {
    if (!isAdmin) return;
    if (endDateStr < todayStr) {
      toast.error('Không thể sao chép lịch trực cho tuần đã qua trong quá khứ');
      return;
    }
    setCopyConfirmOpen(true);
  };

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
        .filter(item => item.workDate >= todayStr);

      if (newItems.length === 0) {
        toast.warning('Tất cả các ngày tương ứng trong tuần này đều đã qua, không thể sao chép.');
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
      console.error('Failed to copy previous week schedule:', e);
      toast.error(e?.message || 'Không thể sao chép lịch trực từ tuần trước');
    } finally {
      setCopying(false);
    }
  };

  const pageContent = (
    <div className="p-3.5 sm:p-5 max-w-full space-y-4">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pb-1">
        <div>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-teal-50 text-teal-600">
              <CalendarDays className="w-5 h-5 sm:w-6 sm:h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
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
              <p className="text-xs text-slate-500 mt-0.5">
                {isAdmin
                  ? 'Quản lý, phân công và thiết lập lịch làm việc theo tuần (Ca Sáng: 08:00 - 11:30 & Ca Chiều: 13:00 - 17:30)'
                  : 'Xem lịch phân công làm việc theo tuần của các khoa phòng (Ca Sáng & Ca Chiều)'}
              </p>
            </div>
          </div>
        </div>
      </div>


      {/* Week Selector Bar */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-3 sm:p-3.5 shadow-sm flex items-center justify-between flex-wrap gap-3">
        {/* Week Navigation */}
        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center bg-slate-100 rounded-xl p-1 border border-slate-200/60">
            <button
              type="button"
              onClick={handlePrevWeek}
              className="p-1.5 rounded-lg text-slate-600 hover:text-slate-900 hover:bg-white transition-all shadow-xs cursor-pointer"
              title="Tuần trước"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={handleCurrentWeek}
              className="px-3 py-1 rounded-lg text-xs font-bold text-slate-700 hover:text-teal-700 hover:bg-white transition-all flex items-center gap-1 cursor-pointer"
              title="Về tuần hiện tại"
            >
              <RotateCcw className="w-3 h-3 text-slate-400" />
              <span>Tuần này</span>
            </button>
            <button
              type="button"
              onClick={handleNextWeek}
              className="p-1.5 rounded-lg text-slate-600 hover:text-slate-900 hover:bg-white transition-all shadow-xs cursor-pointer"
              title="Tuần sau"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          <div className="text-sm font-bold text-slate-800 px-3 py-1.5 bg-teal-50/50 rounded-xl border border-teal-100/80">
            Tuần: <span className="text-teal-700">{days[0].displayDate}</span> đến{' '}
            <span className="text-teal-700">{days[6].displayDate}/{days[6].date.getFullYear()}</span>
          </div>
        </div>

        {/* Nút Sao chép tuần trước (Bên phải ngoài cùng ngang hàng) */}
        {isAdmin && (
          <button
            type="button"
            onClick={handleRequestCopy}
            disabled={copying || endDateStr < todayStr}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl border border-teal-200 bg-teal-50/80 hover:bg-teal-100/80 text-teal-800 text-xs font-bold transition-all shadow-2xs hover:shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            title="Sao chép toàn bộ phân công ca trực từ tuần trước sang tuần này"
          >
            <Copy className="w-3.5 h-3.5 text-teal-600" />
            <span>{copying ? 'Đang sao chép...' : 'Sao chép tuần trước'}</span>
          </button>
        )}
      </div>

      {/* Weekly Matrix Schedule Grid */}
      <div className="relative">
        {loading && (
          <div className="absolute inset-0 bg-white/60 backdrop-blur-[1px] z-10 flex items-center justify-center rounded-2xl transition-all">
            <div className="flex items-center gap-2 px-4 py-2 bg-white/90 shadow-md rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold">
              <div className="w-4 h-4 border-2 border-teal-600 border-t-transparent rounded-full animate-spin" />
              <span>Đang đồng bộ dữ liệu...</span>
            </div>
          </div>
        )}
        <DutyWeeklyMatrix
          days={days}
          scheduleItems={scheduleItems}
          canEdit={isAdmin}
          onAddDuty={handleOpenAddForCell}
          onEditDuty={handleOpenEdit}
          onDeleteDuty={handleConfirmDelete}
        />
      </div>

      {/* Modal Phân công / Chỉnh sửa (Admin only) */}
      {isAdmin && (
        <DutyScheduleModal
          isOpen={modalOpen}
          onClose={() => {
            setModalOpen(false);
            setEditingItem(null);
          }}
          onSave={handleSaveDuty}
          initialData={editingItem}
          weekStartDate={modalDefaultDate || startDateStr}
          staffList={staffList}
          roomList={roomList}
          existingSchedules={scheduleItems}
        />
      )}

      {/* Modal Xác nhận Xoá (Admin only) */}
      {isAdmin && (
        <ConfirmModal
          isOpen={deleteConfirmOpen}
          title="Xác nhận xoá ca trực"
          description={`Bạn có chắc chắn muốn huỷ phân công ca trực của ${itemToDelete?.staffName || 'nhân viên này'} vào ngày ${itemToDelete?.workDate || ''}?`}
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
          description={`Hệ thống sẽ sao chép toàn bộ phân công ca trực từ tuần trước (${prevWeekDisplayStr}) sang tuần này (${days[0].displayDate} - ${days[6].displayDate}/${days[6].date.getFullYear()}).\n\nLưu ý: Các ca trực thuộc ngày đã qua trong quá khứ sẽ được giữ nguyên không thay đổi.`}
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
      >
        {pageContent}
      </AdminLayout>
    );
  }

  // Otherwise return standard container
  return pageContent;
}

export default ScheduleManagementPage;
