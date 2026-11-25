import { Fragment, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { ChevronDown, ChevronRight, Search, X } from 'lucide-react';
import { AdminLayout } from '../../layouts';
import { ROUTES } from '../../routes/routes';
import { AccountPageHeader } from '../../components/accounts/AccountFormParts';
import { FormAlert, INPUT_CLASS } from '../../components/shared/FormFields';
import { LabValueTypeBadge } from '../../components/services/LabValueTypeBadge';
import { formatReferenceConditions, formatReferenceValue } from '../../components/services/serviceFormat';
import { clinicToday, isCurrentRange } from '../../components/services/labRangeRevision';
import { serviceErrorMessage } from '../../components/services/serviceErrors';
import { MSG01, MSG92 } from '../../components/services/serviceMessages';
import { createServiceApi } from '../../apis/serviceApi';
import { apiClient } from '../../services/session';

const { ADMIN } = ROUTES;
const defaultApi = createServiceApi(apiClient);

function formatDate(day) {
  if (!day) return '';
  const [year, month, date] = day.split('-');
  return `${date}/${month}/${year}`;
}

function effectivePeriod(range) {
  return range.effectiveTo
    ? `${formatDate(range.effectiveFrom)} – ${formatDate(range.effectiveTo)}`
    : `Từ ${formatDate(range.effectiveFrom)}`;
}

function RangeConditions({ range }) {
  return formatReferenceConditions(range) || <span className="text-slate-400">Mọi giới tính, độ tuổi</span>;
}

/** UC-93 step 5: the reference ranges of one parameter that are in force today (read-only). */
function RangeDetail({ parameter, today }) {
  const current = (parameter.referenceRanges ?? []).filter((range) => isCurrentRange(range, today));

  return (
    <div className="bg-slate-50/60 px-4 py-4 sm:px-6">
        {current.length === 0 ? (
          <p className="text-sm text-slate-400">Chỉ số này chưa có khoảng tham chiếu đang áp dụng.</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-100 bg-white">
            <table className="w-full min-w-[640px] text-left border-collapse">
              <thead>
                <tr className="bg-[#f6f8f9] text-xs font-semibold text-[#1b2533]">
                  <th scope="col" className="px-3 py-2.5">Áp dụng cho</th>
                  <th scope="col" className="px-3 py-2.5">Giá trị</th>
                  <th scope="col" className="px-3 py-2.5">Hiệu lực</th>
                  <th scope="col" className="px-3 py-2.5">Nguồn</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-sm">
                {current.map((range) => (
                  <tr key={range.id}>
                    <td className="px-3 py-2.5 text-slate-700"><RangeConditions range={range} /></td>
                    <td className="px-3 py-2.5 font-semibold text-slate-800 whitespace-nowrap">
                      {formatReferenceValue(range)}{parameter.unit ? ` ${parameter.unit}` : ''}
                    </td>
                    <td className="px-3 py-2.5 text-slate-600 whitespace-nowrap">{effectivePeriod(range)}</td>
                    <td className="px-3 py-2.5 text-slate-500">{range.source}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
    </div>
  );
}

/** UC-93 View Laboratory Indicators: the indicator catalog with the reference ranges in force today (read-only). */
export function LabParameterManagementPage({ currentAdmin, onLogout, onChangePassword, api = defaultApi }) {
  const navigate = useNavigate();
  const today = clinicToday();
  const [state, setState] = useState({ loading: true, parameters: [], error: null });
  const [query, setQuery] = useState('');
  const [expandedId, setExpandedId] = useState(null);

  const load = useCallback((signal) => api.listLabParameters(signal)
    .then((parameters) => setState({ loading: false, parameters: parameters ?? [], error: null }))
    .catch((error) => {
      if (error.name !== 'AbortError') {
        setState((previous) => ({ ...previous, loading: false, error: serviceErrorMessage(error) }));
      }
    }), [api]);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const keyword = query.trim().toLowerCase();
  const shown = keyword
    ? state.parameters.filter((parameter) =>
      parameter.code.toLowerCase().includes(keyword) || parameter.name.toLowerCase().includes(keyword))
    : state.parameters;

  const layoutProps = {
    currentAdmin,
    onLogout,
    onChangePassword,
    onOpenProfile: () => navigate(ADMIN.PROFILE),
    activeKey: 'lab-parameters',
  };

  let body;
  if (state.loading && state.parameters.length === 0) {
    body = <p role="status" className="p-10 text-center text-sm text-slate-400">Đang tải danh sách chỉ số…</p>;
  } else if (state.error && state.parameters.length === 0) {
    body = <div className="p-6"><FormAlert>{state.error}</FormAlert></div>;
  } else {
    body = (
      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className="w-full min-w-[900px] text-left border-collapse">
          <thead>
            <tr className="bg-[#f6f8f9] text-xs font-semibold text-[#1b2533]">
              <th scope="col" className="w-10 pl-4 py-3"><span className="sr-only">Mở rộng</span></th>
              <th scope="col" className="px-3 py-3">Mã chỉ số</th>
              <th scope="col" className="px-3 py-3">Tên chỉ số</th>
              <th scope="col" className="px-3 py-3">Kiểu kết quả</th>
              <th scope="col" className="px-3 py-3">Đơn vị</th>
              <th scope="col" className="px-3 py-3">Chỉ số tham chiếu đang áp dụng</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-sm">
            {shown.length === 0 ? (
              <tr>
                <td colSpan={6} className="p-8 text-center text-slate-400">
                  {/* UC-93 AF-01: MSG01 when the search matches nothing, MSG92 when the catalog is empty. */}
                  {keyword ? MSG01 : MSG92}
                </td>
              </tr>
            ) : shown.map((parameter) => {
              const expanded = expandedId === parameter.id;
              const current = (parameter.referenceRanges ?? []).filter((range) => isCurrentRange(range, today));
              const Chevron = expanded ? ChevronDown : ChevronRight;
              return (
                <Fragment key={parameter.id}>
                  <tr
                    onClick={() => setExpandedId(expanded ? null : parameter.id)}
                    className={`cursor-pointer transition-colors ${expanded ? 'bg-[#f0f9f8]' : 'hover:bg-slate-50/70'}`}
                  >
                    <td className="pl-4 py-3">
                      <button
                        type="button"
                        aria-expanded={expanded}
                        aria-label={`${expanded ? 'Thu gọn' : 'Xem'} khoảng tham chiếu của ${parameter.code}`}
                        onClick={(event) => {
                          event.stopPropagation();
                          setExpandedId(expanded ? null : parameter.id);
                        }}
                        className="rounded-md p-1 text-slate-500 hover:bg-white cursor-pointer"
                      >
                        <Chevron aria-hidden="true" className="h-4 w-4" />
                      </button>
                    </td>
                    <td className="px-3 py-3 font-mono text-xs text-slate-500 whitespace-nowrap">{parameter.code}</td>
                    <td className="px-3 py-3 font-semibold text-slate-800">
                      {parameter.name}
                      {!parameter.active && <span className="ml-2 text-xs font-medium text-rose-600">Ngừng hoạt động</span>}
                    </td>
                    <td className="px-3 py-3"><LabValueTypeBadge valueType={parameter.valueType} /></td>
                    <td className="px-3 py-3 text-slate-600">{parameter.unit || '—'}</td>
                    <td className="px-3 py-3 text-slate-700">
                      {current.length === 0 ? (
                        <span className="text-slate-400">Chưa cấu hình</span>
                      ) : (
                        <ul className="space-y-1">
                          {current.map((range) => {
                            const conditions = formatReferenceConditions(range);
                            const value = formatReferenceValue(range);
                            return <li key={range.id}>{conditions ? `${conditions}: ${value}` : value}</li>;
                          })}
                        </ul>
                      )}
                    </td>
                  </tr>
                  {expanded && (
                    <tr>
                      <td colSpan={6} className="p-0">
                        <RangeDetail parameter={parameter} today={today} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <AdminLayout {...layoutProps}>
      <div className="px-6 pb-8 space-y-6">
        <AccountPageHeader
          title="Chỉ số xét nghiệm"
          description="Danh mục chỉ số xét nghiệm và khoảng tham chiếu đang áp dụng theo giới tính, độ tuổi."
          crumbs={[{ label: 'Chỉ số xét nghiệm' }]}
        />

        <section className="bg-white rounded-2xl border border-slate-200/70 shadow-2xs p-4 space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <h2 className="text-base! leading-6! font-bold! text-[#1b2533]">
              Danh sách chỉ số
              {state.parameters.length > 0 && (
                <span className="ml-2 text-xs font-semibold text-slate-400">{state.parameters.length} chỉ số</span>
              )}
            </h2>
            <div className="relative w-full sm:w-96">
              <Search aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <label htmlFor="lab-parameter-search" className="sr-only">Tìm chỉ số xét nghiệm</label>
              <input
                id="lab-parameter-search"
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Tìm theo mã hoặc tên chỉ số"
                className={`${INPUT_CLASS} pl-10 ${query ? 'pr-10' : ''}`}
              />
              {query && (
                <button
                  type="button"
                  aria-label="Xóa nội dung tìm kiếm"
                  onClick={() => setQuery('')}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600 cursor-pointer"
                >
                  <X aria-hidden="true" className="h-4 w-4" />
                </button>
              )}
            </div>
          </div>
          {body}
        </section>
      </div>
    </AdminLayout>
  );
}

export default LabParameterManagementPage;
