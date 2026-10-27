package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateMedicalServiceRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull @PositiveOrZero Long version) {
}
