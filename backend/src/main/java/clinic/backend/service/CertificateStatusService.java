package clinic.backend.service;

import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * Revocation status of a certificate (NĐ 23/2025 Điều 15, 16): the CA's status service (OCSP) for a subscriber
 * certificate, the national trust service (Trung tâm Chứng thực điện tử quốc gia) for a CA certificate. Validity
 * dates are checked by the caller. Throws {@code AppException(SIGNING_SERVICE_UNAVAILABLE)} when no service answers.
 */
public interface CertificateStatusService {

    /** The status of the certificate at the given time. */
    CertificateStatus status(X509Certificate certificate, Instant at);

    /**
     * @param responder who answered, kept as verification evidence
     * @param revokedAt the revocation time when {@link State#REVOKED}
     */
    record CertificateStatus(State state, String responder, Instant checkedAt, Instant revokedAt) {

        public boolean isGood() {
            return state == State.GOOD;
        }
    }

    enum State {
        GOOD,
        REVOKED,
        UNKNOWN
    }
}
