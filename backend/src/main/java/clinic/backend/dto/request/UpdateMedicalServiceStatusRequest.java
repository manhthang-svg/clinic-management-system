package clinic.backend.dto.request;

import clinic.backend.enums.ActivationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMedicalServiceStatusRequest(
        @NotNull ActivationStatus status,
        @NotNull @PositiveOrZero Long version) {
}
