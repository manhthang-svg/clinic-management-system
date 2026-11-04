package clinic.backend.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * UC-42 queue counts of one active examination room. Skipped visits are counted only in {@code skippedCount}.
 * A room without an active assigned doctor has {@code hasDoctor = false} and cannot receive patients (AF-01).
 */
public record RoomQueueStatusResponse(
        UUID roomId,
        String roomCode,
        String roomName,
        String location,
        UUID departmentId,
        String departmentName,
        List<String> doctorNames,
        boolean hasDoctor,
        long waitingPaymentCount,
        long waitingCount,
        long inExaminationCount,
        long waitingParaclinicalCount,
        long waitingConclusionCount,
        long skippedCount
) {
}
