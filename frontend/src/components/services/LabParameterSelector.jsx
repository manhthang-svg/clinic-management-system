import { useMemo, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { INPUT_CLASS, FieldError, FieldHint } from './ServiceFormParts';

const TABLE_HEADER_CLASS =
  'border-b border-slate-200 bg-slate-50 px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500';

export function LabParameterSelector({ parameters = [], selectedIds = [], onChange, error }) {
  const [parameterId, setParameterId] = useState('');
  const parameterById = useMemo(
    () => new Map(parameters.map((parameter) => [String(parameter.id), parameter])),
    [parameters],
  );
  const available = parameters.filter((parameter) => !selectedIds.includes(String(parameter.id)));

  const addParameter = () => {
    if (!parameterId || selectedIds.includes(parameterId)) return;
    onChange([...selectedIds, parameterId]);
    setParameterId('');
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
          <select
            id="service-lab-parameter"
            value={parameterId}
            onChange={(event) => setParameterId(event.target.value)}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'service-lab-parameters-error' : undefined}
            className={`${INPUT_CLASS} cursor-pointer`}
          >
            <option value="">Tìm theo mã hoặc tên chỉ số</option>
            {available.map((parameter) => (
              <option key={parameter.id} value={parameter.id}>
                {parameter.code} — {parameter.name}{parameter.unit ? ` (${parameter.unit})` : ''}
              </option>
            ))}
          </select>
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

      <div className="overflow-hidden rounded-xl border border-slate-200">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] border-collapse">
            <thead>
              <tr>
                <th scope="col" className={`${TABLE_HEADER_CLASS} w-24`}>Thứ tự</th>
                <th scope="col" className={`${TABLE_HEADER_CLASS} w-32`}>Mã chỉ số</th>
                <th scope="col" className={TABLE_HEADER_CLASS}>Tên chỉ số</th>
                <th scope="col" className={`${TABLE_HEADER_CLASS} w-28`}>Đơn vị</th>
                <th scope="col" className={TABLE_HEADER_CLASS}>Trị số tham chiếu</th>
                <th scope="col" className={`${TABLE_HEADER_CLASS} w-24 text-center`}>Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {selectedIds.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-slate-400">
                    Chưa có chỉ số xét nghiệm nào được chọn.
                  </td>
                </tr>
              ) : selectedIds.map((id, index) => {
                const parameter = parameterById.get(String(id));
                if (!parameter) return null;
                const reference = parameter.referenceRanges?.map((range) => range.displayText).join('\n')
                  || 'Chưa cấu hình';
                return (
                  <tr key={id} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-4 py-3">
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
                        className="h-9 w-16 rounded-lg border border-slate-200 bg-white px-2 text-center text-sm tabular-nums text-slate-700 focus:border-[#127e7a] focus:outline-none focus:ring-2 focus:ring-[#127e7a]/10"
                      />
                    </td>
                    <td className="px-4 py-3 font-mono text-sm font-semibold text-slate-700">{parameter.code}</td>
                    <td className="px-4 py-3 text-sm font-medium text-slate-800">{parameter.name}</td>
                    <td className="px-4 py-3 text-sm text-slate-600">{parameter.unit || '—'}</td>
                    <td className="whitespace-pre-line px-4 py-3 text-sm leading-5 text-slate-600">{reference}</td>
                    <td className="px-4 py-3 text-center">
                      <button
                        type="button"
                        onClick={() => onChange(selectedIds.filter((selectedId) => selectedId !== id))}
                        aria-label={`Gỡ chỉ số ${parameter.name}`}
                        className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-rose-200 px-3 text-xs font-semibold text-rose-600 transition-colors hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-200"
                      >
                        <Trash2 aria-hidden="true" className="h-3.5 w-3.5" />
                        Gỡ
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
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
