package clinic.backend.enums;

/**
 * Action recorded in the business audit trail (CV 365 III.1.1.c, III.1.1.đ).
 */
public enum AuditLogAction {
    CREATE,
    UPDATE,
    CANCEL,
    DELETE,
    RESTORE,
    LOCK,
    UNLOCK,
    VIEW_RECORD,
    PRINT,
    EXPORT,
    SIGN
}
