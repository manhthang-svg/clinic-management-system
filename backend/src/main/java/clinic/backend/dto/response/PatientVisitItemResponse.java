package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Screen #39 Cấp 2: Danh sách lượt khám của một bệnh nhân được chọn.
 */
public record PatientVisitItemResponse(
        UUID visitId,
        UUID medicalRecordId,
        String registrationCode,
        Instant registeredAt,
        String roomName,
        String doctorName,
        String patientType,
        String finalDiagnosisIcd,
        String finalDiagnosisName,
        String status,
        String statusLabel
) {
}

