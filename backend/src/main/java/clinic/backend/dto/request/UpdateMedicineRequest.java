package clinic.backend.dto.request;

import clinic.backend.enums.MedicineControlGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** UC-83 Update Medicine; the code cannot change (I1-D46). */
public record UpdateMedicineRequest(
        @NotBlank @Size(max = 20) String registrationNumber,
        @Size(max = 255) String insuranceCode,
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Size(min = 2, max = 500) String activeIngredient,
        @NotBlank @Size(max = 255) String strength,
        @NotBlank String route,
        @NotBlank @Size(max = 50) String unit,
        @NotNull MedicineControlGroup controlGroup,
        @NotNull @PositiveOrZero Long version) {
}
