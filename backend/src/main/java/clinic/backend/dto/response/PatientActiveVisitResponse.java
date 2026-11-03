package clinic.backend.dto.response;

import clinic.backend.enums.VisitStatus;

/**
 * The visit of a patient that is not Completed, Cancelled or Abandoned (BR-17), shown in UC-35 results.
 */
public record PatientActiveVisitResponse(
        String registrationCode,
        VisitStatus status,
        String roomName
) {
}
