/** Envelope of every successful backend response that has a body. */
export interface ApiResponse<T> {
  readonly success: true
  readonly message?: string
  readonly data: T
  readonly timestamp?: string
}

/**
 * Body of every backend error response. `details` maps a field to a message for request validation, and
 * to a stable reason code for business-rule violations (for example `newPassword: 'SAME_AS_CURRENT'`).
 */
export interface ErrorResponse {
  readonly code: string
  readonly message: string
  readonly details?: Readonly<Record<string, string>>
  readonly path?: string
  readonly requestId?: string
  readonly timestamp?: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return isRecord(value) && value.success === true && 'data' in value
}

export function isErrorResponse(value: unknown): value is ErrorResponse {
  return (
    isRecord(value) &&
    typeof value.code === 'string' &&
    typeof value.message === 'string'
  )
}
