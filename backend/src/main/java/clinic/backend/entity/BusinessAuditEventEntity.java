package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.AuditedEntityType;
import clinic.backend.enums.BusinessAuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "business_audit_events")
public class BusinessAuditEventEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_account_id", nullable = false, updatable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private BusinessAuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 30)
    private AuditedEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private AuditOutcome outcome;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "changed_fields", updatable = false)
    private String[] changedFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", updatable = false)
    private String changes;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    // Audit events are append-only: always new.
    @Transient
    private final boolean isNew = true;

    protected BusinessAuditEventEntity() {
    }

    public BusinessAuditEventEntity(
            UUID id,
            Instant occurredAt,
            UUID actorAccountId,
            BusinessAuditEventType eventType,
            AuditedEntityType entityType,
            UUID entityId,
            AuditOutcome outcome,
            String[] changedFields,
            String changes,
            String correlationId) {

        this.id = id;
        this.occurredAt = occurredAt;
        this.actorAccountId = actorAccountId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.outcome = outcome;
        this.changedFields = changedFields;
        this.changes = changes;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public BusinessAuditEventType getEventType() {
        return eventType;
    }

    public AuditedEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String[] getChangedFields() {
        return changedFields;
    }

    public String getChanges() {
        return changes;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
