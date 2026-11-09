package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabResultRepository extends JpaRepository<LabResult, UUID> {

    /** UC-67: the result of one ordered lab service (one result per order item). */
    Optional<LabResult> findByOrderDetailId(UUID orderDetailId);
}
