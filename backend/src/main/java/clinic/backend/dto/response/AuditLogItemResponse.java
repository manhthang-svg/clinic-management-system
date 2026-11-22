package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.Role;

/**
 * UC-78: one row of the audit log list. The actor is empty for changes made by a scheduled system task.
 */
public record AuditLogItemResponse(
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
        String ipAddress) {
}
