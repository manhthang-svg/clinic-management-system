package clinic.backend.dto.response;

import java.time.Instant;

/**
 * Dev profile only: a certificate issued by the simulated CA. The subject is not returned because it carries the
 * full identification number, which is shown masked outside the account edit form (BR-59-16).
 *
 * @param doctorName       the Doctor whose account holds the certificate's identification number; null if none
 * @param maskedCitizenId  that number masked, e.g. "0790•••••001"; null if no account holds it
 * @param registered       the certificate is the account's active signing certificate in the clinic system
 */
public record SimulatedCertificateResponse(
        String serial,
        String doctorName,
        String maskedCitizenId,
        Instant validTo,
        Instant revokedAt,
        boolean registered) {
}
