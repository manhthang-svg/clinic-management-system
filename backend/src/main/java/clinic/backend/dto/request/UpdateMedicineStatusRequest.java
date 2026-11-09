package clinic.backend.dto.request;

import clinic.backend.enums.ActivationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** UC-84 Activate/Deactivate Medicine. */
public record UpdateMedicineStatusRequest(
        @NotNull ActivationStatus status,
        @NotNull @PositiveOrZero Long version) {
}
