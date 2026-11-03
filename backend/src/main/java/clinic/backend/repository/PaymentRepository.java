package clinic.backend.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.Payment;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * UC-70 / UC-69: receipts of the given statuses created in [from, to). The keyword matches the receipt number,
     * patient code, registration code (case-insensitive) or patient name (case- and accent-insensitive).
     */
    @Query("""
            select p from Payment p
            join Visit v on v.id = p.visitId
            join Patient patient on patient.id = v.patientId
            where p.status in :statuses
              and (:type is null or p.type = :type)
              and p.createdAt >= :fromTime and p.createdAt < :toTime
              and (
                    cast(:search as String) is null
                    or lower(p.paymentCode) like lower(concat('%', cast(:search as String), '%'))
                    or lower(coalesce(p.receiptNumber, '')) like lower(concat('%', cast(:search as String), '%'))
                    or lower(coalesce(v.sovaovien, '')) like lower(concat('%', cast(:search as String), '%'))
                    or lower(patient.mabenhnhan) like lower(concat('%', cast(:search as String), '%'))
                    or lower(function('unaccent', patient.hoten))
                        like lower(function('unaccent', concat('%', cast(:search as String), '%')))
                  )
            """)
    Page<Payment> search(
            @Param("statuses") Collection<PaymentStatus> statuses,
            @Param("type") PaymentType type,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            @Param("search") String search,
            Pageable pageable
    );

    /** UC-73: paid or cancelled receipts whose paid / cancelled time is in [from, to). */
    @Query("""
            select p from Payment p
            where p.status in :statuses
              and (:type is null or p.type = :type)
              and (:method is null or p.paymentMethod = :method)
              and (:cashierId is null or p.cashierAccountId = :cashierId)
              and coalesce(p.paidAt, p.cancelledAt) >= :fromTime
              and coalesce(p.paidAt, p.cancelledAt) < :toTime
            """)
    Page<Payment> history(
            @Param("statuses") Collection<PaymentStatus> statuses,
            @Param("type") PaymentType type,
            @Param("method") PaymentMethod method,
            @Param("cashierId") UUID cashierId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );

    /** UC-73 step 4: patient amount collected per payment method for the same filters (Paid receipts only). */
    @Query("""
            select p.paymentMethod as method, sum(p.patientTotalAmount) as total, count(p) as count
            from Payment p
            where p.status = clinic.backend.enums.PaymentStatus.PAID
              and (:type is null or p.type = :type)
              and (:method is null or p.paymentMethod = :method)
              and (:cashierId is null or p.cashierAccountId = :cashierId)
              and p.paidAt >= :fromTime and p.paidAt < :toTime
            group by p.paymentMethod
            """)
    List<MethodTotal> historyTotals(
            @Param("type") PaymentType type,
            @Param("method") PaymentMethod method,
            @Param("cashierId") UUID cashierId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime
    );

    interface MethodTotal {
        PaymentMethod getMethod();

        BigDecimal getTotal();

        Long getCount();
    }

    List<Payment> findByVisitId(UUID visitId);

    Optional<Payment> findByPayosOrderCode(Long payosOrderCode);

    boolean existsByPayosOrderCode(Long payosOrderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId") UUID paymentId
    );
}
