package clinic.backend.enums;

/**
 * Visit statuses from Report 3; Iteration 1 creates WAITING_FEE_PAYMENT and WAITING only.
 */
public enum VisitStatus {
    WAITING_FEE_PAYMENT,
    WAITING,
    SKIPPED,
    IN_EXAMINATION,
    WAITING_PARACLINICAL,
    WAITING_CONCLUSION,
    COMPLETED,
    CANCELLED,
    ABANDONED
}
