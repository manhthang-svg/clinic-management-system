package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.Role;

/**
 * UC-78: one audit log entry with its old and new values (JSON text, already masked when written).
 */
public record AuditLogDetailResponse(
        UUID id,
        Instant occurredAt,
        UUID actorAccountId,
        String actorUsername,
        String actorFullName,
        Role actorRole,
        AuditLogAction action,
        String entityType,
        UUID entityId,
        String sovaovien,
        AuditOutcome outcome,
        String[] changedFields,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent,
        String correlationId) {
}
