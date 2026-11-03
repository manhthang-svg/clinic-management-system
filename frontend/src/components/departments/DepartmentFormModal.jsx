import { useState, useMemo, useRef, useEffect } from 'react';
import { Button } from '../ui/button';
import {
  Building2,
  DoorOpen,
  X,
  Stethoscope,
  Info,
  Search,
  ChevronDown,
  Trash2,
} from 'lucide-react';
import { useDepartmentServices } from '../../hooks';
import { describeApiError } from '../../utils/describeApiError';
import { formatPrice } from '../services/serviceFormat';
import { apiClient } from '../../services/session';
import { createServiceApi } from '../../apis/serviceApi';
import {
  SERVICE_GROUP_TYPES,
  getParaclinicalDepartmentType,
  resolveServiceGroupType,
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
  const paraclinicalType = getParaclinicalDepartmentType(dept);
  return paraclinicalType ? 'PARACLINICAL' : 'CLINICAL';
}

function _unusedLegacyCategory(dept) {
  if (!dept) return 'CLINICAL';
  if (dept.type === 'PARACLINICAL') return 'PARACLINICAL';
  const name = (dept.name || dept.tenkhoa || '').toLowerCase();
  if (
    name.includes('chẩn đoán') ||
    name.includes('xét nghiệm') ||
    name.includes('hình ảnh') ||
    name.includes('thăm dò') ||
    name.includes('nội soi') ||
    name.includes('huyết học') ||
    name.includes('sinh hóa') ||
    name.includes('vi sinh')
  ) {
    return 'PARACLINICAL';
  }
  return 'CLINICAL';
}

