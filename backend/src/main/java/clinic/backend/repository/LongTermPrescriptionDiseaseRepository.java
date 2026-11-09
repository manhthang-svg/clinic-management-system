package clinic.backend.repository;

import java.util.List;
import java.util.UUID;

import clinic.backend.entity.LongTermPrescriptionDisease;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermPrescriptionDiseaseRepository extends JpaRepository<LongTermPrescriptionDisease, UUID> {

    List<LongTermPrescriptionDisease> findAllByVanBan(String vanBan);
}
