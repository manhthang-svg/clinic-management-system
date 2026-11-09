package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ImagingResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingResultRepository extends JpaRepository<ImagingResult, UUID> {

    /** UC-67: the result of one ordered imaging or functional service (one result per order item). */
    Optional<ImagingResult> findByOrderDetailId(UUID orderDetailId);
}
