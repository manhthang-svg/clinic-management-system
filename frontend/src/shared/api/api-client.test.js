import { describe, expect, it, vi } from 'vitest';
import { ApiClient } from './api-client';
import { ApiError } from './api-error';
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
            return Promise.resolve(new Response(null, { status: 204 }));
        }));
        const client = new ApiClient({ baseUrl: '/api/v1' });
        await expect(client.request('/status')).resolves.toBeUndefined();
        vi.unstubAllGlobals();
    });
    it('supports successful responses without a body', async () => {
        const client = new ApiClient({
            baseUrl: '/api/v1',
            fetchImplementation: vi
                .fn()
                .mockResolvedValue(new Response(null, { status: 204 })),
        });
        await expect(client.request('/status')).resolves.toBeUndefined();
    });
    it('preserves the standard error response', async () => {
        const fetchImplementation = vi.fn().mockResolvedValue(new Response(JSON.stringify({
            code: 'REQUEST_VALIDATION_FAILED',
            message: 'One or more request fields are invalid.',
            details: { username: 'must not be blank' },
            path: '/api/v1/resources',
            requestId: 'request-id',
            timestamp: '2026-10-15T07:00:00Z',
        }), {
            status: 400,
            headers: { 'Content-Type': 'application/json' },
        }));
        const client = new ApiClient({
            baseUrl: '/api/v1',
            fetchImplementation,
        });
        const error = await client.request('/resources').catch((cause) => cause);
        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({
            message: 'One or more request fields are invalid.',
            status: 400,
            errorResponse: {
                code: 'REQUEST_VALIDATION_FAILED',
                details: { username: 'must not be blank' },
                requestId: 'request-id',
            },
        });
    });
    it('uses a safe fallback for malformed error responses', async () => {
        const client = new ApiClient({
            baseUrl: '/api/v1',
            fetchImplementation: vi.fn().mockResolvedValue(new Response('not-json', {
                status: 500,
                headers: { 'Content-Type': 'application/json' },
            })),
        });
        await expect(client.request('/resources')).rejects.toMatchObject({
            name: 'ApiError',
            message: 'The API request failed.',
            status: 500,
        });
    });
});
