import { useState, useMemo, useRef, useEffect } from 'react';
import { Button } from '../ui/button';
import {
  Building2,
  DoorOpen,
  X,
  Clock,
  Activity,
  Layers,
  Stethoscope,
  Info,
  Search,
  ChevronDown,
  Trash2,
} from 'lucide-react';
import {
  getServicesForRoom,
  getAvailableServicesForDepartment,
} from '../../constants/serviceData';
import { formatPrice } from '../services/serviceFormat';

const INPUT_CLASS =
  'w-full px-3.5 py-2.5 text-xs rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all placeholder:text-slate-400 disabled:bg-slate-50 disabled:text-slate-400';

const SELECT_CLASS =
  'w-full px-3.5 py-2.5 text-xs rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all cursor-pointer disabled:bg-slate-50 disabled:text-slate-400';

function getDefaultSpecs(initialData, isRoom) {
  if (!isRoom) return {};
  const isExam = initialData?.type === 'EXAMINATION' || initialData?.code?.startsWith('PK-');
  return {
    operatingHours:
      initialData?.operatingHours || '07:30 – 16:30 (Thứ 2 – Thứ 7)',
    capacity:
      initialData?.capacity ||
      (isExam ? '40 – 60 lượt khám/ngày' : '30 – 50 ca xét nghiệm/ngày'),
    deviceSetup:
      initialData?.deviceSetup ||
      (isExam
        ? 'Bàn khám tiêu chuẩn, máy đo huyết áp điện tử, ống nghe y tế, đèn khám, giường khám bệnh'
        : initialData?.code?.includes('SA')
        ? 'Hệ thống siêu âm 4D màu Doppler kỹ thuật số cao cấp'
        : initialData?.code?.includes('XQ')
        ? 'Hệ thống máy chụp X-quang kỹ thuật số (DR), buồng chì bảo vệ'
        : initialData?.code?.includes('CT')
        ? 'Hệ thống chụp cắt lớp vi tính CT đa dãy đầu dò thế hệ mới'
        : initialData?.code?.includes('MRI')
        ? 'Hệ thống chụp cộng hưởng từ 1.5 Tesla'
        : initialData?.code?.includes('NS')
        ? 'Hệ thống dàn nội soi dạ dày đại tràng ống mềm độ nét cao (NBI)'
        : initialData?.code?.includes('DT')
        ? 'Máy ghi điện tâm đồ 12 kênh kỹ thuật số'
        : 'Hệ thống máy phân tích tự động, buồng an toàn sinh học cấp II'),
  };
}

function buildInitialForm({ initialData, departments, isRoom }) {
  const specs = getDefaultSpecs(initialData, isRoom);

  if (initialData) {
    return {
      code: initialData.code || '',
      name: initialData.name || '',
      departmentId: initialData.departmentId || (departments[0]?.id || ''),
      type: initialData.type || (isRoom ? 'EXAMINATION' : 'CLINICAL'),
      location: initialData.location || '',
      headPerson: initialData.headPerson || '',
      description: initialData.description || '',
      status: initialData.status || 'ACTIVE',
      operatingHours: specs.operatingHours || '07:30 – 16:30 (Thứ 2 – Thứ 7)',
      capacity: specs.capacity || '40 – 60 lượt/ngày',
      deviceSetup: specs.deviceSetup || '',
    };
  }
  return {
    code: '',
    name: '',
    departmentId: departments[0]?.id || '',
    type: isRoom ? 'EXAMINATION' : 'CLINICAL',
    location: '',
    headPerson: '',
    description: '',
    status: 'ACTIVE',
    operatingHours: '07:30 – 16:30 (Thứ 2 – Thứ 7)',
    capacity: isRoom ? '40 – 60 lượt khám/ngày' : '',
    deviceSetup: isRoom
      ? 'Bàn khám tiêu chuẩn, máy đo huyết áp điện tử, ống nghe y tế, đèn khám, giường khám bệnh'
      : '',
  };
}

