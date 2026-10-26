package clinic.backend.repository;

import java.util.UUID;

import clinic.backend.entity.MedicalService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MedicalServiceRepository
        extends JpaRepository<MedicalService, UUID>, JpaSpecificationExecutor<MedicalService> {
}
