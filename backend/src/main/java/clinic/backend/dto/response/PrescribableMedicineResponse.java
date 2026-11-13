package clinic.backend.dto.response;

import java.util.UUID;

/**
 * UC-59: an active ordinary medicine of the catalog the Doctor can add to a prescription.
 *
 * @param code         internal code of the facility (mathuocvattu_bv)
 * @param registration registration number of the Drug Administration of Vietnam (so_dang_ky, BR-79)
 */
public record PrescribableMedicineResponse(
        UUID id,
        String code,
        String registration,
        String name,
        String activeIngredient,
        String strength,
        String route,
        String unit
) {
}
