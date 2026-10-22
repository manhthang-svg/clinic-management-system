package clinic.backend.enums;

/**
 * Receipt status (BR-37). A receipt whose patient total is 0 is FEE_EXEMPT (I1-D41).
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FEE_EXEMPT,
    CANCELLED
}
