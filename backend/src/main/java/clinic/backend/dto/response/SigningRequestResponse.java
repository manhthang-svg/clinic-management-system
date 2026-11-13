package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-90: a request to sign the prescription with the Doctor's digital signature (states K01-K06).
 *
 * @param status        PENDING, SIGNED, FAILED, DECLINED, EXPIRED or CANCELLED
 * @param documentVersion the prescription version being signed; copying the diagnoses (BR-59-08) creates it, so a
 *                      new request after a decline or expiry names this version
 * @param expiresAt     the end of the approval window (MSG120)
 * @param failureReason why the signature was refused (FAILED, MSG124)
 * @param prescription  the prescription after the send, when SIGNED; the send result is its {@code lastSubmission}
 */
public record SigningRequestResponse(
        UUID id,
        String status,
        long documentVersion,
        Instant expiresAt,
        String failureReason,
        PrescriptionResponse prescription) {
}
