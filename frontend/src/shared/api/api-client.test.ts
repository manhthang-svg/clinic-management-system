import { describe, expect, it, vi } from 'vitest'

import { ApiClient } from './api-client'
import { ApiError } from './api-error'

describe('ApiClient', () => {
  it('returns JSON and obtains authentication through an abstraction', async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ ready: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const client = new ApiClient({
      baseUrl: '/api/v1',
      accessTokenProvider: () => 'access-token',
      fetchImplementation,
    })

    await expect(client.request<{ ready: boolean }>('/status')).resolves.toEqual({
      ready: true,
    })
    expect(fetchImplementation).toHaveBeenCalledWith(
      '/api/v1/status',
      expect.objectContaining({
        headers: expect.any(Headers),
        credentials: 'same-origin',
      }),
    )

    const request = fetchImplementation.mock.calls[0]?.[1]
    expect(new Headers(request?.headers).get('Authorization')).toBe(
      'Bearer access-token',
    )
  })

  it('calls the global fetch with the global object as receiver by default', async () => {
    // Browsers throw "Illegal invocation" when fetch is called on another receiver.
    vi.stubGlobal(
      'fetch',
      vi.fn(function (this: unknown) {
        if (this !== undefined && this !== globalThis) {
          throw new TypeError('Illegal invocation')
        }
        return Promise.resolve(new Response(null, { status: 204 }))
      }),
    )
    const client = new ApiClient({ baseUrl: '/api/v1' })

    await expect(client.request<void>('/status')).resolves.toBeUndefined()
    vi.unstubAllGlobals()
  })

  it('supports successful responses without a body', async () => {
    const client = new ApiClient({
      baseUrl: '/api/v1',
      fetchImplementation: vi
        .fn<typeof fetch>()
        .mockResolvedValue(new Response(null, { status: 204 })),
    })

    await expect(client.request<void>('/status')).resolves.toBeUndefined()
  })

  it('preserves standard Problem Details errors', async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          type: 'about:blank',
          title: 'Request validation failed',
          status: 400,
          detail: 'One or more request fields are invalid.',
          code: 'REQUEST_VALIDATION_FAILED',
          traceId: 'trace-id',
          errors: [],
        }),
        {
          status: 400,
          headers: { 'Content-Type': 'application/problem+json' },
        },
      ),
    )
    const client = new ApiClient({
      baseUrl: '/api/v1',
      fetchImplementation,
    })

    const error = await client.request('/resources').catch((cause: unknown) => cause)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      problem: {
        code: 'REQUEST_VALIDATION_FAILED',
        traceId: 'trace-id',
      },
    })
  })

  it('uses a safe fallback for malformed error responses', async () => {
    const client = new ApiClient({
      baseUrl: '/api/v1',
      fetchImplementation: vi.fn<typeof fetch>().mockResolvedValue(
        new Response('not-json', {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    })

    await expect(client.request('/resources')).rejects.toMatchObject({
      name: 'ApiError',
      message: 'The API request failed.',
      status: 500,
    })
  })
})
