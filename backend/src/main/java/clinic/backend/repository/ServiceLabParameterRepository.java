package clinic.backend.repository;

import java.util.List;
import java.util.UUID;

import clinic.backend.entity.ServiceLabParameter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceLabParameterRepository extends JpaRepository<ServiceLabParameter, UUID> {

    List<ServiceLabParameter> findAllByServiceIdOrderByDisplayOrderAsc(UUID serviceId);
}
