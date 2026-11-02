package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Complete read model for UC-43, scoped to the authenticated Doctor's assigned room. */
public record DoctorQueueResponse(
        UUID roomId,
        String roomCode,
        String roomName,
        String doctorName,
        boolean onDuty,
        Instant refreshedAt,
        long waitingCount,
        long inExaminationCount,
        long waitingConclusionCount,
        long skippedCount,
        PageResponse<DoctorQueueItemResponse> visits) {
}
