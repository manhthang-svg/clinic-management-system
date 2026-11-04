package clinic.backend.dto.response;

import java.util.UUID;
import java.util.List;

import clinic.backend.enums.LabParameterValueType;

public record LabParameterOptionResponse(
        UUID id,
        String code,
        String name,
        LabParameterValueType valueType,
        String unit,
        List<LabReferenceRangeResponse> referenceRanges) {
}
