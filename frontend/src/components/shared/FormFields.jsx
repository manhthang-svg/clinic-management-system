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

export function Field({ id, label, required, error, hint, className = '', children }) {
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
