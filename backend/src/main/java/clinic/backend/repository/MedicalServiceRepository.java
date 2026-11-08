
package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
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

    boolean existsByCode(String code);

    // Matches uq_services_name_ci (V38): names are compared case-insensitively after trimming.
    boolean existsByTenchidinhIgnoreCase(String tenchidinh);

    boolean existsByTenchidinhIgnoreCaseAndIdNot(String tenchidinh, UUID id);

    List<MedicalService> findAllByStatusAndTypeInOrderByTenchidinhAsc(
            ActivationStatus status,
            Collection<MedicalServiceType> types
    );

    List<MedicalService> findAllByStatus(ActivationStatus status);

    List<MedicalService> findAllByTypeAndStatus(MedicalServiceType type, ActivationStatus status);

    List<MedicalService> findAllByServiceGroupIdAndStatus(UUID serviceGroupId, ActivationStatus status);

    boolean existsByTypeAndStatus(
            MedicalServiceType type,
            ActivationStatus status
    );

    boolean existsByTypeAndStatusAndIdNot(
            MedicalServiceType type,
            ActivationStatus status,
            UUID id
    );

    Optional<MedicalService>
    findByTypeAndDepartmentIdIsNullAndServiceGroupIdIsNullAndStatus(
            MedicalServiceType type,
            ActivationStatus status
    );
}
