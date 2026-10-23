import { useState, useEffect } from 'react';
import { Button } from '../ui/button';
import { Building2, DoorOpen, X } from 'lucide-react';

const INPUT_CLASS =
  'w-full px-3.5 py-2.5 text-xs rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all placeholder:text-slate-400 disabled:bg-slate-50 disabled:text-slate-400';

const SELECT_CLASS =
  'w-full px-3.5 py-2.5 text-xs rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-teal-500/10 transition-all cursor-pointer disabled:bg-slate-50 disabled:text-slate-400';

export function DepartmentFormModal({
  isOpen,
  mode = 'CREATE', // 'CREATE' | 'EDIT'
  targetType = 'ROOM', // 'ROOM' | 'DEPARTMENT'
  initialData = null,
  departments = [],
  onSave,
  onClose,
}) {
  const isEdit = mode === 'EDIT';
  const isRoom = targetType === 'ROOM';

  const [formData, setFormData] = useState({
    code: '',
    name: '',
    departmentId: '',
    type: isRoom ? 'EXAMINATION' : 'CLINICAL',
    location: '',
    headPerson: '',
    description: '',
    status: 'ACTIVE',
  });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      if (initialData) {
        setFormData({
          code: initialData.code || '',
          name: initialData.name || '',
          departmentId: initialData.departmentId || (departments[0]?.id || ''),
          type: initialData.type || (isRoom ? 'EXAMINATION' : 'CLINICAL'),
          location: initialData.location || '',
          headPerson: initialData.headPerson || '',
          description: initialData.description || '',
          status: initialData.status || 'ACTIVE',
        });
      } else {
        setFormData({
          code: '',
          name: '',
          departmentId: departments[0]?.id || '',
          type: isRoom ? 'EXAMINATION' : 'CLINICAL',
          location: isRoom ? '' : 'Tòa A - Tầng 1',
          headPerson: '',
          description: '',
          status: 'ACTIVE',
        });
      }
      setErrors({});
      setSubmitting(false);
    }
  }, [isOpen, initialData, departments, isRoom]);

  if (!isOpen) return null;

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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl border border-[#e3ece9] transition-all transform scale-100 max-h-[90vh] overflow-y-auto"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3.5 border-b border-slate-100">
          <div className="flex items-center gap-3">
            <div
              className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${
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
                  ? `Cập nhật thông tin cho ${formData.name || formData.code}`
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

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
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

          {/* Form specific for ROOM: Chuyên khoa trực thuộc */}
          {isRoom && (
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
          )}

          {/* Loại phòng / Loại chuyên môn & Trạng thái */}
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

          {/* Vị trí / Khu vực */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              {isRoom ? 'Vị trí / Tầng buồng' : 'Vị trí / Khu vực'}{' '}
              {isRoom && <span className="text-rose-500">*</span>}
            </label>
            <input
              type="text"
              value={formData.location}
              onChange={(e) => handleChange('location', e.target.value)}
              placeholder={isRoom ? 'VD: Tòa A - Tầng 1 - P.102' : 'VD: Tòa A - Tầng 1'}
              className={INPUT_CLASS}
            />
            {errors.location && (
              <p className="text-[11px] text-rose-500 mt-1">{errors.location}</p>
            )}
          </div>

          {/* Người phụ trách */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              {isRoom ? 'Bác sĩ / KTV phụ trách' : 'Trưởng khoa phụ trách'}
            </label>
            <input
              type="text"
              value={formData.headPerson}
              onChange={(e) => handleChange('headPerson', e.target.value)}
              placeholder="VD: BSCKII Trần Văn An"
              className={INPUT_CLASS}
            />
          </div>

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
          <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-2.5">
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
