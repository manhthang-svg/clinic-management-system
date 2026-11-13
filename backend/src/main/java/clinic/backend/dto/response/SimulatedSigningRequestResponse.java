package clinic.backend.dto.response;

import java.time.Instant;

/** Dev profile only: a request waiting in the simulated CA app for the Doctor's approval. */
public record SimulatedSigningRequestResponse(
        String id,
        String title,
        String documentSha256,
        Instant requestedAt,
        Instant expiresAt) {
}
