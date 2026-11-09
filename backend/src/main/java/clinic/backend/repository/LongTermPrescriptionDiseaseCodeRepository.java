package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.LongTermPrescriptionDiseaseCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermPrescriptionDiseaseCodeRepository
        extends JpaRepository<LongTermPrescriptionDiseaseCode, UUID> {

    List<LongTermPrescriptionDiseaseCode> findAllByDiseaseIdIn(Collection<UUID> diseaseIds);
}
