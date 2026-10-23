package clinic.backend.repository;

import java.util.UUID;

import clinic.backend.entity.Visit;
import clinic.backend.enums.VisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, UUID> {
    long countByDoctorAccountIdAndStatus(UUID doctorAccountId, VisitStatus status);
}
