package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Result of a room transfer (UC-48): where the patient now waits and with which queue number (MSG24). */
public record VisitTransferResponse(
        UUID visitId,
        UUID toRoomId,
        String toRoomCode,
        String toRoomName,
        String toRoomLocation,
        int newQueueNumber,
        Instant transferredAt) {
}
