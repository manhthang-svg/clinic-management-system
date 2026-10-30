package clinic.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignRoomRequest(@NotNull UUID roomId) {
}
