package clinic.backend.dto.response;

import java.util.List;
import java.util.UUID;

import clinic.backend.enums.LabParameterValueType;

/** A lab parameter with every reference range it has had, newest effective date first. */
public record LabParameterManagementResponse(
        UUID id,
        String code,
        String name,
        LabParameterValueType valueType,
        String unit,
        boolean active,
        List<LabReferenceRangeResponse> referenceRanges) {
}
