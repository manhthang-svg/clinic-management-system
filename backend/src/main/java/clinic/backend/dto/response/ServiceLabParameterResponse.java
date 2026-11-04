package clinic.backend.dto.response;

import java.util.UUID;

import clinic.backend.enums.LabParameterValueType;

public record ServiceLabParameterResponse(
        UUID parameterId,
        String code,
        String name,
        LabParameterValueType valueType,
        String unit,
        int displayOrder) {
}
