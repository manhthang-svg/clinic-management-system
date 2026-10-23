package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.SecurityAuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * Append-only security audit trail (docs/security.md, Logging and Audit Restrictions).
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private SecurityAuditEventType eventType;

    /** The authenticated caller, or {@code null} when unauthenticated. */
    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    /** The account the event is about, or {@code null} when unknown. */
    @Column(name = "target_account_id", updatable = false)
    private UUID targetAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    // Audit events are append-only: always new.
    @Getter(AccessLevel.NONE)
    @Transient
    private final boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
