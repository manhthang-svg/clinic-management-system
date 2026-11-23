package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UC-58 result: the visit is completed and its record closed and archived (MSG130).
 *
 * @param visitCode         mã lượt khám, for MSG130
 * @param archiveNumber     số lưu trữ (BR-58-06)
 * @param retentionYears    from the retention category (TT 33/2025/TT-BYT Phụ lục)
 * @param expiresOn         31/12 of (closing year + retention years)
 * @param prescriptionId    the accepted prescription to print (UC-60), or null
 * @param followUpAppointmentId the scheduled appointment to print (UC-62), or null
 */
public record ConclusionConfirmationResponse(
        UUID visitId,
        String visitCode,
        String visitStatus,
        Instant closedAt,
        String archiveNumber,
        String retentionCategory,
        short retentionYears,
        LocalDate expiresOn,
        UUID prescriptionId,
        UUID followUpAppointmentId
) {
}
