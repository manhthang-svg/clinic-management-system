package clinic.backend.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only security audit trail (docs/security.md, Logging and Audit Restrictions).
 */
public interface SecurityAuditLog {

    void record(Event event);

    enum EventType {
        LOGIN_SUCCEEDED,
        LOGIN_FAILED,
        LOGIN_THROTTLED,
        LOGOUT,
        REFRESH_TOKEN_REUSE_DETECTED,
        PASSWORD_CHANGED,
        ACCOUNT_CREATED,
        ACCOUNT_UPDATED,
        ROLE_CHANGED,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        PASSWORD_RESET
    }

    enum Outcome {
        SUCCESS,
        FAILURE
    }

    /**
     * @param actorAccountId  the authenticated caller, or {@code null} when unauthenticated
     * @param targetAccountId the account the event is about, or {@code null} when unknown
     */
    record Event(EventType type, UUID actorAccountId, UUID targetAccountId, Outcome outcome, Instant occurredAt) {
    }
}
