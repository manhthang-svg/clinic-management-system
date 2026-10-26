import { useState, useMemo } from 'react';
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
} from 'lucide-react';
import { getServicesForRoom } from '../../constants/serviceData';
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
    location: isRoom ? '' : 'Tòa A - Tầng 1',
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

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
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

  // Preview danh sách dịch vụ phục vụ tương ứng với phòng
  const previewServices = useMemo(() => {
    if (!isRoom) return [];
    return getServicesForRoom({
      code: formData.code,
      name: formData.name,
      type: formData.type,
    });
  }, [formData.code, formData.name, formData.type, isRoom]);

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

            {/* Vị trí (khi là khoa) */}
            {!isRoom && (
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Vị trí / Khu vực
                </label>
                <input
                  type="text"
                  value={formData.location}
                  onChange={(e) => handleChange('location', e.target.value)}
                  placeholder="VD: Tòa A - Tầng 1"
                  className={INPUT_CLASS}
                />
              </div>
            )}
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

              {/* Bảng xem trước dịch vụ phục vụ tại phòng */}
              <div className="pt-1 space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-slate-800 text-[11px]">
                    <Stethoscope className="w-3.5 h-3.5 text-[#0d9488]" />
                    <span>Dịch vụ y tế phòng tiếp nhận phục vụ ({previewServices.length})</span>
                  </div>
                  <span className="text-[10px] text-slate-400">Tự động đối soát theo mã phòng</span>
                </div>

                <div className="overflow-x-auto rounded-lg border border-slate-200/80 bg-slate-50/50">
                  <table className="w-full text-left border-collapse text-[10.5px]">
                    <thead>
                      <tr className="bg-slate-100/80 text-slate-700 border-b border-slate-200/80">
                        <th className="px-2.5 py-2 font-bold">Mã DV</th>
                        <th className="px-2.5 py-2 font-bold">Tên dịch vụ</th>
                        <th className="px-2.5 py-2 font-bold text-right">Viện phí</th>
                        <th className="px-2.5 py-2 font-bold text-right">BHYT</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {previewServices.length === 0 ? (
                        <tr>
                          <td colSpan="4" className="p-3 text-center text-slate-400 text-[10px]">
                            Chưa có dịch vụ nào liên kết.
                          </td>
                        </tr>
                      ) : (
                        previewServices.map((s) => (
                          <tr key={s.code} className="hover:bg-white transition-colors">
                            <td className="px-2.5 py-1.5 font-mono font-semibold text-slate-600">
                              {s.code}
                            </td>
                            <td className="px-2.5 py-1.5 text-slate-800 font-medium">
                              {s.name}
                            </td>
                            <td className="px-2.5 py-1.5 text-right font-bold text-slate-800">
                              {formatPrice(s.clinicPrice)} đ
                            </td>
                            <td className="px-2.5 py-2 text-right text-emerald-700 font-semibold">
                              {s.insurancePrice ? `${formatPrice(s.insurancePrice)} đ` : '—'}
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

