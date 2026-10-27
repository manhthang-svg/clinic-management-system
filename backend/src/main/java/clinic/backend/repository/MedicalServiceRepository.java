package clinic.backend.repository;

import java.util.UUID;

import clinic.backend.entity.MedicalService;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MedicalServiceRepository
        extends JpaRepository<MedicalService, UUID>, JpaSpecificationExecutor<MedicalService> {

    boolean existsByCode(String code);

    boolean existsByTypeAndStatus(MedicalServiceType type, ActivationStatus status);

    boolean existsByTypeAndStatusAndIdNot(
            MedicalServiceType type, ActivationStatus status, UUID excludedServiceId);
}
