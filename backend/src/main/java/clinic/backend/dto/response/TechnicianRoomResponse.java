package clinic.backend.dto.response;

import java.util.UUID;

public record TechnicianRoomResponse(
        UUID id,
        String code,
        String name,
        String location,
        String activeTech,
        boolean isAssigned
) {
    public TechnicianRoomResponse(UUID id, String code, String name, String location, String activeTech) {
        this(id, code, name, location, activeTech, false);
    }
}

