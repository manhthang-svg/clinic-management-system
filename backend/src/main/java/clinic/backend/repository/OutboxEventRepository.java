
package clinic.backend.repository;

import java.util.List;
import java.util.UUID;

import clinic.backend.entity.OutboxEvent;
import clinic.backend.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );
}
