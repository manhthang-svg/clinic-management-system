package clinic.backend.dto.response;

import java.util.UUID;

/**
 * UC-42 queue counts of one active examination room. Skipped visits are counted only in {@code skippedCount}.
 */
public record RoomQueueStatusResponse(
        UUID roomId,
        String roomCode,
        String roomName,
        String location,
        long waitingCount,
        long inExaminationCount,
        long waitingParaclinicalCount,
        long waitingConclusionCount,
        long skippedCount
) {
}
