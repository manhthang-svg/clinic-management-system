package clinic.backend.dto.request;

import clinic.backend.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LockAccountRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotNull AccountStatus expectedStatus,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
