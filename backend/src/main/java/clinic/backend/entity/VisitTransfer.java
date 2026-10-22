package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One room transfer of a visit (BR-24); the current room is on the visit.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "visit_transfers")
public class VisitTransfer extends UuidEntity {

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "from_room_id", nullable = false)
    private UUID fromRoomId;

    @Column(name = "to_room_id", nullable = false)
    private UUID toRoomId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "old_queue_number", nullable = false)
    private int oldQueueNumber;

    @Column(name = "new_queue_number", nullable = false)
    private int newQueueNumber;

    @Column(name = "transferred_by_account_id", nullable = false)
    private UUID transferredByAccountId;

    @Column(name = "transferred_at", nullable = false)
    private Instant transferredAt;
}
