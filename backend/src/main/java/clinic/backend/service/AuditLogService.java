package clinic.backend.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import clinic.backend.enums.AuditLogAction;

/** Writes detailed business audit rows in the caller's transaction. */
public interface AuditLogService {

    void recordAccountUpdate(
            UUID accountId,
            Map<String, String> oldValues,
            Map<String, String> newValues,
            Instant occurredAt);

    /** {@code oldValues} is empty when the facility is declared for the first time. */
    void recordFacilityChange(
            UUID actorAccountId,
            UUID facilityId,
            AuditLogAction action,
            Map<String, ?> oldValues,
            Map<String, ?> newValues,
            Instant occurredAt);

    void recordAccountChange(
            UUID actorAccountId,
            UUID targetAccountId,
            AuditLogAction action,
            Map<String, ?> oldValues,
            Map<String, ?> newValues,
            Instant occurredAt);

    void recordBusinessChange(
            UUID actorAccountId,
            AuditLogAction action,
            String entityType,
            UUID entityId,
            String sovaovien,
            Map<String, ?> values,
            Instant occurredAt);
}
