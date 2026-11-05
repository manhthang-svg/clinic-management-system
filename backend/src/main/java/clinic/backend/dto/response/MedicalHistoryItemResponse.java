package clinic.backend.dto.response;

import java.util.UUID;

import clinic.backend.enums.MedicalHistoryType;

public record MedicalHistoryItemResponse(
        UUID id,
        Long version,
        MedicalHistoryType type,
        String code,
        String content,
        String value,
        String notes) {
}
