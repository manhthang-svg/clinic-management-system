import { describe, expect, it, vi } from 'vitest';
import { ApiClient, fileNameOf } from './apiClient';
import { ApiError } from './apiError';

describe('ApiClient.download (UC-33)', () => {
  it('returns the file with the name from Content-Disposition and sends the token', async () => {
    const fetchImplementation = vi.fn().mockResolvedValue(new Response('xlsx-bytes', {
      status: 200,
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': 'attachment; filename="bao-cao-thong-ke_2026-11-01_2026-11-12.xlsx"',
      },
    }));
    const client = new ApiClient({ baseUrl: '/api/v1', accessTokenProvider: () => 'access-token', fetchImplementation });

    const file = await client.download('/statistics/export?reports=VISITS');

    expect(file.fileName).toBe('bao-cao-thong-ke_2026-11-01_2026-11-12.xlsx');
    expect(await file.blob.text()).toBe('xlsx-bytes');
    expect(fetchImplementation.mock.calls[0]?.[0]).toBe('/api/v1/statistics/export?reports=VISITS');
    const request = fetchImplementation.mock.calls[0]?.[1];
    expect(new Headers(request?.headers).get('Authorization')).toBe('Bearer access-token');
  });

  it('turns an error response into an ApiError and reports an expired session', async () => {
    const onUnauthorized = vi.fn();
    const fetchImplementation = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'FORBIDDEN', message: 'Forbidden', details: {}, requestId: 'r-1',
      }), { status: 403, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response('', { status: 401 }));
    const client = new ApiClient({ baseUrl: '/api/v1', fetchImplementation, onUnauthorized });

    const forbidden = await client.download('/statistics/export').catch((error) => error);
    expect(forbidden).toBeInstanceOf(ApiError);
    expect(forbidden.status).toBe(403);
    expect(forbidden.errorResponse?.code).toBe('FORBIDDEN');

    await expect(client.download('/statistics/export')).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('reads plain and encoded file names', () => {
    expect(fileNameOf('attachment; filename="a.xlsx"')).toBe('a.xlsx');
    expect(fileNameOf("attachment; filename*=UTF-8''b%C3%A1o-c%C3%A1o.xlsx")).toBe('báo-cáo.xlsx');
    expect(fileNameOf(null)).toBeNull();
  });
});
