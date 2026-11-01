package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.HealthInsuranceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthInsuranceRecordRepository extends JpaRepository<HealthInsuranceRecord, UUID> {
    Optional<HealthInsuranceRecord> findBySocialInsuranceNumber(String socialInsuranceNumber);
}
