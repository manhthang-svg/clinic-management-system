package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ClinicalExamination;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalExaminationRepository extends JpaRepository<ClinicalExamination, UUID> {

    @EntityGraph(attributePaths = "diagnoses")
    Optional<ClinicalExamination> findByMedicalRecordId(UUID medicalRecordId);

    List<ClinicalExamination> findByMedicalRecordIdIn(Collection<UUID> medicalRecordIds);
}
