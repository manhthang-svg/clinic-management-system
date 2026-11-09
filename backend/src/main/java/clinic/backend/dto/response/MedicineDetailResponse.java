package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicineControlGroup;

/** A medicine with every field (UC-81 detail, and the result of UC-83 and UC-84). */
public record MedicineDetailResponse(
        UUID id,
        String code,
        String registrationNumber,
        String insuranceCode,
        String name,
        String activeIngredient,
        String strength,
        String route,
        String unit,
        MedicineControlGroup controlGroup,
        ActivationStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
