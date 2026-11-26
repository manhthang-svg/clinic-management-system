import { useState, useMemo, useEffect } from 'react';
import { Button } from '../ui/button';
import {
  Building2,
  DoorOpen,
  X,
  Stethoscope,
  Info,
} from 'lucide-react';
import { useDepartmentServices } from '../../hooks';
import { describeApiError } from '../../utils/describeApiError';
import { formatPrice } from '../services/serviceFormat';
import { apiClient } from '../../services/session';
import { createServiceApi } from '../../apis/serviceApi';
import {
  getParaclinicalDepartmentType,
  doesServiceGroupMatchDepartment,
} from '../../constants/serviceData';

const serviceApi = createServiceApi(apiClient);

const INPUT_CLASS =
  'w-full px-3.5 py-2.5 text-xs rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all placeholder:text-slate-400 disabled:bg-slate-50 disabled:text-slate-400';

const SELECT_CLASS =
  'w-full px-3.5 py-2.5 text-xs rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all cursor-pointer disabled:bg-slate-50 disabled:text-slate-400';

function getDepartmentCategory(dept) {
  if (!dept) return 'CLINICAL';
  if (dept.type === 'PARACLINICAL') return 'PARACLINICAL';
  // the stored type wins; the name is only a fallback for items without one
  if (dept.type === 'CLINICAL') return 'CLINICAL';
  const paraclinicalType = getParaclinicalDepartmentType(dept);
  return paraclinicalType ? 'PARACLINICAL' : 'CLINICAL';
}

