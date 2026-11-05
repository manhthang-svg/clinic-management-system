package clinic.backend.dto.response;

import clinic.backend.enums.OrganSystem;

public record ExaminationFindingResponse(
        OrganSystem organSystem,
        String finding,
        String notes) {
}
