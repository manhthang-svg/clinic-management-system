package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.DepartmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "departments")
public class DepartmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private DepartmentType type;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_account_id")
    private UUID updatedByAccountId;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected DepartmentEntity() {
    }

    public DepartmentEntity(
            UUID id,
            String name,
            DepartmentType type,
            String description,
            Instant createdAt,
            Instant updatedAt,
            UUID updatedByAccountId,
            Long version) {

        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.updatedByAccountId = updatedByAccountId;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DepartmentType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    public Long getVersion() {
        return version;
    }
}
