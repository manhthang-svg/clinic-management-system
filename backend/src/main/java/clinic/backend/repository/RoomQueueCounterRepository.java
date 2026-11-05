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
            SELECT :roomId, :queueDate, COALESCE(MAX(visit.queue_number), 0) + 1
            FROM visits visit
            WHERE visit.room_id = :roomId
              AND visit.queue_date = :queueDate
            ON CONFLICT (room_id, queue_date)
            DO UPDATE SET last_queue_number = GREATEST(
                room_queue_counters.last_queue_number + 1,
                (SELECT COALESCE(MAX(visit.queue_number), 0) + 1
                 FROM visits visit
                 WHERE visit.room_id = :roomId
                   AND visit.queue_date = :queueDate)
            )
            RETURNING last_queue_number
            """, nativeQuery = true)
    int takeNext(@Param("roomId") UUID roomId, @Param("queueDate") LocalDate queueDate);
}
