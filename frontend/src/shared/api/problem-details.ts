export interface ApiFieldError {
  readonly field: string
  readonly code: string
  readonly message: string
}

export interface ProblemDetails {
  readonly type?: string
  readonly title: string
  readonly status: number
  readonly detail?: string
  readonly instance?: string
  readonly code?: string
  readonly traceId?: string
  readonly errors?: readonly ApiFieldError[]
  readonly [extension: string]: unknown
}

export function isProblemDetails(value: unknown): value is ProblemDetails {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.title === 'string' &&
    typeof candidate.status === 'number'
  )
}
