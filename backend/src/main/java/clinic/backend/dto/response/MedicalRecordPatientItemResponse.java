package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Screen #39 Cấp 1: Thông tin bệnh nhân trong danh sách hồ sơ bệnh án.
 */
public record MedicalRecordPatientItemResponse(
        UUID patientId,
        String patientCode,
        String fullName,
        LocalDate birthDate,
        String gender,
        String citizenId,
        String phoneNumber,
        long totalVisits,
        LocalDate lastVisitDate,
        String lastDiagnosisIcd,
        String lastDiagnosisName
) {
}

