package clinic.backend.dto.response;

import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicineControlGroup;

/** One row of the medicine catalog list (UC-81). */
public record MedicineSummaryResponse(
        UUID id,
        String code,
        String name,
        String activeIngredient,
        String strength,
        String unit,
        String route,
        MedicineControlGroup controlGroup,
        ActivationStatus status,
        Long version) {
}
