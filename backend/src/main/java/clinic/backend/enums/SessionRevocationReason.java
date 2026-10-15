package clinic.backend.enums;

/**
 * Why a login session (refresh-token family) was revoked (docs/security.md, revocation events).
 */
public enum SessionRevocationReason {
    LOGOUT,
    REUSE_DETECTED,
    ACCOUNT_LOCKED,
    ROLE_CHANGED,
    PASSWORD_RESET,
    PASSWORD_CHANGED
}
