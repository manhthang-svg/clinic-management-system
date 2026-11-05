package clinic.backend.dto.request;

import clinic.backend.enums.OrganSystem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExaminationFindingRequest(
        @NotNull OrganSystem organSystem,
        @Size(min = 2, max = 2000) String finding,
        @Size(max = 500) String notes) {
}
