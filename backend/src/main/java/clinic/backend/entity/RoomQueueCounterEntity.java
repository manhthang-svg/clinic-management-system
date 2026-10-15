package clinic.backend.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Last queue number issued by a room on a clinic-local day (I1-D28, I1-D28a).
 *
 * <p>Registration increments it with a native {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}
 * so concurrent registrations get consecutive numbers; this mapping exists for reads and schema
 * validation.
 */
@Entity
@Table(name = "room_queue_counters")
public class RoomQueueCounterEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "last_queue_number", nullable = false)
    private int lastQueueNumber;

    protected RoomQueueCounterEntity() {
    }

    public RoomQueueCounterEntity(Key key, int lastQueueNumber) {
        this.key = key;
        this.lastQueueNumber = lastQueueNumber;
    }

    public Key getKey() {
        return key;
    }

    public int getLastQueueNumber() {
        return lastQueueNumber;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "room_id", nullable = false, updatable = false)
        private UUID roomId;

        @Column(name = "queue_date", nullable = false, updatable = false)
        private LocalDate queueDate;

        protected Key() {
        }

        public Key(UUID roomId, LocalDate queueDate) {
            this.roomId = roomId;
            this.queueDate = queueDate;
        }

        public UUID getRoomId() {
            return roomId;
        }

        public LocalDate getQueueDate() {
            return queueDate;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && roomId.equals(key.roomId) && queueDate.equals(key.queueDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, queueDate);
        }
    }
}
