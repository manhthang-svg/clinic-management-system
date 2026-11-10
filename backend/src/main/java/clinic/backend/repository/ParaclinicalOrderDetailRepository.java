package clinic.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParaclinicalOrderDetailRepository extends JpaRepository<ParaclinicalOrderDetail, UUID> {
    long countByPerformerAccountIdAndStatus(UUID performerAccountId, ParaclinicalOrderDetailStatus status);

    long countByRoomIdAndStatus(UUID roomId, ParaclinicalOrderDetailStatus status);

    long countByServiceIdAndStatusIn(
            UUID serviceId, Collection<ParaclinicalOrderDetailStatus> statuses);

    List<ParaclinicalOrderDetail> findByRoomIdOrderByQueueNumberAsc(UUID roomId);

    List<ParaclinicalOrderDetail> findByRoomIdAndNgaychidinhGreaterThanEqualOrderByQueueNumberAsc(
            UUID roomId, Instant startOfDay);

    List<ParaclinicalOrderDetail> findByOrderId(UUID orderId);

    List<ParaclinicalOrderDetail> findByOrderIdIn(Collection<UUID> orderIds);

    @Query("""
            select d from ParaclinicalOrderDetail d
            join fetch d.order o
            where d.roomId = :roomId
              and (d.status in (clinic.backend.enums.ParaclinicalOrderDetailStatus.WAITING, clinic.backend.enums.ParaclinicalOrderDetailStatus.IN_PROGRESS)
                   or d.ngaychidinh >= :startOfDay)
            order by d.queueNumber asc, d.ngaychidinh asc
            """)
    List<ParaclinicalOrderDetail> findQueueDetails(
            @Param("roomId") UUID roomId,
            @Param("startOfDay") Instant startOfDay);

    @Query("""
            select d from ParaclinicalOrderDetail d
            join fetch d.order o
            where d.roomId = :roomId
              and d.status = clinic.backend.enums.ParaclinicalOrderDetailStatus.PENDING_RESULT
            order by d.queueNumber asc, d.ngaychidinh asc
            """)
    List<ParaclinicalOrderDetail> findPendingResultsByRoom(@Param("roomId") UUID roomId);
}
