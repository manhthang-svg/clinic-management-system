package clinic.backend.repository;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.entity.RoomQueueCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomQueueCounterRepository
        extends JpaRepository<RoomQueueCounter, RoomQueueCounter.Key> {

    @Query(value = """
            INSERT INTO room_queue_counters (room_id, queue_date, last_queue_number)
            VALUES (:roomId, :queueDate, 1)
            ON CONFLICT (room_id, queue_date)
            DO UPDATE SET last_queue_number = room_queue_counters.last_queue_number + 1
            RETURNING last_queue_number
            """, nativeQuery = true)
    int takeNext(@Param("roomId") UUID roomId, @Param("queueDate") LocalDate queueDate);
}
