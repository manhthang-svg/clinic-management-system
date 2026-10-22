package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Base for entities that record when they were created but are not versioned.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class CreatedOnlyEntity extends UuidEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CreatedOnlyEntity(UUID id, Instant createdAt) {
        super(id);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
