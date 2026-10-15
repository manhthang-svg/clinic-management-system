import { ApiError } from './api-error'
import { isApiResponse, isErrorResponse, type ErrorResponse } from './api-response'

export type AccessTokenProvider = () => Promise<string | null> | string | null

export interface ApiClientConfiguration {
  readonly baseUrl: string
  readonly accessTokenProvider?: AccessTokenProvider
  readonly credentials?: RequestCredentials
  readonly fetchImplementation?: typeof fetch
}

export interface ApiRequestOptions<TBody = unknown> {
  readonly method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  readonly body?: TBody
  readonly headers?: HeadersInit
  readonly signal?: AbortSignal
  readonly credentials?: RequestCredentials
}

function assertRelativeApiPath(path: string): void {
  if (!path.startsWith('/') || path.startsWith('//')) {
    throw new Error('API request paths must be root-relative.')
  }
}

async function parseErrorResponse(response: Response): Promise<ErrorResponse | undefined> {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('json')) {
    return undefined
  }

  try {
    const body: unknown = await response.json()
    return isErrorResponse(body) ? body : undefined
  } catch {
    return undefined
  }
}

export class ApiClient {
  private readonly baseUrl: string
  private readonly accessTokenProvider?: AccessTokenProvider
  private readonly credentials: RequestCredentials
  private readonly fetchImplementation: typeof fetch

  public constructor(configuration: ApiClientConfiguration) {
    this.baseUrl = configuration.baseUrl.replace(/\/+$/, '')
    this.accessTokenProvider = configuration.accessTokenProvider
    this.credentials = configuration.credentials ?? 'same-origin'
    // Browsers reject fetch called with a receiver other than the global object ("Illegal invocation").
    this.fetchImplementation =
      configuration.fetchImplementation ?? ((input, init) => globalThis.fetch(input, init))
  }

  public async request<TResponse, TBody = unknown>(
    path: string,
    options: ApiRequestOptions<TBody> = {},
  ): Promise<TResponse> {
    assertRelativeApiPath(path)

    const headers = new Headers(options.headers)
    headers.set('Accept', 'application/json')

    let body: string | undefined
    if (options.body !== undefined) {
      headers.set('Content-Type', 'application/json')
      body = JSON.stringify(options.body)
    }

    const accessToken = await this.accessTokenProvider?.()
    if (accessToken && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }

    let response: Response
    try {
      response = await this.fetchImplementation(`${this.baseUrl}${path}`, {
        method: options.method ?? 'GET',
        headers,
        body,
        signal: options.signal,
        credentials: options.credentials ?? this.credentials,
      })
    } catch (cause) {
      throw new ApiError('Unable to reach the API.', { cause })
    }

    if (!response.ok) {
      const errorResponse = await parseErrorResponse(response)
      throw new ApiError(errorResponse?.message || 'The API request failed.', {
        status: response.status,
        errorResponse,
      })
    }

    if (response.status === 204) {
      return undefined as TResponse
    }

    const contentType = response.headers.get('content-type') ?? ''
    if (!contentType.includes('json')) {
      throw new ApiError('The API returned an unsupported response format.', {
        status: response.status,
      })
    }

    let responseBody: unknown
    try {
      responseBody = await response.json()
    } catch (cause) {
      throw new ApiError('The API returned an invalid JSON response.', {
        status: response.status,
        cause,
      })
    }
    // Successful bodies arrive in the backend's ApiResponse envelope; callers receive only its data.
    return (isApiResponse(responseBody) ? responseBody.data : responseBody) as TResponse
  }
}
