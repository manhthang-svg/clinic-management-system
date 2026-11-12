package clinic.backend.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.entity.StaffAccount;
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

    /** UC-30: performed services of one service in one room, by status (awaiting result or with result). */
    interface PerformedServiceCount {
        UUID getServiceId();

        UUID getRoomId();

        ParaclinicalOrderDetailStatus getStatus();

        long getTotal();
    }

    /** Services performed in {@code [start, end)}: still awaiting their result, or with the result submitted. */
    @Query("""
            select d.serviceId as serviceId, d.roomId as roomId, d.status as status, count(d) as total
            from ParaclinicalOrderDetail d
            where d.performedAt >= :start and d.performedAt < :end
              and d.status in (clinic.backend.enums.ParaclinicalOrderDetailStatus.PENDING_RESULT,
                               clinic.backend.enums.ParaclinicalOrderDetailStatus.COMPLETED)
            group by d.serviceId, d.roomId, d.status
            """)
    List<PerformedServiceCount> countPerformedServices(@Param("start") Instant start, @Param("end") Instant end);

    @Query("select s from MedicalService s where s.id in :ids")
    List<MedicalService> servicesByIds(@Param("ids") Collection<UUID> ids);

    @Query("select g from ServiceGroup g where g.id in :ids")
    List<ServiceGroup> serviceGroupsByIds(@Param("ids") Collection<UUID> ids);

    @Query("select r from Room r where r.id in :ids")
    List<Room> roomsByIds(@Param("ids") Collection<UUID> ids);

    /** UC-29: visits registered in one examination room, by status. */
    interface RoomVisitCount {
        UUID getRoomId();

        VisitStatus getStatus();

        long getTotal();
    }

    /** UC-29: paraclinical services sent to one room, by status. */
    interface RoomServiceCount {
        UUID getRoomId();

        ParaclinicalOrderDetailStatus getStatus();

        long getTotal();
    }

    @Query("""
            select v.roomId as roomId, v.status as status, count(v) as total
            from Visit v
            where v.queueDate between :from and :to
            group by v.roomId, v.status
            """)
    List<RoomVisitCount> countVisitsByRoomAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Services sent to a room in {@code [start, end)}: they reached the room when they were released to it. */
    @Query("""
            select d.roomId as roomId, d.status as status, count(d) as total
            from ParaclinicalOrderDetail d
            where d.roomId is not null and d.releasedAt >= :start and d.releasedAt < :end
            group by d.roomId, d.status
            """)
    List<RoomServiceCount> countServicesByRoomAndStatus(@Param("start") Instant start, @Param("end") Instant end);

    @Query("select r from Room r")
    List<Room> allRooms();

    @Query("select d from Department d")
    List<Department> allDepartments();

    /** UC-28: visits of one doctor in one status. */
    interface DoctorStatusCount {
        UUID getDoctorId();

        VisitStatus getStatus();

        long getTotal();
    }

    /** UC-28: distinct patients of one doctor. */
    interface DoctorPatientCount {
        UUID getDoctorId();

        long getTotal();
    }

    @Query("""
            select v.doctorAccountId as doctorId, v.status as status, count(v) as total
            from Visit v
            where v.queueDate between :from and :to and v.doctorAccountId is not null
            group by v.doctorAccountId, v.status
            """)
    List<DoctorStatusCount> countVisitsByDoctorAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select v.doctorAccountId as doctorId, count(distinct v.patientId) as total
            from Visit v
            where v.queueDate between :from and :to and v.doctorAccountId is not null
            group by v.doctorAccountId
            """)
    List<DoctorPatientCount> countPatientsByDoctor(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select count(v)
            from Visit v
            where v.queueDate between :from and :to and v.doctorAccountId is null
            """)
    long countVisitsWithoutDoctor(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** UC-28: every Doctor account, and any other account that examined patients in the period. */
    @Query("""
            select a from StaffAccount a
            where a.role = clinic.backend.enums.Role.DOCTOR or a.id in :ids
            """)
    List<StaffAccount> doctorsOrAccounts(@Param("ids") Collection<UUID> ids);

    /** UC-27: one row per (day, status, patient type, priority); {@code followUps} counts follow-up visits. */
    interface VisitBreakdown {
        LocalDate getDay();

        VisitStatus getStatus();

        VisitDoituongbnLoai getPatientType();

        boolean getPriority();

        long getTotal();

        long getFollowUps();
    }

    @Query("""
            select v.queueDate as day, v.status as status, v.doituongbnLoai as patientType,
                   v.isPriority as priority, count(v) as total, count(v.followupAppointmentId) as followUps
            from Visit v
            where v.queueDate between :from and :to
            group by v.queueDate, v.status, v.doituongbnLoai, v.isPriority
            """)
    List<VisitBreakdown> visitBreakdown(@Param("from") LocalDate from, @Param("to") LocalDate to);

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
