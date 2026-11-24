// Cloudflare Pages Function: forwards every /api/* request to the Spring Boot backend, so the browser keeps one
// HTTPS origin for the app and the API (refresh-token cookie and PayOS return pages work as on localhost).
// Set BACKEND_ORIGIN in the Pages project settings, e.g. http://<env>.elasticbeanstalk.com (no trailing slash).

// Hop-by-hop and Cloudflare headers that must not be forwarded to the backend.
const DROPPED_REQUEST_HEADERS = ['host', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor', 'x-real-ip'];

export function backendUrl(requestUrl, backendOrigin) {
  const incoming = new URL(requestUrl);
  return `${backendOrigin.replace(/\/+$/, '')}${incoming.pathname}${incoming.search}`;
}

export function forwardedHeaders(request) {
  const headers = new Headers(request.headers);
  DROPPED_REQUEST_HEADERS.forEach((name) => headers.delete(name));
  const client = request.headers.get('cf-connecting-ip');
  if (client) headers.set('X-Forwarded-For', client);
  headers.set('X-Forwarded-Proto', 'https');
  headers.set('X-Forwarded-Host', new URL(request.url).host);
  return headers;
}

export async function onRequest({ request, env }) {
  if (!env.BACKEND_ORIGIN) {
    return Response.json(
      { code: 'BACKEND_NOT_CONFIGURED', message: 'BACKEND_ORIGIN is not set for this Pages project.' },
      { status: 502 },
    );
  }
  const hasBody = request.method !== 'GET' && request.method !== 'HEAD';
  // redirect: 'manual' passes backend redirects and Set-Cookie headers back to the browser unchanged.
  return fetch(backendUrl(request.url, env.BACKEND_ORIGIN), {
    method: request.method,
    headers: forwardedHeaders(request),
    body: hasBody ? request.body : undefined,
    redirect: 'manual',
  });
}
