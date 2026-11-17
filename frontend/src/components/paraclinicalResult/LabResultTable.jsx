import { QUALITATIVE_OPTIONS } from './resultMockData';
import { INTERPRETATION, formatReference, interpret, qualitativeLabel } from './resultForm';


const CELL_INPUT =
  'w-28 px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-sm text-right font-semibold ' +
  'focus:outline-none focus:border-[#127e7a] focus:ring-2 focus:ring-[#127e7a]/10 ' +
  'aria-invalid:border-rose-400 aria-invalid:bg-rose-50/60';

// UC-67: bang ket qua xet nghiem, moi chi so 1 dong (gia tri, khoang tham chieu, don vi, danh gia)
export function LabResultTable({ parameters, values, errors = {}, readOnly, onChange }) {
  let lastGroup = null;
  const rows = [];

  parameters.forEach((parameter, index) => {
    if (parameter.group && parameter.group !== lastGroup) {
      lastGroup = parameter.group;
      rows.push(
        <tr key={`group-${parameter.group}`} className="bg-slate-50/80">
          <td colSpan={6} className="px-3 py-2 text-xs font-bold text-slate-600">{parameter.group}</td>
        </tr>,
      );
    }

    const value = values[parameter.code] ?? '';
    const error = errors[`value:${parameter.code}`];
    const result = interpret(parameter, value);
    const inputId = `lab-${parameter.code}`;

    rows.push(
      <tr key={parameter.code} className="border-t border-slate-100">
        <td className="px-3 py-2 text-center text-xs text-slate-400">{index + 1}</td>
        <td className="px-3 py-2">
          <label htmlFor={inputId} className="text-sm font-medium text-slate-800">{parameter.name}</label>
        </td>
        <td className="px-3 py-2">
          {readOnly ? (
            <span className={`text-sm font-bold ${result && result !== 'NORMAL' ? 'text-rose-600' : 'text-slate-900'}`}>
              {parameter.valueType === 'QUALITATIVE' ? qualitativeLabel(value) : value || '—'}
            </span>
          ) : parameter.valueType === 'QUALITATIVE' ? (
            <select
              id={inputId}
              value={value}
              onChange={(event) => onChange(parameter.code, event.target.value)}
              aria-invalid={error ? true : undefined}
              className={`${CELL_INPUT} text-left w-32`}
            >
              <option value="">Chọn</option>
              {QUALITATIVE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          ) : (
            <input
              id={inputId}
              value={value}
              inputMode="decimal"
              autoComplete="off"
              onChange={(event) => onChange(parameter.code, event.target.value)}
              aria-invalid={error ? true : undefined}
              className={CELL_INPUT}
            />
          )}
          {error && <p className="text-[11px] font-medium text-rose-600 mt-0.5">{error}</p>}
        </td>
        <td className="px-3 py-2 text-sm text-slate-600 whitespace-nowrap">{formatReference(parameter.ref)}</td>
        <td className="px-3 py-2 text-sm text-slate-600 whitespace-nowrap">{parameter.unit}</td>
        <td className="px-3 py-2">
          {result && (
            <span className={`inline-flex px-2 py-0.5 rounded-md border text-[11px] font-bold whitespace-nowrap ${INTERPRETATION[result].className}`}>
              {INTERPRETATION[result].label}
            </span>
          )}
        </td>
      </tr>,
    );
  });

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200">
      <table className="w-full text-left">
        <thead>
          <tr className="bg-slate-100/80 text-xs font-semibold text-slate-500">
            <th className="px-3 py-2.5 w-12 text-center">TT</th>
            <th className="px-3 py-2.5">Tên xét nghiệm</th>
            <th className="px-3 py-2.5">Kết quả</th>
            <th className="px-3 py-2.5">Tham chiếu</th>
            <th className="px-3 py-2.5">Đơn vị</th>
            <th className="px-3 py-2.5">Đánh giá</th>
          </tr>
        </thead>
        <tbody>{rows}</tbody>
      </table>
    </div>
  );
}

export default LabResultTable;
