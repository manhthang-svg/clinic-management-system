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

  it('uploads and deletes result images, and saves their captions with the draft', async () => {
    const client = clientReturning({ id: ID, result: { status: 'DRAFT', files: [] } });
    const api = createResultApi(client);
    expect(api.supportsFiles).toBe(true);

    const image = new File(['png'], 'gan.png', { type: 'image/png' });
    await api.uploadImage(ID, image, 'Gan');
    const [uploadPath, uploadOptions] = client.request.mock.calls[0];
    expect(uploadPath).toBe(`/paraclinical-results/${ID}/images`);
    expect(uploadOptions.method).toBe('POST');
    expect(uploadOptions.body).toBeInstanceOf(FormData);
    expect(uploadOptions.body.get('file')).toBe(image);
    expect(uploadOptions.body.get('caption')).toBe('Gan');

    await api.deleteImage(ID, 'img-1');
    expect(client.request.mock.calls[1]).toEqual([`/paraclinical-results/${ID}/images/img-1`, { method: 'DELETE' }]);

    await api.saveDraft(ID, { conclusion: '', files: [{ id: 'img-1', caption: 'Gan phải' }] });
    expect(client.request.mock.calls[2][1].body.imageCaptions).toEqual({ 'img-1': 'Gan phải' });
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
