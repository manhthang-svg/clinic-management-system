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
        // UC-45/UC-46: a skipped visit the Doctor can accept again; false for visits that are not skipped.
        boolean canAcceptAgain,
        String sourceRoomName,
        Instant transferredAt,
        String note,
        // BR-19, BR-20: the visit is assigned to the signed-in Doctor, who may finish it after the shift ends.
        boolean assignedToMe) {
}
