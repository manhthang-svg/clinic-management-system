package clinic.backend.account.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.account.domain.SessionRevocationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "auth_sessions")
class AuthSessionEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_account_id", nullable = false, updatable = false)
    private UUID staffAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "absolute_expires_at", nullable = false, updatable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 30)
    private SessionRevocationReason revocationReason;

    @Transient
    private boolean isNew = true;

    protected AuthSessionEntity() {
    }

    AuthSessionEntity(
            UUID id,
            UUID staffAccountId,
            Instant createdAt,
            Instant absoluteExpiresAt,
            Instant revokedAt,
            SessionRevocationReason revocationReason) {

        this.id = id;
        this.staffAccountId = staffAccountId;
        this.createdAt = createdAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.revokedAt = revokedAt;
        this.revocationReason = revocationReason;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    UUID getStaffAccountId() {
        return staffAccountId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    SessionRevocationReason getRevocationReason() {
        return revocationReason;
    }
}
