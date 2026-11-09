import { ChevronsLeft, ChevronsRight } from 'lucide-react';
import { MEDICINE_CONTROL_GROUP_LABELS, MEDICINE_STATUS_LABELS } from '../../constants/medicineData';
import { PAGE_SIZE_OPTIONS } from '../../constants/accountData';
import { MSG01, MSG92 } from '../services/serviceMessages';

const STATUS_TONES = {
  ACTIVE: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]',
  INACTIVE: 'bg-rose-50 text-rose-600 border-rose-200',
};
const PAGE_BUTTON_CLASS =
  'w-8 h-8 rounded-lg border border-slate-200 flex items-center justify-center text-slate-500 hover:bg-slate-50 disabled:text-slate-300 disabled:cursor-not-allowed cursor-pointer';
const COLUMNS = ['Mã thuốc', 'Tên thuốc', 'Hoạt chất', 'Hàm lượng', 'Đơn vị', 'Đường dùng', 'Nhóm kiểm soát', 'Trạng thái'];

/** UC-81 medicine list with paging; a row opens the medicine detail. */
export function MedicineTable({
  medicines,
  loading = false,
  error,
  filtered = false,
  totalCount,
  page,
  pageSize,
  onPageChange,
  onPageSizeChange,
  onView,
}) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const from = totalCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, totalCount);

  let message = null;
  if (error) message = <span role="alert" className="text-rose-600">{error}</span>;
  else if (loading && medicines.length === 0) message = <span role="status">Đang tải danh mục thuốc…</span>;
  // MSG01 when the search or filters match nothing, MSG92 when the catalog is empty.
  else if (medicines.length === 0) message = filtered ? MSG01 : MSG92;

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className={`w-full text-left border-collapse transition-opacity ${loading && !message ? 'opacity-60' : ''}`}>
          <thead>
            <tr className="bg-[#f6f8f9] text-[11px] font-semibold text-[#1b2533]">
              {COLUMNS.map((column) => <th key={column} className="px-2 py-3 first:pl-3">{column}</th>)}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-[11px]">
            {message ? (
              <tr>
                <td colSpan={COLUMNS.length} className="p-10 text-center text-slate-400">{message}</td>
              </tr>
            ) : (
              medicines.map((medicine) => (
                <tr
                  key={medicine.id}
                  tabIndex={0}
                  aria-label={`Xem chi tiết ${medicine.name}`}
                  onClick={() => onView(medicine)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      onView(medicine);
                    }
                  }}
                  className="cursor-pointer transition-colors hover:bg-slate-50/70 focus:bg-slate-50/70 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-[#127e7a]/20"
                >
                  <td className="pl-3 pr-2 py-3 font-mono text-[10px] text-slate-500 whitespace-nowrap">{medicine.code}</td>
                  <td className="px-2 py-3 font-semibold text-slate-800 min-w-[160px]">{medicine.name}</td>
                  <td className="px-2 py-3 text-slate-700 min-w-[140px]">{medicine.activeIngredient}</td>
                  <td className="px-2 py-3 text-slate-700 whitespace-nowrap">{medicine.strength}</td>
                  <td className="px-2 py-3 text-slate-700 whitespace-nowrap">{medicine.unit}</td>
                  <td className="px-2 py-3 text-slate-700 whitespace-nowrap">{medicine.route}</td>
                  <td className="px-2 py-3 text-slate-700 whitespace-nowrap">
                    {MEDICINE_CONTROL_GROUP_LABELS[medicine.controlGroup] ?? medicine.controlGroup}
                  </td>
                  <td className="px-2 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded-md text-[10px] font-semibold border whitespace-nowrap ${STATUS_TONES[medicine.status]}`}>
                      {MEDICINE_STATUS_LABELS[medicine.status]}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="pt-5 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
        <div className="flex items-center gap-3">
          <span>Hiển thị {from}–{to} trên tổng số {totalCount} thuốc</span>
          <select
            aria-label="Số dòng mỗi trang"
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value))}
            className="bg-white border border-slate-200 rounded-lg px-2 py-1 text-xs focus:outline-none cursor-pointer"
          >
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>{size} / trang</option>
            ))}
          </select>
        </div>
        <nav aria-label="Phân trang" className="flex items-center gap-1.5">
          <button
            type="button"
            aria-label="Trang trước"
            disabled={page === 1}
            onClick={() => onPageChange(page - 1)}
            className={PAGE_BUTTON_CLASS}
          >
            <ChevronsLeft className="w-3.5 h-3.5" />
          </button>
          <span className="px-2">Trang {page}/{totalPages}</span>
          <button
            type="button"
            aria-label="Trang sau"
            disabled={page === totalPages}
            onClick={() => onPageChange(page + 1)}
            className={PAGE_BUTTON_CLASS}
          >
            <ChevronsRight className="w-3.5 h-3.5" />
          </button>
        </nav>
      </div>
    </div>
  );
}

export default MedicineTable;
