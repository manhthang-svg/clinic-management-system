package clinic.backend.service;

import java.time.Instant;

/**
 * The Cổng kết nối dịch vụ chứng thực chữ ký số công cộng (NĐ 23/2025 Điều 44 khoản 2) in front of the Doctor's
 * remote-signing CA (Điều 26 khoản 1 điểm c). The CA keeps the private key (Điều 37 khoản 4); the clinic sends only
 * the document hash and receives the signature. Every call throws {@code AppException(SIGNING_SERVICE_UNAVAILABLE)}
 * when the service does not answer (UC-90 EF-15).
 */
public interface RemoteSigningGateway {

    /** Identifies the provider in {@code staff_signing_certificates.provider_code}. */
    String providerCode();

    /**
     * BR-59-10 step 3: asks the CA to sign the SHA-256 of a document with the given certificate; the signer approves
     * it in the CA's app before {@code expiresAt}. Returns the provider's transaction id.
     */
    String requestSignature(
            String providerUserId, String certificateSerial, byte[] documentSha256, String documentTitle,
            Instant expiresAt);

    /** The current state of a transaction; the signed data is present only when {@link Status#SIGNED}. */
    SigningResult result(String transactionId);

    /** UC-90 AF-03: withdraws a pending transaction; no effect on a finished one. */
    void cancel(String transactionId);

    /**
     * @param signedData detached CMS SignedData (DER) with the signer's certificate chain, when signed
     */
    record SigningResult(Status status, byte[] signedData) {

        public static SigningResult of(Status status) {
            return new SigningResult(status, null);
        }
    }

    enum Status {
        PENDING,
        SIGNED,
        DECLINED,
        EXPIRED
    }
}
