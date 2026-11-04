import { useMemo, useState } from 'react';
import { Plus, Search, Trash2, X } from 'lucide-react';
import { INPUT_CLASS, FieldError, FieldHint } from './ServiceFormParts';
import { LabValueTypeBadge } from './LabValueTypeBadge';
import { formatReferenceRangeLine } from './serviceFormat';

function optionLabel(parameter) {
  return `${parameter.code} — ${parameter.name}${parameter.unit ? ` (${parameter.unit})` : ''}`;
}

export function LabParameterSelector({ parameters = [], selectedIds = [], onChange, error }) {
  const [parameterId, setParameterId] = useState('');
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(0);
  const parameterById = useMemo(
    () => new Map(parameters.map((parameter) => [String(parameter.id), parameter])),
    [parameters],
  );
  const available = parameters.filter((parameter) => !selectedIds.includes(String(parameter.id)));
  const keyword = query.trim().toLowerCase();
  const matches = keyword
    ? available.filter((parameter) =>
      parameter.code.toLowerCase().includes(keyword) || parameter.name.toLowerCase().includes(keyword))
    : available;

  const choose = (parameter) => {
    setParameterId(String(parameter.id));
    setQuery(optionLabel(parameter));
    setOpen(false);
  };

  const addParameter = () => {
    if (!parameterId || selectedIds.includes(parameterId)) return;
    onChange([...selectedIds, parameterId]);
    setParameterId('');
    setQuery('');
  };

  // Arrow keys move through the suggestions, Enter picks one, Escape closes the list.
  const onSearchKeyDown = (event) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setHighlighted((index) => Math.min(Math.max(index + step, 0), Math.max(matches.length - 1, 0)));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (open && matches[highlighted]) choose(matches[highlighted]);
      else if (parameterId) addParameter();
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  };

  const moveTo = (currentIndex, requestedPosition) => {
    const targetIndex = Number(requestedPosition) - 1;
    if (!Number.isInteger(targetIndex) || targetIndex < 0 || targetIndex >= selectedIds.length) return;
    const next = [...selectedIds];
    const [movedId] = next.splice(currentIndex, 1);
    next.splice(targetIndex, 0, movedId);
    onChange(next);
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        Chọn ít nhất một chỉ số. Chỉ số không sửa được sau khi lưu dịch vụ.
      </p>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1 space-y-1.5">
          <label htmlFor="service-lab-parameter" className="block text-sm font-semibold text-slate-700">
            Chọn chỉ số
          </label>
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              id="service-lab-parameter"
              type="text"
              role="combobox"
              autoComplete="off"
              value={query}
              placeholder="Tìm theo mã hoặc tên chỉ số"
              onChange={(event) => {
                setQuery(event.target.value);
                setParameterId('');
                setOpen(true);
                setHighlighted(0);
              }}
              onFocus={() => setOpen(true)}
              // Delay so a click on a suggestion lands before the list closes.
              onBlur={() => window.setTimeout(() => setOpen(false), 150)}
              onKeyDown={onSearchKeyDown}
              aria-expanded={open}
              aria-controls="service-lab-parameter-list"
              aria-autocomplete="list"
              aria-activedescendant={open && matches[highlighted] ? `lab-parameter-option-${matches[highlighted].id}` : undefined}
              aria-invalid={Boolean(error)}
              aria-describedby={error ? 'service-lab-parameters-error' : undefined}
              className={`${INPUT_CLASS} pl-10 ${query ? 'pr-10' : ''}`}
            />
            {query && (
              <button
                type="button"
                aria-label="Xóa nội dung tìm kiếm"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => {
                  setQuery('');
                  setParameterId('');
                  setHighlighted(0);
                  setOpen(true);
                }}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600 cursor-pointer"
              >
                <X aria-hidden="true" className="h-4 w-4" />
              </button>
            )}
            {open && (
              <ul
                id="service-lab-parameter-list"
                role="listbox"
                aria-label="Chỉ số xét nghiệm"
                className="absolute z-20 mt-1 max-h-72 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white py-1 shadow-lg"
              >
                {matches.length === 0 ? (
                  <li className="px-4 py-3 text-sm text-slate-400">Không tìm thấy chỉ số phù hợp.</li>
                ) : matches.map((parameter, index) => (
                  <li
                    key={parameter.id}
                    id={`lab-parameter-option-${parameter.id}`}
                    role="option"
                    aria-selected={String(parameter.id) === parameterId}
                    onMouseDown={(event) => event.preventDefault()}
                    onMouseEnter={() => setHighlighted(index)}
                    onClick={() => choose(parameter)}
                    className={`flex cursor-pointer items-center justify-between gap-3 px-4 py-2.5 text-sm ${
                      index === highlighted ? 'bg-[#f0f9f8]' : ''
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="font-semibold text-slate-800">{parameter.name}</span>
                      <span className="ml-2 font-mono text-xs text-slate-400">{parameter.code}</span>
                    </span>
                    {parameter.unit && <span className="shrink-0 text-xs text-slate-500">{parameter.unit}</span>}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
        <button
          type="button"
          onClick={addParameter}
          disabled={!parameterId}
          className="inline-flex h-11 shrink-0 cursor-pointer items-center justify-center gap-2 rounded-xl bg-[#127e7a] px-4 text-sm font-semibold text-white transition-colors hover:bg-[#0e6b67] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#127e7a]/30 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Plus aria-hidden="true" className="h-4 w-4" />
          Thêm chỉ số
        </button>
      </div>

      <div className="overflow-x-auto rounded-xl border border-slate-100">
        <table className="w-full min-w-[900px] text-left border-collapse">
          <thead>
            <tr className="bg-[#f6f8f9] text-xs font-semibold text-[#1b2533]">
              <th scope="col" className="pl-4 pr-2 py-3 w-20 text-center">STT</th>
              <th scope="col" className="px-3 py-3">Mã chỉ số</th>
              <th scope="col" className="px-3 py-3">Tên chỉ số</th>
              <th scope="col" className="px-3 py-3">Kiểu kết quả</th>
              <th scope="col" className="px-3 py-3">Đơn vị</th>
              <th scope="col" className="px-3 py-3">Chỉ số tham chiếu</th>
              <th scope="col" className="px-3 py-3 text-center">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-sm">
            {selectedIds.length === 0 ? (
              <tr>
                <td colSpan={7} className="p-8 text-center text-slate-400">
                  Chưa có chỉ số xét nghiệm nào được chọn.
                </td>
              </tr>
            ) : selectedIds.map((id, index) => {
              const parameter = parameterById.get(String(id));
              if (!parameter) return null;
              const ranges = parameter.referenceRanges ?? [];
              return (
                <tr key={id} className="hover:bg-slate-50/70">
                  <td className="pl-4 pr-2 py-3 text-center">
                    <label className="sr-only" htmlFor={`parameter-order-${id}`}>
                      Thứ tự của {parameter.name}
                    </label>
                    <input
                      id={`parameter-order-${id}`}
                      type="number"
                      min={1}
                      max={selectedIds.length}
                      value={index + 1}
                      onChange={(event) => moveTo(index, event.target.value)}
                      className="h-9 w-14 rounded-lg border border-slate-200 bg-white px-2 text-center text-sm font-semibold tabular-nums text-[#127e7a] focus:border-[#127e7a] focus:outline-none focus:ring-2 focus:ring-[#127e7a]/10"
                    />
                  </td>
                  <td className="px-3 py-3 font-mono text-xs text-slate-500 whitespace-nowrap">{parameter.code}</td>
                  <td className="px-3 py-3 font-semibold text-slate-800">{parameter.name}</td>
                  <td className="px-3 py-3"><LabValueTypeBadge valueType={parameter.valueType} /></td>
                  <td className="px-3 py-3 text-slate-600">{parameter.unit || '—'}</td>
                  <td className="px-3 py-3 text-slate-700">
                    {ranges.length === 0 ? (
                      <span className="text-slate-400">Chưa cấu hình</span>
                    ) : (
                      <ul className="space-y-1">
                        {ranges.map((range) => (
                          <li key={range.id}>{formatReferenceRangeLine(range)}</li>
                        ))}
                      </ul>
                    )}
                  </td>
                  <td className="px-3 py-3">
                      <div className="flex items-center justify-center gap-2">
                        <button
                          type="button"
                          onClick={() => onChange(selectedIds.filter((selectedId) => selectedId !== id))}
                          aria-label={`Gỡ chỉ số ${parameter.name}`}
                          className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-rose-200 px-3 text-xs font-semibold text-rose-600 transition-colors hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-200"
                        >
                          <Trash2 aria-hidden="true" className="h-3.5 w-3.5" />
                          Gỡ
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

      {error ? (
        <FieldError id="service-lab-parameters-error">{error}</FieldError>
      ) : (
        <FieldHint>
          Thứ tự trong bảng sẽ được dùng khi nhập và hiển thị kết quả xét nghiệm.
        </FieldHint>
      )}
    </div>
  );
}
