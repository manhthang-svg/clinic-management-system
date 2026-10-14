import { useState, type FormEvent } from 'react'

import { ApiError } from '../../shared/api/api-error'

interface LoginPageProps {
  readonly onLogin: (username: string, password: string) => Promise<void>
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Tên đăng nhập hoặc mật khẩu không đúng.'
  }
  return 'Không thể đăng nhập lúc này. Vui lòng thử lại.'
}

/** UC-1 Login. */
export function LoginPage({ onLogin }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onLogin(username.trim(), password)
    } catch (cause) {
      setError(errorMessage(cause))
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <div className="login-panel">
        <p className="app-name">Clinic Management System</p>
        <div className="card">
          <h1>Đăng nhập</h1>
          <form className="form" onSubmit={handleSubmit} noValidate>
            <div className="field">
              <label htmlFor="login-username">Tên đăng nhập</label>
              <input
                id="login-username"
                className="input input--large"
                name="username"
                autoComplete="username"
                required
                maxLength={100}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="login-password">Mật khẩu</label>
              <input
                id="login-password"
                className="input input--large"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                maxLength={200}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>

            {error && (
              <p role="alert" className="banner banner--error">
                {error}
              </p>
            )}

            <div className="form-actions">
              <button
                type="submit"
                className="button button--primary button--large button--block"
                disabled={submitting || username.trim() === '' || password === ''}
              >
                {submitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </main>
  )
}
