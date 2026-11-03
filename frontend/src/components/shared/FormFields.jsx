// Form pieces shared by the admin and receptionist forms, styled after the account forms.
import { AlertCircle } from 'lucide-react';
import { INPUT_CLASS as BASE_INPUT_CLASS } from '../accounts/AccountFormParts';

// Invalid fields get a tinted background and a rose focus ring instead of the teal one.
export const INPUT_CLASS =
  `${BASE_INPUT_CLASS} aria-invalid:bg-rose-50/60 aria-invalid:focus:border-rose-400 aria-invalid:focus:ring-rose-500/15`;

export function FieldError({ id, children }) {
  return (
    <p id={id} className="flex items-start gap-1.5 text-xs font-medium text-rose-600">
      <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" />
      <span>{children}</span>
    </p>
  );
}

export function FieldHint({ children }) {
  return <p className="text-xs text-slate-400">{children}</p>;
}

// Width of the label column of an inline field; content that lines up with inline fields uses INLINE_OFFSET_CLASS.
export const INLINE_LABEL_CLASS = 'md:w-40 md:shrink-0 md:pt-2.5';
export const INLINE_OFFSET_CLASS = 'md:pl-44';

/** A labelled control; inline puts the label beside the control from md up instead of above it. */
export function Field({ id, label, required, error, hint, inline = false, className = '', children }) {
  if (inline) {
    return (
      <div className={`flex flex-col md:flex-row gap-1.5 md:gap-4 ${className}`}>
        <label htmlFor={id} className={`block text-sm font-semibold text-slate-700 ${INLINE_LABEL_CLASS}`}>
          {label}
          {required && <span className="text-rose-500 ml-0.5">*</span>}
        </label>
        <div className="flex-1 min-w-0 space-y-1.5">
          {children}
          {error ? <FieldError id={`${id}-error`}>{error}</FieldError> : hint && <FieldHint>{hint}</FieldHint>}
        </div>
      </div>
    );
  }
  return (
    <div className={`space-y-1.5 ${className}`}>
      <label htmlFor={id} className="block text-sm font-semibold text-slate-700">
        {label}
        {required && <span className="text-rose-500 ml-0.5">*</span>}
      </label>
      {children}
      {error ? <FieldError id={`${id}-error`}>{error}</FieldError> : hint && <FieldHint>{hint}</FieldHint>}
    </div>
  );
}

export function FormAlert({ children }) {
  return (
    <div role="alert" className="flex items-start gap-2.5 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700">
      <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
      <p className="text-xs font-medium leading-5">{children}</p>
    </div>
  );
}
