/** So giay con lai den expiredAt (ISO tu backend); null khi khong co thoi han. */
export function secondsUntil(expiredAt, now = Date.now()) {
    if (!expiredAt) return null;
    const end = new Date(expiredAt).getTime();
    if (Number.isNaN(end)) return null;
    return Math.max(0, Math.ceil((end - now) / 1000));
}

// hien so giay dang mm:ss
export function formatCountdown(seconds) {
    const m = String(Math.floor(seconds / 60)).padStart(2, '0');
    const s = String(seconds % 60).padStart(2, '0');
    return `${m}:${s}`;
}
