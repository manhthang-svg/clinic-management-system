package clinic.backend.dto.response;

import java.util.List;
import java.util.UUID;

import clinic.backend.enums.LabParameterValueType;

/** A lab parameter of a LAB service with the parameter's active reference ranges. */
public record ServiceLabParameterResponse(
        UUID parameterId,
        String code,
        String name,
        LabParameterValueType valueType,
        String unit,
        int displayOrder,
        List<LabReferenceRangeResponse> referenceRanges) {
}
