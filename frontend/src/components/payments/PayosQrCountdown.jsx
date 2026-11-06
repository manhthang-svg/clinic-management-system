import { useEffect, useState } from 'react';
import { formatCountdown, secondsUntil } from './payosQrExpiry';

/** Đếm ngược thời hạn mã QR PayOS theo expiredAt do backend trả về. */
export function PayosQrCountdown({ expiredAt, onRenew, renewing = false }) {
    const [now, setNow] = useState(() => Date.now());
    const remaining = secondsUntil(expiredAt, now);
    const ticking = remaining !== null && remaining > 0;

    useEffect(() => {
        if (!ticking) return undefined;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [ticking]);

    if (remaining === null) return null;

    if (remaining === 0) {
        return (
            <div role="alert" className="flex items-center justify-between gap-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                <span className="font-semibold">Mã QR đã hết hạn.</span>
                {onRenew && (
                    <button
                        type="button"
                        onClick={onRenew}
                        disabled={renewing}
                        className="rounded-lg bg-rose-600 px-3 py-1.5 font-semibold text-white hover:bg-rose-700 disabled:opacity-50"
                    >
                        {renewing ? 'Đang tạo mã mới…' : 'Tạo mã QR mới'}
                    </button>
                )}
            </div>
        );
    }

    return (
        <p className="text-center text-xs text-slate-600" aria-live="polite">
            Mã QR hết hạn sau{' '}
            <strong className={remaining <= 60 ? 'font-mono text-rose-600' : 'font-mono text-slate-800'}>
                {formatCountdown(remaining)}
            </strong>
        </p>
    );
}
