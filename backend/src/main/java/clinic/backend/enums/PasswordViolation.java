package clinic.backend.enums;

/**
 * Why a new password was refused (docs/security.md, UC-03). Sent to clients as a stable reason code.
 */
public enum PasswordViolation {
    TOO_SHORT,
    TOO_LONG,
    SAME_AS_CURRENT
}
