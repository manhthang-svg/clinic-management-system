import type { ProblemDetails } from './problem-details'

interface ApiErrorOptions {
  readonly status?: number
  readonly problem?: ProblemDetails
  readonly cause?: unknown
}

export class ApiError extends Error {
  public readonly status?: number
  public readonly problem?: ProblemDetails

  public constructor(message: string, options: ApiErrorOptions = {}) {
    super(message, { cause: options.cause })
    this.name = 'ApiError'
    this.status = options.status
    this.problem = options.problem
  }
}
