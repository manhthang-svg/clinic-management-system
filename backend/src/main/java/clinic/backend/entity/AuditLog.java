package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only business audit trail (CV 365 III.1.1.đ). Sign-in events go to security_audit_events.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog extends UuidEntity {

    private static final String STAFF_ACCOUNT_ENTITY = "staff_accounts";
    private static final String CLINIC_FACILITY_ENTITY = "clinic_facility";

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private AuditLogAction action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "sovaovien", updatable = false, length = 100)
    private String sovaovien;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private AuditOutcome outcome;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "changed_fields", updatable = false)
    private String[] changedFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", updatable = false)
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", updatable = false)
    private String newValue;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 255)
    private String userAgent;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    private AuditLog(UUID id) {
        super(id);
    }

    /** Creates the detailed, append-only audit row required by Report 3 UC-04. */
    public static AuditLog accountUpdated(
            UUID id,
            UUID accountId,
            Instant occurredAt,
            String[] changedFields,
            String oldValue,
            String newValue,
            String correlationId) {

        AuditLog auditLog = new AuditLog(id);
        auditLog.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        auditLog.actorAccountId = Objects.requireNonNull(accountId, "accountId");
        auditLog.action = AuditLogAction.UPDATE;
        auditLog.entityType = STAFF_ACCOUNT_ENTITY;
        auditLog.entityId = accountId;
        auditLog.outcome = AuditOutcome.SUCCESS;
        auditLog.changedFields = Objects.requireNonNull(changedFields, "changedFields").clone();
        auditLog.oldValue = Objects.requireNonNull(oldValue, "oldValue");
        auditLog.newValue = Objects.requireNonNull(newValue, "newValue");
        auditLog.correlationId = correlationId;
        return auditLog;
    }

    /** UC-77: the Admin declared (CREATE) or changed (UPDATE) the clinic facility information. */
    public static AuditLog facilityChanged(
            UUID id,
            UUID actorAccountId,
            UUID facilityId,
            AuditLogAction action,
            Instant occurredAt,
            String[] changedFields,
            String oldValue,
            String newValue,
            String correlationId) {
        AuditLog auditLog = new AuditLog(id);
        auditLog.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        auditLog.actorAccountId = Objects.requireNonNull(actorAccountId, "actorAccountId");
        auditLog.action = Objects.requireNonNull(action, "action");
        auditLog.entityType = CLINIC_FACILITY_ENTITY;
        auditLog.entityId = Objects.requireNonNull(facilityId, "facilityId");
        auditLog.outcome = AuditOutcome.SUCCESS;
        auditLog.changedFields = Objects.requireNonNull(changedFields, "changedFields").clone();
        auditLog.oldValue = oldValue;
        auditLog.newValue = Objects.requireNonNull(newValue, "newValue");
        auditLog.correlationId = correlationId;
        return auditLog;
    }

    public static AuditLog accountChanged(
            UUID id,
            UUID actorAccountId,
            UUID targetAccountId,
            AuditLogAction action,
            Instant occurredAt,
            String[] changedFields,
            String oldValue,
            String newValue,
            String correlationId) {
        AuditLog auditLog = new AuditLog(id);
        auditLog.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        auditLog.actorAccountId = Objects.requireNonNull(actorAccountId, "actorAccountId");
        auditLog.action = Objects.requireNonNull(action, "action");
        auditLog.entityType = STAFF_ACCOUNT_ENTITY;
        auditLog.entityId = Objects.requireNonNull(targetAccountId, "targetAccountId");
        auditLog.outcome = AuditOutcome.SUCCESS;
        auditLog.changedFields = changedFields == null ? null : changedFields.clone();
        auditLog.oldValue = oldValue;
        auditLog.newValue = newValue;
        auditLog.correlationId = correlationId;
        return auditLog;
    }

    public static AuditLog businessChange(
            UUID id,
            UUID actorAccountId,
            AuditLogAction action,
            String entityType,
            UUID entityId,
            String sovaovien,
            Instant occurredAt,
            String newValue,
            String correlationId) {
        AuditLog auditLog = new AuditLog(id);
        auditLog.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        auditLog.actorAccountId = Objects.requireNonNull(actorAccountId, "actorAccountId");
        auditLog.action = Objects.requireNonNull(action, "action");
        auditLog.entityType = Objects.requireNonNull(entityType, "entityType");
        auditLog.entityId = Objects.requireNonNull(entityId, "entityId");
        auditLog.sovaovien = sovaovien;
        auditLog.outcome = AuditOutcome.SUCCESS;
        auditLog.newValue = newValue;
        auditLog.correlationId = correlationId;
        return auditLog;
    }
}
