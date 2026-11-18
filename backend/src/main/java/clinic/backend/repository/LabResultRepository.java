package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabResultRepository extends JpaRepository<LabResult, UUID> {

    /** UC-67: the result of one ordered lab service (one result per order item). */
    Optional<LabResult> findByOrderDetailId(UUID orderDetailId);

    List<LabResult> findByOrderDetailIdIn(Collection<UUID> orderDetailIds);

    /** UC-52: the results of several services with their values, in one query. */
    @Query("select distinct r from LabResult r left join fetch r.items where r.orderDetailId in :orderDetailIds")
    List<LabResult> findWithItemsByOrderDetailIdIn(@Param("orderDetailIds") Collection<UUID> orderDetailIds);
}
