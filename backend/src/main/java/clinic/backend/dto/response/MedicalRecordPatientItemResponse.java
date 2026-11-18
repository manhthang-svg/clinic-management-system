package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * UC-51 level 1: one patient of the medical record search (BR-51-05). {@code gender} is the
 * {@code patients.gioitinh} code; the client shows its label.
 */
public record MedicalRecordPatientItemResponse(
        UUID patientId,
        String patientCode,
        String fullName,
        LocalDate birthDate,
        short gender,
        long totalVisits,
        LocalDate lastVisitDate,
        String lastDiagnosisIcd,
        String lastDiagnosisName
) {
}
