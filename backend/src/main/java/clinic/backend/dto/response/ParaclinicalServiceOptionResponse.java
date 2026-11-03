package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import clinic.backend.enums.MedicalServiceType;

/**
 * An active paraclinical service the Doctor can order (UC-49 step 2).
 */
public record ParaclinicalServiceOptionResponse(
        UUID id,
        String code,
        String name,
        MedicalServiceType type,
        UUID serviceGroupId,
        String serviceGroupName,
        BigDecimal clinicPrice,
        BigDecimal insurancePrice
) {
}