function buildInitialForm({ initialData, departments, isRoom }) {
  if (initialData) {
    return {
      code: initialData.code || '',
      name: initialData.name || '',
      departmentId: initialData.departmentId || (departments[0]?.id || ''),
      serviceGroupId: initialData.serviceGroupId || '',
      type: initialData.type || (isRoom ? '' : 'CLINICAL'),
      location: initialData.location || '',
      description: initialData.description || '',
      status: initialData.status || 'ACTIVE',
      version: initialData.version ?? null,
    };
  }

  // Khi THÊM MỚI PHÒNG: mặc định là chưa chọn loại phòng ('')
  return {
    code: '',
    name: '',
    departmentId: '',
    serviceGroupId: '',
    type: isRoom ? '' : 'CLINICAL',
    location: '',
    description: '',
    status: 'ACTIVE',
    version: null,
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
  existingItems = [],
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

  // Danh sách các nhóm dịch vụ kỹ thuật (service_groups) từ backend
  const [serviceGroups, setServiceGroups] = useState([]);
  useEffect(() => {
    let isMounted = true;
    serviceApi.formOptions()
      .then((data) => {
        if (isMounted && Array.isArray(data?.serviceGroups)) {
          setServiceGroups(data.serviceGroups);
        }
      })
      .catch((err) => {
        console.error('Không thể tải nhóm dịch vụ:', err);
      });
    return () => { isMounted = false; };
  }, []);

  // Nhóm dịch vụ kỹ thuật của phòng cận lâm sàng
  const effectiveServiceGroupId = useMemo(() => {
    if (formData.serviceGroupId) return formData.serviceGroupId;
    if (initialData?.serviceGroupId) return initialData.serviceGroupId;
    return '';
  }, [formData.serviceGroupId, initialData]);

  // Lấy dịch vụ thật từ backend theo đúng loại phòng và nhóm dịch vụ (không dùng mock)
  const { services: deptServices, loading: deptServicesLoading, error: deptServicesError } =
    useDepartmentServices(
      isRoom
        ? {
            departmentId: formData.departmentId || null,
            serviceGroupId: effectiveServiceGroupId || null,
            roomType: formData.type || 'EXAMINATION',
          }
        : null
    );

  // Danh sách dịch vụ được gán cho phòng theo quy tắc nghiệp vụ BR-28:
  // - Phòng khám lâm sàng: Dịch vụ khám chung (EXAMINATION)
  // - Phòng cận lâm sàng: Toàn bộ dịch vụ thuộc nhóm kỹ thuật (serviceGroupId) của phòng
  const assignedServices = useMemo(() => {
    if (!isRoom) return [];
    if (Array.isArray(deptServices) && deptServices.length > 0) {
      return deptServices;
    }
    if (isEdit && Array.isArray(initialData?.assignedServices) && initialData.assignedServices.length > 0) {
      return initialData.assignedServices;
    }
    return [];
  }, [isRoom, deptServices, isEdit, initialData?.assignedServices]);

  // Khoa hiện tại mà phòng trực thuộc
  const currentDeptObj = useMemo(() => {
    return departments.find((d) => d.id === formData.departmentId) || null;
  }, [departments, formData.departmentId]);

  // Hiển thị đầy đủ cả Khoa lâm sàng và Khoa cận lâm sàng trong danh sách chọn.
  // Khi tạo phòng mới (!isEdit), chỉ hiển thị các chuyên khoa đang hoạt động (ACTIVE)
  // để không cho phép tạo phòng trong chuyên khoa đang tạm ngưng.
  const clinicalDepartments = useMemo(() => {
    return departments.filter(
      (d) => getDepartmentCategory(d) === 'CLINICAL' && (isEdit || d.status === 'ACTIVE' || d.active === true)
    );
  }, [departments, isEdit]);

  const paraclinicalDepartments = useMemo(() => {
    return departments.filter(
      (d) => getDepartmentCategory(d) === 'PARACLINICAL' && (isEdit || d.status === 'ACTIVE' || d.active === true)
    );
  }, [departments, isEdit]);

  // Lọc nhóm dịch vụ kỹ thuật tương ứng chính xác với Chuyên khoa cận lâm sàng đang chọn
  const filteredServiceGroups = useMemo(() => {
    if (!formData.departmentId) return [];
    const selectedDept = departments.find((d) => d.id === formData.departmentId);
    if (!selectedDept) return serviceGroups;

    const matched = serviceGroups.filter((g) => doesServiceGroupMatchDepartment(g, selectedDept));
    if (isEdit && effectiveServiceGroupId) {
      const current = serviceGroups.find((g) => g.id === effectiveServiceGroupId);
      if (current && !matched.some((g) => g.id === current.id)) {
        return [current, ...matched];
      }
    }
    return matched;
  }, [departments, formData.departmentId, serviceGroups, isEdit, effectiveServiceGroupId]);

  // Nhóm dịch vụ kỹ thuật hiện tại của phòng cận lâm sàng
  const currentGroupObj = useMemo(() => {
    return serviceGroups.find((g) => g.id === effectiveServiceGroupId) || null;
  }, [serviceGroups, effectiveServiceGroupId]);

  // Xử lý đổi chuyên khoa trực thuộc -> tự động cập nhật loại phòng theo chuyên khoa đã chọn
  const handleDepartmentChange = (deptId) => {
    if (isEdit) return; // Không được sửa chuyên khoa khi edit
    if (!deptId) {
      setFormData((prev) => ({
        ...prev,
        departmentId: '',
        type: '',
        serviceGroupId: '',
      }));
      return;
    }

    const selectedDept = departments.find((d) => d.id === deptId);
    const category = getDepartmentCategory(selectedDept);

    if (category === 'PARACLINICAL') {
      setFormData((prev) => ({
        ...prev,
        departmentId: deptId,
        type: 'PARACLINICAL',
        serviceGroupId: '', // Reset nhóm kỹ thuật để chọn đúng nhóm của chuyên khoa này
      }));
    } else {
      setFormData((prev) => ({
        ...prev,
        departmentId: deptId,
        type: 'EXAMINATION',
        serviceGroupId: '',
      }));
    }
    if (errors.departmentId || errors.type || errors.serviceGroupId) {
      setErrors((prev) => ({ ...prev, departmentId: null, type: null, serviceGroupId: null }));
    }
  };

  // Xử lý đổi loại phòng -> kiểm tra tính tương thích với chuyên khoa
  const handleRoomTypeChange = (newType) => {
    if (isEdit) return; // Không được sửa loại phòng khi edit

    if (!newType) {
      setFormData((prev) => ({
        ...prev,
        type: '',
        serviceGroupId: '',
      }));
      return;
    }

    const currentDept = departments.find((d) => d.id === formData.departmentId);
    const currentCategory = currentDept ? getDepartmentCategory(currentDept) : null;

    let targetDeptId = formData.departmentId;
    // Nếu loại phòng chọn không tương thích với khoa hiện tại thì reset để người dùng chọn lại từ danh mục đầy đủ
    if (newType === 'EXAMINATION' && currentCategory === 'PARACLINICAL') {
      targetDeptId = '';
    } else if (newType === 'PARACLINICAL' && currentCategory === 'CLINICAL') {
      targetDeptId = '';
    }

    setFormData((prev) => ({
      ...prev,
      type: newType,
      departmentId: targetDeptId,
      serviceGroupId: newType === 'PARACLINICAL' ? prev.serviceGroupId : '',
    }));
    if (errors.type) {
      setErrors((prev) => ({ ...prev, type: null }));
    }
  };

  const handleChange = (field, value) => {
    setFormData((prev) => {
      const next = { ...prev, [field]: value };
      if (field === 'type' && value !== 'PARACLINICAL') {
        next.serviceGroupId = '';
      }
      return next;
    });
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  const validate = () => {
    const newErrors = {};
    const code = (formData.code || '').trim().toUpperCase();
    const name = (formData.name || '').trim();

    // BR-67 (Khoa: 2-50 ký tự, regex ^[A-Z0-9-]+$) & BR-71 (Phòng: 2-20 ký tự, regex ^[A-Z0-9-]+$)
    if (!code) {
      newErrors.code = isRoom ? 'Vui lòng nhập mã phòng' : 'Vui lòng nhập mã chuyên khoa';
    } else if (isRoom) {
      if (code.length < 2 || code.length > 20) {
        newErrors.code = 'Mã phòng phải từ 2 đến 20 ký tự';
      } else if (!/^[A-Z0-9-]+$/.test(code)) {
        newErrors.code = 'Mã phòng chỉ bao gồm chữ in hoa, chữ số hoặc gạch ngang (-)';
      }
    } else {
      if (code.length < 2 || code.length > 50) {
        newErrors.code = 'Mã chuyên khoa phải từ 2 đến 50 ký tự';
      } else if (!/^[A-Z0-9-]+$/.test(code)) {
        newErrors.code = 'Mã chuyên khoa chỉ bao gồm chữ in hoa, chữ số hoặc gạch ngang (-)';
      }
    }

    if (!newErrors.code && existingItems && existingItems.length > 0) {
      const isDuplicateCode = existingItems.some(
        (item) =>
          item.unitType === targetType &&
          (!isEdit || item.id !== initialData?.id) &&
          (item.code || '').trim().toUpperCase() === code
      );
      if (isDuplicateCode) {
        newErrors.code = isRoom
          ? 'Mã phòng khám đã tồn tại trong hệ thống'
          : 'Mã chuyên khoa đã tồn tại trong hệ thống';
      }
    }

    // BR-68 (Khoa: 2-100 ký tự) & BR-72 (Phòng: 2-100 ký tự)
    if (!name) {
      newErrors.name = isRoom ? 'Vui lòng nhập tên phòng' : 'Vui lòng nhập tên chuyên khoa';
    } else if (name.length < 2 || name.length > 100) {
      newErrors.name = isRoom
        ? 'Tên phòng phải từ 2 đến 100 ký tự'
        : 'Tên chuyên khoa phải từ 2 đến 100 ký tự';
    }

    if (!newErrors.name && existingItems && existingItems.length > 0) {
      const isDuplicateName = existingItems.some(
        (item) =>
          item.unitType === targetType &&
          (!isEdit || item.id !== initialData?.id) &&
          (item.name || '').trim().toLowerCase() === name.toLowerCase()
      );
      if (isDuplicateName) {
        newErrors.name = isRoom
          ? 'Tên phòng khám đã tồn tại trong hệ thống'
          : 'Tên chuyên khoa đã tồn tại trong hệ thống';
      }
    }

    if (isRoom) {
      if (!formData.departmentId) {
        newErrors.departmentId = 'Vui lòng chọn chuyên khoa trực thuộc';
      } else if (!isEdit) {
        const dept = departments.find((d) => d.id === formData.departmentId);
        if (dept && (dept.status === 'INACTIVE' || dept.active === false)) {
          newErrors.departmentId = 'Không thể tạo phòng trong chuyên khoa đang tạm ngưng hoạt động';
        }
      }
      if (!formData.type) {
        newErrors.type = 'Vui lòng chọn loại phòng';
      }
      if (formData.departmentId && formData.type) {
        const dept = departments.find((d) => d.id === formData.departmentId);
        const deptCat = getDepartmentCategory(dept);
        if (formData.type === 'EXAMINATION' && deptCat !== 'CLINICAL') {
          newErrors.type = 'Phòng khám lâm sàng phải trực thuộc Khoa lâm sàng';
        } else if (formData.type === 'PARACLINICAL' && deptCat !== 'PARACLINICAL') {
          newErrors.type = 'Phòng khám cận lâm sàng phải trực thuộc Khoa cận lâm sàng';
        }
      }
      if (formData.type === 'PARACLINICAL' && !effectiveServiceGroupId) {
        newErrors.serviceGroupId = 'Vui lòng chọn nhóm dịch vụ kỹ thuật cho phòng cận lâm sàng';
      }
      // BR-73 (Vị trí: 2-255 ký tự)
      const location = (formData.location || '').trim();
      if (!location) {
        newErrors.location = 'Vui lòng nhập vị trí/buồng (ví dụ: Tòa A - Tầng 1 - P.102)';
      } else if (location.length < 2 || location.length > 255) {
        newErrors.location = 'Vị trí buồng phải từ 2 đến 255 ký tự';
      }
      if (
        isEdit &&
        initialData?.status === 'ACTIVE' &&
        formData.status === 'INACTIVE' &&
        (initialData?.hasDoctorOnDutyToday || initialData?.doctorOnDutyToday || initialData?.hasActiveVisitsToday || initialData?.hasFutureDutySchedules)
      ) {
        newErrors.status =
          (initialData?.hasDoctorOnDutyToday || initialData?.doctorOnDutyToday || initialData?.hasActiveVisitsToday || initialData?.hasFutureDutySchedules)
            ? 'Không được phép tạm ngừng phòng nếu phòng đó đang có bác sĩ trực trong ngày'
            : initialData?.hasActiveVisitsToday
            ? 'Không được phép tạm ngừng phòng nếu phòng vẫn còn bệnh nhân chờ hoặc đang khám dở trong ngày'
            : 'Không được phép tạm ngừng phòng nếu phòng vẫn còn nhân viên được xếp ca trực trong các ngày tới';
      }
    } else {
      // BR-70 (Mô tả khoa: tối đa 500 ký tự)
      if (formData.description && formData.description.length > 500) {
        newErrors.description = 'Mô tả chuyên khoa tối đa 500 ký tự';
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
          serviceGroupId:
            isRoom && formData.type === 'PARACLINICAL'
              ? (effectiveServiceGroupId || null)
              : null,
          assignedServices: isRoom ? assignedServices : [],
          unitType: targetType,
        },
        targetType,
        isEdit
      );
      onClose();
    } catch (err) {
      if (err?.errorResponse?.details && typeof err.errorResponse.details === 'object') {
        const details = err.errorResponse.details;
        const mappedErrors = {};
        if (details.roomCode || details.departmentCode) {
          mappedErrors.code = details.roomCode || details.departmentCode;
        }
        if (details.roomName || details.departmentName) {
          mappedErrors.name = details.roomName || details.departmentName;
        }
        if (details.departmentId) mappedErrors.departmentId = details.departmentId;
        if (details.serviceGroupId) mappedErrors.serviceGroupId = details.serviceGroupId;
        if (details.location) mappedErrors.location = details.location;
        if (details.description) mappedErrors.description = details.description;
        if (details.type) mappedErrors.type = details.type;
        setErrors((prev) => ({ ...prev, ...mappedErrors }));
      }
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
                  ? 'Khai báo buồng khám chuyên khoa hoặc phòng khám cận lâm sàng'
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
                  maxLength={isRoom ? 20 : 50}
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
                  maxLength={100}
                  value={formData.name}
                  onChange={(e) => handleChange('name', e.target.value)}
                  placeholder={isRoom ? 'VD: Phòng khám Nội 1' : 'VD: Khoa Nội Tổng Hợp'}
                  className={INPUT_CLASS}
                />
                {errors.name && <p className="text-[11px] text-rose-500 mt-1">{errors.name}</p>}
              </div>
            </div>

            {/* Chuyên khoa trực thuộc & Loại phòng (khi là phòng) */}
            {isRoom ? (
              <>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                  {/* Cột trái: Chuyên khoa trực thuộc */}
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-xs font-semibold text-slate-700">
                        Chuyên khoa trực thuộc <span className="text-rose-500">*</span>
                      </label>
                      {isEdit && (
                        <span className="text-[10.5px] text-slate-400 italic">
                          (Cố định)
                        </span>
                      )}
                    </div>
                    <select
                      value={formData.departmentId}
                      onChange={(e) => handleDepartmentChange(e.target.value)}
                      disabled={isEdit}
                      className={`${SELECT_CLASS} ${
                        isEdit
                          ? 'bg-slate-100 text-slate-600 cursor-not-allowed border-slate-200 opacity-90'
                          : ''
                      }`}
                    >
                      <option value="">-- Chọn chuyên khoa trực thuộc --</option>
                      {clinicalDepartments.length > 0 && (
                        <optgroup label="Khoa lâm sàng (Khám chữa bệnh)">
                          {clinicalDepartments.map((dept) => (
                            <option key={dept.id || dept.code} value={dept.id}>
                              {dept.name} ({dept.code}) - Khoa lâm sàng
                            </option>
                          ))}
                        </optgroup>
                      )}
                      {paraclinicalDepartments.length > 0 && (
                        <optgroup label="Khoa cận lâm sàng (Kỹ thuật / Xét nghiệm)">
                          {paraclinicalDepartments.map((dept) => (
                            <option key={dept.id || dept.code} value={dept.id}>
                              {dept.name} ({dept.code}) - Khoa cận lâm sàng
                            </option>
                          ))}
                        </optgroup>
                      )}
                    </select>
                    {errors.departmentId && (
                      <p className="text-[11px] text-rose-500 mt-1">{errors.departmentId}</p>
                    )}
                  </div>

                  {/* Cột phải: Loại phòng (thay thế vị trí của Vị trí / Tầng buồng) */}
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-xs font-semibold text-slate-700">
                        Loại phòng <span className="text-rose-500">*</span>
                      </label>
                      {isEdit && (
                        <span className="text-[10.5px] text-slate-400 italic">
                          (Cố định)
                        </span>
                      )}
                    </div>
                    <select
                      value={formData.type}
                      onChange={(e) => handleRoomTypeChange(e.target.value)}
                      disabled={isEdit}
                      className={`${SELECT_CLASS} ${
                        isEdit
                          ? 'bg-slate-100 text-slate-600 cursor-not-allowed border-slate-200 opacity-90'
                          : ''
                      }`}
                    >
                      <option value="">-- Chưa chọn loại phòng --</option>
                      <option value="EXAMINATION">Phòng khám lâm sàng</option>
                      <option value="PARACLINICAL">Phòng khám cận lâm sàng</option>
                    </select>
                    {errors.type && (
                      <p className="text-[11px] text-rose-500 mt-1">{errors.type}</p>
                    )}
                  </div>
                </div>

                {/* Hàng 3: Vị trí / Tầng buồng (nằm ở chỗ Loại phòng cũ) & Nhóm dịch vụ kỹ thuật & Trạng thái */}
                <div
                  className={`grid grid-cols-1 ${
                    formData.type === 'PARACLINICAL' ? 'sm:grid-cols-3' : 'sm:grid-cols-2'
                  } gap-3.5`}
                >
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Vị trí / Tầng buồng <span className="text-rose-500">*</span>
                    </label>
                    <input
                      type="text"
                      maxLength={255}
                      value={formData.location}
                      onChange={(e) => handleChange('location', e.target.value)}
                      placeholder="VD: Tòa A - Tầng 1 - P.102"
                      className={INPUT_CLASS}
                    />
                    {errors.location && (
                      <p className="text-[11px] text-rose-500 mt-1">{errors.location}</p>
                    )}
                  </div>

                  {/* Nhóm dịch vụ kỹ thuật (chỉ xuất hiện cho phòng Cận lâm sàng) */}
                  {formData.type === 'PARACLINICAL' && (
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">
                        Nhóm dịch vụ kỹ thuật <span className="text-rose-500">*</span>
                      </label>
                      <select
                        value={effectiveServiceGroupId || ''}
                        onChange={(e) => handleChange('serviceGroupId', e.target.value)}
                        className={SELECT_CLASS}
                      >
                        <option value="">
                          {filteredServiceGroups.length > 0
                            ? '-- Chọn nhóm dịch vụ --'
                            : '-- Không có nhóm dịch vụ phù hợp --'}
                        </option>
                        {filteredServiceGroups.map((g) => (
                          <option key={g.id || g.code} value={g.id}>
                            {g.name} ({g.code})
                          </option>
                        ))}
                      </select>
                      {errors.serviceGroupId && (
                        <p className="text-[11px] text-rose-500 mt-1">{errors.serviceGroupId}</p>
                      )}
                    </div>
                  )}

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">Trạng thái</label>
                    <select
                      value={formData.status}
                      onChange={(e) => handleChange('status', e.target.value)}
                      className={SELECT_CLASS}
                    >
                      <option value="ACTIVE">Đang hoạt động</option>
                      <option
                        value="INACTIVE"
                        disabled={Boolean(
                          isEdit &&
                            (initialData?.hasDoctorOnDutyToday ||
                              initialData?.doctorOnDutyToday ||
                              initialData?.hasActiveVisitsToday ||
                              initialData?.hasFutureDutySchedules)
                        )}
                      >
                        Tạm ngưng
                        {isEdit && (initialData?.hasDoctorOnDutyToday || initialData?.doctorOnDutyToday)
                          ? ` (Có ${initialData?.type === 'EXAMINATION' ? 'BS' : 'nhân sự'} trực hôm nay)`
                          : isEdit && initialData?.hasActiveVisitsToday
                          ? ' (Đang có bệnh nhân)'
                          : isEdit && initialData?.hasFutureDutySchedules
                          ? ' (Còn ca trực tương lai)'
                          : ''}
                      </option>
                    </select>
                    {isEdit && (initialData?.hasDoctorOnDutyToday || initialData?.doctorOnDutyToday) && (
                      <p className="text-[11px] text-amber-600 mt-1">
                        Phòng đang có {initialData?.type === 'EXAMINATION' ? 'bác sĩ' : 'bác sĩ/kỹ thuật viên'} trực trong ngày
                        {initialData?.doctorOnDutyToday ? ` (${initialData.doctorOnDutyToday})` : ''}.
                        Không thể tạm ngưng.
                      </p>
                    )}
                    {isEdit && !initialData?.hasDoctorOnDutyToday && !initialData?.doctorOnDutyToday && initialData?.hasActiveVisitsToday && (
                      <p className="text-[11px] text-amber-600 mt-1">
                        Phòng đang có bệnh nhân chờ hoặc đang khám dở trong ngày. Không thể tạm ngưng.
                      </p>
                    )}
                    {isEdit && !initialData?.hasDoctorOnDutyToday && !initialData?.doctorOnDutyToday && !initialData?.hasActiveVisitsToday && initialData?.hasFutureDutySchedules && (
                      <p className="text-[11px] text-amber-600 mt-1">
                        Phòng còn nhân viên được xếp ca trực trong các ngày tới. Không thể tạm ngưng.
                      </p>
                    )}
                    {errors.status && <p className="text-[11px] text-rose-500 mt-1">{errors.status}</p>}
                  </div>
                </div>
              </>
            ) : (
              /* Dành cho CHUYÊN KHOA */
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-xs font-semibold text-slate-700">
                      Phân loại chuyên khoa
                    </label>
                    {isEdit && (
                      <span className="text-[10.5px] text-slate-400 italic">
                        (Cố định)
                      </span>
                    )}
                  </div>
                  <select
                    value={formData.type}
                    onChange={(e) => handleChange('type', e.target.value)}
                    disabled={isEdit}
                    className={`${SELECT_CLASS} ${
                      isEdit
                        ? 'bg-slate-100 text-slate-600 cursor-not-allowed border-slate-200 opacity-90'
                        : ''
                    }`}
                  >
                    <option value="CLINICAL">Lâm sàng (Khám chữa bệnh)</option>
                    <option value="PARACLINICAL">Cận lâm sàng (Xét nghiệm/CĐHA/TDCN)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Trạng thái</label>
                  <select
                    value={formData.status}
                    onChange={(e) => handleChange('status', e.target.value)}
                    className={SELECT_CLASS}
                  >
                    <option value="ACTIVE">Đang hoạt động</option>
                    <option value="INACTIVE">Đang khóa</option>
                  </select>
                </div>
              </div>
            )}
          </div>

          {/* Section 2 (Chỉ cho ROOM): Dịch vụ y tế phòng tiếp nhận phục vụ */}
          {isRoom && (
            <div className="p-4 bg-white rounded-xl border border-slate-200/90 space-y-3">
              {!formData.type ? (
                <div className="p-4 text-center text-xs text-slate-500 bg-slate-50 rounded-xl border border-dashed border-slate-200 flex items-center justify-center gap-2">
                  <Info className="w-4 h-4 text-slate-400 shrink-0" />
                  <span>
                    Vui lòng chọn <strong>Loại phòng</strong> ở trên để nạp danh mục dịch vụ tiếp nhận.
                  </span>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <div className="w-6 h-6 rounded-md bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
                        <Stethoscope className="w-3.5 h-3.5" />
                      </div>
                      <span className="font-bold text-slate-800 text-xs">
                        {formData.type === 'EXAMINATION'
                          ? `Dịch vụ khám bệnh tiếp nhận (${assignedServices.length})`
                          : `Dịch vụ kỹ thuật phòng thực hiện (${assignedServices.length})`}
                      </span>
                      {formData.type === 'PARACLINICAL' && currentGroupObj && (
                        <span className="text-[10.5px] font-semibold text-teal-700 bg-teal-50 px-2 py-0.5 rounded-md border border-teal-100">
                          Nhóm: {currentGroupObj.name}
                        </span>
                      )}
                    </div>
                    <span className="text-[10.5px] text-slate-400">Đơn vị: VNĐ</span>
                  </div>

                  {formData.type === 'PARACLINICAL' && (
                    <div className="p-2.5 px-3 text-[11.5px] text-teal-900 bg-teal-50/70 rounded-xl border border-teal-100 flex items-start gap-2">
                      <Info className="w-4 h-4 text-[#0d9488] shrink-0 mt-0.5" />
                      <div className="leading-relaxed">
                        Danh mục dịch vụ kỹ thuật được đồng bộ tự động theo <strong>Nhóm dịch vụ kỹ thuật</strong> của phòng. Để thêm mới hoặc ngừng cung cấp một dịch vụ kỹ thuật, vui lòng điều chỉnh tại <strong>Danh mục dịch vụ y tế</strong>.
                      </div>
                    </div>
                  )}

                  {formData.type === 'PARACLINICAL' && !effectiveServiceGroupId ? (
                    <div className="p-4 text-center text-xs text-amber-800 bg-amber-50 rounded-xl border border-amber-200 flex items-center justify-center gap-2">
                      <Info className="w-4 h-4 text-amber-600 shrink-0" />
                      <span>
                        Vui lòng chọn <strong>Nhóm dịch vụ kỹ thuật</strong> ở trên để nạp danh mục dịch vụ phòng sẽ tiếp nhận thực hiện.
                      </span>
                    </div>
                  ) : deptServicesLoading ? (
                    <div className="p-6 text-center text-slate-400 text-xs flex items-center justify-center gap-2">
                      <div className="w-3.5 h-3.5 border-2 border-teal-600 border-t-transparent rounded-full animate-spin" />
                      <span>Đang tải danh mục dịch vụ từ máy chủ...</span>
                    </div>
                  ) : deptServicesError ? (
                    <div className="p-4 text-center text-rose-500 text-xs">
                      {describeApiError(deptServicesError, 'Lỗi tải danh mục dịch vụ')}
                    </div>
                  ) : (
                    /* Bảng danh sách dịch vụ kỹ thuật phòng thực hiện */
                    <div className="overflow-x-auto rounded-xl border border-slate-200/90 bg-white">
                      <table className="w-full text-left border-collapse text-[11px]">
                        <thead>
                          <tr className="bg-[#f6f8f9] text-[10.5px] font-bold text-[#1b2533] border-b border-slate-200/80">
                            <th className="px-3 py-2.5">Mã DV</th>
                            <th className="px-3 py-2.5">Tên dịch vụ y tế</th>
                            <th className="px-3 py-2.5 text-right">Viện phí</th>
                            <th className="px-3 py-2.5 text-right">BHYT chi trả</th>
                            <th className="px-3 py-2.5 text-center">Trạng thái</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {assignedServices.length === 0 ? (
                            <tr>
                              <td colSpan="5" className="p-6 text-center text-slate-400 italic">
                                {formData.type === 'EXAMINATION'
                                  ? 'Chưa có dịch vụ khám bệnh nào.'
                                  : 'Không tìm thấy dịch vụ kỹ thuật nào thuộc nhóm này trong danh mục y tế.'}
                              </td>
                            </tr>
                          ) : (
                            assignedServices.map((service) => (
                              <tr key={service.code || service.id} className="hover:bg-slate-50/80 transition-colors">
                                <td className="px-3 py-2.5 font-mono font-semibold text-teal-800 whitespace-nowrap">
                                  {service.code}
                                </td>
                                <td className="px-3 py-2.5 font-medium text-slate-800">
                                  <div>{service.name || service.tenchidinh}</div>
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
                                <td className="px-3 py-2.5 text-center whitespace-nowrap">
                                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                    Hoạt động
                                  </span>
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}



          {/* Mô tả (cho Chuyên khoa) */}
          {!isRoom && (
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-semibold text-slate-700">
                  Mô tả chức năng & nhiệm vụ
                </label>
                <span className="text-[10.5px] text-slate-400">
                  {formData.description?.length || 0}/500
                </span>
              </div>
              <textarea
                rows={3}
                maxLength={500}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                placeholder="Mô tả các lĩnh vực khám chữa bệnh, điều trị chuyên sâu của khoa..."
                className={`${INPUT_CLASS} resize-none`}
              />
              {errors.description && (
                <p className="text-[11px] text-rose-500 mt-1">{errors.description}</p>
              )}
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

