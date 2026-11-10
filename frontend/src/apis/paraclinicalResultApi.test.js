import { describe, expect, it, vi } from 'vitest';

import ApiError from './apiError';
import { createResultApi, fromLocalInput, resultApi, resultApiFor, toLocalInput } from './paraclinicalResultApi';

const ID = '0192f1a2-3b4c-7d5e-8f60-123456789abc';

function clientReturning(value) {
  return { request: vi.fn().mockResolvedValue(value) };
}

describe('paraclinicalResultApi (UC-67, UC-68)', () => {
  it('reads the result form of an ordered service', async () => {
    const client = clientReturning({ id: ID, result: null });
    await createResultApi(client).get(ID);

    expect(client.request).toHaveBeenCalledWith(`/paraclinical-results/${ID}`, undefined);
  });

  it('saves a draft with the lab values and the received time as an instant', async () => {
    const client = clientReturning({ id: ID, result: { status: 'DRAFT', receivedAt: '2026-11-10T01:30:00Z' } });
    const saved = await createResultApi(client).saveDraft(ID, {
      receivedAt: '2026-11-10T08:30', values: { GLU: '6,2' }, conclusion: 'Bình thường', instrumentId: '',
    });

    const [path, options] = client.request.mock.calls[0];
    expect(path).toBe(`/paraclinical-results/${ID}/draft`);
    expect(options.method).toBe('PUT');
    expect(options.body).toMatchObject({ values: { GLU: '6,2' }, conclusion: 'Bình thường', instrumentId: null });
    expect(options.body.receivedAt).toBe(new Date('2026-11-10T08:30').toISOString());
    expect(saved.result.receivedAt).toBe(toLocalInput('2026-11-10T01:30:00Z'));
  });

  it('submits and records prints with commands', async () => {
    const client = clientReturning({ id: ID });
    const api = createResultApi(client);
    await api.submit(ID, { conclusion: 'x', description: 'y', instrumentId: 'm-1' });
    await api.recordPrint(ID);

    expect(client.request.mock.calls[0][0]).toBe(`/paraclinical-results/${ID}/commands/submit`);
    expect(client.request.mock.calls[0][1].body.instrumentId).toBe('m-1');
    expect(client.request.mock.calls[1]).toEqual([`/paraclinical-results/${ID}/commands/record-print`, { method: 'POST' }]);
  });

  it.each([
    [403, 'FORBIDDEN', 'NOT_PERFORMER'],
    [404, 'NOT_FOUND', 'NOT_FOUND'],
    [409, 'RESULT_ALREADY_SUBMITTED', 'RESULT_ALREADY_SUBMITTED'],
    [400, 'REQUEST_VALIDATION_FAILED', 'REQUEST_VALIDATION_FAILED'],
  ])('maps a %s %s failure to the code the result page understands', async (status, code, expected) => {
    const client = { request: vi.fn().mockRejectedValue(new ApiError('raw', { status, errorResponse: { code } })) };

    await expect(createResultApi(client).get(ID)).rejects.toMatchObject({ code: expected });
  });

  it('does not store images yet', () => {
    expect(createResultApi(clientReturning({})).supportsFiles).toBe(false);
  });

  it('uses the API for real orders and the demo data for demo rows', () => {
    const demo = {};
    expect(resultApiFor(ID, demo)).toBe(resultApi);
    expect(resultApiFor('ord-res-105-01', demo)).toBe(demo);
  });

  it('converts between instants and datetime-local values', () => {
    expect(toLocalInput('')).toBe('');
    expect(fromLocalInput('')).toBeNull();
    expect(fromLocalInput(toLocalInput('2026-11-10T01:30:00Z'))).toBe('2026-11-10T01:30:00.000Z');
  });
});
