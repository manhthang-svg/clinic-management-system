package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RoomServiceItemResponse(
        UUID id,
        String code,
        String name,
        String type,
        UUID serviceGroupId,
        String serviceGroupName,
        UUID departmentId,
        BigDecimal clinicPrice,
        BigDecimal insurancePrice,
        String status
) {}