/**
 * Modal chỉ mount phần nội dung khi đang mở; mỗi lần mở (hoặc đổi item/loại/chế độ)
 * sẽ mount lại nên form được khởi tạo sạch mà không cần setState trong effect.
 */
export function DepartmentFormModal(props) {
  if (!props.isOpen) return null;
  const { mode = 'CREATE', targetType = 'ROOM', initialData = null } = props;
  return (
    <DepartmentFormModalContent
      key={`${mode}-${targetType}-${initialData?.id ?? 'new'}`}
      {...props}
    />
  );
}

function DepartmentFormModalContent({
  mode = 'CREATE', // 'CREATE' | 'EDIT'
  targetType = 'ROOM', // 'ROOM' | 'DEPARTMENT'
  initialData = null,
  departments = [],
  onSave,
  onClose,
}) {
  const isEdit = mode === 'EDIT';
  const isRoom = targetType === 'ROOM';

  const [formData, setFormData] = useState(() =>
    buildInitialForm({ initialData, departments, isRoom })
  );
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Danh sách dịch vụ được gán cho phòng
  const [assignedServices, setAssignedServices] = useState(() => {
    if (!isRoom) return [];
    if (initialData) {
      return getServicesForRoom(initialData);
    }
    // Khi tạo phòng mới: ban đầu chưa gán dịch vụ nào
    return [];
  });

  // State các mã dịch vụ được tick tạm thời trong dropdown
  const [selectedCodes, setSelectedCodes] = useState(() => {
    if (!isRoom) return new Set();
    if (initialData) {
      const init = getServicesForRoom(initialData);
      return new Set(init.map((s) => s.code));
    }
    return new Set();
  });

  // State tìm kiếm dịch vụ
  const [serviceSearchQuery, setServiceSearchQuery] = useState('');
  const [serviceDropdownOpen, setServiceDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);

  // Đóng dropdown khi click ra ngoài ô chọn và khôi phục tick theo assignedServices
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setServiceDropdownOpen(false);
        setSelectedCodes(new Set(assignedServices.map((s) => s.code)));
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [assignedServices]);

  // Khoa hiện tại mà phòng trực thuộc
  const currentDeptObj = useMemo(() => {
    return departments.find((d) => d.id === formData.departmentId) || null;
  }, [departments, formData.departmentId]);

  // Toàn bộ dịch vụ thuộc Chuyên khoa đã chọn
  const deptServices = useMemo(() => {
    if (!isRoom) return [];
    return getAvailableServicesForDepartment(currentDeptObj);
  }, [isRoom, currentDeptObj]);

  // Danh sách dịch vụ lọc theo từ khóa tìm kiếm
  const filteredDeptServices = useMemo(() => {
    const term = serviceSearchQuery.trim().toLowerCase();
    if (!term) return deptServices;
    return deptServices.filter(
      (s) =>
        s.code.toLowerCase().includes(term) ||
        s.name.toLowerCase().includes(term)
    );
  }, [deptServices, serviceSearchQuery]);

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
    // Nếu đổi chuyên khoa trực thuộc -> lọc bỏ các dịch vụ không còn phù hợp với khoa mới
    if (field === 'departmentId') {
      const selectedDept = departments.find((d) => d.id === value);
      const newDeptServices = getAvailableServicesForDepartment(selectedDept);
      const allowedCodes = new Set(newDeptServices.map((s) => s.code));
      setAssignedServices((prev) => prev.filter((s) => allowedCodes.has(s.code)));
      setSelectedCodes((prev) => {
        const next = new Set();
        prev.forEach((c) => {
          if (allowedCodes.has(c)) next.add(c);
        });
        return next;
      });
    }
  };

  const handleOpenDropdown = () => {
    setSelectedCodes(new Set(assignedServices.map((s) => s.code)));
    setServiceDropdownOpen(true);
  };

  const handleToggleService = (serviceCode) => {
    setSelectedCodes((prev) => {
      const next = new Set(prev);
      if (next.has(serviceCode)) {
        next.delete(serviceCode);
      } else {
        next.add(serviceCode);
      }
      return next;
    });
  };

  const handleToggleAll = () => {
    const displayedCodes = filteredDeptServices.map((s) => s.code);
    const allChecked = displayedCodes.every((c) => selectedCodes.has(c));

    setSelectedCodes((prev) => {
      const next = new Set(prev);
      if (allChecked) {
        displayedCodes.forEach((c) => next.delete(c));
      } else {
        displayedCodes.forEach((c) => next.add(c));
      }
      return next;
    });
  };

  const handleSaveSelectedServices = () => {
    const newAssigned = deptServices.filter((s) => selectedCodes.has(s.code));
    setAssignedServices(newAssigned);
    setServiceDropdownOpen(false);
    setServiceSearchQuery('');
  };

  const handleCancelSelection = () => {
    setSelectedCodes(new Set(assignedServices.map((s) => s.code)));
    setServiceDropdownOpen(false);
    setServiceSearchQuery('');
  };

  const handleRemoveService = (serviceCode) => {
    setAssignedServices((prev) => prev.filter((s) => s.code !== serviceCode));
    setSelectedCodes((prev) => {
      const next = new Set(prev);
      next.delete(serviceCode);
      return next;
    });
  };

  const validate = () => {
    const newErrors = {};
    if (!formData.code.trim()) {
      newErrors.code = isRoom ? 'Vui lòng nhập mã phòng' : 'Vui lòng nhập mã chuyên khoa';
    }
    if (!formData.name.trim()) {
      newErrors.name = isRoom ? 'Vui lòng nhập tên phòng' : 'Vui lòng nhập tên chuyên khoa';
    }
    if (isRoom) {
      if (!formData.departmentId) {
        newErrors.departmentId = 'Vui lòng chọn chuyên khoa trực thuộc';
      }
      if (!formData.location.trim()) {
        newErrors.location = 'Vui lòng nhập vị trí/buồng (ví dụ: Tòa A - Tầng 1 - P.102)';
      }
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;

    try {
      setSubmitting(true);
      await onSave(
        {
          ...formData,
          assignedServices: isRoom ? assignedServices : [],
          serviceIds: isRoom ? assignedServices.map((s) => s.id || s.code) : [],
          unitType: targetType,
        },
        targetType,
        isEdit
      );
      onClose();
    } catch {
      // Handled by parent
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        className={`w-full ${
          isRoom ? 'max-w-3xl' : 'max-w-lg'
        } rounded-2xl bg-white p-5 sm:p-6 shadow-2xl border border-[#e3ece9] transition-all transform scale-100 max-h-[92vh] flex flex-col`}
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3.5 border-b border-slate-100 shrink-0">
          <div className="flex items-center gap-3">
            <div
              className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                isRoom ? 'bg-teal-50 text-[#0d9488]' : 'bg-blue-50 text-blue-600'
              }`}
            >
              {isRoom ? <DoorOpen className="w-5 h-5" /> : <Building2 className="w-5 h-5" />}
            </div>
            <div>
              <h3 className="text-base font-bold text-[#142534] tracking-tight">
                {isEdit
                  ? isRoom
                    ? 'Chỉnh sửa phòng chức năng'
                    : 'Chỉnh sửa chuyên khoa'
                  : isRoom
                  ? 'Thêm mới phòng chức năng'
                  : 'Thêm mới chuyên khoa'}
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">
                {isEdit
                  ? `Cập nhật thông số chi tiết & danh mục dịch vụ cho ${formData.name || formData.code}`
                  : isRoom
                  ? 'Khai báo buồng khám, phòng cận lâm sàng hoặc quầy tiếp đón'
                  : 'Tạo mới danh mục chuyên khoa cấp cao'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body with scroll */}
        <form onSubmit={handleSubmit} className="mt-4 space-y-4 overflow-y-auto flex-1 pr-1 text-xs">
          {/* Section 1: Thông tin nhận diện cơ bản */}
          <div className="p-3.5 bg-slate-50/70 rounded-xl border border-slate-100 space-y-3.5">
            <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs">
              <Info className="w-3.5 h-3.5 text-[#127e7a]" />
              <span>Thông tin định danh & phân cấp</span>
            </div>

            {/* Mã & Tên */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  {isRoom ? 'Mã phòng' : 'Mã khoa'} <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  disabled={isEdit}
                  value={formData.code}
                  onChange={(e) => handleChange('code', e.target.value.toUpperCase())}
                  placeholder={isRoom ? 'VD: PK-NOI-01' : 'VD: KHOA-NOI'}
                  className={INPUT_CLASS}
                />
                {errors.code && <p className="text-[11px] text-rose-500 mt-1">{errors.code}</p>}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  {isRoom ? 'Tên phòng' : 'Tên khoa'} <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => handleChange('name', e.target.value)}
                  placeholder={isRoom ? 'VD: Phòng khám Nội 1' : 'VD: Khoa Nội Tổng Hợp'}
                  className={INPUT_CLASS}
                />
                {errors.name && <p className="text-[11px] text-rose-500 mt-1">{errors.name}</p>}
              </div>
            </div>

            {/* Chuyên khoa trực thuộc (khi là phòng) */}
            {isRoom && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Chuyên khoa trực thuộc <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={formData.departmentId}
                    onChange={(e) => handleChange('departmentId', e.target.value)}
                    className={SELECT_CLASS}
                  >
                    <option value="">-- Chọn chuyên khoa trực thuộc --</option>
                    {departments.map((dept) => (
                      <option key={dept.id || dept.code} value={dept.id}>
                        {dept.name} ({dept.code})
                      </option>
                    ))}
                  </select>
                  {errors.departmentId && (
                    <p className="text-[11px] text-rose-500 mt-1">{errors.departmentId}</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Vị trí / Tầng buồng <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.location}
                    onChange={(e) => handleChange('location', e.target.value)}
                    placeholder="VD: Tòa A - Tầng 1 - P.102"
                    className={INPUT_CLASS}
                  />
                  {errors.location && (
                    <p className="text-[11px] text-rose-500 mt-1">{errors.location}</p>
                  )}
                </div>
              </div>
            )}

            {/* Phân loại & Trạng thái */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  {isRoom ? 'Loại phòng' : 'Phân loại chuyên khoa'}
                </label>
                {isRoom ? (
                  <select
                    value={formData.type}
                    onChange={(e) => handleChange('type', e.target.value)}
                    className={SELECT_CLASS}
                  >
                    <option value="EXAMINATION">Phòng khám lâm sàng</option>
                    <option value="PARACLINICAL">Phòng cận lâm sàng</option>
                    <option value="ADMINISTRATIVE">Phòng thu ngân / Lễ tân / Hành chính</option>
                  </select>
                ) : (
                  <select
                    value={formData.type}
                    onChange={(e) => handleChange('type', e.target.value)}
                    className={SELECT_CLASS}
                  >
                    <option value="CLINICAL">Lâm sàng (Khám chữa bệnh)</option>
                    <option value="PARACLINICAL">Cận lâm sàng (Xét nghiệm/CĐHA/TDCN)</option>
                    <option value="ADMINISTRATIVE">Hành chính / Quản trị</option>
                  </select>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Trạng thái</label>
                <select
                  value={formData.status}
                  onChange={(e) => handleChange('status', e.target.value)}
                  className={SELECT_CLASS}
                >
                  <option value="ACTIVE">Đang hoạt động</option>
                  <option value="INACTIVE">{isRoom ? 'Tạm ngưng' : 'Đang khóa'}</option>
                  {isRoom && <option value="MAINTENANCE">Đang bảo trì thiết bị</option>}
                </select>
              </div>
            </div>
          </div>

          {/* Section 2 (Chỉ cho ROOM): Thông số vận hành & Cơ sở vật chất */}
          {isRoom && (
            <div className="p-3.5 bg-white rounded-xl border border-slate-200/90 space-y-3.5">
              <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs">
                <Clock className="w-3.5 h-3.5 text-[#0d9488]" />
                <span>Thông số vận hành & Thiết bị buồng phòng</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Khung giờ tiếp nhận
                  </label>
                  <input
                    type="text"
                    value={formData.operatingHours}
                    onChange={(e) => handleChange('operatingHours', e.target.value)}
                    placeholder="VD: 07:30 – 16:30 (Thứ 2 – Thứ 7)"
                    className={INPUT_CLASS}
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Công suất phục vụ dự kiến
                  </label>
                  <input
                    type="text"
                    value={formData.capacity}
                    onChange={(e) => handleChange('capacity', e.target.value)}
                    placeholder="VD: 40 – 60 lượt/ngày"
                    className={INPUT_CLASS}
                  />
                </div>
              </div>

              {/* Trang thiết bị */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Cơ sở vật chất & Trang thiết bị buồng phòng
                </label>
                <textarea
                  rows={2}
                  value={formData.deviceSetup}
                  onChange={(e) => handleChange('deviceSetup', e.target.value)}
                  placeholder="Khai báo các trang thiết bị y tế chính bố trí tại phòng..."
                  className={`${INPUT_CLASS} resize-none`}
                />
              </div>

              {/* Khối: Dịch vụ y tế phòng tiếp nhận phục vụ */}
              <div className="pt-1 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs">
                    <Stethoscope className="w-4 h-4 text-[#0d9488]" />
                    <span>Dịch vụ y tế phòng tiếp nhận phục vụ ({assignedServices.length})</span>
                  </div>
                  <span className="text-[10.5px] text-slate-400">Đơn vị: VNĐ</span>
                </div>

                {/* Thanh tìm kiếm & chọn dịch vụ (Search & Select Dropdown) */}
                <div ref={dropdownRef} className="relative">
                  <div className="relative flex items-center">
                    <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 pointer-events-none" />
                    <input
                      type="text"
                      value={serviceSearchQuery}
                      onChange={(e) => {
                        setServiceSearchQuery(e.target.value);
                        if (!serviceDropdownOpen) handleOpenDropdown();
                      }}
                      onFocus={handleOpenDropdown}
                      placeholder="Tìm và gán dịch vụ y tế (chọn một hoặc nhiều)..."
                      className="w-full pl-9 pr-14 py-2.5 text-xs rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 placeholder:text-slate-400"
                    />
                    <div className="absolute right-2.5 flex items-center gap-1">
                      {serviceSearchQuery && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setServiceSearchQuery('');
                          }}
                          className="p-1 text-slate-400 hover:text-slate-600 rounded-full hover:bg-slate-100 transition-colors cursor-pointer"
                          title="Xóa tìm kiếm"
                        >
                          <X className="w-3 h-3" />
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => {
                          if (serviceDropdownOpen) {
                            handleCancelSelection();
                          } else {
                            handleOpenDropdown();
                          }
                        }}
                        className="p-1 text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
                        title={serviceDropdownOpen ? 'Đóng danh sách' : 'Mở danh sách'}
                      >
                        <ChevronDown
                          className={`w-3.5 h-3.5 transition-transform duration-200 ${
                            serviceDropdownOpen ? 'rotate-180' : ''
                          }`}
                        />
                      </button>
                    </div>
                  </div>

                  {/* Dropdown danh sách gợi ý kèm checkbox bên trái và nút Lưu ở góc dưới bên phải */}
                  {serviceDropdownOpen && (
                    <div
                      className="absolute left-0 right-0 top-full mt-1.5 z-30 bg-white rounded-xl border border-slate-200 shadow-xl max-h-72 flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-100"
                    >
                      {/* Dropdown Header */}
                      <div className="px-3 py-2 bg-slate-50 border-b border-slate-100 text-[11px] font-semibold text-slate-600 flex items-center justify-between shrink-0">
                        <span>
                          Dịch vụ chuyên khoa {currentDeptObj ? currentDeptObj.name : ''} ({filteredDeptServices.length})
                        </span>
                        <span className="text-[10px] text-teal-700 font-bold bg-teal-50 px-2 py-0.5 rounded border border-teal-100">
                          Đã tick: {selectedCodes.size}
                        </span>
                      </div>

                      {/* Danh sách các dịch vụ kèm ô checkbox bên trái */}
                      <div className="overflow-y-auto flex-1 divide-y divide-slate-100">
                        {filteredDeptServices.length === 0 ? (
                          <div className="p-4 text-center text-slate-400 text-xs">
                            Không tìm thấy dịch vụ nào phù hợp với chuyên khoa này.
                          </div>
                        ) : (
                          filteredDeptServices.map((srv) => {
                            const isChecked = selectedCodes.has(srv.code);
                            return (
                              <div
                                key={srv.code}
                                onClick={() => handleToggleService(srv.code)}
                                className={`px-3 py-2.5 flex items-center justify-between cursor-pointer transition-colors group ${
                                  isChecked ? 'bg-teal-50/50' : 'hover:bg-slate-50'
                                }`}
                              >
                                <div className="flex items-center gap-2.5 min-w-0 pr-3">
                                  {/* Ô checkbox nằm bên trái */}
                                  <input
                                    type="checkbox"
                                    checked={isChecked}
                                    onChange={() => handleToggleService(srv.code)}
                                    onClick={(e) => e.stopPropagation()}
                                    className="w-4 h-4 rounded border-slate-300 text-[#0d9488] focus:ring-teal-500 cursor-pointer accent-[#0d9488] shrink-0"
                                  />
                                  <span className="font-mono font-bold text-[10.5px] text-teal-700 bg-teal-50 px-1.5 py-0.5 rounded border border-teal-100 shrink-0">
                                    {srv.code}
                                  </span>
                                  <span
                                    className={`text-xs line-clamp-1 ${
                                      isChecked
                                        ? 'font-bold text-teal-900'
                                        : 'font-semibold text-slate-800'
                                    }`}
                                  >
                                    {srv.name}
                                  </span>
                                </div>
                                <div className="text-right shrink-0">
                                  <div className="font-bold text-slate-800 text-[11px]">
                                    {formatPrice(srv.clinicPrice)} đ
                                  </div>
                                  <div className="text-[10px] text-emerald-700 font-medium">
                                    {srv.insurancePrice
                                      ? `BHYT: ${formatPrice(srv.insurancePrice)} đ`
                                      : 'Tự túc'}
                                  </div>
                                </div>
                              </div>
                            );
                          })
                        )}
                      </div>

                      {/* Dropdown Footer: Nút Lưu ở phía dưới bên phải */}
                      <div className="p-2.5 px-3 bg-slate-50 border-t border-slate-200 flex items-center justify-between shrink-0">
                        <div className="flex items-center gap-2 text-[11px] text-slate-600">
                          {filteredDeptServices.length > 0 && (
                            <button
                              type="button"
                              onClick={handleToggleAll}
                              className="text-[11px] text-teal-700 hover:text-teal-900 font-medium cursor-pointer underline"
                            >
                              {filteredDeptServices.every((s) => selectedCodes.has(s.code))
                                ? 'Bỏ tick tất cả'
                                : 'Tick chọn tất cả'}
                            </button>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={handleCancelSelection}
                            className="px-2.5 py-1 text-xs text-slate-500 hover:text-slate-700 font-medium cursor-pointer"
                          >
                            Hủy
                          </button>
                          <button
                            type="button"
                            onClick={handleSaveSelectedServices}
                            className="px-4 py-1.5 text-xs font-semibold rounded-lg bg-[#0d9488] text-white hover:bg-[#0b7c72] shadow-xs transition-colors flex items-center gap-1.5 cursor-pointer"
                          >
                            <span>Lưu</span>
                            {selectedCodes.size > 0 && (
                              <span className="bg-white/20 px-1.5 py-0.2 rounded-full text-[10px]">
                                {selectedCodes.size}
                              </span>
                            )}
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>

                {/* Bảng danh sách dịch vụ đã chọn */}
                <div className="overflow-x-auto rounded-xl border border-slate-200/90 bg-white">
                  <table className="w-full text-left border-collapse text-[11px]">
                    <thead>
                      <tr className="bg-[#f6f8f9] text-[10.5px] font-bold text-[#1b2533] border-b border-slate-200/80">
                        <th className="px-3 py-2.5">Mã DV</th>
                        <th className="px-3 py-2.5">Tên dịch vụ</th>
                        <th className="px-3 py-2.5 text-right">Viện phí</th>
                        <th className="px-3 py-2.5 text-right">BHYT</th>
                        <th className="px-3 py-2.5 text-center w-16">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {assignedServices.length === 0 ? (
                        <tr>
                          <td colSpan="5" className="p-6 text-center text-slate-400 italic">
                            Chưa có dịch vụ nào được gán cho phòng này
                          </td>
                        </tr>
                      ) : (
                        assignedServices.map((service) => (
                          <tr key={service.code} className="hover:bg-slate-50/80 transition-colors">
                            <td className="px-3 py-2.5 font-mono font-semibold text-slate-700 whitespace-nowrap">
                              {service.code}
                            </td>
                            <td className="px-3 py-2.5 font-medium text-slate-800">
                              <div>{service.name}</div>
                              {service.estimatedMinutes && (
                                <div className="text-[10px] text-slate-400 mt-0.5">
                                  Thời gian chuẩn: ~{service.estimatedMinutes} phút
                                </div>
                              )}
                            </td>
                            <td className="px-3 py-2.5 text-right font-bold text-slate-800 whitespace-nowrap">
                              {formatPrice(service.clinicPrice)} đ
                            </td>
                            <td className="px-3 py-2.5 text-right whitespace-nowrap">
                              {service.insurancePrice ? (
                                <span className="font-semibold text-emerald-700">
                                  {formatPrice(service.insurancePrice)} đ
                                </span>
                              ) : (
                                <span className="text-slate-400 italic">Không hỗ trợ</span>
                              )}
                            </td>
                            <td className="px-3 py-2.5 text-center">
                              <button
                                type="button"
                                title="Gỡ dịch vụ"
                                onClick={() => handleRemoveService(service.code)}
                                className="w-6 h-6 inline-flex items-center justify-center rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* Người phụ trách (Chỉ dành cho Chuyên khoa) */}
          {!isRoom && (
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Trưởng khoa phụ trách
              </label>
              <input
                type="text"
                value={formData.headPerson}
                onChange={(e) => handleChange('headPerson', e.target.value)}
                placeholder="VD: BSCKII Trần Văn An"
                className={INPUT_CLASS}
              />
            </div>
          )}

          {/* Mô tả (cho Chuyên khoa) */}
          {!isRoom && (
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Mô tả chức năng & nhiệm vụ
              </label>
              <textarea
                rows={3}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                placeholder="Mô tả các lĩnh vực khám chữa bệnh, điều trị chuyên sâu của khoa..."
                className={`${INPUT_CLASS} resize-none`}
              />
            </div>
          )}

          {/* Footer buttons */}
          <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-2.5 shrink-0">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              Hủy
            </Button>
            <Button type="submit" variant="mint" size="sm" disabled={submitting}>
              {submitting ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : isRoom ? 'Thêm phòng' : 'Thêm chuyên khoa'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default DepartmentFormModal;

