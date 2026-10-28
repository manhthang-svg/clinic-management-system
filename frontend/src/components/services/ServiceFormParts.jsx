import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { INPUT_CLASS, FormAlert } from '../shared/FormFields';
import { formatPrice } from './serviceFormat';

export { INPUT_CLASS, Field, FieldError, FieldHint, FormAlert } from '../shared/FormFields';

// While focused the field shows the raw digits: reformatting under the caret (1000 -> 1.000)
// desyncs Vietnamese IMEs such as Unikey, which then retype their buffer (1000 -> 1100).
export function PriceInput({ id, value, onChange, error, disabled }) {
  const [focused, setFocused] = useState(false);
  const shown = value === '' || focused ? value : formatPrice(Number(value));

  return (
    <div className="relative">
      <input
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        value={shown}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 10))}
        placeholder="0"
        disabled={disabled}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`${INPUT_CLASS} pr-14 text-right`}
      />
      <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400 pointer-events-none">VNĐ</span>
    </div>
  );
}

/** Dialog shell with a form body and Hủy / submit buttons; Escape closes it unless saving. */
export function ServiceFormModal({
  id,
  icon: Icon,
  title,
  description,
  submitLabel,
  submitting,
  submitDisabled,
  alert,
  onSubmit,
  onClose,
  children,
}) {
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape' && !submitting) onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose, submitting]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-title`}
        onSubmit={onSubmit}
        noValidate
        className="w-full max-w-xl max-h-[calc(100vh-2rem)] overflow-y-auto rounded-2xl bg-white shadow-2xl border border-[#e3ece9]"
      >
        <div className="flex items-start gap-3 p-5 border-b border-slate-100">
          <div className="w-10 h-10 rounded-xl bg-[#e6f5f3] text-[#127e7a] flex items-center justify-center shrink-0">
            <Icon className="w-5 h-5" />
          </div>
          <div className="flex-1 min-w-0">
            <h2 id={`${id}-title`} className="text-base! leading-6! font-bold! text-[#1b2533]">{title}</h2>
            {description && <p className="text-xs text-slate-500 mt-0.5">{description}</p>}
          </div>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            disabled={submitting}
            className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 cursor-pointer disabled:cursor-not-allowed"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-5">
          {children}
          {alert && <FormAlert>{alert}</FormAlert>}
        </div>

        <div className="flex justify-end gap-3 px-5 py-4 border-t border-slate-100 bg-slate-50/60 rounded-b-2xl">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="px-4 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 cursor-pointer disabled:cursor-not-allowed"
          >
            Hủy
          </button>
          <button
            type="submit"
            disabled={submitting || submitDisabled}
            className="px-5 py-2.5 text-sm font-semibold text-white bg-[#127e7a] hover:bg-[#0e6b67] rounded-xl shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {submitting ? 'Đang lưu…' : submitLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
