package clinic.backend.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

/**
 * UC-36 View Patient Information: administrative data and visit history. Clinical details (findings,
 * results, diagnosis, prescription) are never included; they are shown to the Doctor in UC-52.
 */
public record PatientOverviewResponse(
        PatientResponse patient,
        /** Drug allergies recorded by the Doctor, shown read-only (BR-23). */
        List<String> drugAllergies,
        /** Insurance card number of the latest visit that used one. */
        String lastInsuranceCard,
        /** The visit that is not Completed, Cancelled or Abandoned, if any (BR-17). */
        CurrentVisit activeVisit,
        /** All visits of the patient, newest first. */
        List<VisitHistoryItem> visits
) {

    public record CurrentVisit(
            UUID visitId,
            String registrationCode,
            VisitStatus status,
            String roomName,
            /** Status of the examination fee receipt (UC-36 AF-02). */
            PaymentStatus examinationFeeStatus
    ) {
    }

    public record VisitHistoryItem(
            UUID visitId,
            String registrationCode,
            Instant registeredAt,
            String roomName,
            String doctorName,
            VisitDoituongbnLoai visitType,
            VisitStatus status
    ) {
    }
}
