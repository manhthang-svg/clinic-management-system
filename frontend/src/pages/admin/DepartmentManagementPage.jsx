import { useState, useMemo, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { ConfirmModal } from '../../components/ui';
import { toast } from 'sonner';
import {
  DepartmentStatCards,
  RoomFilterBar,
  DepartmentFilterBar,
  RoomTable,
  DepartmentTable,
  DepartmentFormModal,
  DepartmentDetailModal,
  RoomAssignmentDialog,
} from '../../components/departments';
import { departmentApi } from '../../apis/departmentApi';
import { createAccountApi } from '../../apis/accountApi';
import { dutyScheduleApi } from '../../apis/dutyScheduleApi';
import { apiClient } from '../../services/session';
import { getVietnamTodayStr } from '../../utils/vietnamTimeUtils';
import { describeApiError } from '../../utils/describeApiError';
import { Building2, DoorOpen, ChevronRight, House } from 'lucide-react';

const { ADMIN } = ROUTES;
const defaultAccountApi = createAccountApi(apiClient);
const STAFF_PAGE_SIZE = 50;

export function DepartmentManagementPage({ currentAdmin, onLogout, onChangePassword, accountApi = defaultAccountApi }) {
  const navigate = useNavigate();

  // Active Tab: 'rooms' (Mặc định) | 'departments'
  const [activeTab, setActiveTab] = useState('rooms');

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  // Filter & Search states cho TAB 1: PHÒNG CHỨC NĂNG
  const [roomSearchQuery, setRoomSearchQuery] = useState('');
  const [roomDeptFilter, setRoomDeptFilter] = useState('ALL');
  const [roomTypeFilter, setRoomTypeFilter] = useState('ALL');
  const [roomStatusFilter, setRoomStatusFilter] = useState('ALL');
  const [roomPage, setRoomPage] = useState(1);
  const [roomPageSize, setRoomPageSize] = useState(10);

  // Filter & Search states cho TAB 2: CHUYÊN KHOA
  const [deptSearchQuery, setDeptSearchQuery] = useState('');
  const [deptTypeFilter, setDeptTypeFilter] = useState('ALL');
  const [deptStatusFilter, setDeptStatusFilter] = useState('ALL');
  const [deptPage, setDeptPage] = useState(1);
  const [deptPageSize, setDeptPageSize] = useState(10);

  // Modal states
  const [formModalOpen, setFormModalOpen] = useState(false);
  const [formMode, setFormMode] = useState('CREATE'); // 'CREATE' | 'EDIT'
  const [formTargetType, setFormTargetType] = useState('ROOM'); // 'ROOM' | 'DEPARTMENT'
  const [editingItem, setEditingItem] = useState(null);

  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [viewingItem, setViewingItem] = useState(null);

  const [confirmDialog, setConfirmDialog] = useState(null);
  const [roomAssignment, setRoomAssignment] = useState(null);
  const [staffOptions, setStaffOptions] = useState([]);
  const [selectedStaffIds, setSelectedStaffIds] = useState([]);
  const [staffLoading, setStaffLoading] = useState(false);

  // Fetch data từ backend
  const fetchData = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoading(true);
      const todayStr = getVietnamTodayStr();
      const [fetchedItems, dutyData] = await Promise.all([
        departmentApi.getItems(),
        dutyScheduleApi.getWeeklySchedule(todayStr, todayStr).catch((scheduleErr) => {
          console.warn('Không thể tải lịch trực hôm nay:', scheduleErr);
          return null;
        }),
      ]);

      // Xây dựng map nhân sự (bác sĩ & KTV) trực hôm nay theo phòng
      const staffDutyMap = {};
      if (dutyData?.items && Array.isArray(dutyData.items)) {
        dutyData.items.forEach((item) => {
          if (
            item.roomId &&
            item.workDate === todayStr &&
            (item.staffRole === 'DOCTOR' || item.staffRole === 'TECHNICIAN')
          ) {
            if (!staffDutyMap[item.roomId]) {
              staffDutyMap[item.roomId] = [];
            }
            if (item.staffName && !staffDutyMap[item.roomId].includes(item.staffName)) {
              staffDutyMap[item.roomId].push(item.staffName);
            }
          }
        });
      }

      if (Array.isArray(fetchedItems)) {
        const mergedItems = fetchedItems.map((it) => {
          if (it.unitType !== 'ROOM') return it;
          const scheduledStaff = staffDutyMap[it.id];
          const hasStaffScheduled = Boolean(
            it.hasDoctorOnDutyToday || (scheduledStaff && scheduledStaff.length > 0)
          );
          const staffName =
            (scheduledStaff && scheduledStaff.length > 0
              ? scheduledStaff.join(', ')
              : null) || it.doctorOnDutyToday;
          return {
            ...it,
            hasDoctorOnDutyToday: hasStaffScheduled,
            doctorOnDutyToday: staffName,
            hasFutureDutySchedules: Boolean(it.hasFutureDutySchedules),
            hasActiveVisitsToday: Boolean(it.hasActiveVisitsToday),
          };
        });
        setItems(mergedItems);
      }
    } catch (err) {
      console.error('Lỗi tải dữ liệu khoa/phòng:', err);
      toast.error(describeApiError(err, 'Không thể tải danh sách khoa/phòng'));
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchData(false);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [fetchData]);

  // Danh sách các chuyên khoa để hiển thị dropdown
  const departmentsList = useMemo(() => {
    return items.filter((i) => i.unitType === 'DEPARTMENT');
  }, [items]);

  // Danh sách toàn bộ các phòng
  const allRoomsList = useMemo(() => {
    return items.filter((i) => i.unitType === 'ROOM');
  }, [items]);

  // Tính toán số liệu thống kê động
  const currentStats = useMemo(() => {
    const totalDepts = departmentsList.length;
    const clinicalDepts = departmentsList.filter((dept) => dept.type === 'CLINICAL').length;
    const paraclinicalDepts = departmentsList.filter((dept) => dept.type === 'PARACLINICAL').length;

    const totalRooms = allRoomsList.length;
    const clinicalRooms = allRoomsList.filter((i) => i.type === 'EXAMINATION').length;
    const paraclinicalRooms = allRoomsList.filter((i) => i.type === 'PARACLINICAL').length;
    const activeRooms = allRoomsList.filter((i) => i.status === 'ACTIVE').length;
    const maintenanceRooms = totalRooms - activeRooms;
    const activeRate = totalRooms > 0 ? Number(((activeRooms / totalRooms) * 100).toFixed(1)) : 0;
    const maintenanceNote = maintenanceRooms > 0
      ? `${maintenanceRooms} phòng đang tạm ngưng`
      : 'Tất cả phòng hoạt động bình thường';

    return {
      totalDepartments: totalDepts,
      clinicalDepartments: clinicalDepts,
      paraclinicalDepartments: paraclinicalDepts,
      totalRooms: totalRooms,
      clinicalRooms: clinicalRooms,
      paraclinicalRooms: paraclinicalRooms,
      activeRooms: activeRooms,
      maintenanceRooms: maintenanceRooms,
      activeRate: activeRate,
      maintenanceNote: maintenanceNote,
      activeCount: activeRooms,
      maintenanceCount: maintenanceRooms,
    };
  }, [departmentsList, allRoomsList]);

  // Dữ liệu lọc cho TAB 1: PHÒNG CHỨC NĂNG
  const filteredRooms = useMemo(() => {
    const query = roomSearchQuery.trim().toLowerCase();
    return allRoomsList.filter((room) => {
      // Tìm kiếm theo mã, tên, khoa, vị trí, người phụ trách
      const matchSearch =
        !query ||
        room.code?.toLowerCase().includes(query) ||
        room.name?.toLowerCase().includes(query) ||
        room.departmentName?.toLowerCase().includes(query) ||
        room.location?.toLowerCase().includes(query) ||
        room.headPerson?.toLowerCase().includes(query);

      // Lọc theo Chuyên khoa
      const matchDept =
        roomDeptFilter === 'ALL' ||
        room.departmentId === roomDeptFilter ||
        room.departmentName === roomDeptFilter;

      // Lọc theo Loại phòng
      const matchType = roomTypeFilter === 'ALL' || room.type === roomTypeFilter;

      // Lọc theo Trạng thái
      const matchStatus = roomStatusFilter === 'ALL' || room.status === roomStatusFilter;

      return matchSearch && matchDept && matchType && matchStatus;
    });
  }, [allRoomsList, roomSearchQuery, roomDeptFilter, roomTypeFilter, roomStatusFilter]);

  // Phân trang cho Phòng
  const totalRoomPages = Math.max(1, Math.ceil(filteredRooms.length / roomPageSize));
  const currentRoomPage = Math.min(roomPage, totalRoomPages);
  const pagedRooms = useMemo(() => {
    const start = (currentRoomPage - 1) * roomPageSize;
    return filteredRooms.slice(start, start + roomPageSize);
  }, [filteredRooms, currentRoomPage, roomPageSize]);

  // Dữ liệu lọc cho TAB 2: CHUYÊN KHOA
  const filteredDepartments = useMemo(() => {
    const query = deptSearchQuery.trim().toLowerCase();
    return departmentsList.filter((dept) => {
      // Tìm kiếm theo mã khoa, tên chuyên khoa
      const matchSearch =
        !query ||
        dept.code?.toLowerCase().includes(query) ||
        dept.name?.toLowerCase().includes(query) ||
        dept.description?.toLowerCase().includes(query);

      // Lọc theo Loại khoa (RỦI RO 6 / UC-12)
      const matchType = deptTypeFilter === 'ALL' || dept.type === deptTypeFilter;

      // Lọc theo Trạng thái
      const matchStatus = deptStatusFilter === 'ALL' || dept.status === deptStatusFilter;

      return matchSearch && matchType && matchStatus;
    });
  }, [departmentsList, deptSearchQuery, deptTypeFilter, deptStatusFilter]);

  // Phân trang cho Chuyên khoa
  const totalDeptPages = Math.max(1, Math.ceil(filteredDepartments.length / deptPageSize));
  const currentDeptPage = Math.min(deptPage, totalDeptPages);
  const pagedDepartments = useMemo(() => {
    const start = (currentDeptPage - 1) * deptPageSize;
    return filteredDepartments.slice(start, start + deptPageSize);
  }, [filteredDepartments, currentDeptPage, deptPageSize]);

  // Đặt lại bộ lọc Tab 1 (Phòng)
  const handleResetRoomFilters = () => {
    setRoomSearchQuery('');
    setRoomDeptFilter('ALL');
    setRoomTypeFilter('ALL');
    setRoomStatusFilter('ALL');
    setRoomPage(1);
  };

  // Đặt lại bộ lọc Tab 2 (Khoa)
  const handleResetDeptFilters = () => {
    setDeptSearchQuery('');
    setDeptTypeFilter('ALL');
    setDeptStatusFilter('ALL');
    setDeptPage(1);
  };

  // Mở modal Thêm phòng mới
  const handleOpenCreateRoom = () => {
    setFormTargetType('ROOM');
    setFormMode('CREATE');
    setEditingItem(null);
    setFormModalOpen(true);
  };

  // Mở modal Thêm chuyên khoa
  const handleOpenCreateDept = () => {
    setFormTargetType('DEPARTMENT');
    setFormMode('CREATE');
    setEditingItem(null);
    setFormModalOpen(true);
  };

  // Mở modal Chỉnh sửa
  const handleOpenEdit = (item) => {
    setFormTargetType(item.unitType);
    setFormMode('EDIT');
    setEditingItem(item);
    setFormModalOpen(true);
  };

  // Mở modal Xem chi tiết
  const handleOpenView = (item) => {
    setViewingItem(item);
    setDetailModalOpen(true);
  };

  const handleOpenRoomAssignment = async (room) => {
    const role = room.type === 'EXAMINATION' ? 'DOCTOR' : 'TECHNICIAN';
    setRoomAssignment(room);
    setSelectedStaffIds([]);
    setStaffOptions([]);
    setStaffLoading(true);
    try {
      const accounts = [];
      for (let page = 0; ; page += 1) {
        const result = await accountApi.list({ role, status: 'ACTIVE', page, size: STAFF_PAGE_SIZE });
        accounts.push(...(result.content || []));
        if (page + 1 >= (result.page?.totalPages || 1)) break;
      }
      setStaffOptions(accounts);
    } catch (err) {
      setRoomAssignment(null);
      toast.error(describeApiError(err, 'Không thể tải danh sách nhân sự'));
    } finally {
      setStaffLoading(false);
    }
  };

  const handleConfirmRoomAssignment = async () => {
    if (!roomAssignment || selectedStaffIds.length === 0) {
      toast.error('Vui lòng chọn nhân sự cần phân phòng');
      return;
    }
    // The API assigns one account per call, so the selected staff are assigned one after another;
    // a rejection for one person does not stop the others.
    const assigned = [];
    const failedIds = [];
    for (const staffId of selectedStaffIds) {
      const staff = staffOptions.find((option) => option.id === staffId);
      const staffName = staff?.fullName || staff?.username || 'nhân sự';
      try {
        await accountApi.assignRoom(staffId, { roomId: roomAssignment.id });
        assigned.push(staffName);
      } catch (err) {
        failedIds.push(staffId);
        if (err?.errorResponse?.code === 'STAFF_HAS_DUTY_SCHEDULES') {
          // MSG83 (BR-55).
          const shiftCount = err.errorResponse.details?.shiftCount;
          toast.error(
            `${staffName} còn ${shiftCount ?? 'một số'} ca làm việc từ hôm nay trở đi nên chưa thể đổi hoặc rút khỏi phòng. Vui lòng xóa các ca của những ngày tới; ca của hôm nay chỉ có thể đổi từ ngày mai.`
          );
        } else {
          toast.error(describeApiError(err, `Không thể phân phòng cho ${staffName}`));
        }
      }
    }

    if (assigned.length > 0) {
      toast.success(`Đã phân ${assigned.join(', ')} vào phòng "${roomAssignment.name}"`);
      fetchData(true);
    }
    if (failedIds.length === 0) {
      setRoomAssignment(null);
      setSelectedStaffIds([]);
      setStaffOptions([]);
      return;
    }
    // Keep the dialog open with only the staff that failed still selected; the assigned ones
    // move to the room's assigned list.
    const assignedIds = selectedStaffIds.filter((id) => !failedIds.includes(id));
    setStaffOptions((prev) =>
      prev.map((option) =>
        assignedIds.includes(option.id)
          ? {
              ...option,
              roomId: roomAssignment.id,
              roomCode: roomAssignment.code,
              roomName: roomAssignment.name,
            }
          : option
      )
    );
    setSelectedStaffIds(failedIds);
  };

  // Tạm ngưng / Kích hoạt lại (Soft delete)
  const handleToggleStatus = (item) => {
    const isRoom = item.unitType === 'ROOM';
    const isDeactivating = item.status === 'ACTIVE';

    if (!isRoom) {
      // Đang thao tác trên CHUYÊN KHOA
      const deptRooms = allRoomsList.filter(
        (r) =>
          r.departmentId === item.id ||
          r.departmentId === item.departmentId ||
          r.departmentName === item.name ||
          (item.code && r.departmentCode === item.code)
      );
      const activeDeptRoomsCount = deptRooms.filter((r) => r.status === 'ACTIVE').length;

      if (isDeactivating) {
        // RỦI RO 3: Chặn nếu ngưng chuyên khoa làm tắt toàn bộ phòng khám lâm sàng còn lại trong cơ sở
        const activeExamRoomsInDept = deptRooms.filter(
          (r) => r.type === 'EXAMINATION' && r.status === 'ACTIVE'
        ).length;
        const totalActiveExamRooms = allRoomsList.filter(
          (r) => r.type === 'EXAMINATION' && r.status === 'ACTIVE'
        ).length;
        if (activeExamRoomsInDept > 0 && totalActiveExamRooms <= activeExamRoomsInDept) {
          toast.error(
            `Không thể tạm ngưng chuyên khoa "${item.name}": thao tác này sẽ ngừng hoạt động toàn bộ phòng khám lâm sàng còn lại trong cơ sở.`
          );
          return;
        }

        // Cảnh báo khi ngưng hoạt động khoa (Cascade ngưng hoạt động toàn bộ phòng trực thuộc)
        setConfirmDialog({
          title: 'Khóa chuyên khoa',
          description: activeDeptRoomsCount > 0
            ? `Chuyên khoa "${item.name}" đang có ${activeDeptRoomsCount} phòng hoạt động.\n\nKhi tạm ngưng chuyên khoa, toàn bộ các phòng trực thuộc sẽ tự động tạm ngưng hoạt động.\nLưu ý: Hệ thống sẽ từ chối nếu có bất kỳ phòng nào đang có bệnh nhân chờ, đang khám dở hoặc còn lịch trực trong các ngày tới.\n\nBạn có chắc chắn muốn tiếp tục?`
            : `Bạn có chắc muốn tạm ngưng chuyên khoa "${item.name}"?\nBạn sẽ không thể tạo thêm phòng mới thuộc chuyên khoa này.`,
          confirmText: 'Khóa chuyên khoa',
          variant: 'destructive',
          onConfirm: async () => {
            try {
              await departmentApi.toggleDepartmentStatus(item.id);
              // Cập nhật chuyên khoa và toàn bộ phòng trực thuộc sang INACTIVE trên giao diện
              setItems((prev) =>
                prev.map((i) => {
                  if (i.id === item.id && i.unitType === 'DEPARTMENT') {
                    return { ...i, status: 'INACTIVE' };
                  }
                  if (
                    i.unitType === 'ROOM' &&
                    (i.departmentId === item.id ||
                      i.departmentId === item.departmentId ||
                      i.departmentName === item.name)
                  ) {
                    return { ...i, status: 'INACTIVE' };
                  }
                  return i;
                })
              );
              toast.success(
                `Đã tạm ngưng chuyên khoa "${item.name}" và các phòng trực thuộc!`
              );
              setConfirmDialog(null);
              fetchData();
            } catch (err) {
              console.error('Lỗi tạm ngưng chuyên khoa:', err);
              toast.error(describeApiError(err, 'Không thể tạm ngưng chuyên khoa'));
            }
          },
        });
        return;
      } else {
        // Mở khóa chuyên khoa
        setConfirmDialog({
          title: 'Mở khóa chuyên khoa',
          description: `Kích hoạt lại chuyên khoa "${item.name}"? Bạn sẽ có thể tạo thêm phòng mới thuộc chuyên khoa này.`,
          confirmText: 'Kích hoạt lại',
          variant: 'mint',
          onConfirm: async () => {
            try {
              await departmentApi.toggleDepartmentStatus(item.id);
              // Chuyển chuyên khoa sang ACTIVE trên giao diện
              setItems((prev) =>
                prev.map((i) =>
                  i.id === item.id && i.unitType === 'DEPARTMENT'
                    ? { ...i, status: 'ACTIVE' }
                    : i
                )
              );
              toast.success(`Đã kích hoạt lại chuyên khoa "${item.name}"!`);
              setConfirmDialog(null);
              fetchData();
            } catch (err) {
              console.error('Lỗi kích hoạt chuyên khoa:', err);
              toast.error(describeApiError(err, 'Không thể kích hoạt lại chuyên khoa'));
            }
          },
        });
        return;
      }
    }

    // Trường hợp là ROOM (Phòng chức năng)
    if (isDeactivating && (item.hasDoctorOnDutyToday || item.doctorOnDutyToday)) {
      const isExam = item.type === 'EXAMINATION';
      const roleLabel = isExam ? 'bác sĩ' : 'bác sĩ/kỹ thuật viên';
      toast.error(
        `Không được phép tạm ngừng phòng "${item.name}" vì phòng đang có ${roleLabel} trực trong ngày${
          item.doctorOnDutyToday ? ` (${item.doctorOnDutyToday})` : ''
        }!`
      );
      return;
    }

    if (isDeactivating && item.hasActiveVisitsToday) {
      toast.error(
        item.type === 'EXAMINATION'
          ? `Không thể tạm ngưng phòng "${item.name}": phòng vẫn còn bệnh nhân đang chờ hoặc đang khám dở trong ngày hôm nay.`
          : `Không thể tạm ngưng phòng "${item.name}": phòng vẫn còn dịch vụ cận lâm sàng đang chờ hoặc đang xử lý.`
      );
      return;
    }

    if (isDeactivating && item.hasFutureDutySchedules) {
      toast.error(
        `Không thể tạm ngưng phòng "${item.name}": phòng vẫn còn nhân viên được xếp ca trực trong các ngày tới. Vui lòng điều chỉnh lịch trực trước.`
      );
      return;
    }

    // RỦI RO 3 (EF-06 / MSG94): Chặn nếu là phòng khám lâm sàng duy nhất còn hoạt động
    if (isDeactivating && item.type === 'EXAMINATION') {
      const activeExamRooms = allRoomsList.filter(
        (r) => r.type === 'EXAMINATION' && r.status === 'ACTIVE'
      );
      if (activeExamRooms.length <= 1) {
        toast.error(
          `Không thể tạm ngưng phòng "${item.name}": đây là phòng khám duy nhất còn hoạt động nên không thể ngừng hoạt động.`
        );
        return;
      }
    }

    setConfirmDialog({
      title: isDeactivating ? 'Tạm ngưng hoạt động phòng' : 'Kích hoạt lại phòng khám',
      description: isDeactivating
        ? `Bạn có chắc muốn tạm ngưng "${item.name}" (${item.code})?\nĐơn vị sẽ không hiển thị khi phân bổ lịch khám mới.\n\nLưu ý: Hệ thống sẽ từ chối nếu phòng đang có bệnh nhân chờ, đang khám dở hoặc vẫn còn ca trực trong các ngày tới.`
        : `Kích hoạt lại "${item.name}" (${item.code}) để sẵn sàng phục vụ tiếp nhận bệnh nhân?`,
      confirmText: isDeactivating ? 'Tạm ngưng' : 'Kích hoạt',
      variant: isDeactivating ? 'destructive' : 'mint',
      onConfirm: async () => {
        try {
          await departmentApi.toggleRoomStatus(item.id);
          const newStatus = isDeactivating ? 'INACTIVE' : 'ACTIVE';
          setItems((prev) =>
            prev.map((i) =>
              i.id === item.id && i.unitType === 'ROOM' ? { ...i, status: newStatus } : i
            )
          );
          toast.success(
            isDeactivating
              ? `Đã tạm ngưng "${item.name}" thành công!`
              : `Đã kích hoạt "${item.name}" thành công!`
          );
          setConfirmDialog(null);
        } catch (err) {
          console.error('Lỗi đổi trạng thái phòng:', err);
          toast.error(describeApiError(err, 'Không thể thay đổi trạng thái phòng'));
        }
      },
    });
  };

  // Lưu form Thêm mới hoặc Chỉnh sửa
  const handleSaveForm = async (formData, unitType, isEdit) => {
    const isDept = unitType === 'DEPARTMENT';
    try {
      if (isDept) {
        if (isEdit) {
          const res = await departmentApi.updateDepartment(editingItem.id, {
            departmentName: formData.name,
            tenkhoa: formData.name,
            type: editingItem?.type || formData.type,
            description: formData.description,
            active: formData.status === 'ACTIVE',
            version: formData.version,
          });
          setItems((prev) =>
            prev.map((i) =>
              i.id === editingItem.id && i.unitType === 'DEPARTMENT'
                ? {
                    ...i,
                    ...res,
                  }
                : i
            )
          );
          toast.success(`Đã cập nhật chuyên khoa "${formData.name}" thành công!`);
        } else {
          const res = await departmentApi.createDepartment({
            departmentCode: formData.code,
            departmentName: formData.name,
            makhoa: formData.code,
            tenkhoa: formData.name,
            type: formData.type,
            description: formData.description,
            active: formData.status === 'ACTIVE',
          });
          setItems((prev) => [res, ...prev]);
          toast.success(`Đã thêm mới chuyên khoa "${formData.name}" thành công!`);
        }
      } else {
        // Handle ROOM
        if (isEdit) {
          if (
            editingItem?.status === 'ACTIVE' &&
            formData.status === 'INACTIVE' &&
            (editingItem?.hasDoctorOnDutyToday || editingItem?.doctorOnDutyToday)
          ) {
            toast.error(
              `Không được phép tạm ngừng phòng "${editingItem.name}" vì phòng đang có bác sĩ trực trong ngày${
                editingItem.doctorOnDutyToday ? ` (${editingItem.doctorOnDutyToday})` : ''
              }!`
            );
            return;
          }

          if (editingItem?.status === 'ACTIVE' && formData.status === 'INACTIVE' && editingItem?.hasActiveVisitsToday) {
            toast.error(
              editingItem.type === 'EXAMINATION'
                ? `Không thể tạm ngưng phòng "${editingItem.name}": phòng vẫn còn bệnh nhân đang chờ hoặc đang khám dở trong ngày hôm nay.`
                : `Không thể tạm ngưng phòng "${editingItem.name}": phòng vẫn còn dịch vụ cận lâm sàng đang chờ hoặc đang xử lý.`
            );
            return;
          }

          if (editingItem?.status === 'ACTIVE' && formData.status === 'INACTIVE' && editingItem?.hasFutureDutySchedules) {
            toast.error(
              `Không thể tạm ngưng phòng "${editingItem.name}": phòng vẫn còn nhân viên được xếp ca trực trong các ngày tới. Vui lòng điều chỉnh lịch trực trước.`
            );
            return;
          }

          const res = await departmentApi.updateRoom(editingItem.id, {
            roomName: formData.name,
            phongkham: formData.name,
            departmentId: formData.departmentId,
            serviceGroupId: formData.serviceGroupId || null,
            type: formData.type,
            location: formData.location,
            status: formData.status,
            version: formData.version,
          });
          setItems((prev) =>
            prev.map((i) =>
              i.id === editingItem.id && i.unitType === 'ROOM'
                ? {
                    ...i,
                    ...res,
                  }
                : i
            )
          );
          toast.success(`Đã cập nhật phòng "${formData.name}" thành công!`);
        } else {
          const res = await departmentApi.createRoom({
            roomCode: formData.code,
            roomName: formData.name,
            maphongkham: formData.code,
            phongkham: formData.name,
            departmentId: formData.departmentId,
            serviceGroupId: formData.serviceGroupId || null,
            type: formData.type,
            location: formData.location,
            status: formData.status,
          });
          setItems((prev) => [
            res,
            ...prev.map((i) =>
              i.id === res.departmentId && i.unitType === 'DEPARTMENT'
                ? { ...i, roomCount: (i.roomCount || 0) + 1 }
                : i
            ),
          ]);
          toast.success(`Đã thêm mới phòng "${formData.name}" thành công!`);
        }
      }
    } catch (err) {
      console.error('Lỗi lưu form khoa/phòng:', err);
      toast.error(describeApiError(err, isEdit ? 'Không thể cập nhật' : 'Không thể thêm mới'));
      throw err;
    }
  };

  const handleSelectDepartmentRooms = (dept) => {
    setActiveTab('rooms');
    setRoomDeptFilter(dept.id || dept.name);
    setRoomSearchQuery('');
    setRoomTypeFilter('ALL');
    setRoomStatusFilter('ALL');
    setRoomPage(1);
  };

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    onNavigate: (key) => {
      if (key === 'accounts') {
        navigate(ADMIN.ACCOUNTS);
      } else if (key === 'departments' || key === 'rooms') {
        setActiveTab('rooms');
        navigate(ADMIN.DEPARTMENTS);
      } else if (ADMIN[key.toUpperCase()]) {
        navigate(ADMIN[key.toUpperCase()]);
      }
    },
    activeKey: 'departments',
  };

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        {/* Breadcrumb & Header */}
        <div className="space-y-1.5">
          <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-slate-400">
            <button
              type="button"
              onClick={() => navigate(ADMIN.ACCOUNTS)}
              className="hover:text-slate-600 transition-colors cursor-pointer flex items-center gap-1"
            >
              <House className="w-3.5 h-3.5" />
            </button>
            <ChevronRight className="w-3.5 h-3.5" />
            <span className="font-semibold text-slate-700">Khoa / Phòng</span>
          </nav>

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1">
            <div>
              <h1 className="text-2xl font-extrabold text-[#142534] tracking-tight">
                Quản lý khoa/phòng
              </h1>
              <p className="text-xs text-slate-500 mt-1">
                Quản lý danh sách khoa chuyên môn và phòng chức năng của bệnh viện
              </p>
            </div>
          </div>
        </div>

        {/* 4 Thẻ thống kê KPI */}
        <DepartmentStatCards stats={currentStats} />

        {/* Container Bảng dữ liệu với Tabs phân tách */}
        <section className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
          {/* Tabs phân tách ở ngay trên bảng dữ liệu */}
          <div className="flex items-center gap-2 border-b border-slate-200/80 pb-0">
            {/* Tab 1: Phòng chức năng (Mặc định) */}
            <button
              type="button"
              onClick={() => setActiveTab('rooms')}
              className={`pb-3 px-4 text-xs sm:text-sm font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer ${
                activeTab === 'rooms'
                  ? 'border-[#127e7a] text-[#127e7a]'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              <DoorOpen className="w-4 h-4" />
              <span>Phòng chức năng</span>
              <span
                className={`ml-1 px-2 py-0.5 text-[11px] rounded-full font-semibold ${
                  activeTab === 'rooms'
                    ? 'bg-teal-50 text-[#127e7a] border border-teal-200/60'
                    : 'bg-slate-100 text-slate-600'
                }`}
              >
                {allRoomsList.length}
              </span>
            </button>

            {/* Tab 2: Chuyên khoa */}
            <button
              type="button"
              onClick={() => setActiveTab('departments')}
              className={`pb-3 px-4 text-xs sm:text-sm font-bold flex items-center gap-2 border-b-2 transition-all cursor-pointer ${
                activeTab === 'departments'
                  ? 'border-[#127e7a] text-[#127e7a]'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              <Building2 className="w-4 h-4" />
              <span>Chuyên khoa</span>
              <span
                className={`ml-1 px-2 py-0.5 text-[11px] rounded-full font-semibold ${
                  activeTab === 'departments'
                    ? 'bg-teal-50 text-[#127e7a] border border-teal-200/60'
                    : 'bg-slate-100 text-slate-600'
                }`}
              >
                {departmentsList.length}
              </span>
            </button>
          </div>

          {/* Nội dung TAB 1: PHÒNG CHỨC NĂNG */}
          {activeTab === 'rooms' && (
            <div className="space-y-4 animate-in fade-in duration-150">
              {/* Bộ lọc Tab Phòng */}
              <RoomFilterBar
                searchQuery={roomSearchQuery}
                setSearchQuery={(val) => {
                  setRoomSearchQuery(val);
                  setRoomPage(1);
                }}
                departmentFilter={roomDeptFilter}
                setDepartmentFilter={(val) => {
                  setRoomDeptFilter(val);
                  setRoomPage(1);
                }}
                roomTypeFilter={roomTypeFilter}
                setRoomTypeFilter={(val) => {
                  setRoomTypeFilter(val);
                  setRoomPage(1);
                }}
                statusFilter={roomStatusFilter}
                setStatusFilter={(val) => {
                  setRoomStatusFilter(val);
                  setRoomPage(1);
                }}
                departments={departmentsList}
                onReset={handleResetRoomFilters}
                onAdd={handleOpenCreateRoom}
              />

              {/* Bảng dữ liệu Phòng */}
              <RoomTable
                items={pagedRooms}
                totalCount={filteredRooms.length}
                page={currentRoomPage}
                pageSize={roomPageSize}
                onPageChange={setRoomPage}
                onPageSizeChange={(size) => {
                  setRoomPageSize(size);
                  setRoomPage(1);
                }}
                onView={handleOpenView}
                onEdit={handleOpenEdit}
                onAssignRoom={handleOpenRoomAssignment}
                onToggleStatus={handleToggleStatus}
              />
            </div>
          )}

          {/* Nội dung TAB 2: CHUYÊN KHOA */}
          {activeTab === 'departments' && (
            <div className="space-y-4 animate-in fade-in duration-150">
              {/* Bộ lọc Tab Khoa */}
              <DepartmentFilterBar
                searchQuery={deptSearchQuery}
                setSearchQuery={(val) => {
                  setDeptSearchQuery(val);
                  setDeptPage(1);
                }}
                typeFilter={deptTypeFilter}
                setTypeFilter={(val) => {
                  setDeptTypeFilter(val);
                  setDeptPage(1);
                }}
                statusFilter={deptStatusFilter}
                setStatusFilter={(val) => {
                  setDeptStatusFilter(val);
                  setDeptPage(1);
                }}
                onReset={handleResetDeptFilters}
                onAdd={handleOpenCreateDept}
              />

              {/* Bảng dữ liệu Khoa */}
              <DepartmentTable
                items={pagedDepartments}
                totalCount={filteredDepartments.length}
                page={currentDeptPage}
                pageSize={deptPageSize}
                onPageChange={setDeptPage}
                onPageSizeChange={(size) => {
                  setDeptPageSize(size);
                  setDeptPage(1);
                }}
                onView={handleOpenView}
                onEdit={handleOpenEdit}
                onToggleStatus={handleToggleStatus}
                onSelectDepartmentRooms={handleSelectDepartmentRooms}
              />
            </div>
          )}
        </section>
      </div>

      {/* Modal Thêm mới / Chỉnh sửa */}
      <DepartmentFormModal
        isOpen={formModalOpen}
        mode={formMode}
        targetType={formTargetType}
        initialData={editingItem}
        departments={departmentsList}
        existingItems={items}
        onSave={handleSaveForm}
        onClose={() => setFormModalOpen(false)}
      />

      {/* Modal Xem chi tiết */}
      <DepartmentDetailModal
        isOpen={detailModalOpen}
        item={viewingItem}
        rooms={allRoomsList}
        onEdit={(item) => {
          handleOpenEdit(item);
        }}
        onClose={() => setDetailModalOpen(false)}
      />

      {/* Modal Xác nhận đổi trạng thái / Vô hiệu hóa (Soft delete) */}
      {confirmDialog && (
        <ConfirmModal
          isOpen={Boolean(confirmDialog)}
          title={confirmDialog.title}
          description={confirmDialog.description}
          confirmText={confirmDialog.confirmText}
          variant={confirmDialog.variant}
          onConfirm={confirmDialog.onConfirm}
          onClose={() => setConfirmDialog(null)}
        />
      )}

      {roomAssignment && (
        <RoomAssignmentDialog
          room={roomAssignment}
          staffOptions={staffOptions}
          loading={staffLoading}
          selectedStaffIds={selectedStaffIds}
          onToggle={(staffId) =>
            setSelectedStaffIds((prev) =>
              prev.includes(staffId) ? prev.filter((id) => id !== staffId) : [...prev, staffId]
            )
          }
          onConfirm={handleConfirmRoomAssignment}
          onClose={() => {
            setRoomAssignment(null);
            setSelectedStaffIds([]);
            setStaffOptions([]);
          }}
        />
      )}
    </AdminLayout>
  );
}

export default DepartmentManagementPage;
