package clinic.backend.enums;

/**
 * Receipt status (BR-37); fee-exempt receipts are PAID, there is no EXEMPT status (I1-D25).
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    CANCELLED
}
