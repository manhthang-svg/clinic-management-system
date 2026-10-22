package clinic.backend.enums;

/**
 * Visit statuses. A skipped visit keeps its status and is flagged with {@code is_skipped} (I1-D41).
 */
public enum VisitStatus {
    WAITING_FEE_PAYMENT,
    WAITING,
    IN_EXAMINATION,
    WAITING_PARACLINICAL,
    WAITING_CONCLUSION,
    COMPLETED,
    CANCELLED,
    ABANDONED
}
