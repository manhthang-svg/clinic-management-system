package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "staff_room_assignments")
public class StaffRoomAssignment {

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

    public void remove(UUID actorAccountId, Instant now) {
        if (removedAt != null) {
            return;
        }
        removedAt = Objects.requireNonNull(now, "now");
        removedByAccountId = Objects.requireNonNull(actorAccountId, "actorAccountId");
    }
}
