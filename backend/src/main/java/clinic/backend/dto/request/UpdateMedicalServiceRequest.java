package clinic.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateMedicalServiceRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @Size(max = 500) String description,
        @NotNull @PositiveOrZero Long version) {
}
