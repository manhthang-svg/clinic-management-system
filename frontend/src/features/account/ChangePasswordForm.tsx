import { useState, type FormEvent } from 'react'

import { ApiError } from '../../shared/api/api-error'

const MINIMUM_LENGTH = 8
const MAXIMUM_LENGTH = 64
const MAXIMUM_UTF8_BYTES = 72

interface ChangePasswordFormProps {
  readonly onSubmit: (currentPassword: string, newPassword: string) => Promise<void>
  readonly onChanged: () => void
}

/** Mirrors the backend policy (AQ-03) for early feedback; the backend remains authoritative. */
function policyError(password: string): string | null {
  const length = [...password].length
  if (length < MINIMUM_LENGTH) {
    return `Mật khẩu mới phải có ít nhất ${MINIMUM_LENGTH} ký tự.`
  }
  if (length > MAXIMUM_LENGTH || new TextEncoder().encode(password).length > MAXIMUM_UTF8_BYTES) {
    return `Mật khẩu mới quá dài (tối đa ${MAXIMUM_LENGTH} ký tự).`
  }
  return null
}

function serverError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.problem?.code) {
      case 'CURRENT_PASSWORD_INCORRECT':
        return 'Mật khẩu hiện tại không đúng.'
      case 'PASSWORD_POLICY_VIOLATION':
        return error.problem.errors?.[0]?.code === 'SAME_AS_CURRENT'
          ? 'Mật khẩu mới phải khác mật khẩu hiện tại.'
          : 'Mật khẩu mới không đáp ứng yêu cầu (8–64 ký tự).'
    }
  }
  return 'Không thể đổi mật khẩu lúc này. Vui lòng thử lại.'
}

/** UC-3 Change Password. */
export function ChangePasswordForm({ onSubmit, onChanged }: ChangePasswordFormProps) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const invalid =
      policyError(newPassword) ??
      (newPassword !== confirmation ? 'Xác nhận mật khẩu không khớp.' : null) ??
      (newPassword === currentPassword ? 'Mật khẩu mới phải khác mật khẩu hiện tại.' : null)
    if (invalid) {
      setError(invalid)
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await onSubmit(currentPassword, newPassword)
      onChanged()
    } catch (cause) {
      setError(serverError(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="password-form">
      <h2>Đổi mật khẩu</h2>
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="current-password">Mật khẩu hiện tại</label>
        <input
          id="current-password"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
        />

        <label htmlFor="new-password">Mật khẩu mới</label>
        <input
          id="new-password"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
        />

        <label htmlFor="confirm-password">Nhập lại mật khẩu mới</label>
        <input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
        />

        {error && <p role="alert">{error}</p>}

        <button type="submit" disabled={submitting || currentPassword === ''}>
          {submitting ? 'Đang lưu…' : 'Đổi mật khẩu'}
        </button>
      </form>
    </section>
  )
}
