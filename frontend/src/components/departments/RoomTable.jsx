import { Eye, Pencil, Power, ChevronsLeft, ChevronsRight, Building2, MapPin, UserCheck, DoorOpen } from 'lucide-react';

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

export function RoomTable({
  items,
  totalCount,
  page,
  pageSize,
  onPageChange,
  onPageSizeChange,
  onView,
  onEdit,
  onToggleStatus,
}) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const from = totalCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, totalCount);

  const getRoomTypeBadge = (type) => {
    switch (type) {
      case 'EXAMINATION':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200">
            Khám lâm sàng
          </span>
        );
      case 'PARACLINICAL':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold bg-purple-50 text-purple-700 border border-purple-200">
            Cận lâm sàng
          </span>
        );
      case 'ADMINISTRATIVE':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-50 text-slate-700 border border-slate-200">
            Hành chính / Thu ngân
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold bg-teal-50 text-[#0d9488] border border-teal-200">
            Phòng chức năng
          </span>
        );
    }
  };

  const getStatusBadge = (status) => {
    if (status === 'ACTIVE') {
      return (
        <span className="inline-flex items-center px-2.5 py-0.5 rounded-md text-[11px] font-semibold bg-[#e3f6ec] text-[#1f9d68] border border-[#c6ebd8]">
          Đang hoạt động
        </span>
      );
    }
    if (status === 'MAINTENANCE') {
      return (
        <span className="inline-flex items-center px-2.5 py-0.5 rounded-md text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
          Đang bảo trì
        </span>
      );
    }
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-md text-[11px] font-semibold bg-[#fde9eb] text-[#d93b4a] border border-[#f9cfd4]">
        Tạm ngưng
      </span>
    );
  };

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-slate-200/80 bg-white">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-[#f6f8f9] text-[11px] font-bold text-[#1b2533] border-b border-slate-200/80">
              <th className="px-4 py-3.5">Mã phòng</th>
              <th className="px-3 py-3.5">Tên phòng</th>
              <th className="px-3 py-3.5">Chuyên khoa</th>
              <th className="px-3 py-3.5">Loại phòng</th>
              <th className="px-3 py-3.5">Vị trí</th>
              <th className="px-3 py-3.5">Bác sĩ / KTV phụ trách</th>
              <th className="px-3 py-3.5">Trạng thái</th>
              <th className="px-4 py-3.5 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-[11px]">
            {items.length === 0 ? (
              <tr>
                <td colSpan="8" className="p-10 text-center text-slate-400">
                  Không tìm thấy phòng khám nào phù hợp với điều kiện lọc.
                </td>
              </tr>
            ) : (
              items.map((room) => {
                const isActive = room.status === 'ACTIVE';

                return (
                  <tr
                    key={`room-${room.id}`}
                    className="hover:bg-[#f9fbfb] transition-colors"
                  >
                    {/* Mã phòng */}
                    <td className="px-4 py-3 font-mono font-bold text-slate-700 whitespace-nowrap">
                      {room.code}
                    </td>

                    {/* Tên phòng */}
                    <td className="px-3 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-lg bg-teal-50 text-[#0d9488] flex items-center justify-center shrink-0">
                          <DoorOpen className="w-3.5 h-3.5" />
                        </div>
                        <span className="font-semibold text-slate-800 text-xs">
                          {room.name}
                        </span>
                      </div>
                    </td>

                    {/* Chuyên khoa */}
                    <td className="px-3 py-3 whitespace-nowrap">
                      <div className="flex items-center gap-1.5 text-slate-700 font-medium">
                        <Building2 className="w-3 h-3 text-slate-400" />
                        <span>{room.departmentName || '—'}</span>
                      </div>
                    </td>

                    {/* Loại phòng */}
                    <td className="px-3 py-3 whitespace-nowrap">
                      {getRoomTypeBadge(room.type)}
                    </td>

                    {/* Vị trí */}
                    <td className="px-3 py-3 text-slate-600 whitespace-nowrap">
                      <div className="flex items-center gap-1.5">
                        <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
                        <span className="line-clamp-1">{room.location || '—'}</span>
                      </div>
                    </td>

                    {/* Bác sĩ/KTV phụ trách */}
                    <td className="px-3 py-3 text-slate-700 font-medium whitespace-nowrap">
                      <div className="flex items-center gap-1.5">
                        <UserCheck className="w-3 h-3 text-slate-400 shrink-0" />
                        <span>{room.headPerson || 'Chưa phân công'}</span>
                      </div>
                    </td>

                    {/* Trạng thái */}
                    <td className="px-3 py-3 whitespace-nowrap">
                      {getStatusBadge(room.status)}
                    </td>

                    {/* Thao tác */}
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          title="Xem chi tiết"
                          aria-label={`Xem chi tiết ${room.name}`}
                          onClick={() => onView(room)}
                          className={`${ACTION_CLASS} text-slate-500 hover:bg-slate-50 hover:text-slate-800`}
                        >
                          <Eye className="w-3.5 h-3.5" />
                        </button>

                        <button
                          type="button"
                          title="Chỉnh sửa"
                          aria-label={`Chỉnh sửa ${room.name}`}
                          onClick={() => onEdit(room)}
                          className={`${ACTION_CLASS} text-slate-500 hover:bg-slate-50 hover:text-slate-800`}
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </button>

                        <button
                          type="button"
                          title={isActive ? 'Vô hiệu hóa / Tạm ngưng' : 'Kích hoạt lại'}
                          aria-label={`Đổi trạng thái ${room.name}`}
                          onClick={() => onToggleStatus(room)}
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
            Hiển thị {from}–{to} trên tổng số {totalCount} phòng chức năng
          </span>
          <select
            aria-label="Số dòng mỗi trang"
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="bg-white border border-slate-200 rounded-lg px-2.5 py-1 text-xs focus:outline-none cursor-pointer"
          >
            {[10, 15, 20, 50].map((size) => (
              <option key={size} value={size}>
                {size} / trang
              </option>
            ))}
          </select>
        </div>

        <nav aria-label="Phân trang phòng" className="flex items-center gap-1.5">
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

export default RoomTable;
