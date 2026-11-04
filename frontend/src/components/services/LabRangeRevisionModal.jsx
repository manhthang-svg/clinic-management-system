import { useState } from 'react';
import { FlaskConical } from 'lucide-react';
import { INPUT_CLASS, Field, ServiceFormModal } from './ServiceFormParts';
import { formatReferenceConditions, formatReferenceValue } from './serviceFormat';
import {
  rangeToRevisionForm,
  revisionError,
  revisionFormToRequest,
  validateRevisionForm,
} from './labRangeRevision';

function formatDate(day) {
  const [year, month, date] = day.split('-');
  return `${date}/${month}/${year}`;
}

/**
 * Revises one reference range of a lab parameter: the new values take effect today and the old range ends
 * yesterday. Who the range applies to (sex, age) is kept.
 */
export function LabRangeRevisionModal({ parameter, range, today, onRevise, onClose, onStale }) {
  const [form, setForm] = useState(() => rangeToRevisionForm(range));
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState(null);

  const type = range.referenceType;
  const errors = validateRevisionForm(form, type);
  const shownErrors = touched ? errors : {};
  const unit = parameter.unit ? ` (${parameter.unit})` : '';
  const update = (field) => (event) => setForm((previous) => ({ ...previous, [field]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    setTouched(true);
    if (Object.keys(errors).length > 0) return;
    setSubmitting(true);
    setServerError(null);
    try {
      await onRevise(revisionFormToRequest(range, form, today));
    } catch (error) {
      const result = revisionError(error);
      setServerError(result.message);
      if (result.reload) onStale?.();
      setSubmitting(false);
    }
  };

  const numberField = (field, label) => (
    <Field id={`revision-${field}`} label={`${label}${unit}`} required error={shownErrors[field]}>
      <input
        id={`revision-${field}`}
        type="text"
        inputMode="decimal"
        value={form[field]}
        onChange={update(field)}
        aria-invalid={Boolean(shownErrors[field])}
        aria-describedby={shownErrors[field] ? `revision-${field}-error` : undefined}
        className={INPUT_CLASS}
      />
    </Field>
  );

  return (
    <ServiceFormModal
      id="lab-range-revision"
      icon={FlaskConical}
      title={`Cập nhật chỉ số tham chiếu · ${parameter.code}`}
      description={`${parameter.name}. Giá trị mới áp dụng từ hôm nay ${formatDate(today)}; giá trị cũ được giữ lại trong lịch sử.`}
      submitLabel="Lưu giá trị mới"
      submitting={submitting}
      alert={serverError}
      onSubmit={submit}
      onClose={onClose}
    >
      <dl className="grid grid-cols-1 gap-3 rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-xs text-slate-500">Áp dụng cho</dt>
          <dd className="font-semibold text-slate-800">{formatReferenceConditions(range) || 'Mọi giới tính, mọi độ tuổi'}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Giá trị hiện tại</dt>
          <dd className="font-semibold text-slate-800">{formatReferenceValue(range)}{parameter.unit ? ` ${parameter.unit}` : ''}</dd>
        </div>
      </dl>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {(type === 'RANGE' || type === 'LOWER_LIMIT') && numberField('lowerBound', 'Giá trị nhỏ nhất')}
        {(type === 'RANGE' || type === 'UPPER_LIMIT') && numberField('upperBound', 'Giá trị lớn nhất')}
        {type === 'QUALITATIVE' && (
          <Field
            id="revision-normalValues"
            label="Giá trị bình thường"
            required
            error={shownErrors.normalValues}
            hint="Ngăn cách bằng dấu phẩy, ví dụ: Âm tính, Vết"
            className="sm:col-span-2"
          >
            <input
              id="revision-normalValues"
              type="text"
              value={form.normalValues}
              onChange={update('normalValues')}
              aria-invalid={Boolean(shownErrors.normalValues)}
              aria-describedby={shownErrors.normalValues ? 'revision-normalValues-error' : undefined}
              className={INPUT_CLASS}
            />
          </Field>
        )}
        {type === 'TEXT' && (
          <Field
            id="revision-referenceText"
            label="Giá trị tham chiếu"
            required
            error={shownErrors.referenceText}
            className="sm:col-span-2"
          >
            <input
              id="revision-referenceText"
              type="text"
              maxLength={255}
              value={form.referenceText}
              onChange={update('referenceText')}
              aria-invalid={Boolean(shownErrors.referenceText)}
              aria-describedby={shownErrors.referenceText ? 'revision-referenceText-error' : undefined}
              className={INPUT_CLASS}
            />
          </Field>
        )}
        <Field
          id="revision-source"
          label="Nguồn tham chiếu"
          required
          error={shownErrors.source}
          hint="Tài liệu hoặc quy trình xét nghiệm làm căn cứ cho giá trị mới"
          className="sm:col-span-2"
        >
          <input
            id="revision-source"
            type="text"
            maxLength={255}
            value={form.source}
            onChange={update('source')}
            aria-invalid={Boolean(shownErrors.source)}
            aria-describedby={shownErrors.source ? 'revision-source-error' : undefined}
            className={INPUT_CLASS}
          />
        </Field>
      </div>
    </ServiceFormModal>
  );
}

export default LabRangeRevisionModal;
