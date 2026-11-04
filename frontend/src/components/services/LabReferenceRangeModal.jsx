import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, Edit3, FlaskConical, Plus, RotateCcw } from 'lucide-react';
import { MSG12, MSG13, MSG80, msg05, msg75 } from './serviceMessages';
import { Field, FieldError, FormAlert, INPUT_CLASS, ServiceFormModal } from './ServiceFormParts';
import {
  emptyReferenceRangeForm,
  referenceRangeToForm,
  referenceRangeToRequest,
  validateReferenceRange,
} from './labReferenceRangeForm';

const TYPE_OPTIONS = [
  ['RANGE', 'Khoảng giá trị'],
  ['UPPER_LIMIT', 'Giới hạn trên'],
  ['LOWER_LIMIT', 'Giới hạn dưới'],
  ['QUALITATIVE', 'Giá trị định tính'],
  ['TEXT', 'Mô tả'],
];

function apiErrorMessage(error) {
  const code = error?.errorResponse?.code;
  if (code === 'CONCURRENT_MODIFICATION') return msg75('Trị số tham chiếu');
  if (code === 'LAB_REFERENCE_RANGE_INVALID' || code === 'REQUEST_VALIDATION_FAILED') {
    return msg05('Trị số tham chiếu');
  }
  if (code === 'UNAUTHORIZED') return MSG12;
  if (code === 'FORBIDDEN') return MSG13;
  return MSG80;
}

