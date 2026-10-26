package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;

public record MedicalServiceDetailResponse(
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
        String description,
        ActivationStatus status,
        Long version,
        List<PerformingRoomResponse> performingRooms) {
}
