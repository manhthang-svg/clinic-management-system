// Field blocks shared by the per-service result pages.
import { Field, INPUT_CLASS } from '../shared/FormFields';
import { useContext } from 'react';
import { ResultFiles } from './ResultFiles';
import { ResultApiContext } from './resultContext';
import { ResultSection } from './ResultEditor';
import { sectionsFor } from './resultSections';

function invalid(id, error) {
  return error ? { 'aria-invalid': true, 'aria-describedby': `${id}-error` } : {};
}

export function TextAreaField({ id, label, value, onChange, readOnly, error, required, rows = 3, hint, mono }) {
  return (
    <Field id={id} label={label} required={required} error={error} hint={hint}>
      <textarea
        id={id}
        rows={rows}
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value)}
        disabled={readOnly}
        className={`${INPUT_CLASS} ${mono ? 'font-mono text-[13px] leading-6' : ''}`}
        {...invalid(id, error)}
      />
    </Field>
  );
}

/** One input per organ / site / ECG item, in the order printed on the slip. */
export function SectionFields({ order, form, set, errors, readOnly, columns = 1 }) {
  const sections = sectionsFor(order) ?? [];
  const update = (key, value) => set('sections', { ...form.sections, [key]: value });
  return (
    <div className={`grid gap-x-5 gap-y-3 ${columns === 2 ? 'grid-cols-1 md:grid-cols-2' : 'grid-cols-1'}`}>
      {sections.map((section) => {
        const id = `section-${section.key}`;
        const error = errors[`section:${section.key}`];
        return (
          <div key={section.key} className={`grid grid-cols-1 sm:grid-cols-[180px_1fr] gap-x-3 gap-y-1 items-start ${section.short ? '' : columns === 2 ? 'md:col-span-2' : ''}`}>
            <label htmlFor={id} className="text-sm font-semibold text-slate-700 sm:pt-2.5">
              {section.label}<span className="text-rose-500 ml-0.5">*</span>
            </label>
            <div>
              <div className="flex items-center gap-2">
                <input
                  id={id}
                  value={form.sections?.[section.key] ?? ''}
                  onChange={(event) => update(section.key, event.target.value)}
                  disabled={readOnly}
                  placeholder={section.placeholder ?? section.normal}
                  autoComplete="off"
                  className={INPUT_CLASS}
                  {...invalid(id, error)}
                />
                {section.unit && <span className="text-sm text-slate-500 shrink-0">{section.unit}</span>}
              </div>
              {error && <p id={`${id}-error`} className="text-xs font-medium text-rose-600 mt-1">{error}</p>}
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function ConclusionSection({ form, set, errors, readOnly, label = 'Kết luận', children }) {
  return (
    <ResultSection title={label}>
      <TextAreaField
        id="result-conclusion"
        label={label}
        required
        value={form.conclusion}
        onChange={(value) => set('conclusion', value)}
        readOnly={readOnly}
        error={errors.conclusion}
      />
      {children}
    </ResultSection>
  );
}

export function RecommendationField({ form, set, readOnly }) {
  return (
    <TextAreaField
      id="result-recommendation"
      label="Lời dặn của BS chuyên khoa"
      rows={2}
      value={form.recommendation}
      onChange={(value) => set('recommendation', value)}
      readOnly={readOnly}
    />
  );
}

export function SignerField({ order, form, set, readOnly, label = 'Bác sĩ chuyên khoa ký phiếu' }) {
  return (
    <Field id="result-readingDoctor" label={label} hint="Để trống thì in tên người thực hiện.">
      <input
        id="result-readingDoctor"
        value={form.readingDoctor ?? ''}
        onChange={(event) => set('readingDoctor', event.target.value)}
        disabled={readOnly}
        maxLength={255}
        placeholder={order.performer?.name}
        className={INPUT_CLASS}
      />
    </Field>
  );
}

export function ImageLinkField({ form, set, errors, readOnly }) {
  return (
    <Field id="result-imageLink" label="Link xem ảnh (PACS)" error={errors.imageLink} hint="In thành mã QR trên phiếu.">
      <input
        id="result-imageLink"
        value={form.imageLink ?? ''}
        onChange={(event) => set('imageLink', event.target.value)}
        disabled={readOnly}
        placeholder="https://"
        className={INPUT_CLASS}
        {...invalid('result-imageLink', errors.imageLink)}
      />
    </Field>
  );
}

export function FilesSection({ form, set, readOnly, title = 'Hình ảnh', description }) {
  const { supportsFiles } = useContext(ResultApiContext);
  return (
    <ResultSection title={title} description={description}>
      {supportsFiles ? (
        <ResultFiles files={form.files ?? []} readOnly={readOnly} onChange={(files) => set('files', files)} />
      ) : (
        <p className="text-sm text-slate-500">Chức năng tải ảnh kết quả chưa được hỗ trợ.</p>
      )}
    </ResultSection>
  );
}
