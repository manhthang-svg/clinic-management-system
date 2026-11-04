package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.LabReferenceRange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabReferenceRangeRepository extends JpaRepository<LabReferenceRange, UUID> {

    List<LabReferenceRange> findAllByParameterIdOrderByEffectiveFromDesc(UUID parameterId);

    List<LabReferenceRange> findAllByOrderByEffectiveFromDesc();

    List<LabReferenceRange> findAllByParameterIdInAndActiveTrueOrderByEffectiveFromAsc(Collection<UUID> parameterIds);
}
