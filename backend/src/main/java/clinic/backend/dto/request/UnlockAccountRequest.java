package clinic.backend.dto.request;

import clinic.backend.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UnlockAccountRequest(
        @NotNull AccountStatus expectedStatus,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
