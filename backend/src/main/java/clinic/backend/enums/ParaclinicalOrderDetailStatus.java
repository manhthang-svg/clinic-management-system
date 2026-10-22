package clinic.backend.enums;

/**
 * Lifecycle of one ordered paraclinical service.
 */
public enum ParaclinicalOrderDetailStatus {
    PENDING_PAYMENT,
    WAITING,
    IN_PROGRESS,
    PENDING_RESULT,
    COMPLETED,
    CANCELLED,
    ABANDONED
}
