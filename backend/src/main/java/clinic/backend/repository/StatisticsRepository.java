package clinic.backend.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.Visit;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregate queries for UC-26 View Statistics Dashboard.
 * Visits are counted by their queue date; payments, paraclinical services and diagnoses by the
 * instant they happened, within {@code [start, end)}.
 */
public interface StatisticsRepository extends Repository<Visit, UUID> {

    interface StatusCount {
        VisitStatus getStatus();

        long getTotal();
    }

    interface PatientTypeCount {
        VisitDoituongbnLoai getPatientType();

        long getTotal();
    }

    interface DayCount {
        LocalDate getDay();

        long getTotal();
    }

    interface RoomCount {
        UUID getRoomId();

        String getRoomName();

        long getTotal();
    }

    interface DiagnosisCount {
        String getCode();

        String getName();

        long getTotal();
    }

    interface RevenueTotals {
        long getPaymentCount();

        BigDecimal getTotalAmount();

        BigDecimal getInsuranceAmount();

        BigDecimal getPatientAmount();
    }

    @Query("""
            select v.status as status, count(v) as total
            from Visit v
            where v.queueDate between :from and :to
            group by v.status
            """)
    List<StatusCount> countVisitsByStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select v.doituongbnLoai as patientType, count(v) as total
            from Visit v
            where v.queueDate between :from and :to
            group by v.doituongbnLoai
            """)
    List<PatientTypeCount> countVisitsByPatientType(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select count(distinct v.patientId)
            from Visit v
            where v.queueDate between :from and :to
            """)
    long countDistinctPatients(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select v.queueDate as day, count(v) as total
            from Visit v
            where v.queueDate between :from and :to
            group by v.queueDate
            order by v.queueDate
            """)
    List<DayCount> countVisitsByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select v.roomId as roomId, r.roomName as roomName, count(v) as total
            from Visit v join Room r on r.id = v.roomId
            where v.queueDate between :from and :to
            group by v.roomId, r.roomName
            order by count(v) desc, r.roomName
            """)
    List<RoomCount> topRoomsByVisits(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable limit);

    @Query("""
            select count(d)
            from ParaclinicalOrderDetail d
            where d.status = :status and d.performedAt >= :start and d.performedAt < :end
            """)
    long countParaclinicalServices(
            @Param("status") ParaclinicalOrderDetailStatus status,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            select count(p) as paymentCount,
                   coalesce(sum(p.totalAmount), 0) as totalAmount,
                   coalesce(sum(p.insuranceTotalAmount), 0) as insuranceAmount,
                   coalesce(sum(p.patientTotalAmount), 0) as patientAmount
            from Payment p
            where p.status = :status and p.paidAt >= :start and p.paidAt < :end
            """)
    RevenueTotals sumRevenue(
            @Param("status") PaymentStatus status,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            select c.maicd as code, c.tenicd as name, count(c) as total
            from Conclusion c
            where c.maicd is not null and c.concludedAt >= :start and c.concludedAt < :end
            group by c.maicd, c.tenicd
            order by count(c) desc, c.maicd
            """)
    List<DiagnosisCount> topDiagnoses(@Param("start") Instant start, @Param("end") Instant end, Pageable limit);
}
