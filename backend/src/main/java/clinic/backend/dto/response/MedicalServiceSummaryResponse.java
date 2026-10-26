package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;

public record MedicalServiceSummaryResponse(
        UUID id,
        String code,
        String name,
        MedicalServiceType type,
        UUID serviceGroupId,
        String serviceGroupName,
        UUID departmentId,
        String departmentName,
        BigDecimal clinicPrice,
        BigDecimal insurancePrice,
        ActivationStatus status,
        Long version) {
}
