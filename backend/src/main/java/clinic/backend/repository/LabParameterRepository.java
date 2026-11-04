package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.LabParameter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabParameterRepository extends JpaRepository<LabParameter, UUID> {

    List<LabParameter> findAllByActiveTrueOrderByNameAsc();

    List<LabParameter> findAllByIdInAndActiveTrue(Collection<UUID> ids);
}
