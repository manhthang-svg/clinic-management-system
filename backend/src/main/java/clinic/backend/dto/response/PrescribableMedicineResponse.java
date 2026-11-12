package clinic.backend.dto.response;

import java.util.UUID;

/** UC-59: an active ordinary medicine of the catalog the Doctor can add to a prescription. */
public record PrescribableMedicineResponse(
        UUID id,
        String code,
        String name,
        String activeIngredient,
        String strength,
        String route,
        String unit
) {
}
