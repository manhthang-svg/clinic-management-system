package clinic.backend.enums;

/**
 * Security events kept in the append-only security audit trail (docs/security.md, Logging and Audit Restrictions).
 */
public enum SecurityAuditEventType {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGIN_THROTTLED,
    LOGOUT,
    REFRESH_TOKEN_REUSE_DETECTED,
    PASSWORD_CHANGED,
    ACCOUNT_CREATED,
    ACCOUNT_UPDATED,
    ROLE_CHANGED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    PASSWORD_RESET
}
