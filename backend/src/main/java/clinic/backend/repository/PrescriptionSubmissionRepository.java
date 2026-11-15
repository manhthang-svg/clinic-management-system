package clinic.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.PrescriptionSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionSubmissionRepository extends JpaRepository<PrescriptionSubmission, UUID> {

    /** The latest attempt of a prescription; attempt numbers grow by one per send. */
    Optional<PrescriptionSubmission> findFirstByPrescriptionIdOrderByAttemptNoDesc(UUID prescriptionId);

    /** UC-91: every attempt of a prescription, newest first. */
    List<PrescriptionSubmission> findAllByPrescriptionIdOrderByAttemptNoDesc(UUID prescriptionId);

    /** UC-59 AF-03: a prescription that was ever sent cannot be deleted. */
    boolean existsByPrescriptionId(UUID prescriptionId);
}
