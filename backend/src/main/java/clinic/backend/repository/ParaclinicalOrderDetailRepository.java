package clinic.backend.repository;

import java.util.Collection;
import java.util.UUID;

import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParaclinicalOrderDetailRepository extends JpaRepository<ParaclinicalOrderDetail, UUID> {
    long countByPerformerAccountIdAndStatus(UUID performerAccountId, ParaclinicalOrderDetailStatus status);

    long countByRoomIdAndStatus(UUID roomId, ParaclinicalOrderDetailStatus status);

    long countByServiceIdAndStatusIn(
            UUID serviceId, Collection<ParaclinicalOrderDetailStatus> statuses);
}
