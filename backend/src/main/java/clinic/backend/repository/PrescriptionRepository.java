package clinic.backend.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.Prescription;
import clinic.backend.enums.PrescriptionLienthongStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    /** At most one prescription per visit record (uq_prescriptions_medical_record_id). */
    Optional<Prescription> findByMedicalRecordId(UUID medicalRecordId);

    boolean existsByMadonthuoc(String madonthuoc);

    /** BR-47: a prescription of the record was sent to the national prescription system. */
    boolean existsByMedicalRecordIdAndLienthongStatusIn(
            UUID medicalRecordId, Collection<PrescriptionLienthongStatus> statuses);
}
