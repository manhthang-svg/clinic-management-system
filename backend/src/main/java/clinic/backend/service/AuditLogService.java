package clinic.backend.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Writes detailed business audit rows in the caller's transaction. */
public interface AuditLogService {

    void recordAccountUpdate(
            UUID accountId,
            Map<String, String> oldValues,
            Map<String, String> newValues,
            Instant occurredAt);
}
