import { ApiError } from './apiError';
import { isApiResponse, isErrorResponse } from './apiResponse';

function assertRelativeApiPath(path) {
    if (!path.startsWith('/') || path.startsWith('//')) {
        throw new Error('API request paths must be root-relative.');
    }
}

async function parseErrorResponse(response) {
    const contentType = response.headers.get('content-type') ?? '';
    if (!contentType.includes('json')) {
        return undefined;
    }
    try {
        const body = await response.json();
        return isErrorResponse(body) ? body : undefined;
    }
    catch {
        return undefined;
    }
}

export class ApiClient {
    baseUrl;
    accessTokenProvider;
    credentials;
    fetchImplementation;
    onUnauthorized;
    constructor(configuration) {
        this.baseUrl = configuration.baseUrl.replace(/\/+$/, '');
        this.accessTokenProvider = configuration.accessTokenProvider;
        this.credentials = configuration.credentials ?? 'same-origin';
        this.fetchImplementation =
            configuration.fetchImplementation ?? ((input, init) => globalThis.fetch(input, init));
        this.onUnauthorized = configuration.onUnauthorized ?? null;
    }
    async request(path, options = {}) {
        assertRelativeApiPath(path);
        const headers = new Headers(options.headers);
        headers.set('Accept', 'application/json');
        let body;
        if (options.body !== undefined) {
            headers.set('Content-Type', 'application/json');
            body = JSON.stringify(options.body);
        }
        const accessToken = await this.accessTokenProvider?.();
        if (accessToken && !headers.has('Authorization')) {
            headers.set('Authorization', `Bearer ${accessToken}`);
        }
        let response;
        try {
            response = await this.fetchImplementation(`${this.baseUrl}${path}`, {
                method: options.method ?? 'GET',
                headers,
                body,
                signal: options.signal,
                credentials: options.credentials ?? this.credentials,
            });
        }
        catch (cause) {
            // A cancelled request is not a network failure; callers ignore AbortError.
            if (cause?.name === 'AbortError') {
                throw cause;
            }
            throw new ApiError('Unable to reach the API.', { cause });
        }
        if (!response.ok) {
            if (response.status === 401 && this.onUnauthorized) {
                this.onUnauthorized();
            }
            const errorResponse = await parseErrorResponse(response);
            throw new ApiError(errorResponse?.message || 'The API request failed.', {
                status: response.status,
                errorResponse,
            });
        }
        if (response.status === 204) {
            return undefined;
        }
        const contentType = response.headers.get('content-type') ?? '';
        if (!contentType.includes('json')) {
            throw new ApiError('The API returned an unsupported response format.', {
                status: response.status,
            });
        }
        let responseBody;
        try {
            responseBody = await response.json();
        }
        catch (cause) {
            throw new ApiError('The API returned an invalid JSON response.', {
                status: response.status,
                cause,
            });
        }
        return (isApiResponse(responseBody) ? responseBody.data : responseBody);
    }

    /**
     * Downloads a file (e.g. UC-33 Excel export): same authentication and error handling as {@link request}, but
     * returns the body as a Blob with the file name taken from Content-Disposition.
     */
    async download(path, options = {}) {
        assertRelativeApiPath(path);
        const headers = new Headers(options.headers);
        headers.set('Accept', '*/*');
        const accessToken = await this.accessTokenProvider?.();
        if (accessToken && !headers.has('Authorization')) {
            headers.set('Authorization', `Bearer ${accessToken}`);
        }
        let response;
        try {
            response = await this.fetchImplementation(`${this.baseUrl}${path}`, {
                method: options.method ?? 'GET',
                headers,
                signal: options.signal,
                credentials: options.credentials ?? this.credentials,
            });
        }
        catch (cause) {
            if (cause?.name === 'AbortError') {
                throw cause;
            }
            throw new ApiError('Unable to reach the API.', { cause });
        }
        if (!response.ok) {
            if (response.status === 401 && this.onUnauthorized) {
                this.onUnauthorized();
            }
            const errorResponse = await parseErrorResponse(response);
            throw new ApiError(errorResponse?.message || 'The API request failed.', {
                status: response.status,
                errorResponse,
            });
        }
        return {
            blob: await response.blob(),
            fileName: fileNameOf(response.headers.get('content-disposition')),
        };
    }
}

/** The file name of a Content-Disposition header (RFC 6266 filename* first), or null. */
export function fileNameOf(contentDisposition) {
    if (!contentDisposition) return null;
    const encoded = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(contentDisposition);
    if (encoded) {
        try {
            return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ''));
        }
        catch {
            // fall back to the plain filename
        }
    }
    const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(contentDisposition);
    return plain ? plain[1].trim() : null;
}

export default ApiClient;
