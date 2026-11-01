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
        Long version) {
}
