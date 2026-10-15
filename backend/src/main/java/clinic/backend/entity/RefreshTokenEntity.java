package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity implements Persistable<UUID> {

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

    @Transient
    private boolean isNew = true;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(
            UUID id,
            UUID authSessionId,
            byte[] tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant usedAt) {

        this.id = id;
        this.authSessionId = authSessionId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
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
    public void markNotNew() {
        isNew = false;
    }

    public UUID getAuthSessionId() {
        return authSessionId;
    }

    public byte[] getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
