package clinic.backend.account.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.account.application.port.SecurityAuditLog.EventType;
import clinic.backend.account.application.port.SecurityAuditLog.Outcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "security_audit_events")
class SecurityAuditEventEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private EventType eventType;

    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    @Column(name = "target_account_id", updatable = false)
    private UUID targetAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private Outcome outcome;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    // Audit events are append-only: always new.
    @Transient
    private final boolean isNew = true;

    protected SecurityAuditEventEntity() {
    }

    SecurityAuditEventEntity(
            UUID id,
            Instant occurredAt,
            EventType eventType,
            UUID actorAccountId,
            UUID targetAccountId,
            Outcome outcome,
            String correlationId) {

        this.id = id;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.actorAccountId = actorAccountId;
        this.targetAccountId = targetAccountId;
        this.outcome = outcome;
        this.correlationId = correlationId;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    EventType getEventType() {
        return eventType;
    }

    UUID getActorAccountId() {
        return actorAccountId;
    }

    UUID getTargetAccountId() {
        return targetAccountId;
    }

    Outcome getOutcome() {
        return outcome;
    }

    String getCorrelationId() {
        return correlationId;
    }
}
