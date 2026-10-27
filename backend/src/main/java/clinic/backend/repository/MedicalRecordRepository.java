package clinic.backend.repository;

import clinic.backend.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByVisitId(UUID visitId);

    boolean existsBySoba(String soba);
}
