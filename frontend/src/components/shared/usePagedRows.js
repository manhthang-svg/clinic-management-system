import { useState } from 'react';

// so dong moi trang cua cac danh sach
export const LIST_PAGE_SIZE = 20;

// chia trang tren may cho danh sach da tai du tu server: 20 dong / trang.
// danh sach ngan lai (vd lam moi) thi dung o trang cuoi; doi bo loc thi goi setPage(1).
export function usePagedRows(rows, pageSize = LIST_PAGE_SIZE) {
  const [page, setPage] = useState(1);
  const total = rows.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const current = Math.min(page, pageCount);
  const start = (current - 1) * pageSize;
  const end = start + pageSize;
  return {
    page: current,
    setPage,
    pageCount,
    pageSize,
    total,
    rows: rows.slice(start, end),
    // bang co ban in: ve du dong, dong ngoai trang dang xem chi an tren man hinh (khi in van hien).
    // printDisplay: kieu hien khi in (dong bang: print:table-row; dong flex: print:flex)
    rowClass: (index, printDisplay = 'print:table-row') => (
      index >= start && index < end ? '' : `hidden ${printDisplay}`),
  };
}
