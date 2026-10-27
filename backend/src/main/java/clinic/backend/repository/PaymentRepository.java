
package clinic.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import clinic.backend.entity.Payment;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByVisitIdAndType(
            UUID visitId,
            PaymentType type
    );

    Page<Payment> findByStatusAndType(
            PaymentStatus status,
            PaymentType type,
            Pageable pageable
    );

    Page<Payment> findByStatus(
            PaymentStatus status,
            Pageable pageable
    );

    Page<Payment> findByType(
            PaymentType type,
            Pageable pageable
    );

    List<Payment> findByVisitId(UUID visitId);

    Optional<Payment> findByPayosOrderCode(Long payosOrderCode);

    boolean existsByPayosOrderCode(Long payosOrderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId") UUID paymentId
    );
}
