// UC-78 xem nhat ky he thong
import { useCallback, useEffect, useState } from 'react';
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  ScrollText,
  Search,
  X,
} from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { auditLogApi } from '../../apis/auditLogApi';
import {
  AUDIT_ACTION_LABELS,
  AUDIT_ACTION_TONES,
  AUDIT_OUTCOME_LABELS,
  auditEntityLabel,
  formatAuditTime,
} from '../../constants/auditLogData';
import { ROLE_LABELS, PAGE_SIZE_OPTIONS } from '../../constants/accountData';

const INPUT_CLASS =
  'px-3 py-2 text-xs bg-slate-50/70 border border-slate-200 rounded-xl text-slate-800 focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#127e7a]';

const parseJson = (text) => {
  if (!text) return null;
  try {
    const value = JSON.parse(text);
    return value && typeof value === 'object' && !Array.isArray(value) ? value : { value };
  } catch {
    return { value: text };
  }
};

const showValue = (value) => {
  if (value === undefined || value === null || value === '') return '—';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
};

const actorText = (item) => {
  if (!item.actorAccountId) return { name: 'Hệ thống', sub: 'Tác vụ tự động' };
  if (!item.actorUsername) return { name: 'Tài khoản không xác định', sub: item.actorAccountId };
  const role = ROLE_LABELS[item.actorRole];
  return { name: item.actorFullName || item.actorUsername, sub: role ? `${item.actorUsername} • ${role}` : item.actorUsername };
};

function ActionBadge({ action }) {
  return (
    <span className={`inline-block whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-semibold ${AUDIT_ACTION_TONES[action] || 'bg-slate-50 text-slate-600 border-slate-200'}`}>
      {AUDIT_ACTION_LABELS[action] || action}
    </span>
  );
}

