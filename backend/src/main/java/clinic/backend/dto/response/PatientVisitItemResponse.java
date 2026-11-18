package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

/**
 * UC-51 level 2: one visit of the selected patient (BR-51-06). {@code medicalRecordId} is empty when the visit has
 * no medical record, which then cannot be opened in UC-52.
 */
public record PatientVisitItemResponse(
        UUID visitId,
        UUID medicalRecordId,
        String registrationCode,
        Instant registeredAt,
        LocalDate visitDate,
        String roomName,
        String doctorName,
        VisitDoituongbnLoai patientType,
        String finalDiagnosisIcd,
        String finalDiagnosisName,
        VisitStatus status,
        boolean skipped
) {
}
