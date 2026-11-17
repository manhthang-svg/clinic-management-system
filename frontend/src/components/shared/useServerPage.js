import { useEffect, useMemo, useState } from 'react';
import { LIST_PAGE_SIZE } from './usePagedRows';

// lay mang dong + tong so dong tu PageResponse cua backend ({ content, page: { totalElements, totalPages } })
function pageOf(result) {
  const data = result?.data ?? result;
  return {
    rows: Array.isArray(data?.content) ? data.content : [],
    total: data?.page?.totalElements ?? data?.totalElements ?? 0,
    totalPages: data?.page?.totalPages ?? data?.totalPages ?? 0,
  };
}

/**
 * Bang loc + phan trang o server: chi gui so trang va bo loc, nhan ve dung 20 dong dang xem va tong so dong.
 * filters phai giu nguyen tham chieu khi khong doi (useMemo); bo loc doi thi ve trang 1.
 * Tra ve dang dung duoc cho ListPagination.
 */
export function useServerPage(fetcher, filters, { enabled = true, toMessage = () => 'Không tải được dữ liệu.' } = {}) {
  const [page, setPage] = useState(1);
  const [lastFilters, setLastFilters] = useState(filters);
  if (lastFilters !== filters) {
    setLastFilters(filters);
    setPage(1);
  }

  const params = useMemo(() => ({ ...filters, page: page - 1, size: LIST_PAGE_SIZE }), [filters, page]);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [settled, setSettled] = useState(null);
  const loading = enabled && (settled?.params !== params || settled?.reloadKey !== reloadKey);

  useEffect(() => {
    if (!enabled) return undefined;
    const controller = new AbortController();
    (async () => {
      try {
        const data = await fetcher(params, controller.signal);
        setResult(pageOf(data));
        setError('');
      } catch (err) {
        if (err?.name === 'AbortError' || controller.signal.aborted) return;
        setError(toMessage(err));
      }
      if (!controller.signal.aborted) setSettled({ params, reloadKey });
    })();
    return () => controller.abort();
  }, [fetcher, params, enabled, reloadKey, toMessage]);

  const total = result?.total ?? 0;
  const pageCount = Math.max(1, result?.totalPages ?? Math.ceil(total / LIST_PAGE_SIZE));
  // danh sach ngan lai (vd lam moi) ma dang o trang qua cuoi -> lui ve trang cuoi
  if (result && !loading && page > pageCount) setPage(pageCount);
  return {
    rows: result?.rows ?? [],
    loaded: result !== null,
    total,
    page,
    setPage,
    pageCount,
    pageSize: LIST_PAGE_SIZE,
    loading,
    error,
    reload: () => setReloadKey((key) => key + 1),
  };
}

export default useServerPage;
