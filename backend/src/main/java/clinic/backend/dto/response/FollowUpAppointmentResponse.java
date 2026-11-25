package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record FollowUpAppointmentResponse(
        UUID id,
        LocalDate appointmentDate,
        String doctorName,
        UUID suggestedRoomId,
        String suggestedRoomName,
        String status,
        Long version,
        /** BR-124: the last day the appointment can be linked to a visit. */
        LocalDate validUntil) {
}