function _unusedLegacyGetParaclinicalDepartmentType(dept) {
  if (!dept) return null;
  const code = (dept.code || dept.departmentCode || dept.makhoa || '').toUpperCase();
  const name = (dept.name || dept.departmentName || dept.tenkhoa || '').toLowerCase();

  // LAB: Khoa Xét nghiệm (Huyết học, Sinh hóa, Vi sinh)
  if (
    code.includes('XN') ||
    code.includes('LAB') ||
    name.includes('xét nghiệm') ||
    name.includes('huyết học') ||
    name.includes('sinh hóa') ||
    name.includes('vi sinh')
  ) {
    return 'LAB';
  }

  // IMAGING: Khoa Chẩn đoán hình ảnh (CT, MRI, X-quang, Siêu âm)
  if (
    code.includes('CDHA') ||
    code.includes('IMAGE') ||
    code.includes('IMAGING') ||
    code.includes('XQ') ||
    name.includes('chẩn đoán') ||
    name.includes('hình ảnh') ||
    name.includes('x-quang') ||
    name.includes('siêu âm')
  ) {
    return 'IMAGING';
  }

  // FUNCTIONAL: Khoa Thăm dò chức năng (Nội soi, Điện tim)
  if (
    code.includes('TDCN') ||
    code.includes('FUNC') ||
    name.includes('thăm dò') ||
    name.includes('chức năng') ||
    name.includes('nội soi') ||
    name.includes('điện tim')
  ) {
    return 'FUNCTIONAL';
  }

  return null;
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
      headPerson: initialData.headPerson || '',
      description: initialData.description || '',
      status: initialData.status || 'ACTIVE',
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
    headPerson: '',
    description: '',
    status: 'ACTIVE',
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

  // Lấy dịch vụ thật từ backend theo đúng loại phòng và nhóm dịch vụ (không dùng mock)
  const { services: deptServices, loading: deptServicesLoading, error: deptServicesError } =
    useDepartmentServices(
      isRoom
        ? {
            departmentId: formData.departmentId || null,
            serviceGroupId: formData.serviceGroupId || null,
            roomType: formData.type || 'EXAMINATION',
          }
        : null
    );

  // Danh sách dịch vụ tùy chỉnh do người dùng thao tác (thêm/xóa)
  const [customServices, setCustomServices] = useState(null);

  // Danh sách dịch vụ được gán cho phòng:
  // 1. Nếu người dùng đã tự sửa (thêm/bớt) -> dùng customServices
  // 2. Nếu có sẵn trong initialData -> dùng initialData.assignedServices
  // 3. Mặc định là [] (chưa chọn dịch vụ nào)
  const assignedServices = useMemo(() => {
    if (!isRoom) return [];
    if (customServices !== null) return customServices;
    if (isEdit && Array.isArray(initialData?.assignedServices) && initialData.assignedServices.length > 0) {
      return initialData.assignedServices;
    }
    return [];
  }, [isRoom, customServices, isEdit, initialData?.assignedServices]);

  const _unusedAssignedServices = useMemo(() => {
    if (!isRoom) return [];
    if (customServices !== null) return customServices;
    if (Array.isArray(initialData?.assignedServices)) {
      return initialData.assignedServices;
    }
    return [];
  }, [isRoom, customServices, initialData?.assignedServices]);

  // Nhóm dịch vụ kỹ thuật của phòng cận lâm sàng
  const effectiveServiceGroupId = useMemo(() => {
    if (formData.serviceGroupId) return formData.serviceGroupId;
    if (initialData?.serviceGroupId) return initialData.serviceGroupId;
    if (isEdit && isRoom && formData.type === 'PARACLINICAL') {
      const fromServices = assignedServices[0]?.serviceGroupId;
      if (fromServices) return fromServices;
    }
    return '';
  }, [formData.serviceGroupId, initialData, isEdit, isRoom, formData.type, assignedServices]);

  // State các mã dịch vụ được tick tạm thời trong dropdown
  const [customSelectedCodes, setCustomSelectedCodes] = useState(null);
  const selectedCodes = useMemo(() => {
    if (!isRoom) return new Set();
    if (customSelectedCodes !== null) return customSelectedCodes;
    return new Set(assignedServices.map((s) => s.code));
  }, [isRoom, customSelectedCodes, assignedServices]);

  // State tìm kiếm dịch vụ
  const [serviceSearchQuery, setServiceSearchQuery] = useState('');
  const [serviceDropdownOpen, setServiceDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);

  // Đóng dropdown khi click ra ngoài ô chọn
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setServiceDropdownOpen(false);
        setCustomSelectedCodes(null);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // Khoa hiện tại mà phòng trực thuộc
  const currentDeptObj = useMemo(() => {
    return departments.find((d) => d.id === formData.departmentId) || null;
  }, [departments, formData.departmentId]);

  // Hiển thị đầy đủ cả Khoa lâm sàng và Khoa cận lâm sàng trong danh sách chọn
  const clinicalDepartments = useMemo(() => {
    return departments.filter((d) => getDepartmentCategory(d) === 'CLINICAL');
  }, [departments]);

  const paraclinicalDepartments = useMemo(() => {
    return departments.filter((d) => getDepartmentCategory(d) === 'PARACLINICAL');
  }, [departments]);

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

  const _unusedLegacyFilteredServiceGroups = useMemo(() => {
    if (!formData.departmentId) return [];
    const selectedDept = departments.find((d) => d.id === formData.departmentId);
    if (!selectedDept) return serviceGroups;

    const deptType = getParaclinicalDepartmentType(selectedDept);
    if (!deptType) return serviceGroups;

    const matched = serviceGroups.filter((g) => {
      // 1. Kiểm tra trường type từ API backend (LAB, IMAGING, FUNCTIONAL)
      if (g.type && String(g.type).toUpperCase() === deptType) return true;
      // 2. Kiểm tra theo bảng mapping mã code nhóm (HEMATOLOGY -> LAB, ...)
      const groupCodeType = SERVICE_GROUP_TYPES[g.code];
      if (groupCodeType && groupCodeType === deptType) return true;
      // 3. Fallback theo tên nhóm
      const groupName = (g.name || '').toLowerCase();
      if (deptType === 'LAB') {
        return (
          groupName.includes('huyết học') ||
          groupName.includes('sinh hóa') ||
          groupName.includes('vi sinh') ||
          groupName.includes('xét nghiệm')
        );
      }
      if (deptType === 'IMAGING') {
        return (
          groupName.includes('cắt lớp') ||
          groupName.includes('cộng hưởng') ||
          groupName.includes('x-quang') ||
          groupName.includes('siêu âm') ||
          groupName.includes('hình ảnh')
        );
      }
      if (deptType === 'FUNCTIONAL') {
        return (
          groupName.includes('nội soi') ||
          groupName.includes('điện tim') ||
          groupName.includes('thăm dò')
        );
      }
      return false;
    });

    // Khi edit, nếu phòng đã có nhóm kỹ thuật mà không nằm trong danh sách lọc thì vẫn bảo toàn
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

  // Danh sách dịch vụ lọc theo từ khóa tìm kiếm
  const filteredDeptServices = useMemo(() => {
    const term = serviceSearchQuery.trim().toLowerCase();
    if (!term) return deptServices;
    return deptServices.filter(
      (s) =>
        s.code?.toLowerCase().includes(term) ||
        s.name?.toLowerCase().includes(term)
    );
  }, [deptServices, serviceSearchQuery]);

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
      setCustomServices(null);
      setCustomSelectedCodes(new Set());
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
    setCustomServices(null);
    setCustomSelectedCodes(new Set());
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
      setCustomServices(null);
      setCustomSelectedCodes(new Set());
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
    setCustomServices(null);
    setCustomSelectedCodes(new Set());
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
    // Nếu đổi nhóm kỹ thuật -> reset danh sách dịch vụ đã chọn
    if (field === 'serviceGroupId') {
      setCustomServices(null);
      setCustomSelectedCodes(new Set());
    }
  };

  const handleOpenDropdown = () => {
    setCustomSelectedCodes(new Set(assignedServices.map((s) => s.code)));
    setServiceDropdownOpen(true);
  };

  const handleToggleService = (serviceCode) => {
    setCustomSelectedCodes((prev) => {
      const base = prev !== null ? prev : new Set(assignedServices.map((s) => s.code));
      const next = new Set(base);
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
    const base =
      customSelectedCodes !== null
        ? customSelectedCodes
        : new Set(assignedServices.map((s) => s.code));
    const allChecked = displayedCodes.every((c) => base.has(c));

    setCustomSelectedCodes(() => {
      const next = new Set(base);
      if (allChecked) {
        displayedCodes.forEach((c) => next.delete(c));
      } else {
        displayedCodes.forEach((c) => next.add(c));
      }
      return next;
    });
  };

  const handleSaveSelectedServices = () => {
    const currentCodes = selectedCodes;
    const existingMap = new Map(assignedServices.map((s) => [s.code, s]));
    const deptMap = new Map((deptServices || []).map((s) => [s.code, s]));

    const newAssigned = [];
    currentCodes.forEach((code) => {
      const srv = deptMap.get(code) || existingMap.get(code);
      if (srv) newAssigned.push(srv);
    });

    setCustomServices(newAssigned);
    setCustomSelectedCodes(null);
    setServiceDropdownOpen(false);
    setServiceSearchQuery('');
  };

  const handleCancelSelection = () => {
    setCustomSelectedCodes(null);
    setServiceDropdownOpen(false);
    setServiceSearchQuery('');
  };

  const handleRemoveService = (serviceCode) => {
    const next = assignedServices.filter((s) => s.code !== serviceCode);
    setCustomServices(next);
    setCustomSelectedCodes(null);
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

    // BR-68 (Khoa: 2-100 ký tự) & BR-72 (Phòng: 2-100 ký tự)
    if (!name) {
      newErrors.name = isRoom ? 'Vui lòng nhập tên phòng' : 'Vui lòng nhập tên chuyên khoa';
    } else if (name.length < 2 || name.length > 100) {
      newErrors.name = isRoom
        ? 'Tên phòng phải từ 2 đến 100 ký tự'
        : 'Tên chuyên khoa phải từ 2 đến 100 ký tự';
    }

    if (isRoom) {
      if (!formData.departmentId) {
        newErrors.departmentId = 'Vui lòng chọn chuyên khoa trực thuộc';
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
                      <option value="INACTIVE">Tạm ngưng</option>
                      <option value="MAINTENANCE">Đang bảo trì thiết bị</option>
                    </select>
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
            <div className="p-3.5 bg-white rounded-xl border border-slate-200/90 space-y-3">
              {!formData.type ? (
                <div className="p-4 text-center text-xs text-slate-500 bg-slate-50 rounded-xl border border-dashed border-slate-200 flex items-center justify-center gap-2">
                  <Info className="w-4 h-4 text-slate-400 shrink-0" />
                  <span>
                    Vui lòng chọn <strong>Loại phòng</strong> ở trên để cấu hình danh mục dịch vụ tiếp nhận.
                  </span>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs">
                      <Stethoscope className="w-4 h-4 text-[#0d9488]" />
                      <span>
                        {formData.type === 'EXAMINATION'
                          ? `Dịch vụ khám bệnh tiếp nhận (${assignedServices.length})`
                          : `Dịch vụ kỹ thuật phòng thực hiện (${assignedServices.length})`}
                      </span>
                    </div>
                    <span className="text-[10.5px] text-slate-400">Đơn vị: VNĐ</span>
                  </div>

                  {formData.type === 'PARACLINICAL' && !effectiveServiceGroupId && assignedServices.length === 0 ? (
                    <div className="p-3.5 text-center text-xs text-amber-800 bg-amber-50 rounded-xl border border-amber-200 flex items-center justify-center gap-2">
                      <Info className="w-4 h-4 text-amber-600 shrink-0" />
                      <span>
                        Vui lòng chọn <strong>Nhóm dịch vụ kỹ thuật</strong> ở trên để tải danh mục dịch vụ phù hợp cho phòng.
                      </span>
                    </div>
                  ) : (
                  /* Thanh tìm kiếm & chọn dịch vụ (Search & Select Dropdown) */
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
                            {formData.type === 'EXAMINATION'
                              ? `Danh mục khám lâm sàng (${filteredDeptServices.length})`
                              : `Dịch vụ nhóm ${currentGroupObj ? currentGroupObj.name : ''} (${filteredDeptServices.length})`}
                          </span>
                          <span className="text-[10px] text-teal-700 font-bold bg-teal-50 px-2 py-0.5 rounded border border-teal-100">
                            Đã tick: {selectedCodes.size}
                          </span>
                        </div>

                        {/* Danh sách các dịch vụ kèm ô checkbox bên trái */}
                        <div className="overflow-y-auto flex-1 divide-y divide-slate-100">
                          {deptServicesLoading ? (
                            <div className="p-4 text-center text-slate-400 text-xs flex items-center justify-center gap-2">
                              <div className="w-3.5 h-3.5 border-2 border-teal-600 border-t-transparent rounded-full animate-spin" />
                              <span>Đang tải danh mục dịch vụ từ máy chủ...</span>
                            </div>
                          ) : deptServicesError ? (
                            <div className="p-4 text-center text-rose-500 text-xs">
                              {describeApiError(deptServicesError, 'Lỗi tải dịch vụ')}
                            </div>
                          ) : filteredDeptServices.length === 0 ? (
                            <div className="p-4 text-center text-slate-400 text-xs">
                              {formData.type === 'EXAMINATION'
                                ? 'Không tìm thấy dịch vụ khám bệnh nào phù hợp.'
                                : 'Không tìm thấy dịch vụ nào thuộc nhóm kỹ thuật này.'}
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
              )}

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

