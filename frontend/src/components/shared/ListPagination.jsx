import { ChevronLeft, ChevronRight } from 'lucide-react';

const pageButton =
  'flex h-8 min-w-8 cursor-pointer items-center justify-center rounded-full px-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40';

/** Thanh phan trang duoi danh sach (dung voi usePagedRows). An khi chi co 1 trang. */
export function ListPagination({ paged, label = 'Phân trang' }) {
  const { page, pageCount, pageSize, total, setPage } = paged;
  if (pageCount <= 1) return null;
  const from = (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, total);
  return (
    <nav aria-label={label} className="print:hidden flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 px-4 py-3 text-xs text-slate-500">
      <span>Hiển thị {from} – {to} / {total}</span>
      <div className="flex flex-wrap items-center gap-1">
        <button type="button" aria-label="Trang trước" disabled={page === 1} onClick={() => setPage(page - 1)} className={`${pageButton} text-slate-600 hover:bg-slate-100`}>
          <ChevronLeft aria-hidden="true" className="h-4 w-4" />
        </button>
        {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) => (
          <button
            key={number}
            type="button"
            aria-label={`Trang ${number}`}
            aria-current={number === page ? 'page' : undefined}
            onClick={() => setPage(number)}
            className={`${pageButton} ${number === page ? 'border border-teal-600 text-teal-700' : 'text-slate-600 hover:bg-slate-100'}`}
          >
            {number}
          </button>
        ))}
        <button type="button" aria-label="Trang sau" disabled={page === pageCount} onClick={() => setPage(page + 1)} className={`${pageButton} text-slate-600 hover:bg-slate-100`}>
          <ChevronRight aria-hidden="true" className="h-4 w-4" />
        </button>
      </div>
    </nav>
  );
}

export default ListPagination;
