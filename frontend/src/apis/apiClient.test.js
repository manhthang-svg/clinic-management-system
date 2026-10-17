import { describe, expect, it, vi } from 'vitest';
import { ApiClient } from './apiClient';
import { ApiError } from './apiError';

describe('ApiClient', () => {
    it('unwraps the ApiResponse envelope and obtains authentication through an abstraction', async () => {
        const fetchImplementation = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, message: 'Operation successful', data: { ready: true } }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
        }));
        const client = new ApiClient({
            baseUrl: '/api/v1',
            accessTokenProvider: () => 'access-token',
            fetchImplementation,
        });
        await expect(client.request('/status')).resolves.toEqual({
            ready: true,
        });
        expect(fetchImplementation).toHaveBeenCalledWith('/api/v1/status', expect.objectContaining({
            headers: expect.any(Headers),
            credentials: 'same-origin',
        }));
        const request = fetchImplementation.mock.calls[0]?.[1];
        expect(new Headers(request?.headers).get('Authorization')).toBe('Bearer access-token');
    });
    it('calls the global fetch with the global object as receiver by default', async () => {
        // Browsers throw "Illegal invocation" when fetch is called on another receiver.
        vi.stubGlobal('fetch', vi.fn(function () {
            if (this !== undefined && this !== globalThis) {
                throw new TypeError('Illegal invocation');
            }
            return Promise.resolve(new Response(JSON.stringify({ success: true, message: 'ok', data: null }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            }));
        }));
        const client = new ApiClient({ baseUrl: '/api/v1' });
        await expect(client.request('/status')).resolves.toBeNull();
    });
    it('translates an unsuccessful status into an ApiError with details', async () => {
        const client = new ApiClient({
            baseUrl: '/api/v1',
            fetchImplementation: vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: 'No access' }), {
                status: 401,
                headers: { 'Content-Type': 'application/json' },
            })),
        });
        await expect(client.request('/me')).rejects.toMatchObject({
            name: 'ApiError',
            status: 401,
            message: 'No access',
            errorResponse: { code: 'UNAUTHORIZED' },
        });
    });
    it('rejects with a wrapped cause on network failure', async () => {
        const client = new ApiClient({
            baseUrl: '/api/v1',
            fetchImplementation: vi.fn().mockRejectedValue(new TypeError('Failed to fetch')),
        });
        await expect(client.request('/me')).rejects.toMatchObject({
            name: 'ApiError',
            message: 'Unable to reach the API.',
        });
    });
    it('returns undefined for 204 No Content', async () => {
        const client = new ApiClient({
            baseUrl: '/api/v1',
            fetchImplementation: vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
        });
        await expect(client.request('/status')).resolves.toBeUndefined();
    });
});
