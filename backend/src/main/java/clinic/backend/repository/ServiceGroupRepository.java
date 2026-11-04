package clinic.backend.repository;


import clinic.backend.entity.ServiceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceGroupRepository extends JpaRepository<ServiceGroup, UUID> {

    Optional<ServiceGroup> findByServiceGroupCode(String serviceGroupCode);

    List<ServiceGroup> findAllByActiveTrueOrderByServiceGroupNameAsc();
}
