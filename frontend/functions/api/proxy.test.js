import { afterEach, describe, expect, it, vi } from 'vitest';
import { backendUrl, forwardedHeaders, onRequest } from './[[path]].js';

const BACKEND = 'http://clinic.example.elasticbeanstalk.com';

describe('Cloudflare Pages /api proxy', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps the path and query and ignores a trailing slash on the backend origin', () => {
    expect(backendUrl('https://clinic.pages.dev/api/v1/audit-logs?page=2&size=20', `${BACKEND}/`))
      .toBe(`${BACKEND}/api/v1/audit-logs?page=2&size=20`);
  });

  it('drops Host and Cloudflare headers but keeps cookies, Authorization and Origin', () => {
    const request = new Request('https://clinic.pages.dev/api/v1/auth/refresh', {
      method: 'POST',
      headers: {
        Host: 'clinic.pages.dev',
        Cookie: 'refresh_token=abc',
        Authorization: 'Bearer t',
        Origin: 'https://clinic.pages.dev',
        'CF-Connecting-IP': '203.0.113.7',
        'CF-Ray': 'x',
      },
    });

    const headers = forwardedHeaders(request);

    expect(headers.get('host')).toBeNull();
    expect(headers.get('cf-ray')).toBeNull();
    expect(headers.get('cookie')).toBe('refresh_token=abc');
    expect(headers.get('authorization')).toBe('Bearer t');
    expect(headers.get('origin')).toBe('https://clinic.pages.dev');
    expect(headers.get('x-forwarded-for')).toBe('203.0.113.7');
    expect(headers.get('x-forwarded-proto')).toBe('https');
    expect(headers.get('x-forwarded-host')).toBe('clinic.pages.dev');
  });

  it('forwards the method and body and returns the backend response unchanged', async () => {
    const backendResponse = new Response('{"ok":true}', {
      status: 201,
      headers: { 'Set-Cookie': 'refresh_token=new; HttpOnly; Secure' },
    });
    const fetchMock = vi.fn().mockResolvedValue(backendResponse);
    vi.stubGlobal('fetch', fetchMock);
    const request = new Request('https://clinic.pages.dev/api/v1/patients', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"hoten":"A"}',
    });

    const response = await onRequest({ request, env: { BACKEND_ORIGIN: BACKEND } });

    expect(response).toBe(backendResponse);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${BACKEND}/api/v1/patients`);
    expect(init.method).toBe('POST');
    expect(init.redirect).toBe('manual');
    expect(await new Response(init.body).text()).toBe('{"hoten":"A"}');
  });

  it('sends no body for GET', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]'));
    vi.stubGlobal('fetch', fetchMock);

    await onRequest({ request: new Request('https://clinic.pages.dev/api/v1/me'), env: { BACKEND_ORIGIN: BACKEND } });

    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  });

  it('answers 502 when the backend origin is not configured', async () => {
    const response = await onRequest({ request: new Request('https://clinic.pages.dev/api/v1/me'), env: {} });

    expect(response.status).toBe(502);
    expect((await response.json()).code).toBe('BACKEND_NOT_CONFIGURED');
  });
});
