package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.MedicalHistory;
import clinic.backend.enums.MedicalHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, UUID> {

    List<MedicalHistory> findAllByPatientIdOrderByCreatedAtAsc(UUID patientId);

    /** UC-36: drug allergies shown read-only on the patient profile (BR-23). */
    List<MedicalHistory> findByPatientIdAndHistoryTypeOrderByCreatedAtAsc(UUID patientId, MedicalHistoryType historyType);

    /** UC-39: every history and allergy item of the patient. */
    List<MedicalHistory> findByPatientIdOrderByCreatedAtAsc(UUID patientId);

    List<MedicalHistory> findByPatientIdInOrderByCreatedAtAsc(Collection<UUID> patientIds);
}
