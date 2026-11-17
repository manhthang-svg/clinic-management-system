package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.RecordArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordArchiveRepository extends JpaRepository<RecordArchive, UUID> {

    Optional<RecordArchive> findByMedicalRecordId(UUID medicalRecordId);

    boolean existsBySoluutru(String soluutru);
}
