package clinic.backend.dto.request;

import clinic.backend.enums.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role newRole, @NotNull Role expectedRole) {
}
