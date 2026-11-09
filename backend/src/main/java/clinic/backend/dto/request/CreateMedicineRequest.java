package clinic.backend.dto.request;

import clinic.backend.enums.MedicineControlGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** UC-82 Create Medicine; the limits follow BR-79. The code is upper-cased by the server. */
public record CreateMedicineRequest(
        @NotBlank @Pattern(regexp = "\s*[A-Za-z0-9-]{1,50}\s*") String code,
        @NotBlank @Size(max = 20) String registrationNumber,
        @Size(max = 255) String insuranceCode,
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Size(min = 2, max = 500) String activeIngredient,
        @NotBlank @Size(max = 255) String strength,
        @NotBlank String route,
        @NotBlank @Size(max = 50) String unit,
        @NotNull MedicineControlGroup controlGroup) {
}
