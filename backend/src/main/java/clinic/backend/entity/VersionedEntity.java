package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Base for mutable aggregates protected by optimistic locking (docs/database-conventions.md).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class VersionedEntity extends CreatedOnlyEntity {

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected VersionedEntity(UUID id, Instant createdAt) {
        super(id, createdAt);
        this.updatedAt = createdAt;
    }

    @Override
    public boolean isNew() {
        return version == null;
    }

    /**
     * Records a change; every behavior method that changes persisted state calls it.
     */
    protected void touch(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now");
    }
}
