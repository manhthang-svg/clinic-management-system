package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.VisitTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitTransferRepository extends JpaRepository<VisitTransfer, UUID> {

    List<VisitTransfer> findAllByVisitIdInAndToRoomIdOrderByTransferredAtDesc(
            Collection<UUID> visitIds,
            UUID toRoomId);

    List<VisitTransfer> findAllByVisitIdOrderByTransferredAtAsc(UUID visitId);
}
