const DEFAULT_API_BASE_URL = '/api/v1';
function removeTrailingSlashes(value) {
    return value.replace(/\/+$/, '');
}
export function resolveApiBaseUrl(configuredValue) {
    const candidate = configuredValue?.trim() || DEFAULT_API_BASE_URL;
    if (candidate.startsWith('/')) {
        if (candidate.startsWith('//')) {
            throw new Error('VITE_API_BASE_URL must not be protocol-relative.');
        }
        return removeTrailingSlashes(candidate) || '/';
    }
    let url;
    try {
        url = new URL(candidate);
    }
    catch {
        throw new Error('VITE_API_BASE_URL must be a root-relative path or an HTTP(S) URL.');
    }
    if (!['http:', 'https:'].includes(url.protocol)) {
        throw new Error('VITE_API_BASE_URL must use HTTP or HTTPS.');
    }
    if (url.username || url.password || url.search || url.hash) {
        throw new Error('VITE_API_BASE_URL must not contain credentials, a query, or a fragment.');
    }
    return removeTrailingSlashes(url.toString());
}
export const environment = Object.freeze({
    apiBaseUrl: resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
});
