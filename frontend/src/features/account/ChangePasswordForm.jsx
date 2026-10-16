import { useState } from 'react';
import { ApiError } from '../../shared/api/api-error';
const MINIMUM_LENGTH = 8;
const MAXIMUM_LENGTH = 64;
const MAXIMUM_UTF8_BYTES = 72;
const ERROR_ID = 'change-password-error';
/** Mirrors the backend policy (AQ-03) for early feedback; the backend remains authoritative. */
function policyError(password) {
    const length = [...password].length;
    if (length < MINIMUM_LENGTH) {
        return `Mật khẩu mới phải có ít nhất ${MINIMUM_LENGTH} ký tự.`;
    }
    if (length > MAXIMUM_LENGTH || new TextEncoder().encode(password).length > MAXIMUM_UTF8_BYTES) {
        return `Mật khẩu mới quá dài (tối đa ${MAXIMUM_LENGTH} ký tự).`;
    }
    return null;
}
function clientError(currentPassword, newPassword, confirmation) {
    const policy = policyError(newPassword);
    if (policy) {
        return { message: policy, field: 'new' };
    }
    if (newPassword !== confirmation) {
        return { message: 'Xác nhận mật khẩu không khớp.', field: 'confirmation' };
    }
    if (newPassword === currentPassword) {
        return { message: 'Mật khẩu mới phải khác mật khẩu hiện tại.', field: 'new' };
    }
    return null;
}
function serverError(error) {
    if (error instanceof ApiError) {
        switch (error.errorResponse?.code) {
            case 'CURRENT_PASSWORD_INCORRECT':
                return { message: 'Mật khẩu hiện tại không đúng.', field: 'current' };
            case 'PASSWORD_POLICY_VIOLATION':
                return {
                    message: error.errorResponse.details?.newPassword === 'SAME_AS_CURRENT'
                        ? 'Mật khẩu mới phải khác mật khẩu hiện tại.'
                        : 'Mật khẩu mới không đáp ứng yêu cầu (8–64 ký tự).',
                    field: 'new',
                };
        }
    }
    return { message: 'Không thể đổi mật khẩu lúc này. Vui lòng thử lại.', field: null };
}
/** UC-3 Change Password. */
export function ChangePasswordForm({ onSubmit, onChanged }) {
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmation, setConfirmation] = useState('');
    const [error, setError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    async function handleSubmit(event) {
        event.preventDefault();
        const invalid = clientError(currentPassword, newPassword, confirmation);
        if (invalid) {
            setError(invalid);
            return;
        }
        setSubmitting(true);
        setError(null);
        try {
            await onSubmit(currentPassword, newPassword);
            onChanged();
        }
        catch (cause) {
            setError(serverError(cause));
        }
        finally {
            setSubmitting(false);
        }
    }
    /** Marks the field the current error refers to and links it to the message. */
    function invalidProps(field) {
        return error?.field === field ? { 'aria-invalid': true, 'aria-describedby': ERROR_ID } : {};
    }
    return (<section className="card password-form" aria-labelledby="change-password-title">
      <h2 id="change-password-title">Đổi mật khẩu</h2>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="current-password">Mật khẩu hiện tại</label>
          <input id="current-password" className="input" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} {...invalidProps('current')}/>
        </div>

        <div className="field">
          <label htmlFor="new-password">Mật khẩu mới</label>
          <input id="new-password" className="input" type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} {...invalidProps('new')}/>
        </div>

        <div className="field">
          <label htmlFor="confirm-password">Nhập lại mật khẩu mới</label>
          <input id="confirm-password" className="input" type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} {...invalidProps('confirmation')}/>
        </div>

        {error && (<p id={ERROR_ID} role="alert" className="banner banner--error">
            {error.message}
          </p>)}

        <div className="form-actions">
          <button type="submit" className="button button--primary button--block" disabled={submitting || currentPassword === ''}>
            {submitting ? 'Đang lưu…' : 'Đổi mật khẩu'}
          </button>
        </div>
      </form>
    </section>);
}
