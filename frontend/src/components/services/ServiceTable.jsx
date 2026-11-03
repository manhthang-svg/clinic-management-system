import { Power, PowerOff, ChevronsLeft, ChevronsRight } from 'lucide-react';
import { SERVICE_TYPE_MAP, SERVICE_STATUS_LABELS } from '../../constants/serviceData';
import { PAGE_SIZE_OPTIONS } from '../../constants/accountData';
import { formatPrice } from './serviceFormat';

const STATUS_TONES = {
  ACTIVE: 'bg-[#e3f6ec] text-[#1f9d68] border-[#c6ebd8]',
  INACTIVE: 'bg-rose-50 text-rose-600 border-rose-200',
};

const ACTION_CLASS = 'p-1.5 rounded-lg transition-colors cursor-pointer';
const PAGE_BUTTON_CLASS =
  'w-8 h-8 rounded-lg border border-slate-200 flex items-center justify-center text-slate-500 hover:bg-slate-50 disabled:text-slate-300 disabled:cursor-not-allowed cursor-pointer';

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

export function ServiceTable({
  services,
  loading = false,
  error,
  filtered = false,
  totalCount,
  page,
  pageSize,
  onPageChange,
  onPageSizeChange,
  onToggleStatus,
  onView,
}) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const from = totalCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, totalCount);
  const columnCount = onToggleStatus ? 8 : 7;

  let message = null;
  if (error) message = <span role="alert" className="text-rose-600">{error}</span>;
  else if (loading && services.length === 0) message = <span role="status">Đang tải danh sách dịch vụ…</span>;
  else if (services.length === 0) message = filtered ? 'Không tìm thấy dịch vụ phù hợp.' : 'Chưa có dịch vụ nào.';

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className={`w-full text-left border-collapse transition-opacity ${loading && !message ? 'opacity-60' : ''}`}>
          <thead>
            <tr className="bg-[#f6f8f9] text-[11px] font-semibold text-[#1b2533]">
              <th className="pl-3 pr-2 py-3">Mã DV</th>
              <th className="px-2 py-3">Tên dịch vụ</th>
              <th className="px-2 py-3">Loại</th>
              <th className="px-2 py-3">Nhóm dịch vụ</th>              <th className="px-2 py-3 text-right">Giá viện phí</th>
              <th className="px-2 py-3 text-right">Giá BHYT</th>
              <th className="px-2 py-3">Trạng thái</th>
              {onToggleStatus && <th className="px-2 py-3">Thao tác</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-[11px]">
            {message ? (
              <tr>
                <td colSpan={columnCount} className="p-10 text-center text-slate-400">{message}</td>
              </tr>
            ) : (
              services.map((service) => {
                const type = SERVICE_TYPE_MAP[service.type];
                const active = service.status === 'ACTIVE';
                const ToggleIcon = active ? PowerOff : Power;
                return (
                  <tr
                    key={service.id}
                    tabIndex={onView ? 0 : undefined}
                    aria-label={onView ? `Xem chi tiết ${service.name}` : undefined}
                    onClick={onView ? () => onView(service) : undefined}
                    onKeyDown={onView ? (event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onView(service);
                      }
                    } : undefined}
                    className={`transition-colors hover:bg-slate-50/70 ${
                      onView ? 'cursor-pointer focus:bg-slate-50/70 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-[#127e7a]/20' : ''
                    }`}
                  >
                    <td className="pl-3 pr-2 py-3 font-mono text-[10px] text-slate-500 whitespace-nowrap">{service.code}</td>
                    <td className="px-2 py-3 font-semibold text-slate-800 min-w-[160px]">{service.name}</td>
                    <td className="px-2 py-3">
                      <span className={`inline-block px-2 py-0.5 rounded-md text-[10px] font-semibold border whitespace-nowrap ${type?.color}`}>
                        {type?.label ?? service.type}
                      </span>
                    </td>
                    <td className="px-2 py-3 min-w-[120px] font-medium text-slate-700">
                      {service.serviceGroupName ?? type?.label}
                    </td>                    <td className="px-2 py-3 text-right font-semibold text-slate-700 whitespace-nowrap">
                      {formatPrice(service.clinicPrice)}
                    </td>
                    <td className="px-2 py-3 text-right whitespace-nowrap">
                      {service.insurancePrice === null || service.insurancePrice === undefined ? (
                        <span className="text-slate-400">Không áp dụng</span>
                      ) : (
                        <span className="font-semibold text-slate-700">{formatPrice(service.insurancePrice)}</span>
                      )}
                    </td>
                    <td className="px-2 py-3">
                      <span className={`inline-block px-2 py-0.5 rounded-md text-[10px] font-semibold border whitespace-nowrap ${STATUS_TONES[service.status]}`}>
                        {SERVICE_STATUS_LABELS[service.status]}
                      </span>
                    </td>
                    {onToggleStatus && (
                      <td className="px-2 py-3">
                        <button
                          type="button"
                          title={active ? 'Ngừng hoạt động' : 'Kích hoạt'}
                          aria-label={`${active ? 'Ngừng hoạt động' : 'Kích hoạt'} ${service.name}`}
                          onClick={(event) => {
                            event.stopPropagation();
                            onToggleStatus(service);
                          }}
                          className={`${ACTION_CLASS} ${
                            active ? 'text-slate-500 hover:bg-rose-50 hover:text-rose-600' : 'text-slate-500 hover:bg-teal-50 hover:text-[#127e7a]'
                          }`}
                        >
                          <ToggleIcon className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <div className="pt-5 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
        <div className="flex items-center gap-3">
          <span>
            Hiển thị {from}–{to} trên tổng số {totalCount} dịch vụ · Giá tính theo VNĐ
          </span>
          <select
            aria-label="Số dòng mỗi trang"
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
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
          {pageItems(page, totalPages).map((item) =>
            typeof item === 'string' ? (
              <span key={item} className="w-8 text-center text-slate-400">…</span>
            ) : (
              <button
                key={item}
                type="button"
                aria-current={item === page ? 'page' : undefined}
                onClick={() => onPageChange(item)}
                className={`w-8 h-8 rounded-lg text-xs font-semibold flex items-center justify-center cursor-pointer ${
                  item === page ? 'bg-[#e3f2f0] text-[#127e7a] border border-[#bfe0dc]' : 'text-slate-600 hover:bg-slate-100'
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
            className={PAGE_BUTTON_CLASS}
          >
            <ChevronsRight className="w-3.5 h-3.5" />
          </button>
        </nav>
      </div>
    </div>
  );
}

export default ServiceTable;
