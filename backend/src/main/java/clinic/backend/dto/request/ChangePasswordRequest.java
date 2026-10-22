package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-3 input. The confirmation field stays client-side; the password policy is checked by the use case.
 */
public record ChangePasswordRequest(
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Current account password.")
        @NotBlank @Size(max = 200) String currentPassword,

        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "New password: 8–64 Unicode characters, at most 72 UTF-8 bytes, and different from the current password.")
        @NotBlank @Size(max = 200) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}
