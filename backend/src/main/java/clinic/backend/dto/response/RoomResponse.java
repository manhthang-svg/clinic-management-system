
package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.RoomType;

public record RoomResponse(
        UUID id,
        UUID departmentId,
        UUID serviceGroupId,
        String code,
        String name,
        String location,
        RoomType type,
        ActivationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
