import { Eye, Pencil, Power, ChevronsLeft, ChevronsRight, Building2, DoorOpen } from 'lucide-react';

const ACTION_CLASS =
  'w-7 h-7 rounded-lg border border-slate-200 bg-white flex items-center justify-center transition-colors cursor-pointer';

function pageItems(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const pages = new Set([1, total, current - 1, current, current + 1]);
  const sorted = [...pages].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b);
  const items = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) items.push(`gap-${p}`);
    items.push(p);
  });
  return items;
}

export function DepartmentTable({
  items,
  totalCount,
  page,
  pageSize,
  onPageChange,
  onPageSizeChange,
  onView,
  onEdit,
  onToggleStatus,
  onSelectDepartmentRooms,
}) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const from = totalCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, totalCount);

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-slate-200/80 bg-white">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-[#f6f8f9] text-[11px] font-bold text-[#1b2533] border-b border-slate-200/80">
              <th className="px-4 py-3.5">Mã khoa</th>
              <th className="px-3 py-3.5">Tên chuyên khoa</th>
              <th className="px-3 py-3.5">Phân loại</th>
              <th className="px-3 py-3.5">Phòng trực thuộc</th>
              <th className="px-3 py-3.5">Trạng thái</th>
              <th className="px-4 py-3.5 text-center">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-[11px]">
            {items.length === 0 ? (
              <tr>
                <td colSpan="6" className="p-10 text-center text-slate-400">
                  Không tìm thấy chuyên khoa nào phù hợp với điều kiện lọc.
                </td>
              </tr>
            ) : (
              items.map((dept) => {
                const isActive = dept.status === 'ACTIVE';
                const isClinical = dept.type === 'CLINICAL';

                return (
                  <tr
                    key={`dept-${dept.id}`}
                    className="hover:bg-[#f9fbfb] transition-colors"
                  >
                    {/* Mã khoa */}
                    <td className="px-4 py-3 font-mono font-bold text-slate-700 whitespace-nowrap">
                      {dept.code}
                    </td>

                    {/* Tên khoa */}
                    <td className="px-3 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                          <Building2 className="w-3.5 h-3.5" />
                        </div>
                        <div>
                          <div className="font-semibold text-slate-800 text-xs">
                            {dept.name}
                          </div>
                          {dept.description && (
                            <div className="text-[10px] text-slate-400 line-clamp-1 max-w-xs mt-0.5">
                              {dept.description}
                            </div>
                          )}
                        </div>
                      </div>
                    </td>

                    {/* Phân loại (Lâm sàng / Cận lâm sàng) */}
                    <td className="px-3 py-3 whitespace-nowrap">
                      {isClinical ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10.5px] font-semibold bg-blue-50 text-blue-700 border border-blue-200/70">
                          Lâm sàng
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10.5px] font-semibold bg-purple-50 text-purple-700 border border-purple-200/70">
                          Cận lâm sàng
                        </span>
                      )}
                    </td>

                    {/* Số lượng phòng trực thuộc (Link tương tác được) */}
                    <td className="px-3 py-3 whitespace-nowrap">
                      <button
                        type="button"
                        onClick={() => onSelectDepartmentRooms?.(dept)}
                        title={`Xem các phòng thuộc ${dept.name}`}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold bg-teal-50 text-[#0d9488] hover:bg-teal-100 hover:text-[#0b7267] border border-teal-200/70 transition-all cursor-pointer group shadow-2xs"
                      >
                        <DoorOpen className="w-3.5 h-3.5 text-[#0d9488] group-hover:scale-110 transition-transform" />
                        <span className="underline-offset-2 group-hover:underline">
                          {dept.roomCount || 0} phòng
                        </span>
                      </button>
                    </td>

                    {/* Trạng thái */}
                    <td className="px-3 py-3 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-md text-[11px] font-semibold border ${
                          isActive
                            ? 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]'
                            : 'bg-[#fde9eb] text-[#d93b4a] border-[#f9cfd4]'
                        }`}
                      >
                        {isActive ? 'Đang hoạt động' : 'Đang khóa'}
                      </span>
                    </td>

                    {/* Thao tác */}
                    <td className="px-4 py-3 text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        <button
                          type="button"
                          title="Xem chi tiết"
                          aria-label={`Xem chi tiết ${dept.name}`}
                          onClick={() => onView(dept)}
                          className={`${ACTION_CLASS} text-slate-500 hover:bg-slate-50 hover:text-slate-800`}
                        >
                          <Eye className="w-3.5 h-3.5" />
                        </button>

                        <button
                          type="button"
                          title="Chỉnh sửa"
                          aria-label={`Chỉnh sửa ${dept.name}`}
                          onClick={() => onEdit(dept)}
                          className={`${ACTION_CLASS} text-slate-500 hover:bg-slate-50 hover:text-slate-800`}
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </button>

                        <button
                          type="button"
                          title={isActive ? 'Khóa chuyên khoa' : 'Mở khóa chuyên khoa'}
                          aria-label={`Đổi trạng thái ${dept.name}`}
                          onClick={() => onToggleStatus(dept)}
                          className={`${ACTION_CLASS} ${
                            isActive
                              ? 'text-slate-500 hover:bg-rose-50 hover:text-rose-600'
                              : 'text-emerald-600 hover:bg-emerald-50'
                          }`}
                        >
                          <Power className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Phân trang */}
      <div className="pt-4 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
        <div className="flex items-center gap-3">
          <span>
            Hiển thị {from}–{to} trên tổng số {totalCount} chuyên khoa
          </span>
          <select
            aria-label="Số dòng mỗi trang"
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="bg-white border border-slate-200 rounded-lg px-2.5 py-1 text-xs focus:outline-none cursor-pointer"
          >
            {[5, 10, 15, 20].map((size) => (
              <option key={size} value={size}>
                {size} / trang
              </option>
            ))}
          </select>
        </div>

        <nav aria-label="Phân trang khoa" className="flex items-center gap-1.5">
          <button
            type="button"
            aria-label="Trang trước"
            disabled={page === 1}
            onClick={() => onPageChange(page - 1)}
            className="w-8 h-8 rounded-lg border border-slate-200 flex items-center justify-center text-slate-500 hover:bg-slate-50 disabled:text-slate-300 disabled:cursor-not-allowed cursor-pointer"
          >
            <ChevronsLeft className="w-3.5 h-3.5" />
          </button>
          {pageItems(page, totalPages).map((item) =>
            typeof item === 'string' ? (
              <span key={item} className="w-8 text-center text-slate-400">
                …
              </span>
            ) : (
              <button
                key={item}
                type="button"
                aria-current={item === page ? 'page' : undefined}
                onClick={() => onPageChange(item)}
                className={`w-8 h-8 rounded-lg text-xs font-semibold flex items-center justify-center cursor-pointer ${
                  item === page
                    ? 'bg-[#e3f2f0] text-[#127e7a] border border-[#bfe0dc]'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {item}
              </button>
            )
          )}
          <button
            type="button"
            aria-label="Trang sau"
            disabled={page === totalPages}
            onClick={() => onPageChange(page + 1)}
            className="w-8 h-8 rounded-lg border border-slate-200 flex items-center justify-center text-slate-500 hover:bg-slate-50 disabled:text-slate-300 disabled:cursor-not-allowed cursor-pointer"
          >
            <ChevronsRight className="w-3.5 h-3.5" />
          </button>
        </nav>
      </div>
    </div>
  );
}

export default DepartmentTable;
