import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PayosQrCountdown } from './PayosQrCountdown';
import { formatCountdown, secondsUntil } from './payosQrExpiry';

describe('secondsUntil', () => {
    it('returns null without an expiry', () => {
        expect(secondsUntil(null)).toBeNull();
        expect(secondsUntil('not-a-date')).toBeNull();
    });

    it('counts down to the backend expiry and stops at zero', () => {
        const now = Date.parse('2026-11-06T10:00:00Z');
        expect(secondsUntil('2026-11-06T10:15:00Z', now)).toBe(900);
        expect(secondsUntil('2026-11-06T09:59:00Z', now)).toBe(0);
    });
});

describe('formatCountdown', () => {
    it('formats mm:ss', () => {
        expect(formatCountdown(900)).toBe('15:00');
        expect(formatCountdown(65)).toBe('01:05');
    });
});

describe('PayosQrCountdown', () => {
    afterEach(() => vi.useRealTimers());

    it('ticks down and offers a new QR once expired', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-11-06T10:00:00Z'));
        const onRenew = vi.fn();

        render(<PayosQrCountdown expiredAt="2026-11-06T10:00:02Z" onRenew={onRenew} />);
        expect(screen.getByText('00:02')).toBeInTheDocument();

        act(() => vi.advanceTimersByTime(2000));
        expect(screen.getByRole('alert')).toHaveTextContent('Mã QR đã hết hạn.');

        vi.useRealTimers();
        await userEvent.click(screen.getByRole('button', { name: 'Tạo mã QR mới' }));
        expect(onRenew).toHaveBeenCalledOnce();
    });
});
