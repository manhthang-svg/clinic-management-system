package clinic.backend.dto.response;

import clinic.backend.enums.ActivationStatus;

public record MedicalServiceStatusCheckResponse(
        ActivationStatus currentStatus,
        ActivationStatus targetStatus,
        long unfinishedOrderCount) {
}
