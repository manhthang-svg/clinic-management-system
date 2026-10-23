import { useState, useMemo } from 'react';
import {
  Building2,
  Plus,
  Search,
  Filter,
  CheckCircle2,
  Clock,
  AlertCircle,
  DoorOpen,
  MapPin,
  Stethoscope,
  MoreVertical,
  Download,
  RotateCcw,
  Sparkles,
  Users2,
  Layers,
} from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { CreateRoomPage } from './CreateRoomPage';

// Dữ liệu mock mẫu cho danh sách phòng (dùng để hiển thị giao diện)
const MOCK_ROOMS = [
  {
    id: '1',
    code: 'P-101',
    name: 'Phòng khám Nội 01',
    department: 'Khoa Khám bệnh',
    location: 'Tầng 1 - Khu A',
    type: 'Khám lâm sàng',
    typeKey: 'EXAMINATION',
    staffInCharge: 'BS.CKI Trần Quốc Bảo',
    status: 'ACTIVE',
    statusLabel: 'Đang hoạt động',
    statusColor: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    activeCount: '18 / 50',
  },
  {
    id: '2',
    code: 'P-102',
    name: 'Phòng khám Ngoại tổng quát',
    department: 'Khoa Ngoại',
    location: 'Tầng 1 - Khu A',
    type: 'Khám lâm sàng',
    typeKey: 'EXAMINATION',
    staffInCharge: 'ThS.BS Nguyễn Hoàng Lan',
    status: 'BUSY',
    statusLabel: 'Đang khám',
    statusColor: 'bg-amber-50 text-amber-700 border-amber-200',
    activeCount: '32 / 40',
  },
  {
    id: '3',
    code: 'XN-01',
    name: 'Phòng Xét nghiệm Huyết học',
    department: 'Khoa Xét nghiệm',
    location: 'Tầng 2 - Khu B',
    type: 'Cận lâm sàng',
    typeKey: 'PARACLINICAL',
    staffInCharge: 'KTV Lê Thu Hà',
    status: 'ACTIVE',
    statusLabel: 'Đang hoạt động',
    statusColor: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    activeCount: '65 / 100',
  },
  {
    id: '4',
    code: 'CDHA-02',
    name: 'Phòng Siêu âm Doppler Màu',
    department: 'Khoa CĐHA',
    location: 'Tầng 2 - Khu B',
    type: 'Cận lâm sàng',
    typeKey: 'PARACLINICAL',
    staffInCharge: 'BS. Lê Minh Tuấn',
    status: 'ACTIVE',
    statusLabel: 'Đang hoạt động',
    statusColor: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    activeCount: '12 / 30',
  },
  {
    id: '5',
    code: 'TT-01',
    name: 'Phòng Tiểu phẫu - Xử trí vết thương',
    department: 'Khoa Cấp cứu',
    location: 'Tầng 1 - Khu Cấp cứu',
    type: 'Cận lâm sàng',
    typeKey: 'PARACLINICAL',
    staffInCharge: 'BS. Phạm Văn Hùng',
    status: 'BUSY',
    statusLabel: 'Đang khám',
    statusColor: 'bg-amber-50 text-amber-700 border-amber-200',
    activeCount: '8 / 15',
  },
  {
    id: '6',
    code: 'P-105',
    name: 'Phòng khám Tai Mũi Họng',
    department: 'Khoa Khám bệnh',
    location: 'Tầng 1 - Khu A',
    type: 'Khám lâm sàng',
    typeKey: 'EXAMINATION',
    staffInCharge: 'Chưa phân công',
    status: 'MAINTENANCE',
    statusLabel: 'Bảo trì',
    statusColor: 'bg-slate-100 text-slate-600 border-slate-200',
    activeCount: '0 / 0',
  },
];

