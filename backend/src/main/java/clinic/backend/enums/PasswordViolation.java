package clinic.backend.enums;

/**
 * Why a new password was refused (AQ-03, UC-3). Sent to clients as a stable reason code.
 */
public enum PasswordViolation {
    TOO_SHORT,
    TOO_LONG,
    SAME_AS_CURRENT
}
