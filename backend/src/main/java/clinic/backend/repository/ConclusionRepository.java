package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.Conclusion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConclusionRepository extends JpaRepository<Conclusion, UUID> {

    Optional<Conclusion> findByMedicalRecordId(UUID medicalRecordId);
}
