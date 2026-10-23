package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AuditLogAction;

public record AccountHistoryResponse(
        UUID id,
        Instant occurredAt,
        UUID actorAccountId,
        AuditLogAction action,
        String[] changedFields,
        String oldValue,
        String newValue) {
}
