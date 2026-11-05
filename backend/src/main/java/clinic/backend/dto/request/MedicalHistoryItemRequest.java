package clinic.backend.dto.request;

import clinic.backend.enums.MedicalHistoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC-39 history or drug-allergy item edited during UC-47 (BR-23).
 * ASSUMPTION: the length limits follow BR-99 free-text limits; BR-93 is not in the SRS catalogue.
 */
public record MedicalHistoryItemRequest(
        @NotNull MedicalHistoryType type,
        @NotBlank @Size(min = 2, max = 2000) String content,
        @Size(max = 500) String notes,
        Long version) {
}
