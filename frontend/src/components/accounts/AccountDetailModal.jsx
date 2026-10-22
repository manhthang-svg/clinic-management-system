import { X } from 'lucide-react';
import { ROLE_MAP, STATUS_LABELS } from '../../constants/accountData';
import { avatarTone, initialsOf, formatDate, formatTime, formatPhone } from './accountFormat';

/** UC-6 View Account Detail. */
export function AccountDetailModal({ account, onClose }) {
  if (!account) return null;

  const rows = [
    ['Tên đăng nhập', account.username],
    ['Vai trò', ROLE_MAP[account.role]?.label || account.role],
    ['Học hàm / học vị', account.title || '—'],
    ['Email', account.email || '—'],
    ['Số điện thoại', account.phone ? formatPhone(account.phone) : '—'],
    ['Trạng thái', STATUS_LABELS[account.status]],
    ['Yêu cầu đổi mật khẩu', account.passwordChangeRequired ? 'Có' : 'Không'],
    ['Đăng nhập gần nhất', account.lastLoginAt ? `${formatTime(account.lastLoginAt)} ${formatDate(account.lastLoginAt)}` : 'Chưa đăng nhập'],
    ['Ngày tạo', formatDate(account.createdAt)],
  ];

  return (
    <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4 z-50">
      <div role="dialog" aria-modal="true" aria-labelledby="account-detail-title" className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-md overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <h3 id="account-detail-title" className="text-base font-bold text-slate-900">Chi tiết tài khoản</h3>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-6">
          <div className="flex items-center gap-3 mb-5">
            <div className={`w-12 h-12 rounded-full font-bold text-sm flex items-center justify-center ${avatarTone(account.role)}`}>
              {initialsOf(account.fullName)}
            </div>
            <div>
              <div className="font-bold text-slate-900">{account.fullName}</div>
              <div className="text-xs text-slate-500">{account.email}</div>
            </div>
          </div>

          <dl className="divide-y divide-slate-100 text-xs">
            {rows.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-4 py-2.5">
                <dt className="text-slate-500">{label}</dt>
                <dd className="font-medium text-slate-800 text-right">{value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </div>
  );
}

export default AccountDetailModal;
