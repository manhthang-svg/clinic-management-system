import type { ErrorResponse } from './api-response'

interface ApiErrorOptions {
  readonly status?: number
  readonly errorResponse?: ErrorResponse
  readonly cause?: unknown
}

export class ApiError extends Error {
  public readonly status?: number
  public readonly errorResponse?: ErrorResponse

  public constructor(message: string, options: ApiErrorOptions = {}) {
    super(message, { cause: options.cause })
    this.name = 'ApiError'
    this.status = options.status
    this.errorResponse = options.errorResponse
  }
}
