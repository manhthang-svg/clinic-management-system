package clinic.backend.dto.response;

import java.time.Instant;

/**
 * Dev profile only: a certificate issued by the simulated CA.
 *
 * @param registered the certificate is the account's active signing certificate in the clinic system
 */
public record SimulatedCertificateResponse(
        String serial,
        String username,
        String subject,
        Instant validTo,
        Instant revokedAt,
        boolean registered) {
}
