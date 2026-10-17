import { MoreVertical, ChevronLeft, ChevronRight } from 'lucide-react';

export function AccountTable({
  accounts,
  selectedIds = [],
  onToggleSelectAll,
  onToggleSelectOne,
  activeMenuId,
  setActiveMenuId,
  onToggleLock,
  totalCount = 128,
}) {
  const isAllSelected = accounts.length > 0 && selectedIds.length === accounts.length;

  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-slate-50/80 text-[11px] font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200/80">
              <th className="p-4 w-10 text-center">
                <input
                  type="checkbox"
                  checked={isAllSelected}
                  onChange={onToggleSelectAll}
                  className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-teal-500/20 cursor-pointer accent-[#0d9488]"
                />
              </th>
              <th className="p-4">MÃ NV / TÊN ĐĂNG NHẬP</th>
              <th className="p-4">NHÂN VIÊN</th>
              <th className="p-4">THÔNG TIN LIÊN HỆ</th>
              <th className="p-4">VAI TRÒ</th>
              <th className="p-4">KHOA / PHÒNG</th>
              <th className="p-4">TRẠNG THÁI</th>
              <th className="p-4">NGÀY TẠO</th>
              <th className="p-4 text-center">THAO TÁC</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-xs">
            {accounts.length === 0 ? (
              <tr>
                <td colSpan="9" className="p-8 text-center text-slate-400 text-xs">
                  Không tìm thấy tài khoản nào phù hợp với điều kiện lọc.
                </td>
              </tr>
            ) : (
              accounts.map((account) => {
                const isSelected = selectedIds.includes(account.id);
                return (
                  <tr
                    key={account.id}
                    className={`hover:bg-slate-50/70 transition-colors ${
                      isSelected ? 'bg-teal-50/30' : ''
                    }`}
                  >
                    {/* Checkbox */}
                    <td className="p-4 text-center">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => onToggleSelectOne(account.id)}
                        className="w-4 h-4 rounded text-[#0d9488] border-slate-300 focus:ring-teal-500/20 cursor-pointer accent-[#0d9488]"
                      />
                    </td>

                    {/* Code & Username */}
                    <td className="p-4">
                      <div className="font-bold text-slate-800 tracking-tight">
                        {account.code}
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        {account.username}
                      </div>
                    </td>

                    {/* Full Name & Avatar */}
                    <td className="p-4">
                      <div className="flex items-center gap-3">
                        {account.avatar ? (
                          <img
                            src={account.avatar}
                            alt={account.fullName}
                            className="w-9 h-9 rounded-full object-cover border border-slate-200 shrink-0"
                          />
                        ) : (
                          <div className="w-9 h-9 rounded-full bg-slate-100 text-slate-600 font-bold text-xs flex items-center justify-center border border-slate-200 shrink-0">
                            {account.initials}
                          </div>
                        )}
                        <div>
                          <div className="font-bold text-slate-800 leading-tight">
                            {account.title && (
                              <span className="text-[11px] text-[#0d9488] mr-1 font-semibold">
                                {account.title}
                              </span>
                            )}
                            {account.fullName}
                          </div>
                          <div className="text-[10px] text-slate-400 mt-0.5">
                            {account.staffId} • {account.position}
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Contact Info */}
                    <td className="p-4">
                      <div className="text-slate-700 font-medium">{account.email}</div>
                      <div className="text-[11px] text-slate-400 mt-0.5 font-mono">
                        {account.phone}
                      </div>
                    </td>

                    {/* Role Pill */}
                    <td className="p-4">
                      <span
                        className={`inline-block px-2.5 py-0.5 rounded-md text-[11px] font-semibold border ${account.roleColor}`}
                      >
                        {account.roleLabel}
                      </span>
                    </td>

                    {/* Department */}
                    <td className="p-4">
                      <span className="text-slate-700 font-medium">
                        {account.department}
                      </span>
                    </td>

                    {/* Status */}
                    <td className="p-4">
                      {account.status === 'ACTIVE' ? (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                          <span>Hoạt động</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                          <span>Bị khóa</span>
                        </span>
                      )}
                    </td>

                    {/* Created Date */}
                    <td className="p-4 text-slate-500 text-[11px] font-medium">
                      {account.createdAt}
                    </td>

                    {/* Actions Menu */}
                    <td className="p-4 text-center relative">
                      <button
                        type="button"
                        onClick={() =>
                          setActiveMenuId(activeMenuId === account.id ? null : account.id)
                        }
                        className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                      >
                        <MoreVertical className="w-4 h-4" />
                      </button>

                      {/* Dropdown Action Popover */}
                      {activeMenuId === account.id && (
                        <div className="absolute right-6 top-10 w-44 bg-white rounded-xl shadow-lg border border-slate-200 py-1.5 z-20 text-left text-xs">
                          <button
                            type="button"
                            onClick={() => {
                              alert(`Xem chi tiết tài khoản ${account.fullName}`);
                              setActiveMenuId(null);
                            }}
                            className="w-full px-3.5 py-1.5 text-slate-700 hover:bg-slate-50 flex items-center gap-2 cursor-pointer"
                          >
                            <span>Xem chi tiết</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              alert(`Cấp lại mật khẩu cho ${account.fullName}`);
                              setActiveMenuId(null);
                            }}
                            className="w-full px-3.5 py-1.5 text-slate-700 hover:bg-slate-50 flex items-center gap-2 cursor-pointer"
                          >
                            <span>Đặt lại mật khẩu</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => onToggleLock(account.id)}
                            className="w-full px-3.5 py-1.5 text-rose-600 hover:bg-rose-50 flex items-center gap-2 cursor-pointer font-medium border-t border-slate-100"
                          >
                            <span>
                              {account.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
                            </span>
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Table Pagination Footer */}
      <div className="p-4 border-t border-slate-200/80 bg-white flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
        <div>
          Hiển thị <span className="font-semibold text-slate-800">1-{accounts.length}</span> trong
          tổng số <span className="font-semibold text-slate-800">{totalCount}</span> tài khoản
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1.5">
            <span>Xem:</span>
            <select className="bg-slate-50 border border-slate-200 rounded-lg px-2 py-1 text-xs focus:outline-none cursor-pointer">
              <option>10 / trang</option>
              <option>20 / trang</option>
              <option>50 / trang</option>
            </select>
          </div>

          {/* Page numbers */}
          <div className="flex items-center gap-1">
            <button
              type="button"
              disabled
              className="p-1 rounded-lg border border-slate-200 text-slate-300 cursor-not-allowed"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>

            <button
              type="button"
              className="w-7 h-7 rounded-lg bg-[#0d9488] text-white font-bold text-xs flex items-center justify-center cursor-pointer shadow-xs"
            >
              1
            </button>

            <button
              type="button"
              className="w-7 h-7 rounded-lg hover:bg-slate-100 text-slate-600 text-xs flex items-center justify-center cursor-pointer"
            >
              2
            </button>

            <button
              type="button"
              className="w-7 h-7 rounded-lg hover:bg-slate-100 text-slate-600 text-xs flex items-center justify-center cursor-pointer"
            >
              3
            </button>

            <span className="text-slate-400 px-1">…</span>

            <button
              type="button"
              className="w-7 h-7 rounded-lg hover:bg-slate-100 text-slate-600 text-xs flex items-center justify-center cursor-pointer"
            >
              16
            </button>

            <button
              type="button"
              className="p-1 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 cursor-pointer"
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default AccountTable;
