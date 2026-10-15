package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.VisitRoomAssignmentKind;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "visit_room_assignments")
public class VisitRoomAssignment implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "queue_date", nullable = false, updatable = false)
    private LocalDate queueDate;

    @Column(name = "queue_number", nullable = false, updatable = false)
    private int queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private VisitRoomAssignmentKind kind;

    @Column(name = "assigned_by_account_id", nullable = false, updatable = false)
    private UUID assignedByAccountId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Transient
    private boolean isNew = true;

    public VisitRoomAssignment(
            UUID id,
            UUID visitId,
            UUID roomId,
            LocalDate queueDate,
            int queueNumber,
            VisitRoomAssignmentKind kind,
            UUID assignedByAccountId,
            Instant assignedAt,
            Instant endedAt) {

        this.id = id;
        this.visitId = visitId;
        this.roomId = roomId;
        this.queueDate = queueDate;
        this.queueNumber = queueNumber;
        this.kind = kind;
        this.assignedByAccountId = assignedByAccountId;
        this.assignedAt = assignedAt;
        this.endedAt = endedAt;
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
}