export function LabReferenceRangeModal({ parameter, api, onChanged, onClose }) {
  const [ranges, setRanges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyReferenceRangeForm);
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const errors = useMemo(() => validateReferenceRange(form), [form]);

  const loadRanges = () => {
    setLoading(true);
    setLoadError(null);
    api.listReferenceRanges(parameter.id)
      .then(setRanges)
      .catch(() => setLoadError(MSG80))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    let ignore = false;
    api.listReferenceRanges(parameter.id)
      .then((items) => !ignore && setRanges(items))
      .catch(() => !ignore && setLoadError(MSG80))
      .finally(() => !ignore && setLoading(false));
    return () => { ignore = true; };
  }, [api, parameter.id]);

  const change = (field, value) => {
    setForm((previous) => ({ ...previous, [field]: value }));
    setSubmitError(null);
  };

  const startCreate = () => {
    setEditingId(null);
    setForm(emptyReferenceRangeForm());
    setTouched(false);
    setSubmitError(null);
  };

  const startEdit = (range) => {
    setEditingId(range.id);
    setForm(referenceRangeToForm(range));
    setTouched(false);
    setSubmitError(null);
  };

  const submit = async (event) => {
    event.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const request = referenceRangeToRequest(form);
      const saved = editingId
        ? await api.updateReferenceRange(parameter.id, editingId, request)
        : await api.createReferenceRange(parameter.id, request);
      setRanges((previous) => {
        const exists = previous.some((range) => range.id === saved.id);
        return exists
          ? previous.map((range) => (range.id === saved.id ? saved : range))
          : [saved, ...previous];
      });
      setEditingId(saved.id);
      setForm(referenceRangeToForm(saved));
      setTouched(false);
      await onChanged?.();
    } catch (error) {
      setSubmitError(apiErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const shown = touched ? errors : {};
  const type = form.referenceType;

  return (
    <ServiceFormModal
      id="lab-reference-range"
      icon={FlaskConical}
      title={`Trị số tham chiếu · ${parameter.code}`}
      description={`${parameter.name}${parameter.unit ? ` (${parameter.unit})` : ''}`}
      submitLabel={editingId ? 'Lưu thay đổi' : 'Thêm trị số'}
      submitting={submitting}
      alert={submitError}
      onSubmit={submit}
      onClose={onClose}
      maxWidthClass="max-w-5xl"
    >
      <div className="grid gap-5 lg:grid-cols-[minmax(250px,0.8fr)_minmax(0,1.5fr)]">
        <section aria-label="Danh sách trị số tham chiếu" className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3">
          <div className="flex items-center justify-between gap-2">
            <h3 className="text-sm font-bold text-slate-800">Đã cấu hình</h3>
            <button
              type="button"
              onClick={startCreate}
              className="inline-flex min-h-10 cursor-pointer items-center gap-1.5 rounded-lg border border-teal-200 bg-white px-3 text-xs font-semibold text-[#127e7a] hover:bg-teal-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#127e7a]/30"
            >
              <Plus aria-hidden="true" className="h-3.5 w-3.5" />
              Thêm mới
            </button>
          </div>

          {loading && <p role="status" className="py-6 text-center text-sm text-slate-500">Đang tải…</p>}
          {loadError && (
            <div className="space-y-2 py-4 text-center">
              <p role="alert" className="text-sm text-rose-600">{loadError}</p>
              <button type="button" onClick={loadRanges} className="inline-flex min-h-10 items-center gap-2 rounded-lg px-3 text-sm font-semibold text-[#127e7a] hover:bg-teal-50">
                <RotateCcw aria-hidden="true" className="h-4 w-4" /> Tải lại
              </button>
            </div>
          )}
          {!loading && !loadError && ranges.length === 0 && (
            <p className="rounded-lg border border-dashed border-slate-200 bg-white px-3 py-6 text-center text-sm text-slate-500">
              Chưa có trị số tham chiếu. Hãy nhập cấu hình đầu tiên.
            </p>
          )}
          {!loading && !loadError && ranges.length > 0 && (
            <div className="max-h-72 space-y-2 overflow-y-auto pr-1 lg:max-h-[460px]">
              {ranges.map((range) => (
                <button
                  key={range.id}
                  type="button"
                  onClick={() => startEdit(range)}
                  aria-pressed={editingId === range.id}
                  className={`w-full cursor-pointer rounded-xl border p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#127e7a]/30 ${
                    editingId === range.id ? 'border-[#127e7a] bg-teal-50' : 'border-slate-200 bg-white hover:border-teal-200'
                  }`}
                >
                  <span className="flex items-start justify-between gap-2">
                    <span className="whitespace-pre-line text-sm font-semibold leading-5 text-slate-800">{range.displayText}</span>
                    <Edit3 aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                  </span>
                  <span className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                    <span className={range.active ? 'font-semibold text-emerald-700' : 'font-medium text-slate-500'}>
                      {range.active ? 'Đang áp dụng' : 'Tạm ngưng'}
                    </span>
                    <span>·</span>
                    <span>{range.source}</span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>

        <section aria-label={editingId ? 'Chỉnh sửa trị số tham chiếu' : 'Thêm trị số tham chiếu'} className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-bold text-slate-800">{editingId ? 'Chỉnh sửa cấu hình' : 'Cấu hình mới'}</h3>
            {editingId && <span className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-500"><CheckCircle2 aria-hidden="true" className="h-4 w-4 text-[#127e7a]" /> Đã lưu</span>}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="reference-type" label="Loại trị số" required className="sm:col-span-2">
              <select id="reference-type" autoFocus value={type} onChange={(event) => change('referenceType', event.target.value)} className={`${INPUT_CLASS} cursor-pointer`}>
                {TYPE_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </Field>

            {(type === 'RANGE' || type === 'LOWER_LIMIT') && (
              <Field id="reference-lower" label={type === 'RANGE' ? 'Cận dưới' : 'Giới hạn dưới'} required error={shown.lowerBound}>
                <input id="reference-lower" type="number" step="any" value={form.lowerBound} onChange={(event) => change('lowerBound', event.target.value)} aria-invalid={Boolean(shown.lowerBound)} className={INPUT_CLASS} />
              </Field>
            )}
            {(type === 'RANGE' || type === 'UPPER_LIMIT') && (
              <Field id="reference-upper" label={type === 'RANGE' ? 'Cận trên' : 'Giới hạn trên'} required error={shown.upperBound}>
                <input id="reference-upper" type="number" step="any" value={form.upperBound} onChange={(event) => change('upperBound', event.target.value)} aria-invalid={Boolean(shown.upperBound)} className={INPUT_CLASS} />
              </Field>
            )}
            {type === 'RANGE' && (
              <div className="flex flex-wrap gap-4 sm:col-span-2">
                <label className="inline-flex min-h-10 cursor-pointer items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={form.lowerInclusive} onChange={(event) => change('lowerInclusive', event.target.checked)} className="h-4 w-4 accent-[#127e7a]" /> Bao gồm cận dưới</label>
                <label className="inline-flex min-h-10 cursor-pointer items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={form.upperInclusive} onChange={(event) => change('upperInclusive', event.target.checked)} className="h-4 w-4 accent-[#127e7a]" /> Bao gồm cận trên</label>
              </div>
            )}
            {type === 'QUALITATIVE' && (
              <Field id="reference-values" label="Giá trị bình thường" required error={shown.normalValues} hint="Phân tách nhiều giá trị bằng dấu phẩy." className="sm:col-span-2">
                <input id="reference-values" value={form.normalValues} onChange={(event) => change('normalValues', event.target.value)} placeholder="Ví dụ: Âm tính, Không phát hiện" aria-invalid={Boolean(shown.normalValues)} className={INPUT_CLASS} />
              </Field>
            )}
            {type === 'TEXT' && (
              <Field id="reference-text" label="Nội dung tham chiếu" required error={shown.referenceText} className="sm:col-span-2">
                <textarea id="reference-text" rows={2} value={form.referenceText} onChange={(event) => change('referenceText', event.target.value)} aria-invalid={Boolean(shown.referenceText)} className={INPUT_CLASS} />
              </Field>
            )}

            <Field id="reference-sex" label="Giới tính áp dụng">
              <select id="reference-sex" value={form.sex} onChange={(event) => change('sex', event.target.value)} className={`${INPUT_CLASS} cursor-pointer`}>
                <option value="">Tất cả</option><option value="1">Nam</option><option value="2">Nữ</option><option value="3">Khác</option>
              </select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field id="reference-age-min" label="Tuổi từ (ngày)" error={shown.ageMinDays}><input id="reference-age-min" type="number" min="0" value={form.ageMinDays} onChange={(event) => change('ageMinDays', event.target.value)} aria-invalid={Boolean(shown.ageMinDays)} className={INPUT_CLASS} /></Field>
              <Field id="reference-age-max" label="Đến (ngày)" error={shown.ageMaxDays}><input id="reference-age-max" type="number" min="0" value={form.ageMaxDays} onChange={(event) => change('ageMaxDays', event.target.value)} aria-invalid={Boolean(shown.ageMaxDays)} className={INPUT_CLASS} /></Field>
            </div>
            <Field id="reference-effective-from" label="Hiệu lực từ" required error={shown.effectiveFrom}><input id="reference-effective-from" type="date" value={form.effectiveFrom} onChange={(event) => change('effectiveFrom', event.target.value)} aria-invalid={Boolean(shown.effectiveFrom)} className={INPUT_CLASS} /></Field>
            <Field id="reference-effective-to" label="Hiệu lực đến" error={shown.effectiveTo}><input id="reference-effective-to" type="date" value={form.effectiveTo} onChange={(event) => change('effectiveTo', event.target.value)} aria-invalid={Boolean(shown.effectiveTo)} className={INPUT_CLASS} /></Field>
            <Field id="reference-source" label="Nguồn tham chiếu" required error={shown.source} className="sm:col-span-2"><input id="reference-source" value={form.source} onChange={(event) => change('source', event.target.value)} placeholder="Ví dụ: Hướng dẫn Bộ Y tế 2025" maxLength={255} aria-invalid={Boolean(shown.source)} className={INPUT_CLASS} /></Field>
            <label className="flex min-h-11 cursor-pointer items-center gap-3 rounded-xl border border-slate-200 px-3 text-sm font-medium text-slate-700 sm:col-span-2">
              <input type="checkbox" checked={form.active} onChange={(event) => change('active', event.target.checked)} className="h-4 w-4 accent-[#127e7a]" />
              Áp dụng ngay cho kết quả xét nghiệm
            </label>
          </div>
          {touched && Object.keys(errors).length > 0 && <FieldError id="reference-form-error">Vui lòng sửa các trường chưa hợp lệ trước khi lưu.</FieldError>}
        </section>
      </div>
    </ServiceFormModal>
  );
}

export default LabReferenceRangeModal;
