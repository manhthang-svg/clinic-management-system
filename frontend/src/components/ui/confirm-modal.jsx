import React from 'react';
import { cn } from '../../utils/utils';
import { Button } from './button';

/**
 * Clean, modern confirmation and alert modal matching WellNest / Clinic theme.
 */
export function ConfirmModal({
  isOpen,
  title = 'Xác nhận thao tác',
  description,
  confirmText = 'Xác nhận',
  cancelText = 'Hủy',
  variant = 'default', // 'default' | 'destructive' | 'mint'
  onConfirm,
  onClose,
  children,
}) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/45 backdrop-blur-[2px] animate-in fade-in duration-150">
      <div
        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl border border-[#e3ece9] transition-all transform scale-100"
        role="dialog"
        aria-modal="true"
      >
        <div className="space-y-2">
          <h3 className="text-lg font-bold text-[#142534] tracking-tight">{title}</h3>
          {description && (
            <div className="text-sm text-[#60737d] whitespace-pre-line leading-relaxed">
              {description}
            </div>
          )}
        </div>

        {children && <div className="mt-4">{children}</div>}

        <div className="mt-6 flex justify-end gap-3">
          {cancelText && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onClose}
              className="text-xs font-semibold px-4"
            >
              {cancelText}
            </Button>
          )}
          <Button
            type="button"
            variant={variant === 'destructive' ? 'destructive' : 'mint'}
            size="sm"
            onClick={onConfirm}
            className="text-xs font-semibold px-4"
          >
            {confirmText}
          </Button>
        </div>
      </div>
    </div>
  );
}
