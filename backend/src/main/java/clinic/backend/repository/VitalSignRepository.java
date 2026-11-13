package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalSignRepository extends JpaRepository<VitalSign, UUID> {
    Optional<VitalSign> findByMedicalRecordIdAndIsInitialTrue(UUID medicalRecordId);

    /** UC-59 BR-59-07: the latest weight recorded for the visit record. */
    Optional<VitalSign> findFirstByMedicalRecordIdAndCannangIsNotNullOrderByThoidiemDesc(UUID medicalRecordId);
}
