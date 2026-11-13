package clinic.backend.enums;

/**
 * State of a request to sign a document version (docs/uc-59-e-prescription.md, states K01-K06). Every state but
 * {@link #PENDING} is final.
 */
public enum SigningRequestStatus {
    PENDING,
    SIGNED,
    FAILED,
    DECLINED,
    EXPIRED,
    CANCELLED
}