export function RoomManagementPage({
  currentAdmin,
  onLogout,
  onChangePassword,
  onOpenCreateRoom,
  onOpenCreateTour,
  onSelectNav,
}) {
  const [currentView, setCurrentView] = useState('list'); // 'list' | 'create-room'
  const [searchQuery, setSearchQuery] = useState('');
  const [deptFilter, setDeptFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedIds, setSelectedIds] = useState([]);
  const [activeMenuId, setActiveMenuId] = useState(null);

  const rooms = MOCK_ROOMS;

  const filteredRooms = useMemo(() => {
    return rooms.filter((room) => {
      const matchSearch =
        searchQuery === '' ||
        room.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        room.code.toLowerCase().includes(searchQuery.toLowerCase()) ||
        room.location.toLowerCase().includes(searchQuery.toLowerCase()) ||
        room.staffInCharge.toLowerCase().includes(searchQuery.toLowerCase());

      const matchDept = deptFilter === 'ALL' || room.department === deptFilter;
      const matchType = typeFilter === 'ALL' || room.typeKey === typeFilter;
      const matchStatus = statusFilter === 'ALL' || room.status === statusFilter;

      return matchSearch && matchDept && matchType && matchStatus;
    });
  }, [rooms, searchQuery, deptFilter, typeFilter, statusFilter]);

  const toggleSelectAll = () => {
    if (selectedIds.length === filteredRooms.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(filteredRooms.map((r) => r.id));
    }
  };

  const toggleSelectOne = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const resetFilters = () => {
    setSearchQuery('');
    setDeptFilter('ALL');
    setTypeFilter('ALL');
    setStatusFilter('ALL');
  };

  const handleStartCreateRoom = () => {
    if (onOpenCreateRoom) {
      onOpenCreateRoom();
    } else if (onOpenCreateTour) {
      onOpenCreateTour();
    } else {
      setCurrentView('create-room');
    }
  };

  // Nếu đang ở view tạo phòng, hiển thị CreateRoomPage
  if (currentView === 'create-room') {
    return (
      <AdminLayout
        currentAdmin={currentAdmin}
        onLogout={onLogout}
        onChangePassword={onChangePassword}
        activeKey="rooms"
        onNavigate={onSelectNav}
      >
        <CreateRoomPage
          onBack={() => setCurrentView('list')}
          onSave={(data) => {
            alert(`Đã lưu dữ liệu tạo phòng: ${data.name || data.code}`);
            setCurrentView('list');
          }}
        />
      </AdminLayout>
    );
  }

  return (
    <AdminLayout
      currentAdmin={currentAdmin}
      onLogout={onLogout}
      onChangePassword={onChangePassword}
      activeKey="rooms"
      onNavigate={onSelectNav}
    >
      <div className="p-6 md:p-8 space-y-6">
        {/* HEADER: Tiêu đề & Nút Tạo phòng */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-2xl font-black text-slate-900 tracking-tight">
                Quản lý phòng
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-[#0d9488] border border-teal-200/80">
                METPH Clinic
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Quản lý danh sách phòng khám lâm sàng, phòng cận lâm sàng và phân bổ nhân sự y tế.
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => alert('Xuất danh sách phòng Excel thành công!')}
              className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 shadow-2xs transition-all cursor-pointer"
            >
              <Download className="w-3.5 h-3.5 text-slate-500" />
              <span>Xuất danh sách</span>
            </button>

            {/* NÚT TẠO PHÒNG ĐẶT NỔI BẬT */}
            <button
              type="button"
              onClick={handleStartCreateRoom}
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm hover:shadow transition-all cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Tạo phòng</span>
            </button>
          </div>
        </div>

        {/* 4 SUMMARY STAT CARDS */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Card 1: Tổng số phòng */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500">Tổng số phòng</p>
                <div className="text-2xl font-black text-slate-900 mt-1">24</div>
                <p className="text-[11px] text-slate-400 mt-0.5">Toàn bộ cơ sở METPH</p>
              </div>
              <div className="w-9 h-9 rounded-xl bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
                <DoorOpen className="w-5 h-5" />
              </div>
            </div>
            <div className="flex items-center justify-between pt-3 mt-3 border-t border-slate-100 text-[11px]">
              <span className="text-slate-400">Quy mô khả dụng</span>
              <span className="font-semibold text-teal-700">100% Đăng ký</span>
            </div>
          </div>

          {/* Card 2: Đang hoạt động */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500">Đang hoạt động</p>
                <div className="text-2xl font-black text-emerald-600 mt-1">18</div>
                <p className="text-[11px] text-slate-400 mt-0.5">Đã phân công bác sĩ</p>
              </div>
              <div className="w-9 h-9 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center font-bold">
                <CheckCircle2 className="w-5 h-5" />
              </div>
            </div>
            <div className="flex items-center justify-between pt-3 mt-3 border-t border-slate-100 text-[11px]">
              <span className="text-slate-400">Hiệu suất vận hành</span>
              <span className="font-semibold text-emerald-700">75% Hoạt động</span>
            </div>
          </div>

          {/* Card 3: Đang khám bệnh */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500">Đang có bệnh nhân</p>
                <div className="text-2xl font-black text-amber-600 mt-1">4</div>
                <p className="text-[11px] text-slate-400 mt-0.5">Đang tiếp đón & khám</p>
              </div>
              <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center font-bold">
                <Clock className="w-5 h-5" />
              </div>
            </div>
            <div className="flex items-center justify-between pt-3 mt-3 border-t border-slate-100 text-[11px]">
              <span className="text-slate-400">Hàng chờ hiện tại</span>
              <span className="font-semibold text-amber-700">125 Lượt chờ</span>
            </div>
          </div>

          {/* Card 4: Tạm dừng / Bảo trì */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500">Bảo trì / Tạm ngưng</p>
                <div className="text-2xl font-black text-rose-600 mt-1">2</div>
                <p className="text-[11px] text-slate-400 mt-0.5">Cần kiểm tra thiết bị</p>
              </div>
              <div className="w-9 h-9 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center font-bold">
                <AlertCircle className="w-5 h-5" />
              </div>
            </div>
            <div className="flex items-center justify-between pt-3 mt-3 border-t border-slate-100 text-[11px]">
              <span className="text-slate-400">Kế hoạch khắc phục</span>
              <span className="font-semibold text-rose-600">Trong ngày</span>
            </div>
          </div>
        </div>

        {/* BỘ LỌC TÌM KIẾM (FILTER BAR) */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-4 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
            {/* Search Box */}
            <div className="lg:col-span-2 relative">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Tìm mã phòng, tên phòng, vị trí, người trực..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-3.5 py-2 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-slate-800 transition-all"
              />
            </div>

            {/* Filter Khoa */}
            <div>
              <select
                value={deptFilter}
                onChange={(e) => setDeptFilter(e.target.value)}
                className="w-full px-3 py-2 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-slate-700 transition-all cursor-pointer"
              >
                <option value="ALL">Tất cả khoa phòng</option>
                <option value="Khoa Khám bệnh">Khoa Khám bệnh</option>
                <option value="Khoa Ngoại">Khoa Ngoại</option>
                <option value="Khoa Xét nghiệm">Khoa Xét nghiệm</option>
                <option value="Khoa CĐHA">Khoa CĐHA</option>
                <option value="Khoa Cấp cứu">Khoa Cấp cứu</option>
              </select>
            </div>

            {/* Filter Loại phòng */}
            <div>
              <select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value)}
                className="w-full px-3 py-2 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-slate-700 transition-all cursor-pointer"
              >
                <option value="ALL">Tất cả loại phòng</option>
                <option value="EXAMINATION">Khám lâm sàng</option>
                <option value="PARACLINICAL">Cận lâm sàng</option>
                <option value="PROCEDURE">Thủ thuật / Tiểu phẫu</option>
              </select>
            </div>

            {/* Filter Trạng thái */}
            <div className="flex items-center gap-2">
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="w-full px-3 py-2 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-slate-700 transition-all cursor-pointer"
              >
                <option value="ALL">Tất cả trạng thái</option>
                <option value="ACTIVE">Đang hoạt động</option>
                <option value="BUSY">Đang khám</option>
                <option value="MAINTENANCE">Bảo trì</option>
              </select>

              {(searchQuery || deptFilter !== 'ALL' || typeFilter !== 'ALL' || statusFilter !== 'ALL') && (
                <button
                  type="button"
                  onClick={resetFilters}
                  className="p-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors cursor-pointer shrink-0"
                  title="Đặt lại bộ lọc"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>
        </div>

        {/* BẢNG DANH SÁCH PHÒNG & TOUR (TABLE) */}
        <div className="bg-white rounded-2xl border border-slate-200/80 overflow-hidden shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/75 border-b border-slate-200/80">
                  <th className="px-4 py-3.5 w-10">
                    <input
                      type="checkbox"
                      checked={selectedIds.length > 0 && selectedIds.length === filteredRooms.length}
                      onChange={toggleSelectAll}
                      className="accent-[#0d9488] rounded cursor-pointer w-3.5 h-3.5"
                    />
                  </th>
                  <th className="px-4 py-3.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Mã & Tên phòng
                  </th>
                  <th className="px-4 py-3.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Chuyên khoa & Vị trí
                  </th>
                  <th className="px-4 py-3.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Phân loại
                  </th>
                  <th className="px-4 py-3.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Bác sĩ / KTV phụ trách
                  </th>
                  <th className="px-4 py-3.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Lượt khám
                  </th>
                  <th className="px-4 py-3.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Trạng thái
                  </th>
                  <th className="px-4 py-3.5 text-[11px] font-bold uppercase tracking-wider text-slate-500 text-right">
                    Thao tác
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs text-slate-700">
                {filteredRooms.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-slate-400">
                      Không tìm thấy phòng nào phù hợp với bộ lọc.
                    </td>
                  </tr>
                ) : (
                  filteredRooms.map((room) => {
                    const isSelected = selectedIds.includes(room.id);
                    return (
                      <tr
                        key={room.id}
                        className={`hover:bg-slate-50/60 transition-colors ${
                          isSelected ? 'bg-teal-50/40' : ''
                        }`}
                      >
                        <td className="px-4 py-3.5">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleSelectOne(room.id)}
                            className="accent-[#0d9488] rounded cursor-pointer w-3.5 h-3.5"
                          />
                        </td>
                        <td className="px-4 py-3.5">
                          <div className="font-bold text-slate-900">{room.name}</div>
                          <div className="font-mono text-[11px] text-teal-700 font-semibold mt-0.5">
                            {room.code}
                          </div>
                        </td>
                        <td className="px-4 py-3.5">
                          <div className="font-medium text-slate-800">{room.department}</div>
                          <div className="flex items-center gap-1 text-[11px] text-slate-400 mt-0.5">
                            <MapPin className="w-3 h-3 shrink-0" />
                            <span>{room.location}</span>
                          </div>
                        </td>
                        <td className="px-4 py-3.5">
                          <span className="inline-flex px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 text-slate-700">
                            {room.type}
                          </span>
                        </td>
                        <td className="px-4 py-3.5">
                          <div className="font-medium text-slate-800">{room.staffInCharge}</div>
                        </td>
                        <td className="px-4 py-3.5 font-medium text-slate-700">
                          {room.activeCount}
                        </td>
                        <td className="px-4 py-3.5">
                          <span
                            className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${room.statusColor}`}
                          >
                            <span className="w-1.5 h-1.5 rounded-full bg-current" />
                            {room.statusLabel}
                          </span>
                        </td>
                        <td className="px-4 py-3.5 text-right relative">
                          <button
                            type="button"
                            onClick={() =>
                              setActiveMenuId(activeMenuId === room.id ? null : room.id)
                            }
                            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                          >
                            <MoreVertical className="w-4 h-4" />
                          </button>

                          {activeMenuId === room.id && (
                            <div className="absolute right-4 mt-1 w-40 bg-white rounded-xl shadow-lg border border-slate-200 py-1 z-10 text-left">
                              <button
                                type="button"
                                onClick={() => {
                                  alert(`Chỉnh sửa ${room.name}`);
                                  setActiveMenuId(null);
                                }}
                                className="w-full px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 flex items-center gap-2 cursor-pointer"
                              >
                                Chỉnh sửa phòng
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  alert(`Phân công nhân sự cho ${room.name}`);
                                  setActiveMenuId(null);
                                }}
                                className="w-full px-3 py-1.5 text-xs text-teal-700 hover:bg-teal-50 flex items-center gap-2 cursor-pointer font-semibold"
                              >
                                Phân công nhân sự
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  alert(`Khóa/Dừng phòng ${room.name}`);
                                  setActiveMenuId(null);
                                }}
                                className="w-full px-3 py-1.5 text-xs text-rose-600 hover:bg-rose-50 flex items-center gap-2 cursor-pointer"
                              >
                                Tạm dừng
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Table Footer Summary */}
          <div className="px-5 py-3.5 bg-slate-50/50 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <div>
              Hiển thị <span className="font-bold text-slate-800">{filteredRooms.length}</span> / {rooms.length} phòng
            </div>
            {selectedIds.length > 0 && (
              <div className="text-[#0d9488] font-semibold">
                Đã chọn {selectedIds.length} phòng
              </div>
            )}
          </div>
        </div>
      </div>
    </AdminLayout>
  );
}

export default RoomManagementPage;
