import { useEffect, useState } from 'react';
import { PRESETS, dashboardError, validatePeriod } from './dashboardPeriod';

/** Period selection shared by the statistics pages; starts at "Tháng này". */
export function usePeriod(today) {
  const [period, setPeriod] = useState(() => PRESETS.find((p) => p.id === 'month').range(today));
  const [presetId, setPresetId] = useState('month');

  const choosePreset = (preset) => {
    setPresetId(preset.id);
    setPeriod(preset.range(today));
  };

  const changeDate = (field) => (event) => {
    setPresetId('');
    setPeriod((current) => ({ ...current, [field]: event.target.value }));
  };

  return { period, presetId, periodError: validatePeriod(period), choosePreset, changeDate };
}

/**
 * Loads `fetcher(params, signal)` whenever `params` change and `enabled` is true. `params` must be
 * memoised by the caller. Loading is derived from the last settled request, so the effect never sets
 * state synchronously. The previous data stays visible while a new request runs. `toMessage` turns a
 * failure into the Vietnamese message shown (default: the statistics pages' messages).
 */
export function useStatisticsQuery(fetcher, params, enabled, toMessage = dashboardError) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [settled, setSettled] = useState(null);

  const loading = enabled && (settled?.params !== params || settled?.reloadKey !== reloadKey);

  useEffect(() => {
    if (!enabled) return undefined;
    const controller = new AbortController();
    (async () => {
      try {
        const result = await fetcher(params, controller.signal);
        setData(result);
        setError('');
      } catch (err) {
        if (err?.name === 'AbortError' || controller.signal.aborted) return;
        setError(toMessage(err));
      }
      if (!controller.signal.aborted) setSettled({ params, reloadKey });
    })();
    return () => controller.abort();
  }, [fetcher, params, enabled, reloadKey, toMessage]);

  return { data, error, loading, reload: () => setReloadKey((k) => k + 1) };
}
