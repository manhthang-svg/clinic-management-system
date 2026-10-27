package clinic.backend.repository;

import clinic.backend.entity.ParaclinicalOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParaclinicalOrderRepository extends JpaRepository<ParaclinicalOrder, UUID> {

    Optional<ParaclinicalOrder> findByPaymentId(UUID paymentId);

    Optional<ParaclinicalOrder> findBySophieu(String sophieu);

    boolean existsBySophieu(String sophieu);
}
