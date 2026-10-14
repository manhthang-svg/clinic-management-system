import { ApiError } from '../../shared/api/api-error'
import type { AccountSummary, AuthApi, SessionResponse } from './auth-api'

export type SessionState =
  | { readonly status: 'restoring' }
  | { readonly status: 'anonymous' }
  | {
      readonly status: 'authenticated'
      readonly account: AccountSummary
      readonly passwordChangeRequired: boolean
    }

/** Runs a callback while holding a lock shared by all tabs of this origin. */
export type CrossTabLock = <T>(name: string, callback: () => Promise<T>) => Promise<T>

export interface SessionManagerOptions {
  readonly api: AuthApi
  readonly lock?: CrossTabLock
  readonly now?: () => number
  readonly setTimer?: (callback: () => void, delayMs: number) => unknown
  readonly clearTimer?: (handle: unknown) => void
}

const REFRESH_LOCK_NAME = 'clinic-session-refresh'
const REFRESH_MARGIN_MS = 60_000

/**
 * Web Locks serialize refreshes across tabs: the tabs share one refresh cookie, and a parallel
 * refresh would be treated by the backend as token reuse and end the session (docs/security.md).
 */
export const browserCrossTabLock: CrossTabLock = (name, callback) => {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request(name, callback)
  }
  return callback()
}

/**
 * Holds the access token in memory only and keeps the session alive by refreshing shortly
 * before the access token expires.
 */
export class SessionManager {
  private state: SessionState = { status: 'restoring' }
  private accessToken: string | null = null
  private refreshTimer: unknown = null
  private inFlightRefresh: Promise<boolean> | null = null
  private readonly listeners = new Set<() => void>()

  private readonly api: AuthApi
  private readonly lock: CrossTabLock
  private readonly now: () => number
  private readonly setTimer: (callback: () => void, delayMs: number) => unknown
  private readonly clearTimer: (handle: unknown) => void

  public constructor(options: SessionManagerOptions) {
    this.api = options.api
    this.lock = options.lock ?? browserCrossTabLock
    this.now = options.now ?? Date.now
    this.setTimer =
      options.setTimer ?? ((callback, delayMs) => window.setTimeout(callback, delayMs))
    this.clearTimer =
      options.clearTimer ?? ((handle) => window.clearTimeout(handle as number))
  }

  public readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  public readonly getState = (): SessionState => this.state

  public readonly getAccessToken = (): string | null => this.accessToken

  /** Throws {@link ApiError} when the credentials are rejected or the API is unreachable. */
  public async login(username: string, password: string): Promise<void> {
    this.accept(await this.api.login(username, password))
  }

  /** Restores a session from the refresh cookie after a page load. */
  public async restore(): Promise<void> {
    if (!(await this.refresh())) {
      this.setState({ status: 'anonymous' })
    }
  }

  /**
   * Renews the access token. Concurrent calls in this tab share one request.
   *
   * @returns whether the session is still valid
   */
  public refresh(): Promise<boolean> {
    this.inFlightRefresh ??= this.lock(REFRESH_LOCK_NAME, () => this.api.refresh())
      .then((session) => {
        this.accept(session)
        return true
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) {
          this.clear()
          return false
        }
        throw error
      })
      .finally(() => {
        this.inFlightRefresh = null
      })
    return this.inFlightRefresh
  }

  /** Forgets the session locally. */
  public clear(): void {
    this.cancelTimer()
    this.accessToken = null
    this.setState({ status: 'anonymous' })
  }

  private accept(session: SessionResponse): void {
    this.accessToken = session.accessToken
    this.setState({
      status: 'authenticated',
      account: session.account,
      passwordChangeRequired: session.passwordChangeRequired,
    })
    this.scheduleRefresh(Date.parse(session.expiresAt))
  }

  private scheduleRefresh(expiresAtMs: number): void {
    this.cancelTimer()
    const delay = Math.max(expiresAtMs - this.now() - REFRESH_MARGIN_MS, 0)
    this.refreshTimer = this.setTimer(() => {
      this.refresh().catch(() => {
        // Network failure: keep the current token; the next API call will surface the problem.
      })
    }, delay)
  }

  private cancelTimer(): void {
    if (this.refreshTimer !== null) {
      this.clearTimer(this.refreshTimer)
      this.refreshTimer = null
    }
  }

  private setState(state: SessionState): void {
    this.state = state
    this.listeners.forEach((listener) => listener())
  }
}
