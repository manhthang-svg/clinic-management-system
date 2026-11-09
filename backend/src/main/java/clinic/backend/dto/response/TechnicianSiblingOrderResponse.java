package clinic.backend.dto.response;

import java.util.UUID;

public record TechnicianSiblingOrderResponse(
        UUID id,
        String serviceName,
        String roomName,
        boolean isCurrentRoom,
        String status
) {}

