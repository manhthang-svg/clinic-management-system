package clinic.backend.service;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.SecurityAuditEventType;

/**
 * Append-only security audit trail (docs/security.md, Logging and Audit Restrictions).
 */
public interface SecurityAuditService {

    /**
     * Records an event in the caller's transaction, so an action and its audit commit together.
     *
     * @param actorAccountId  the authenticated caller, or {@code null} when unauthenticated
     * @param targetAccountId the account the event is about, or {@code null} when unknown
     */
    void record(
            SecurityAuditEventType type,
            UUID actorAccountId,
            UUID targetAccountId,
            AuditOutcome outcome,
            Instant occurredAt);
}
