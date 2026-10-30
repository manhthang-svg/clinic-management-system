
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

    @Query("""
            select p from Payment p
            join Visit v on v.id = p.visitId
            join Patient patient on patient.id = v.patientId
            where (:status is null or p.status = :status)
              and (:type is null or p.type = :type)
              and (
                    cast(:search as String) is null
                    or lower(p.paymentCode) like lower(concat('%', cast(:search as String), '%'))
                    or lower(coalesce(p.receiptNumber, '')) like lower(concat('%', cast(:search as String), '%'))
                    or lower(coalesce(v.sovaovien, '')) like lower(concat('%', cast(:search as String), '%'))
                    or lower(patient.mabenhnhan) like lower(concat('%', cast(:search as String), '%'))
                    or lower(patient.hoten) like lower(concat('%', cast(:search as String), '%'))
                    or lower(coalesce(patient.cccdSo, '')) like lower(concat('%', cast(:search as String), '%'))
                    or lower(coalesce(patient.sodienthoai, '')) like lower(concat('%', cast(:search as String), '%'))
                  )
            """)
    Page<Payment> search(
            @Param("status") PaymentStatus status,
            @Param("type") PaymentType type,
            @Param("search") String search,
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
