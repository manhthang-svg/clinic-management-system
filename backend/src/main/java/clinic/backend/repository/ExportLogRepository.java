package clinic.backend.repository;

import java.util.List;
import java.util.UUID;

import clinic.backend.entity.ExportLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportLogRepository extends JpaRepository<ExportLog, UUID> {

    List<ExportLog> findAllByMedicalRecordIdOrderByExportedAtAsc(UUID medicalRecordId);
}
