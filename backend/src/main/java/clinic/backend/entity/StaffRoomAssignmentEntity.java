package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "staff_room_assignments")
public class StaffRoomAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_account_id", nullable = false, updatable = false)
    private UUID staffAccountId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by_account_id", updatable = false)
    private UUID assignedByAccountId;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by_account_id")
    private UUID removedByAccountId;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected StaffRoomAssignmentEntity() {
    }

    public StaffRoomAssignmentEntity(
            UUID id,
            UUID staffAccountId,
            UUID roomId,
            Instant assignedAt,
            UUID assignedByAccountId,
            Instant removedAt,
            UUID removedByAccountId,
            Long version) {

        this.id = id;
        this.staffAccountId = staffAccountId;
        this.roomId = roomId;
        this.assignedAt = assignedAt;
        this.assignedByAccountId = assignedByAccountId;
        this.removedAt = removedAt;
        this.removedByAccountId = removedByAccountId;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffAccountId() {
        return staffAccountId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public UUID getAssignedByAccountId() {
        return assignedByAccountId;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public UUID getRemovedByAccountId() {
        return removedByAccountId;
    }

    public Long getVersion() {
        return version;
    }
}
