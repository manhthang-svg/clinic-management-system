package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.enums.RecordArchiveStatus;

/**
 * Response DTO for UC-79 / BR-120: View Record Archive list for Admin.
 * Contains archive metadata and administrative patient information; no clinical diagnostic data.
 */
public record RecordArchiveResponse(
        UUID archiveId,
        UUID medicalRecordId,
        String soluutru,
        String soba,
        UUID visitId,
        String visitCode,
        Instant admissionTime,
        UUID patientId,
        String patientCode,
        String patientName,
        String patientCccd,
        LocalDate dateOfBirth,
        Integer age,
        Integer gender,
        RecordArchiveRetentionCategory retentionCategory,
        Short retentionYears,
        LocalDate expiresOn,
        String packageSha256,
        RecordArchiveStatus status,
        Instant archivedAt
) {
}

