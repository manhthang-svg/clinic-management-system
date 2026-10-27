import { useState, useMemo, useEffect } from 'react';
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
} from '../../components/departments';
import { departmentApi, INITIAL_DEPARTMENT_ITEMS } from '../../apis/departmentApi';
import { Building2, DoorOpen, ChevronRight, House } from 'lucide-react';

const { ADMIN } = ROUTES;

export function DepartmentManagementPage({ currentAdmin, onLogout, onChangePassword }) {
  const navigate = useNavigate();

  // Active Tab: 'rooms' (Mặc định) | 'departments'
  const [activeTab, setActiveTab] = useState('rooms');

  const [items, setItems] = useState(INITIAL_DEPARTMENT_ITEMS);
  const [stats, setStats] = useState({
    totalDepartments: 5,
    totalRooms: 16,
    activeRooms: 15,
    maintenanceRooms: 1,
    activeRate: 93.8,
    maintenanceNote: '1 phòng đang kiểm tra thiết bị',
    activeCount: 15,
    maintenanceCount: 1,
  });

  // Filter & Search states cho TAB 1: PHÒNG CHỨC NĂNG
  const [roomSearchQuery, setRoomSearchQuery] = useState('');
  const [roomDeptFilter, setRoomDeptFilter] = useState('ALL');
  const [roomTypeFilter, setRoomTypeFilter] = useState('ALL');
  const [roomStatusFilter, setRoomStatusFilter] = useState('ALL');
  const [roomPage, setRoomPage] = useState(1);
  const [roomPageSize, setRoomPageSize] = useState(10);

  // Filter & Search states cho TAB 2: CHUYÊN KHOA
  const [deptSearchQuery, setDeptSearchQuery] = useState('');
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

  // Fetch data từ backend (setState chỉ chạy sau await, không đồng bộ trong effect)
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [fetchedItems, fetchedStats] = await Promise.all([
          departmentApi.getItems(),
          departmentApi.getStats(),
        ]);
        if (cancelled) return;
        if (Array.isArray(fetchedItems) && fetchedItems.length > 0) {
          setItems(fetchedItems);
        }
        if (fetchedStats) {
          setStats(fetchedStats);
        }
      } catch {
        // Giữ mock fallback
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

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
    const clinicalDepts = departmentsList.filter(
      (dept) =>
        dept.type === 'CLINICAL' ||
        dept.code === 'DEMO-NOI' ||
        dept.code === 'DEMO-NGOAI' ||
        dept.name?.toLowerCase().includes('nội') ||
        dept.name?.toLowerCase().includes('ngoại')
    ).length;
    const paraclinicalDepts = totalDepts - clinicalDepts;

    const totalRooms = allRoomsList.length;
    const clinicalRooms = allRoomsList.filter(
      (i) => i.type === 'EXAMINATION' || i.code?.startsWith('PK-')
    ).length;
    const paraclinicalRooms = totalRooms - clinicalRooms;
    const activeRooms = allRoomsList.filter((i) => i.status === 'ACTIVE').length;
    const maintenanceRooms = totalRooms - activeRooms;
    const activeRate = totalRooms > 0 ? Number(((activeRooms / totalRooms) * 100).toFixed(1)) : 0;
    const maintenanceNote = maintenanceRooms > 0
      ? `${maintenanceRooms} phòng đang kiểm tra thiết bị`
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
      // Tìm kiếm theo mã khoa, tên chuyên khoa, trưởng khoa
      const matchSearch =
        !query ||
        dept.code?.toLowerCase().includes(query) ||
        dept.name?.toLowerCase().includes(query) ||
        dept.headPerson?.toLowerCase().includes(query) ||
        dept.location?.toLowerCase().includes(query);

      // Lọc theo Trạng thái
      const matchStatus = deptStatusFilter === 'ALL' || dept.status === deptStatusFilter;

      return matchSearch && matchStatus;
    });
  }, [departmentsList, deptSearchQuery, deptStatusFilter]);

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
        // Cảnh báo bắt buộc khi ngưng hoạt động khoa
        setConfirmDialog({
          title: 'Khóa chuyên khoa',
          description: `Khoa này đang có ${activeDeptRoomsCount} phòng hoạt động. Việc ngưng hoạt động khoa sẽ tạm ngưng toàn bộ các phòng trực thuộc. Bạn có chắc chắn muốn tiếp tục?`,
          confirmText: 'Khóa chuyên khoa',
          variant: 'destructive',
          onConfirm: async () => {
            try {
              await departmentApi.toggleDepartmentStatus(item.id);
            } catch {
              // Mock fallback
            } finally {
              // Chuyển chuyên khoa và toàn bộ phòng trực thuộc sang INACTIVE
              setItems((prev) =>
                prev.map((i) => {
                  if (i.id === item.id && i.unitType === 'DEPARTMENT') {
                    return { ...i, status: 'INACTIVE' };
                  }
                  if (
                    i.unitType === 'ROOM' &&
                    (i.departmentId === item.id ||
                      i.departmentId === item.departmentId ||
                      i.departmentName === item.name ||
                      (item.code && i.departmentCode === item.code))
                  ) {
                    return { ...i, status: 'INACTIVE' };
                  }
                  return i;
                })
              );
              toast.success(
                `Đã tạm ngưng chuyên khoa "${item.name}" và toàn bộ các phòng trực thuộc!`
              );
              setConfirmDialog(null);
            }
          },
        });
        return;
      } else {
        // Mở khóa chuyên khoa: kích hoạt lại chuyên khoa và toàn bộ phòng trực thuộc
        setConfirmDialog({
          title: 'Mở khóa chuyên khoa',
          description: `Kích hoạt lại chuyên khoa "${item.name}"? Toàn bộ các phòng chức năng trực thuộc sẽ được kích hoạt hoạt động trở lại.`,
          confirmText: 'Kích hoạt lại',
          variant: 'mint',
          onConfirm: async () => {
            try {
              await departmentApi.toggleDepartmentStatus(item.id);
            } catch {
              // Mock fallback
            } finally {
              // Chuyển chuyên khoa và toàn bộ phòng trực thuộc sang ACTIVE
              setItems((prev) =>
                prev.map((i) => {
                  if (i.id === item.id && i.unitType === 'DEPARTMENT') {
                    return { ...i, status: 'ACTIVE' };
                  }
                  if (
                    i.unitType === 'ROOM' &&
                    (i.departmentId === item.id ||
                      i.departmentId === item.departmentId ||
                      i.departmentName === item.name ||
                      (item.code && i.departmentCode === item.code))
                  ) {
                    return { ...i, status: 'ACTIVE' };
                  }
                  return i;
                })
              );
              toast.success(
                `Đã kích hoạt lại chuyên khoa "${item.name}" và toàn bộ phòng trực thuộc!`
              );
              setConfirmDialog(null);
            }
          },
        });
        return;
      }
    }

    // Trường hợp là ROOM (Phòng chức năng)
    setConfirmDialog({
      title: isDeactivating ? 'Tạm ngưng hoạt động phòng' : 'Kích hoạt lại phòng khám',
      description: isDeactivating
        ? `Bạn có chắc muốn tạm ngưng "${item.name}" (${item.code})?\nĐơn vị sẽ không hiển thị khi phân bổ lịch khám mới.`
        : `Kích hoạt lại "${item.name}" (${item.code}) để sẵn sàng phục vụ tiếp nhận bệnh nhân?`,
      confirmText: isDeactivating ? 'Tạm ngưng' : 'Kích hoạt',
      variant: isDeactivating ? 'destructive' : 'mint',
      onConfirm: async () => {
        try {
          await departmentApi.toggleRoomStatus(item.id);
        } catch {
          // Fallback UI
        } finally {
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
          await departmentApi.updateDepartment(editingItem.id, {
            departmentName: formData.name,
            tenkhoa: formData.name,
            type: formData.type,
            description: formData.description,
            headPerson: formData.headPerson,
            active: formData.status === 'ACTIVE',
          });
          setItems((prev) =>
            prev.map((i) =>
              i.id === editingItem.id && i.unitType === 'DEPARTMENT'
                ? {
                    ...i,
                    name: formData.name,
                    type: formData.type,
                    description: formData.description,
                    headPerson: formData.headPerson,
                    location: formData.location || i.location,
                    status: formData.status,
                  }
                : i
            )
          );
          toast.success(`Đã cập nhật chuyên khoa "${formData.name}" thành công!`);
        } else {
          await departmentApi.createDepartment({
            departmentCode: formData.code,
            departmentName: formData.name,
            makhoa: formData.code,
            tenkhoa: formData.name,
            type: formData.type,
            description: formData.description,
            headPerson: formData.headPerson,
            active: formData.status === 'ACTIVE',
          });
          const newDept = {
            id: `dept-${Date.now()}`,
            unitType: 'DEPARTMENT',
            code: formData.code,
            name: formData.name,
            departmentId: `dept-${Date.now()}`,
            departmentName: formData.name,
            type: formData.type,
            location: '',
            headPerson: formData.headPerson || 'Chưa bổ nhiệm',
            status: formData.status,
            description: formData.description,
            roomCount: 0,
          };
          setItems((prev) => [newDept, ...prev]);
          toast.success(`Đã thêm mới chuyên khoa "${formData.name}" thành công!`);
        }
      } else {
        // Handle ROOM
        const targetDept = departmentsList.find((d) => d.id === formData.departmentId);
        if (isEdit) {
          await departmentApi.updateRoom(editingItem.id, {
            roomName: formData.name,
            phongkham: formData.name,
            departmentId: formData.departmentId,
            serviceIds: formData.serviceIds || [],
            type: formData.type,
            location: formData.location,
            headPerson: formData.headPerson,
            status: formData.status,
          });
          setItems((prev) =>
            prev.map((i) =>
              i.id === editingItem.id && i.unitType === 'ROOM'
                ? {
                    ...i,
                    name: formData.name,
                    departmentId: formData.departmentId,
                    departmentName: targetDept?.name || i.departmentName,
                    type: formData.type,
                    location: formData.location,
                    headPerson: formData.headPerson,
                    status: formData.status,
                    operatingHours: formData.operatingHours,
                    capacity: formData.capacity,
                    deviceSetup: formData.deviceSetup,
                    assignedServices: formData.assignedServices || [],
                    serviceIds: formData.serviceIds || [],
                  }
                : i
            )
          );
          toast.success(`Đã cập nhật phòng "${formData.name}" thành công!`);
        } else {
          await departmentApi.createRoom({
            roomCode: formData.code,
            roomName: formData.name,
            maphongkham: formData.code,
            phongkham: formData.name,
            departmentId: formData.departmentId,
            serviceIds: formData.serviceIds || [],
            type: formData.type,
            location: formData.location,
            headPerson: formData.headPerson,
            status: formData.status,
          });
          const newRoom = {
            id: `room-${Date.now()}`,
            unitType: 'ROOM',
            code: formData.code,
            name: formData.name,
            departmentId: formData.departmentId,
            departmentName: targetDept?.name || 'Khoa Nội',
            type: formData.type,
            location: formData.location,
            headPerson: formData.headPerson || 'Chưa phân công',
            status: formData.status,
            operatingHours: formData.operatingHours,
            capacity: formData.capacity,
            deviceSetup: formData.deviceSetup,
            assignedServices: formData.assignedServices || [],
            serviceIds: formData.serviceIds || [],
          };
          setItems((prev) => [newRoom, ...prev]);
          toast.success(`Đã thêm mới phòng "${formData.name}" thành công!`);
        }
      }
    } catch {
      // Mock fallback nếu backend offline
      if (isDept) {
        if (isEdit) {
          setItems((prev) =>
            prev.map((i) =>
              i.id === editingItem.id && i.unitType === 'DEPARTMENT'
                ? {
                    ...i,
                    name: formData.name,
                    type: formData.type,
                    description: formData.description,
                    headPerson: formData.headPerson,
                    location: formData.location || i.location,
                    status: formData.status,
                  }
                : i
            )
          );
          toast.success(`Đã cập nhật chuyên khoa "${formData.name}" thành công!`);
        } else {
          const newDept = {
            id: `dept-${Date.now()}`,
            unitType: 'DEPARTMENT',
            code: formData.code,
            name: formData.name,
            departmentId: `dept-${Date.now()}`,
            departmentName: formData.name,
            type: formData.type,
            location: '',
            headPerson: formData.headPerson || 'Chưa bổ nhiệm',
            status: formData.status,
            description: formData.description,
            roomCount: 0,
          };
          setItems((prev) => [newDept, ...prev]);
          toast.success(`Đã thêm mới chuyên khoa "${formData.name}" thành công!`);
        }
      } else {
        const targetDept = departmentsList.find((d) => d.id === formData.departmentId);
        if (isEdit) {
          setItems((prev) =>
            prev.map((i) =>
              i.id === editingItem.id && i.unitType === 'ROOM'
                ? {
                    ...i,
                    name: formData.name,
                    departmentId: formData.departmentId,
                    departmentName: targetDept?.name || i.departmentName,
                    type: formData.type,
                    location: formData.location,
                    headPerson: formData.headPerson,
                    status: formData.status,
                    operatingHours: formData.operatingHours,
                    capacity: formData.capacity,
                    deviceSetup: formData.deviceSetup,
                    assignedServices: formData.assignedServices || [],
                    serviceIds: formData.serviceIds || [],
                  }
                : i
            )
          );
          toast.success(`Đã cập nhật phòng "${formData.name}" thành công!`);
        } else {
          const newRoom = {
            id: `room-${Date.now()}`,
            unitType: 'ROOM',
            code: formData.code,
            name: formData.name,
            departmentId: formData.departmentId,
            departmentName: targetDept?.name || 'Khoa Nội',
            type: formData.type,
            location: formData.location,
            headPerson: formData.headPerson || 'Chưa phân công',
            status: formData.status,
            operatingHours: formData.operatingHours,
            capacity: formData.capacity,
            deviceSetup: formData.deviceSetup,
            assignedServices: formData.assignedServices || [],
            serviceIds: formData.serviceIds || [],
          };
          setItems((prev) => [newRoom, ...prev]);
          toast.success(`Đã thêm mới phòng "${formData.name}" thành công!`);
        }
      }
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
    </AdminLayout>
  );
}

export default DepartmentManagementPage;
