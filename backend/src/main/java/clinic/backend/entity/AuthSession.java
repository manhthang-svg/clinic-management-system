package clinic.backend.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.SessionRevocationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * One login session: the family of refresh tokens created by a single successful login.
 *
 * <p>The absolute expiry is fixed at login and is never extended by rotation.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auth_sessions")
public class AuthSession implements Persistable<UUID> {

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

    @Getter(AccessLevel.NONE)
    @Transient
    private boolean isNew = true;

    private AuthSession(UUID id, UUID staffAccountId, Instant createdAt, Instant absoluteExpiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.staffAccountId = Objects.requireNonNull(staffAccountId, "staffAccountId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
    }

    public static AuthSession start(UUID id, UUID staffAccountId, Instant now, Duration absoluteLifetime) {
        return new AuthSession(id, staffAccountId, now, now.plus(absoluteLifetime));
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && now.isBefore(absoluteExpiresAt);
    }

    /**
     * A refresh token expires after the idle timeout, but never after the session itself.
     */
    public Instant refreshTokenExpiry(Instant issuedAt, Duration idleTimeout) {
        Instant idleExpiry = issuedAt.plus(idleTimeout);
        return idleExpiry.isBefore(absoluteExpiresAt) ? idleExpiry : absoluteExpiresAt;
    }

    /**
     * Revokes a loaded session. Concurrent revocation uses the conditional update in
     * {@code AuthSessionRepository} instead.
     *
     * @return {@code true} when this call revoked it
     */
    public boolean revoke(SessionRevocationReason reason, Instant now) {
        if (revokedAt != null) {
            return false;
        }
        revokedAt = Objects.requireNonNull(now, "now");
        revocationReason = Objects.requireNonNull(reason, "reason");
        return true;
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
}
