import { X } from 'lucide-react';

export function AccountFormModal({
  isOpen,
  onClose,
  onSubmit,
  form,
  setForm,
}) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-in fade-in">
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-lg overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <div>
            <h3 className="text-base font-bold text-slate-900">Thêm tài khoản nhân viên mới</h3>
            <p className="text-xs text-slate-500">
              Khởi tạo thông tin định danh và phân quyền làm việc tại bệnh viện.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={onSubmit} className="p-6 space-y-4 text-xs">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="font-semibold text-slate-700">Tên đăng nhập *</label>
              <input
                type="text"
                required
                placeholder="vd: bs.minhanh"
                value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
              />
            </div>

            <div className="space-y-1">
              <label className="font-semibold text-slate-700">Họ và tên *</label>
              <input
                type="text"
                required
                placeholder="vd: Nguyễn Minh Anh"
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="font-semibold text-slate-700">Vai trò chức vụ</label>
              <select
                value={form.role}
                onChange={(e) => setForm({ ...form, role: e.target.value })}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs cursor-pointer"
              >
                <option value="DOCTOR">Bác sĩ</option>
                <option value="RECEPTIONIST">Lễ tân</option>
                <option value="CASHIER">Thu ngân</option>
                <option value="TECHNICIAN">Kỹ thuật viên</option>
                <option value="ADMIN">Quản trị viên</option>
              </select>
            </div>

            <div className="space-y-1">
              <label className="font-semibold text-slate-700">Khoa / Phòng ban</label>
              <select
                value={form.department}
                onChange={(e) => setForm({ ...form, department: e.target.value })}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs cursor-pointer"
              >
                <option value="Khoa Nội">Khoa Nội</option>
                <option value="Khoa Ngoại">Khoa Ngoại</option>
                <option value="Phòng Xét nghiệm">Phòng Xét nghiệm</option>
                <option value="Bộ phận Lễ tân">Bộ phận Lễ tân</option>
                <option value="Phòng Thu ngân">Phòng Thu ngân</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="font-semibold text-slate-700">Email làm việc</label>
              <input
                type="email"
                placeholder="email@metph.vn"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
              />
            </div>

            <div className="space-y-1">
              <label className="font-semibold text-slate-700">Số điện thoại</label>
              <input
                type="tel"
                placeholder="09xx xxx xxx"
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
              />
            </div>
          </div>

          <div className="space-y-1">
            <label className="font-semibold text-slate-700">Mật khẩu tạm thời</label>
            <input
              type="password"
              placeholder="Để trống hệ thống sẽ tự sinh mật khẩu mặc định..."
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#0d9488] focus:bg-white text-xs"
            />
          </div>

          <div className="pt-4 flex items-center justify-end gap-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 font-medium text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer"
            >
              Hủy bỏ
            </button>

            <button
              type="submit"
              className="px-4 py-2 font-semibold text-white bg-[#0d9488] hover:bg-[#0f766e] rounded-xl shadow-sm cursor-pointer"
            >
              Khởi tạo tài khoản
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default AccountFormModal;
