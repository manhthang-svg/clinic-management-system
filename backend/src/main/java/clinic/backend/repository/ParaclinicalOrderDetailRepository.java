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
    // đếm số lượng dịch vụ cls theo id của ktv
    long countByPerformerAccountIdAndStatus(UUID performerAccountId, ParaclinicalOrderDetailStatus status);

    // đếm số lượng dịch vụ theo phòng
    long countByRoomIdAndStatus(UUID roomId, ParaclinicalOrderDetailStatus status);

    /** Number of services per room in a status, for many rooms in one query. */
    interface RoomCount {
        UUID getRoomId();

        long getTotal();
    }

    @Query("""
            select d.roomId as roomId, count(d) as total from ParaclinicalOrderDetail d
            where d.status = :status and d.roomId in :roomIds
            group by d.roomId
            """)
    List<RoomCount> countByRoomIdsAndStatus(
            @Param("roomIds") Collection<UUID> roomIds,
            @Param("status") ParaclinicalOrderDetailStatus status);

    // kiem tra xem phong co nhan dich vụ nao ko truoc khi xoa phong
    boolean existsByRoomIdAndStatusIn(UUID roomId, Collection<ParaclinicalOrderDetailStatus> statuses);

    @Query("select distinct p.roomId from ParaclinicalOrderDetail p where p.roomId is not null and p.status in :statuses")
    java.util.Set<UUID> findRoomIdsWithStatusIn(@Param("statuses") Collection<ParaclinicalOrderDetailStatus> statuses);

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
              and d.status != clinic.backend.enums.ParaclinicalOrderDetailStatus.PENDING_PAYMENT
              and d.status != clinic.backend.enums.ParaclinicalOrderDetailStatus.ABANDONED
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
