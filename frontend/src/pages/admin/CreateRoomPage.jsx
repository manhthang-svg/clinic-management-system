import { useState } from 'react';
import {
  ArrowLeft,
  Building2,
  CheckCircle2,
  MapPin,
  Layers,
  Sparkles,
} from 'lucide-react';

/**
 * Trang Tạo phòng mới dành cho Admin.
 * Khớp hoàn toàn với CreateRoomRequest trên Backend:
 * - code: Mã phòng
 * - name: Tên phòng
 * - location: Vị trí
 * - departmentId: Chuyên khoa
 * - serviceGroupId: Nhóm dịch vụ (tùy chọn)
 * - type: EXAMINATION | PARACLINICAL
 */
export function CreateRoomPage({ onBack, onSave }) {
  const [formData, setFormData] = useState({
    code: 'P101',
    name: '',
    location: '',
    departmentId: '',
    serviceGroupId: '',
    type: 'EXAMINATION',
    status: 'ACTIVE',
    description: '',
  });

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (onSave) {
      onSave(formData);
    }
  };

  return (
    <div className="p-6 md:p-8 space-y-6 max-w-4xl mx-auto">
      {/* Top Navigation & Breadcrumb */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="p-2 rounded-xl bg-white border border-slate-200 hover:bg-slate-50 text-slate-600 transition-colors shadow-2xs cursor-pointer"
              title="Quay lại danh sách"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
          )}
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-400">Quản lý phòng</span>
              <span className="text-xs text-slate-300">/</span>
              <span className="text-xs font-semibold text-[#0d9488]">Thêm phòng mới</span>
            </div>
            <h1 className="text-xl md:text-2xl font-black text-slate-900 tracking-tight mt-0.5">
              Thêm phòng khám / cận lâm sàng mới
            </h1>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="px-4 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 shadow-2xs transition-all cursor-pointer"
            >
              Hủy bỏ
            </button>
          )}
          <button
            type="button"
            onClick={handleSubmit}
            className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm hover:shadow transition-all cursor-pointer"
          >
            <CheckCircle2 className="w-4 h-4" />
            <span>Lưu & Tạo phòng</span>
          </button>
        </div>
      </div>

      {/* Main Form */}
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Card 1: Thông tin cơ bản */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-5">
          <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
            <div className="w-8 h-8 rounded-lg bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
              <Building2 className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Thông tin phòng khám</h2>
              <p className="text-[11px] text-slate-400">
                Nhập mã hiệu, tên phòng và phân loại chuyên môn
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Mã phòng */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Mã phòng <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                placeholder="VD: P101, CDHA02, XN01"
                maxLength={20}
                value={formData.code}
                onChange={(e) => handleChange('code', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 font-mono transition-all"
              />
              <span className="text-[11px] text-slate-400 mt-1 block">
                Tối đa 20 ký tự, mã duy nhất trong bệnh viện
              </span>
            </div>

            {/* Tên phòng */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Tên phòng <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                placeholder="VD: Phòng khám Nội 01, Phòng Siêu âm 4D"
                maxLength={100}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all"
              />
              <span className="text-[11px] text-slate-400 mt-1 block">
                Tên hiển thị công khai cho bệnh nhân
              </span>
            </div>

            {/* Phân loại phòng (RoomType Enum) */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Loại phòng <span className="text-rose-500">*</span>
              </label>
              <select
                value={formData.type}
                onChange={(e) => handleChange('type', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all cursor-pointer"
              >
                <option value="EXAMINATION">Phòng khám lâm sàng (EXAMINATION)</option>
                <option value="PARACLINICAL">Phòng cận lâm sàng (PARACLINICAL)</option>
              </select>
            </div>

            {/* Khoa phòng (Department) */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Thuộc Khoa / Chuyên khoa <span className="text-rose-500">*</span>
              </label>
              <select
                value={formData.departmentId}
                onChange={(e) => handleChange('departmentId', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all cursor-pointer"
              >
                <option value="">-- Chọn khoa phòng --</option>
                <option value="dept-kham-benh">Khoa Khám bệnh</option>
                <option value="dept-noi">Khoa Nội tổng hợp</option>
                <option value="dept-ngoai">Khoa Ngoại</option>
                <option value="dept-xet-nghiem">Khoa Xét nghiệm</option>
                <option value="dept-cdha">Khoa Chẩn đoán hình ảnh</option>
              </select>
            </div>
          </div>
        </div>

        {/* Card 2: Vị trí & Cấu hình */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-5">
          <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
            <div className="w-8 h-8 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center font-bold">
              <MapPin className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Vị trí & Nhóm dịch vụ</h2>
              <p className="text-[11px] text-slate-400">
                Địa điểm vật lý trong khuôn viên bệnh viện METPH
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Vị trí */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Vị trí trong viện <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                placeholder="VD: Tầng 1, Khu Khám A, Tòa nhà Trung tâm"
                maxLength={255}
                value={formData.location}
                onChange={(e) => handleChange('location', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all"
              />
            </div>

            {/* Nhóm dịch vụ (tùy chọn) */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Nhóm dịch vụ liên kết <span className="text-slate-400 font-normal">(Tùy chọn)</span>
              </label>
              <select
                value={formData.serviceGroupId}
                onChange={(e) => handleChange('serviceGroupId', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all cursor-pointer"
              >
                <option value="">-- Không liên kết nhóm dịch vụ --</option>
                <option value="group-kham-chuyen-khoa">Khám chuyên khoa</option>
                <option value="group-xet-nghiem-mau">Xét nghiệm huyết học</option>
                <option value="group-sieu-am">Siêu âm tổng quát & tim mạch</option>
                <option value="group-x-quang">Chụp X-Quang kỹ thuật số</option>
              </select>
            </div>
          </div>

          {/* Trạng thái hoạt động ban đầu */}
          <div className="pt-2">
            <label className="block text-xs font-bold text-slate-700 mb-2">
              Trạng thái ban đầu
            </label>
            <div className="flex items-center gap-4">
              <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-700">
                <input
                  type="radio"
                  name="status"
                  value="ACTIVE"
                  checked={formData.status === 'ACTIVE'}
                  onChange={() => handleChange('status', 'ACTIVE')}
                  className="accent-[#0d9488] w-4 h-4 cursor-pointer"
                />
                <span>Kích hoạt ngay (Sẵn sàng hoạt động)</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-700">
                <input
                  type="radio"
                  name="status"
                  value="INACTIVE"
                  checked={formData.status === 'INACTIVE'}
                  onChange={() => handleChange('status', 'INACTIVE')}
                  className="accent-[#0d9488] w-4 h-4 cursor-pointer"
                />
                <span>Tạm dừng (Chờ trang bị / bảo trì)</span>
              </label>
            </div>
          </div>
        </div>

        {/* Card 3: Ghi chú thiết bị */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-4">
          <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center font-bold">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Mô tả & Trang thiết bị</h2>
              <p className="text-[11px] text-slate-400">
                Thông tin ghi chú về máy móc hoặc cơ sở vật chất của phòng
              </p>
            </div>
          </div>

          <textarea
            rows={3}
            placeholder="VD: Được trang bị máy đo huyết áp điện tử, giường khám chuyên dụng..."
            value={formData.description}
            onChange={(e) => handleChange('description', e.target.value)}
            className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all resize-none"
          />
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-3 pt-2">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="px-5 py-2.5 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 shadow-2xs transition-all cursor-pointer"
            >
              Hủy bỏ
            </button>
          )}
          <button
            type="submit"
            className="inline-flex items-center gap-2 px-6 py-2.5 text-xs font-semibold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm hover:shadow transition-all cursor-pointer"
          >
            <CheckCircle2 className="w-4 h-4" />
            <span>Lưu & Tạo phòng</span>
          </button>
        </div>
      </form>
    </div>
  );
}

export default CreateRoomPage;
