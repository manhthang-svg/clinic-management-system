import { X, Info } from 'lucide-react';
import { ROLE_MAP } from '../../constants/accountData';

const INPUT_CLASS =
  'w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-[#127e7a] focus:bg-white text-xs disabled:text-slate-400 disabled:cursor-not-allowed';

/** UC-7 Create Account and UC-8 Update Account Information. */
export function AccountFormModal({ isOpen, mode = 'create', onClose, onSubmit, form, setForm }) {
  if (!isOpen) return null;

  const editing = mode === 'edit';
  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  return (
    <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4 z-50">
      <div role="dialog" aria-modal="true" aria-labelledby="account-form-title" className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-lg overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <div>
            <h3 id="account-form-title" className="text-base font-bold text-slate-900">
              {editing ? 'Cập nhật thông tin tài khoản' : 'Tạo tài khoản mới'}
            </h3>
            <p className="text-xs text-slate-500">
              {editing ? 'Chỉnh sửa thông tin cá nhân và vai trò của nhân viên.' : 'Khởi tạo tài khoản đăng nhập cho nhân viên.'}
            </p>
          </div>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={onSubmit} noValidate className="p-6 space-y-4 text-xs">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label htmlFor="account-username" className="font-semibold text-slate-700">Tên đăng nhập *</label>
              <input
                id="account-username"
                type="text"
                required
                disabled={editing}
                placeholder="vd: bs.nguyenvana"
                value={form.username}
                onChange={update('username')}
                className={INPUT_CLASS}
              />
            </div>
            <div className="space-y-1">
              <label htmlFor="account-fullname" className="font-semibold text-slate-700">Họ và tên *</label>
              <input
                id="account-fullname"
                type="text"
                required
                placeholder="vd: Nguyễn Văn A"
                value={form.fullName}
                onChange={update('fullName')}
                className={INPUT_CLASS}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label htmlFor="account-role" className="font-semibold text-slate-700">Vai trò *</label>
              <select id="account-role" value={form.role} onChange={update('role')} className={`${INPUT_CLASS} cursor-pointer`}>
                {Object.entries(ROLE_MAP).map(([value, role]) => (
                  <option key={value} value={value}>{role.label}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <label htmlFor="account-title" className="font-semibold text-slate-700">Học hàm / học vị</label>
              <input
                id="account-title"
                type="text"
                placeholder="vd: ThS.BS"
                value={form.title}
                onChange={update('title')}
                className={INPUT_CLASS}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label htmlFor="account-email" className="font-semibold text-slate-700">Email</label>
              <input
                id="account-email"
                type="email"
                placeholder="ten@clinic.example"
                value={form.email}
                onChange={update('email')}
                className={INPUT_CLASS}
              />
            </div>
            <div className="space-y-1">
              <label htmlFor="account-phone" className="font-semibold text-slate-700">Số điện thoại</label>
              <input
                id="account-phone"
                type="tel"
                placeholder="09xxxxxxxx"
                value={form.phone}
                onChange={update('phone')}
                className={INPUT_CLASS}
              />
            </div>
          </div>

          {!editing && (
            <p className="flex items-start gap-2 p-3 rounded-xl bg-teal-50 text-teal-800 border border-teal-100">
              <Info className="w-4 h-4 shrink-0 mt-px" />
              <span>Hệ thống sẽ tạo mật khẩu tạm thời. Nhân viên phải đổi mật khẩu ở lần đăng nhập đầu tiên.</span>
            </p>
          )}

          <div className="pt-4 flex items-center justify-end gap-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 font-medium text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={!form.username.trim() || !form.fullName.trim()}
              className="px-4 py-2 font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {editing ? 'Lưu thay đổi' : 'Tạo tài khoản'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default AccountFormModal;
