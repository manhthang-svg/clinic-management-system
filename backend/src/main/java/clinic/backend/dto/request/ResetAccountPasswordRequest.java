package clinic.backend.dto.request;

import clinic.backend.enums.TemporaryPasswordMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResetAccountPasswordRequest(
        @NotNull TemporaryPasswordMode mode,
        String password,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
