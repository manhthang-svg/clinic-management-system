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

// cac query thong ke (chi doc) cho UC-26 den UC-32
// luot kham tinh theo ngay vao hang cho; thanh toan, CLS, chan doan tinh theo thoi diem xay ra trong [start, end)
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

    // UC-32: 1 ma ICD-10 chan doan chinh
    interface MainDiagnosisCount {
        String getCode();

        String getName();

        long getCases();

        long getPatients();
    }

    // UC-32: 1 ma ICD-10 (hoac nhom) va so ca
    interface CodeCount {
        String getCode();

        String getName();

        long getCases();
    }

    // UC-32: so ket luan trong ky, so co ma ICD-10
    interface ConclusionTotals {
        long getConclusions();

        long getWithIcd();

        long getDistinctCodes();
    }

    @Query("""
            select count(c) as conclusions, count(c.maicd) as withIcd, count(distinct c.maicd) as distinctCodes
            from Conclusion c
            where c.concludedAt >= :start and c.concludedAt < :end
            """)
    ConclusionTotals conclusionTotals(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select c.maicd as code, max(c.tenicd) as name, count(c) as cases, count(distinct v.patientId) as patients
            from Conclusion c, MedicalRecord r, Visit v
            where r.id = c.medicalRecordId and v.id = r.visitId
              and c.maicd is not null and c.concludedAt >= :start and c.concludedAt < :end
            group by c.maicd
            order by count(c) desc, c.maicd
            """)
    List<MainDiagnosisCount> topMainDiagnoses(
            @Param("start") Instant start, @Param("end") Instant end, Pageable limit);

    // nhom benh = 3 ky tu dau cua ma chan doan chinh
    @Query("""
            select substring(c.maicd, 1, 3) as code, max(i.tenicd) as name, count(c) as cases
            from Conclusion c left join Icd10 i on i.maicd = substring(c.maicd, 1, 3)
            where c.maicd is not null and c.concludedAt >= :start and c.concludedAt < :end
            group by substring(c.maicd, 1, 3)
            order by count(c) desc, substring(c.maicd, 1, 3)
            """)
    List<CodeCount> topDiagnosisCategories(@Param("start") Instant start, @Param("end") Instant end, Pageable limit);

    @Query("""
            select s.maicd as code, max(s.tenicd) as name, count(s) as cases
            from ConclusionSecondaryDiagnosis s join s.conclusion c
            where s.maicd is not null and c.concludedAt >= :start and c.concludedAt < :end
            group by s.maicd
            order by count(s) desc, s.maicd
            """)
    List<CodeCount> topSecondaryDiagnoses(@Param("start") Instant start, @Param("end") Instant end, Pageable limit);

    // UC-31: phieu da thu cua 1 ngay, theo loai phieu va phuong thuc
    interface RevenueRow {
        String getDay();

        String getType();

        String getMethod();

        long getPayments();

        BigDecimal getTotalAmount();

        BigDecimal getInsuranceAmount();

        BigDecimal getPatientAmount();
    }

    // doanh thu theo ngay thu (gio Viet Nam), loai phieu, phuong thuc; dung SQL thuan de doi mui gio
    @Query(value = """
            select to_char(p.paid_at at time zone 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') as day,
                   p.type as type,
                   p.payment_method as method,
                   count(*) as payments,
                   coalesce(sum(p.total_amount), 0) as totalAmount,
                   coalesce(sum(p.insurance_total_amount), 0) as insuranceAmount,
                   coalesce(sum(p.patient_total_amount), 0) as patientAmount
            from payments p
            where p.status = 'PAID' and p.paid_at >= :start and p.paid_at < :end
            group by 1, 2, 3
            """, nativeQuery = true)
    List<RevenueRow> revenueByDayTypeAndMethod(@Param("start") Instant start, @Param("end") Instant end);

    // UC-30: so dich vu da thuc hien theo dich vu, phong, trang thai
    interface PerformedServiceCount {
        UUID getServiceId();

        UUID getRoomId();

        ParaclinicalOrderDetailStatus getStatus();

        long getTotal();
    }

    // dich vu da thuc hien trong ky (dang cho ket qua hoac da co ket qua)
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

    // UC-29: luot kham cua 1 phong kham theo trang thai
    interface RoomVisitCount {
        UUID getRoomId();

        VisitStatus getStatus();

        long getTotal();
    }

    // UC-29: dich vu CLS chuyen ve 1 phong theo trang thai
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

    // dich vu chuyen ve phong trong ky (tinh theo luc chuyen phong)
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

    // UC-28: luot kham cua 1 bac si theo trang thai
    interface DoctorStatusCount {
        UUID getDoctorId();

        VisitStatus getStatus();

        long getTotal();
    }

    // UC-28: so benh nhan khac nhau cua 1 bac si
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

    // UC-28: moi tai khoan bac si + tai khoan khac co kham trong ky
    @Query("""
            select a from StaffAccount a
            where a.role = clinic.backend.enums.Role.DOCTOR or a.id in :ids
            """)
    List<StaffAccount> doctorsOrAccounts(@Param("ids") Collection<UUID> ids);

    // UC-27: 1 dong / (ngay, trang thai, doi tuong, uu tien); followUps = so luot tai kham
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
