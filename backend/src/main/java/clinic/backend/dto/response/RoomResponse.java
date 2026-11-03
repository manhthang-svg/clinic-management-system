package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;

/** A room; doctorName lists the active doctors assigned to it, or is null when none is reported. */
public record RoomResponse(
        UUID id,
        UUID departmentId,
        String departmentName,
        UUID serviceGroupId,
        String code,
        String name,
        String location,
        RoomType type,
        ActivationStatus status,
        String doctorName,
        Instant createdAt,
        Instant updatedAt
) {
}
