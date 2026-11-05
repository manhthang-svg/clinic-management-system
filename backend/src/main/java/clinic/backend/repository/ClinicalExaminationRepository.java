package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ClinicalExamination;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalExaminationRepository extends JpaRepository<ClinicalExamination, UUID> {

    @EntityGraph(attributePaths = "findings")
    Optional<ClinicalExamination> findByMedicalRecordId(UUID medicalRecordId);
}
