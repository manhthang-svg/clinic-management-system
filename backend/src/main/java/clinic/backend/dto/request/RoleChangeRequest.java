package clinic.backend.dto.request;

import clinic.backend.enums.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC-09 role change. A Doctor or Technician role also carries the academic title and practice-license number
 * (I1-D47); other roles ignore them.
 */
public record RoleChangeRequest(
        @NotNull Role newRole,
        @NotNull Role expectedRole,
        @Size(max = 50) String academicTitle,
        @Size(min = 5, max = 50, message = "INVALID_FORMAT")
        @Pattern(regexp = "^[\\p{L}\\p{N}/-]+$", message = "INVALID_FORMAT")
        String practiceLicenseNo) {

    public RoleChangeRequest(Role newRole, Role expectedRole) {
        this(newRole, expectedRole, null, null);
    }
}
