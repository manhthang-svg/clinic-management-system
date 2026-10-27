
package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.MedicalService;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicalServiceType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MedicalServiceRepository
        extends JpaRepository<MedicalService, UUID>,
        JpaSpecificationExecutor<MedicalService> {

    Optional<MedicalService>
    findByTypeAndDepartmentIdIsNullAndServiceGroupIdIsNullAndStatus(
            MedicalServiceType type,
            ActivationStatus status
    );
}
