package clinic.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.SecurityAuditEventType;

public record AccountActivityResponse(
        UUID id, Instant occurredAt, SecurityAuditEventType type, AuditOutcome outcome, String ipAddress) {
}
