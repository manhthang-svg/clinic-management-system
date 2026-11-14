package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

/**
 * UC-34 View Examined Patient List: the visits the logged-in Doctor examined in the period (BR-39), newest first.
 * The totals cover the whole period; {@code visits} is one page, filtered by the keyword when one is given.
 * The list does not restrict record access (BR-35).
 */
public record ExaminedPatientListResponse(
        LocalDate from,
        LocalDate to,
        long examinedVisits,
        long distinctPatients,
        PageResponse<ExaminedVisit> visits) {

    /**
     * One examined visit. {@code gender} is the patient's gioitinh code (1 male, 2 female); {@code icdCode} and
     * {@code icdName} are the main diagnosis of the conclusion, null while the Doctor has not concluded.
     */
    public record ExaminedVisit(
            UUID visitId,
            String registrationCode,
            LocalDate queueDate,
            Instant examinedAt,
            VisitStatus status,
            VisitDoituongbnLoai patientType,
            UUID patientId,
            String patientCode,
            String patientName,
            short gender,
            LocalDate dateOfBirth,
            String roomName,
            String icdCode,
            String icdName) {
    }
}
