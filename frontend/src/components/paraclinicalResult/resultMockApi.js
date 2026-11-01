// In-memory stand-in for the technician result API (UC-64, UC-67). ASSUMPTION: replace with a real
// adapter (same method names) once the backend exposes the endpoints. Data resets on page reload.
import { MOCK_RESULT_ORDERS, MOCK_TECHNICIAN } from './resultMockData';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export class ResultApiError extends Error {
  constructor(code) {
    super(code);
    this.code = code;
  }
}

/** Creates an independent mock store; tests create their own. */
export function createMockResultApi({ orders = MOCK_RESULT_ORDERS, technician = MOCK_TECHNICIAN, latency = 150 } = {}) {
  const store = new Map(clone(orders).map((order) => [order.id, order]));

  const view = (order) => ({ ...clone(order), isPerformer: order.performer?.id === technician.id });

  const editable = (id) => {
    const order = store.get(id);
    if (!order) throw new ResultApiError('NOT_FOUND');
    if (order.performer?.id !== technician.id) throw new ResultApiError('NOT_PERFORMER');
    if (order.result?.status === 'SUBMITTED') throw new ResultApiError('RESULT_ALREADY_SUBMITTED');
    return order;
  };

  return {
    technician,

    /** UC-64: performed orders whose result is not submitted, earliest performed first. */
    async listPending() {
      await delay(latency);
      return [...store.values()]
        .sort((a, b) => a.performedAt.localeCompare(b.performedAt))
        .map(view);
    },

    async get(id) {
      await delay(latency);
      const order = store.get(id);
      if (!order) throw new ResultApiError('NOT_FOUND');
      return view(order);
    },

    /** UC-67 AF-01: saves without locking. */
    async saveDraft(id, form) {
      await delay(latency);
      const order = editable(id);
      order.result = { ...clone(form), status: 'DRAFT', savedAt: new Date().toISOString() };
      return view(order);
    },

    /** UC-68: counts prints so that a reprint is marked "Bản sao" (AF-01). */
    async recordPrint(id) {
      await delay(0);
      const order = store.get(id);
      if (!order?.result || order.result.status !== 'SUBMITTED') throw new ResultApiError('RESULT_NOT_SUBMITTED');
      order.printCount = (order.printCount ?? 0) + 1;
      return view(order);
    },

    /** UC-67 step 9: saves and locks the result and completes the order (BR-29). */
    async submit(id, form) {
      await delay(latency);
      const order = editable(id);
      const now = new Date().toISOString();
      order.result = { ...clone(form), status: 'SUBMITTED', savedAt: now, submittedAt: now };
      order.status = 'COMPLETED';
      return view(order);
    },
  };
}

export const resultMockApi = createMockResultApi();
