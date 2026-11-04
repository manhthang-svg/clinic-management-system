package clinic.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LabParameterSelectionRequest(
        @NotNull UUID parameterId,
        @NotNull @Positive Integer displayOrder) {
}
