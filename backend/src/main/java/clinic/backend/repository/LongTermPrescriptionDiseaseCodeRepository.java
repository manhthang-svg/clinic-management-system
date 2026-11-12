package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.LongTermPrescriptionDiseaseCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermPrescriptionDiseaseCodeRepository
        extends JpaRepository<LongTermPrescriptionDiseaseCode, UUID> {

    List<LongTermPrescriptionDiseaseCode> findAllByDiseaseIdIn(Collection<UUID> diseaseIds);

    /** TT 26/2025: one of the codes is a disease of the long-term prescription list (more than 30 days). */
    boolean existsByMaicdIn(Collection<String> codes);
}
