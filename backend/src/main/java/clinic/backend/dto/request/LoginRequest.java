package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login form input. Only transport limits are checked here; credential checks belong to the use case.
 */
public record LoginRequest(
        @Schema(description = "Case-insensitive staff username; surrounding whitespace is ignored.",
                example = "bs.tranvanan")
        @NotBlank @Size(max = 100) String username,

        @Schema(description = "Staff password.", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                example = "temporary-password")
        @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=redacted]";
    }
}
