import { useState } from 'react';
import {
  ArrowLeft,
  Building2,
  Calendar,
  Clock,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
  Users,
  MapPin,
  Sparkles,
} from 'lucide-react';

/**
 * Trang tạo tour trực / phòng mới dành cho Admin.
 * Được thiết kế đồng bộ với hệ thống METPH mà không sử dụng các component có sẵn.
 */
export function CreateTourPage({ onBack, onSave }) {
  // Form state phục vụ hiển thị UI (logic submit thực tế do người dùng tự xử lý sau)
  const [formData, setFormData] = useState({
    tourCode: 'TOUR-P101',
    tourName: '',
    department: 'KHOA_KHAM_BENH',
    roomType: 'EXAMINATION',
    location: '',
    maxCapacity: '50',
    shiftType: 'ALL_DAY',
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
    <div className="p-6 md:p-8 space-y-6 max-w-5xl mx-auto">
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
              <span className="text-xs font-semibold text-[#0d9488]">Tạo tour mới</span>
            </div>
            <h1 className="text-xl md:text-2xl font-black text-slate-900 tracking-tight mt-0.5">
              Tạo tour trực / phòng mới
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
            <span>Lưu & Khởi tạo tour</span>
          </button>
        </div>
      </div>

      {/* Main Form Content */}
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Card 1: Thông tin định danh tour / phòng */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-5">
          <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
            <div className="w-8 h-8 rounded-lg bg-teal-50 text-[#0d9488] flex items-center justify-center font-bold">
              <Building2 className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Thông tin cơ bản</h2>
              <p className="text-[11px] text-slate-400">
                Thiết lập mã số, tên gọi và phân bổ chuyên khoa cho tour/phòng
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Mã Tour / Phòng */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Mã tour / phòng <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                placeholder="VD: TOUR-P101, PK-NOI-01"
                value={formData.tourCode}
                onChange={(e) => handleChange('tourCode', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all font-mono"
              />
              <span className="text-[11px] text-slate-400 mt-1 block">
                Mã định danh duy nhất trên toàn hệ thống
              </span>
            </div>

            {/* Tên Tour / Tên Phòng */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Tên tour / phòng <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                placeholder="VD: Tour khám Nội tổng quát 01"
                value={formData.tourName}
                onChange={(e) => handleChange('tourName', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all"
              />
              <span className="text-[11px] text-slate-400 mt-1 block">
                Tên hiển thị trên bảng tiếp đón bệnh nhân
              </span>
            </div>

            {/* Chuyên khoa / Khoa phòng */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Chuyên khoa phụ trách <span className="text-rose-500">*</span>
              </label>
              <select
                value={formData.department}
                onChange={(e) => handleChange('department', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all cursor-pointer"
              >
                <option value="KHOA_KHAM_BENH">Khoa Khám bệnh</option>
                <option value="KHOA_NOI">Khoa Nội tổng hợp</option>
                <option value="KHOA_NGOAI">Khoa Ngoại chấn thương</option>
                <option value="KHOA_CAP_CUU">Khoa Cấp cứu & HSTC</option>
                <option value="KHOA_XET_NGHIEM">Khoa Hóa sinh - Vi sinh</option>
                <option value="KHOA_CDHA">Khoa Chẩn đoán hình ảnh</option>
              </select>
            </div>

            {/* Phân loại tour / phòng */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Phân loại phòng <span className="text-rose-500">*</span>
              </label>
              <select
                value={formData.roomType}
                onChange={(e) => handleChange('roomType', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all cursor-pointer"
              >
                <option value="EXAMINATION">Phòng khám lâm sàng</option>
                <option value="PARACLINICAL">Phòng cận lâm sàng</option>
                <option value="PROCEDURE">Phòng tiểu phẫu / thủ thuật</option>
                <option value="EMERGENCY">Phòng cấp cứu lưu</option>
                <option value="WAITING">Khu vực tiếp đón & chờ</option>
              </select>
            </div>
          </div>
        </div>

        {/* Card 2: Vị trí & Lịch trực / Sức chứa */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-5">
          <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
            <div className="w-8 h-8 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center font-bold">
              <MapPin className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Vị trí & Cấu hình vận hành</h2>
              <p className="text-[11px] text-slate-400">
                Địa điểm vật lý trong tòa nhà và định mức tiếp nhận bệnh nhân
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Vị trí */}
            <div className="md:col-span-2">
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Vị trí / Tầng lầu <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                placeholder="VD: Tầng 1, Nhà A - Khu vực Khám Ngoại trú"
                value={formData.location}
                onChange={(e) => handleChange('location', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all"
              />
            </div>

            {/* Sức chứa tối đa */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Giới hạn lượt khám / tour
              </label>
              <input
                type="number"
                min="1"
                max="500"
                placeholder="50"
                value={formData.maxCapacity}
                onChange={(e) => handleChange('maxCapacity', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
            {/* Khung giờ / Ca trực */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Khung giờ trực áp dụng
              </label>
              <select
                value={formData.shiftType}
                onChange={(e) => handleChange('shiftType', e.target.value)}
                className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all cursor-pointer"
              >
                <option value="ALL_DAY">Toàn thời gian (07:00 - 17:00)</option>
                <option value="MORNING">Ca sáng (07:00 - 11:30)</option>
                <option value="AFTERNOON">Ca chiều (13:00 - 17:00)</option>
                <option value="NIGHT">Ca trực đêm (17:00 - 07:00 hôm sau)</option>
              </select>
            </div>

            {/* Trạng thái hoạt động */}
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Trạng thái khởi tạo
              </label>
              <div className="flex items-center gap-3 pt-1">
                <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-700">
                  <input
                    type="radio"
                    name="status"
                    value="ACTIVE"
                    checked={formData.status === 'ACTIVE'}
                    onChange={() => handleChange('status', 'ACTIVE')}
                    className="accent-[#0d9488] w-4 h-4 cursor-pointer"
                  />
                  <span>Sẵn sàng hoạt động</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-700">
                  <input
                    type="radio"
                    name="status"
                    value="MAINTENANCE"
                    checked={formData.status === 'MAINTENANCE'}
                    onChange={() => handleChange('status', 'MAINTENANCE')}
                    className="accent-[#0d9488] w-4 h-4 cursor-pointer"
                  />
                  <span>Bảo trì / Tạm ngưng</span>
                </label>
              </div>
            </div>
          </div>
        </div>

        {/* Card 3: Ghi chú & Thiết bị */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 space-y-4">
          <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center font-bold">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Ghi chú bổ sung</h2>
              <p className="text-[11px] text-slate-400">
                Trang thiết bị máy móc chuyên dụng hoặc lưu ý đặc biệt cho bác sĩ trực tour
              </p>
            </div>
          </div>

          <div>
            <textarea
              rows={3}
              placeholder="VD: Trang bị máy siêu âm Doppler 4D màu, hệ thống đèn mổ di động, điều hòa 2 chiều..."
              value={formData.description}
              onChange={(e) => handleChange('description', e.target.value)}
              className="w-full px-3.5 py-2.5 text-xs bg-slate-50/60 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white focus:ring-2 focus:ring-[#0d9488]/10 text-slate-800 transition-all resize-none"
            />
          </div>
        </div>

        {/* Action Buttons Footer */}
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
            <span>Lưu & Khởi tạo tour</span>
          </button>
        </div>
      </form>
    </div>
  );
}

export default CreateTourPage;
