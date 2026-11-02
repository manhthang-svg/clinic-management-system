package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

/** One visit shown on the assigned Doctor's UC-43 room lists. */
public record DoctorQueueItemResponse(
        UUID id,
        String patientName,
        String patientCode,
        LocalDate patientDob,
        String patientGender,
        int queueNumber,
        boolean isPriority,
        String priorityReason,
        VisitDoituongbnLoai doituongbnLoai,
        String mabhyt,
        String lydovaovien,
        Instant thoigianvaovien,
        VisitStatus status,
        boolean isSkipped,
        Instant skippedAt,
        String sourceRoomName,
        Instant transferredAt) {
}
