package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A refresh token of a session. Only the SHA-256 digest of the raw value is kept.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "auth_session_id", nullable = false, updatable = false)
    private UUID authSessionId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Getter(AccessLevel.NONE)
    @Transient
    private boolean isNew = true;

    private RefreshToken(UUID id, UUID authSessionId, byte[] tokenHash, Instant issuedAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.authSessionId = Objects.requireNonNull(authSessionId, "authSessionId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash").clone();
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static RefreshToken issue(
            UUID id, UUID authSessionId, byte[] tokenHash, Instant issuedAt, Instant expiresAt) {
        return new RefreshToken(id, authSessionId, tokenHash, issuedAt, expiresAt);
    }

    public byte[] getTokenHash() {
        return tokenHash.clone();
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Marks a loaded token as used. Concurrent refreshes use the conditional update in
     * {@code RefreshTokenRepository} instead.
     *
     * @return {@code true} when the token had not been used yet
     */
    public boolean markUsed(Instant now) {
        if (usedAt != null) {
            return false;
        }
        usedAt = Objects.requireNonNull(now, "now");
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

    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", authSessionId=" + authSessionId + "]";
    }
}