/** UC-78: one entry with its old and new values. */
function AuditLogDetailModal({ logId, onClose }) {
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    auditLogApi.getDetail(logId, controller.signal)
      .then(setDetail)
      .catch((err) => {
        if (err?.name === 'AbortError') return;
        setError(err?.status === 404
          ? 'Không tìm thấy dòng nhật ký này.'
          : 'Không thể tải chi tiết nhật ký. Vui lòng thử lại.');
      });
    return () => controller.abort();
  }, [logId]);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const oldValues = parseJson(detail?.oldValue);
  const newValues = parseJson(detail?.newValue);
  const changed = new Set(detail?.changedFields || []);
  const fields = [...new Set([...Object.keys(oldValues || {}), ...Object.keys(newValues || {})])];
  const actor = detail ? actorText(detail) : null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="audit-detail-title"
        className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h2 id="audit-detail-title" className="text-base font-bold text-slate-900">Chi tiết nhật ký</h2>
          <button type="button" onClick={onClose} aria-label="Đóng" className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 cursor-pointer">
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="space-y-5 overflow-y-auto px-5 py-4 text-xs text-slate-700">
          {error ? (
            <p role="alert" className="text-rose-600">{error}</p>
          ) : !detail ? (
            <p className="text-slate-400">Đang tải chi tiết...</p>
          ) : (
            <>
              <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
                <div><dt className="text-slate-400">Thời gian</dt><dd className="font-medium">{formatAuditTime(detail.occurredAt)}</dd></div>
                <div><dt className="text-slate-400">Người thực hiện</dt><dd className="font-medium">{actor.name}<span className="block text-[11px] font-normal text-slate-500">{actor.sub}</span></dd></div>
                <div><dt className="text-slate-400">Hành động</dt><dd><ActionBadge action={detail.action} /></dd></div>
                <div><dt className="text-slate-400">Kết quả</dt><dd className={detail.outcome === 'FAILURE' ? 'font-semibold text-rose-600' : 'font-medium'}>{AUDIT_OUTCOME_LABELS[detail.outcome] || detail.outcome}</dd></div>
                <div><dt className="text-slate-400">Đối tượng</dt><dd className="font-medium">{auditEntityLabel(detail.entityType)}<span className="block break-all font-mono text-[11px] font-normal text-slate-500">{detail.entityId || '—'}</span></dd></div>
                <div><dt className="text-slate-400">Số vào viện</dt><dd className="font-mono">{detail.sovaovien || '—'}</dd></div>
                <div><dt className="text-slate-400">Địa chỉ IP</dt><dd className="font-mono">{detail.ipAddress || '—'}</dd></div>
                <div><dt className="text-slate-400">Mã yêu cầu</dt><dd className="break-all font-mono">{detail.correlationId || '—'}</dd></div>
              </dl>

              <div>
                <h3 className="mb-2 text-sm font-bold text-slate-900">Dữ liệu ghi nhận</h3>
                {fields.length === 0 ? (
                  <p className="text-slate-400">Không có dữ liệu chi tiết.</p>
                ) : (
                  <div className="overflow-x-auto rounded-xl border border-slate-200">
                    <table className="w-full border-collapse text-left">
                      <thead>
                        <tr className="bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                          <th className="px-3 py-2">Trường</th>
                          <th className="px-3 py-2">Giá trị cũ</th>
                          <th className="px-3 py-2">Giá trị mới</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {fields.map((field) => (
                          <tr key={field} className={changed.has(field) ? 'bg-amber-50/60' : undefined}>
                            <td className="px-3 py-2 font-mono font-semibold">{field}</td>
                            <td className="break-all px-3 py-2 text-slate-500">{showValue(oldValues?.[field])}</td>
                            <td className="break-all px-3 py-2">{showValue(newValues?.[field])}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

/** UC-78 View Audit Log. */
export function AuditLogPage({ currentAdmin, onLogout, onChangePassword }) {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [entityTypes, setEntityTypes] = useState([]);

  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [action, setAction] = useState('');
  const [entityType, setEntityType] = useState('');
  const [outcome, setOutcome] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [selectedId, setSelectedId] = useState(null);

  const rangeInvalid = Boolean(fromDate && toDate && fromDate > toDate);

  const fetchLogs = useCallback(async (signal) => {
    if (rangeInvalid) return;
    setLoading(true);
    setError(null);
    try {
      const result = await auditLogApi.search({
        keyword: searchKeyword,
        action,
        entityType,
        outcome,
        from: fromDate,
        to: toDate,
        page,
        size: pageSize,
      }, signal);
      setLogs(result?.content || []);
      setTotalPages(result?.page?.totalPages || 0);
      setTotalElements(result?.page?.totalElements || 0);
    } catch (err) {
      if (err?.name === 'AbortError') return;
      setError('Không thể tải nhật ký hệ thống. Vui lòng thử lại.');
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [rangeInvalid, searchKeyword, action, entityType, outcome, fromDate, toDate, page, pageSize]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => fetchLogs(controller.signal), 0);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [fetchLogs]);

  useEffect(() => {
    const controller = new AbortController();
    auditLogApi.listEntityTypes(controller.signal)
      .then((types) => setEntityTypes(types || []))
      .catch(() => {});
    return () => controller.abort();
  }, []);

  const changeFilter = (setter) => (e) => {
    setter(e.target.value);
    setPage(0);
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    setSearchKeyword(keyword);
  };

  const handleResetFilters = () => {
    setKeyword('');
    setSearchKeyword('');
    setAction('');
    setEntityType('');
    setOutcome('');
    setFromDate('');
    setToDate('');
    setPage(0);
  };

  const hasFilter = searchKeyword || action || entityType || outcome || fromDate || toDate;
  const closeDetail = useCallback(() => setSelectedId(null), []);

  return (
    <AdminLayout
      currentAdmin={currentAdmin}
      onLogout={onLogout}
      onChangePassword={onChangePassword}
      activeKey="audit"
    >
      <div className="space-y-6 pb-12">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-5">
          <div className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#127e7a]/10 text-[#127e7a]">
              <ScrollText className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight text-slate-900">Nhật ký hệ thống</h1>
              <p className="text-xs text-slate-500">Ai đã làm gì, lúc nào, trên dữ liệu nào. Nhật ký chỉ xem, không sửa hay xóa được.</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => fetchLogs()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 cursor-pointer"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Làm mới</span>
          </button>
        </div>

        <div className="space-y-3 rounded-2xl border border-slate-200/90 bg-white p-4 shadow-xs">
          <form onSubmit={handleSearchSubmit} noValidate className="flex flex-wrap items-end gap-3">
            <div className="relative min-w-[240px] flex-1">
              <label htmlFor="audit-keyword" className="sr-only">Từ khóa</label>
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <input
                id="audit-keyword"
                type="text"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm theo tên đăng nhập, họ tên người thực hiện, số vào viện hoặc mã..."
                className={`w-full pl-9 ${INPUT_CLASS}`}
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="audit-action" className="text-[11px] text-slate-500">Hành động</label>
              <select id="audit-action" value={action} onChange={changeFilter(setAction)} className={INPUT_CLASS}>
                <option value="">Tất cả</option>
                {Object.entries(AUDIT_ACTION_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>{label}</option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="audit-entity" className="text-[11px] text-slate-500">Đối tượng</label>
              <select id="audit-entity" value={entityType} onChange={changeFilter(setEntityType)} className={INPUT_CLASS}>
                <option value="">Tất cả</option>
                {entityTypes.map((type) => (
                  <option key={type} value={type}>{auditEntityLabel(type)}</option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="audit-outcome" className="text-[11px] text-slate-500">Kết quả</label>
              <select id="audit-outcome" value={outcome} onChange={changeFilter(setOutcome)} className={INPUT_CLASS}>
                <option value="">Tất cả</option>
                {Object.entries(AUDIT_OUTCOME_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>{label}</option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="audit-from" className="text-[11px] text-slate-500">Từ ngày</label>
              <input id="audit-from" type="date" value={fromDate} onChange={changeFilter(setFromDate)} className={INPUT_CLASS} />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="audit-to" className="text-[11px] text-slate-500">Đến ngày</label>
              <input id="audit-to" type="date" value={toDate} onChange={changeFilter(setToDate)} className={INPUT_CLASS} />
            </div>

            <button type="submit" className="rounded-xl bg-[#127e7a] px-4 py-2 text-xs font-bold text-white hover:bg-[#0e6360] cursor-pointer">
              Tìm kiếm
            </button>
            {hasFilter && (
              <button type="button" onClick={handleResetFilters} className="rounded-xl px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 cursor-pointer">
                Xóa lọc
              </button>
            )}
          </form>
          {rangeInvalid && (
            <p role="alert" className="text-xs text-rose-600">Từ ngày không được sau đến ngày.</p>
          )}
        </div>

        <div className="overflow-hidden rounded-2xl border border-slate-200/90 bg-white shadow-xs">
          {loading ? (
            <div className="flex flex-col items-center justify-center space-y-2 py-20 text-slate-400">
              <RefreshCw className="h-8 w-8 animate-spin text-[#127e7a]" />
              <span className="text-xs">Đang tải nhật ký...</span>
            </div>
          ) : error ? (
            <div className="flex flex-col items-center justify-center space-y-2 py-16 text-rose-500">
              <AlertCircle className="h-8 w-8" />
              <span className="text-sm font-semibold">{error}</span>
              <button type="button" onClick={() => fetchLogs()} className="mt-2 rounded-lg bg-[#127e7a] px-3 py-1.5 text-xs font-semibold text-white cursor-pointer">
                Thử lại
              </button>
            </div>
          ) : logs.length === 0 ? (
            <div className="flex flex-col items-center justify-center space-y-2 py-20 text-slate-400">
              <ScrollText className="h-10 w-10 stroke-1" />
              <span className="text-sm font-medium">Không có nhật ký nào khớp với điều kiện tìm kiếm.</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50/80 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                    <th className="px-4 py-3">Thời gian</th>
                    <th className="px-4 py-3">Người thực hiện</th>
                    <th className="px-4 py-3">Hành động</th>
                    <th className="px-4 py-3">Đối tượng</th>
                    <th className="px-4 py-3">Số vào viện</th>
                    <th className="px-4 py-3">Kết quả</th>
                    <th className="px-4 py-3 text-center">Chi tiết</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-slate-700">
                  {logs.map((item) => {
                    const actor = actorText(item);
                    return (
                      <tr key={item.id} className="hover:bg-slate-50/70">
                        <td className="whitespace-nowrap px-4 py-3 font-medium">{formatAuditTime(item.occurredAt)}</td>
                        <td className="px-4 py-3">
                          <div className="font-semibold text-slate-900">{actor.name}</div>
                          <div className="text-[11px] text-slate-500">{actor.sub}</div>
                        </td>
                        <td className="px-4 py-3"><ActionBadge action={item.action} /></td>
                        <td className="px-4 py-3">
                          <div>{auditEntityLabel(item.entityType)}</div>
                          {item.changedFields?.length > 0 && (
                            <div className="text-[11px] text-slate-500">Thay đổi: {item.changedFields.join(', ')}</div>
                          )}
                        </td>
                        <td className="px-4 py-3 font-mono">{item.sovaovien || '—'}</td>
                        <td className={`px-4 py-3 ${item.outcome === 'FAILURE' ? 'font-semibold text-rose-600' : ''}`}>
                          {AUDIT_OUTCOME_LABELS[item.outcome] || item.outcome}
                        </td>
                        <td className="px-4 py-3 text-center">
                          <button
                            type="button"
                            onClick={() => setSelectedId(item.id)}
                            className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-semibold text-[#127e7a] hover:bg-[#127e7a]/5 cursor-pointer"
                          >
                            Xem
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {!loading && !error && logs.length > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-4 border-t border-slate-200 bg-slate-50/50 px-4 py-3 text-xs text-slate-600">
              <div className="flex items-center gap-2">
                <span>Tổng <strong>{totalElements}</strong> dòng nhật ký</span>
                <label htmlFor="audit-page-size" className="text-slate-400">• Số dòng/trang</label>
                <select
                  id="audit-page-size"
                  value={pageSize}
                  onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}
                  className="rounded-lg border border-slate-200 bg-white px-2 py-1"
                >
                  {PAGE_SIZE_OPTIONS.map((size) => <option key={size} value={size}>{size}</option>)}
                </select>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  aria-label="Trang trước"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="rounded-lg border border-slate-200 bg-white p-1.5 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 cursor-pointer"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <span>Trang <strong>{page + 1}</strong> / <strong>{Math.max(totalPages, 1)}</strong></span>
                <button
                  type="button"
                  aria-label="Trang sau"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page >= totalPages - 1}
                  className="rounded-lg border border-slate-200 bg-white p-1.5 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 cursor-pointer"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          )}
        </div>

        {selectedId && <AuditLogDetailModal logId={selectedId} onClose={closeDetail} />}
      </div>
    </AdminLayout>
  );
}

export default AuditLogPage;
