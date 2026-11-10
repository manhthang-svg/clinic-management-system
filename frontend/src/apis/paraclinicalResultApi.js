// UC-67 / UC-68 adapter for /api/v1/paraclinical-results. Same method names as resultMockApi, so the result
// pages work with either. Image upload is not available yet (TBD: file storage), so `supportsFiles` is false.
import { apiClient as defaultClient } from '../services/session';

const pad = (n) => String(n).padStart(2, '0');

/** ISO instant → value of an <input type="datetime-local"> in the browser's time zone. */
export function toLocalInput(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** <input type="datetime-local"> value → ISO instant, or null when empty. */
export function fromLocalInput(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** Backend codes → the codes the result pages already map to Vietnamese messages. */
function normalizeError(error) {
  const code = error?.errorResponse?.code;
  if (error?.status === 403) error.code = 'NOT_PERFORMER';
  else if (error?.status === 404) error.code = 'NOT_FOUND';
  else if (code) error.code = code;
  return error;
}

function toOrder(view) {
  if (!view?.result) return view;
  return { ...view, result: { ...view.result, receivedAt: toLocalInput(view.result.receivedAt) } };
}

function toRequest(form) {
  return {
    receivedAt: fromLocalInput(form.receivedAt),
    values: form.values ?? null,
    conclusion: form.conclusion ?? '',
    technique: form.technique ?? null,
    description: form.description ?? null,
    sections: form.sections ?? null,
    recommendation: form.recommendation ?? null,
    imageLink: form.imageLink?.trim() || null,
    readingDoctor: null,
    instrumentId: form.instrumentId || null,
  };
}

export function createResultApi(client = defaultClient) {
  const call = async (path, options) => {
    try {
      return toOrder(await client.request(path, options));
    } catch (error) {
      throw normalizeError(error);
    }
  };
  const base = (id) => `/paraclinical-results/${encodeURIComponent(id)}`;

  return {
    supportsFiles: false,
    get: (id) => call(base(id)),
    saveDraft: (id, form) => call(`${base(id)}/draft`, { method: 'PUT', body: toRequest(form) }),
    submit: (id, form) => call(`${base(id)}/commands/submit`, { method: 'POST', body: toRequest(form) }),
    recordPrint: (id) => call(`${base(id)}/commands/record-print`, { method: 'POST' }),
  };
}

export const resultApi = createResultApi();

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Real orders (UUID ids, opened from the technician queue) use the API. ASSUMPTION / temporary: rows of the
 * pending result list (UC-64) are still demo data with demo ids, so they keep the demo API until UC-64 reads
 * the backend.
 */
export function resultApiFor(orderId, demoApi) {
  return UUID_PATTERN.test(orderId ?? '') ? resultApi : demoApi;
}
