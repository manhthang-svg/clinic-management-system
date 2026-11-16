package clinic.backend.repository;

import clinic.backend.entity.Visit;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    /** Locks the visit row so two transfers (or a transfer and another Doctor action) cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Visit v where v.id = :id")
    Optional<Visit> findByIdForUpdate(@Param("id") UUID id);

    List<VisitStatus> FINISHED_STATUSES = List.of(
            VisitStatus.COMPLETED, VisitStatus.CANCELLED, VisitStatus.ABANDONED);

    boolean existsBySovaovien(String sovaovien);

    /** End-of-day job (BR-22): visits of earlier days that are not finished, oldest first. */
    @Query("""
            select v.id from Visit v
            where v.queueDate < :date and v.status not in :finishedStatuses
            order by v.queueDate, v.thoigianvaovien
            """)
    List<UUID> findUnfinishedIdsBefore(
            @Param("date") LocalDate date,
            @Param("finishedStatuses") Collection<VisitStatus> finishedStatuses);

    /** UC-77 BR-77-02: the facility code is locked once any visit exists. */
    @Query("select count(v) > 0 from Visit v")
    boolean existsAny();

    Optional<Visit> findBySovaovien(String sovaovien);

    Optional<Visit> findFirstByPatientIdAndStatusNotInOrderByThoigianvaovienDesc(
            UUID patientId, Collection<VisitStatus> statuses);

    boolean existsByPatientIdAndStatusNotIn(UUID patientId, Collection<VisitStatus> statuses);

    @Query(value = "select nextval('registration_code_seq')", nativeQuery = true)
    long nextRegistrationCode();

    @Query(value = "select nextval('payment_code_seq')", nativeQuery = true)
    long nextPaymentCode();

    boolean existsByPatientIdAndQueueDateAndRoomIdAndStatusIn(
            UUID patientId,
            LocalDate queueDate,
            UUID roomId,
            Collection<VisitStatus> statuses
    );

    @Query("""
            select count(v) > 0 from Visit v
            where v.roomId = :roomId
              and v.queueDate = :queueDate
              and v.status not in :finishedStatuses
            """)
    boolean hasActiveVisitsToday(
            @Param("roomId") UUID roomId,
            @Param("queueDate") LocalDate queueDate,
            @Param("finishedStatuses") Collection<VisitStatus> finishedStatuses
    );

    @Query("""
            select distinct v.roomId from Visit v
            where v.roomId is not null
              and v.queueDate = :queueDate
              and v.status not in :finishedStatuses
            """)
    java.util.Set<UUID> findRoomIdsWithActiveVisitsToday(
            @Param("queueDate") LocalDate queueDate,
            @Param("finishedStatuses") Collection<VisitStatus> finishedStatuses
    );

    @Query("""
            select coalesce(max(v.queueNumber), 0)
            from Visit v
            where v.queueDate = :queueDate
              and v.roomId = :roomId
            """)
    int findMaxQueueNumber(
            @Param("queueDate") LocalDate queueDate,
            @Param("roomId") UUID roomId
    );

    long countByDoctorAccountIdAndStatus(
            UUID doctorAccountId,
            VisitStatus status
    );

    @Query("""
            select v from Visit v
            where v.roomId = :roomId
              and v.queueDate = :queueDate
              and v.status in (
                  clinic.backend.enums.VisitStatus.WAITING,
                  clinic.backend.enums.VisitStatus.IN_EXAMINATION,
                  clinic.backend.enums.VisitStatus.WAITING_PARACLINICAL,
                  clinic.backend.enums.VisitStatus.WAITING_CONCLUSION
              )
            order by v.isPriority desc, v.queueNumber asc
            """)
    List<Visit> findRoomQueue(
            @Param("roomId") UUID roomId,
            @Param("queueDate") LocalDate queueDate
    );

    List<Visit> findAllByRoomIdAndQueueDateAndStatusIn(
            UUID roomId,
            LocalDate queueDate,
            Collection<VisitStatus> statuses);

    @Query("""
            select v from Visit v
            where v.queueDate = :queueDate
              and v.status in (
                  clinic.backend.enums.VisitStatus.WAITING,
                  clinic.backend.enums.VisitStatus.IN_EXAMINATION,
                  clinic.backend.enums.VisitStatus.WAITING_PARACLINICAL,
                  clinic.backend.enums.VisitStatus.WAITING_CONCLUSION
              )
            order by v.isPriority desc, v.queueNumber asc
            """)
    List<Visit> findAllByQueueDate(
            @Param("queueDate") LocalDate queueDate
    );

    @Query("""
            select v.roomId as roomId, v.status as status, v.isSkipped as skipped, count(v) as total
            from Visit v
            where v.queueDate = :queueDate
              and v.status in (
                  clinic.backend.enums.VisitStatus.WAITING_FEE_PAYMENT,
                  clinic.backend.enums.VisitStatus.WAITING,
                  clinic.backend.enums.VisitStatus.IN_EXAMINATION,
                  clinic.backend.enums.VisitStatus.WAITING_PARACLINICAL,
                  clinic.backend.enums.VisitStatus.WAITING_CONCLUSION
              )
            group by v.roomId, v.status, v.isSkipped
            """)
    List<RoomQueueCount> countRoomQueues(
            @Param("queueDate") LocalDate queueDate
    );

    /** UC-35: registration time of the latest visit of each patient. */
    @Query("""
            select v.patientId as patientId, max(v.thoigianvaovien) as lastVisitAt
            from Visit v
            where v.patientId in :patientIds
            group by v.patientId
            """)
    List<PatientLastVisit> findLastVisitTimes(@Param("patientIds") Collection<UUID> patientIds);

    /** UC-35: the active visit of each patient (at most one per patient, BR-17). */
    @Query("""
            select v.patientId as patientId, v.sovaovien as registrationCode, v.status as status,
                   room.roomName as roomName
            from Visit v
            left join Room room on room.id = v.roomId
            where v.patientId in :patientIds
              and v.status not in :finishedStatuses
            """)
    List<PatientActiveVisit> findActiveVisits(
            @Param("patientIds") Collection<UUID> patientIds,
            @Param("finishedStatuses") Collection<VisitStatus> finishedStatuses);

    /** UC-36: visit history of one patient, newest first, without clinical details. */
    @Query("""
            select v.id as visitId, v.sovaovien as registrationCode, v.thoigianvaovien as registeredAt,
                   room.roomName as roomName, doctor.academicTitle as doctorTitle, doctor.fullName as doctorName,
                   v.doituongbnLoai as visitType, v.status as status, v.mabhyt as insuranceCard
            from Visit v
            left join Room room on room.id = v.roomId
            left join StaffAccount doctor on doctor.id = v.doctorAccountId
            where v.patientId = :patientId
            order by v.thoigianvaovien desc
            """)
    List<PatientVisitHistory> findPatientHistory(@Param("patientId") UUID patientId);

    /** Previous visits shown to a Doctor while examining the patient (BR-35). */
    @Query("""
            select v.id as visitId, v.thoigianvaovien as visitedAt, v.lydovaovien as reasonForVisit,
                   coalesce(conclusion.chandoan, examination.chandoansobo) as diagnosis
            from Visit v
            left join MedicalRecord record on record.visitId = v.id
            left join ClinicalExamination examination on examination.medicalRecordId = record.id
            left join Conclusion conclusion on conclusion.medicalRecordId = record.id
            where v.patientId = :patientId and v.id <> :currentVisitId
            order by v.thoigianvaovien desc
            """)
    List<DoctorVisitHistory> findDoctorVisitHistory(
            @Param("patientId") UUID patientId,
            @Param("currentVisitId") UUID currentVisitId);

    interface DoctorVisitHistory {
        UUID getVisitId();

        Instant getVisitedAt();

        String getReasonForVisit();

        String getDiagnosis();
    }

    interface PatientVisitHistory {
        UUID getVisitId();

        String getRegistrationCode();

        Instant getRegisteredAt();

        String getRoomName();

        String getDoctorTitle();

        String getDoctorName();

        VisitDoituongbnLoai getVisitType();

        VisitStatus getStatus();

        String getInsuranceCard();
    }

    interface PatientLastVisit {
        UUID getPatientId();

        Instant getLastVisitAt();
    }

    interface PatientActiveVisit {
        UUID getPatientId();

        String getRegistrationCode();

        VisitStatus getStatus();

        String getRoomName();
    }

    /** Number of queued visits of one room per status and skip flag (UC-42). */
    interface RoomQueueCount {
        UUID getRoomId();

        VisitStatus getStatus();

        Boolean getSkipped();

        long getTotal();
    }

    /** UC-34: one visit the Doctor examined, with its patient, room and main diagnosis (null until concluded). */
    interface ExaminedVisitRow {
        UUID getVisitId();

        String getRegistrationCode();

        LocalDate getQueueDate();

        Instant getExaminedAt();

        VisitStatus getStatus();

        VisitDoituongbnLoai getPatientType();

        UUID getPatientId();

        String getPatientCode();

        String getPatientName();

        short getGender();

        LocalDate getDateOfBirth();

        String getRoomName();

        String getIcdCode();

        String getIcdName();
    }

    /**
     * UC-34 (BR-39): visits examined by {@code doctorId} in {@code [from, to]} (queue date), newest first.
     * {@code keyword} ("" for all) matches the patient code, or the name ignoring case and accents.
     */
    @Query(value = """
            select v.id as visitId, v.sovaovien as registrationCode, v.queueDate as queueDate,
                   v.denkhambenhluc as examinedAt, v.status as status, v.doituongbnLoai as patientType,
                   p.id as patientId, p.mabenhnhan as patientCode, p.hoten as patientName, p.gioitinh as gender,
                   p.ngaysinh as dateOfBirth, r.roomName as roomName, c.maicd as icdCode, c.tenicd as icdName
            from Visit v
            join Patient p on p.id = v.patientId
            left join Room r on r.id = v.roomId
            left join MedicalRecord m on m.visitId = v.id
            left join Conclusion c on c.medicalRecordId = m.id
            where v.doctorAccountId = :doctorId and v.queueDate between :from and :to
              and (lower(p.mabenhnhan) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(function('unaccent', p.hoten))
                      like lower(function('unaccent', concat('%', cast(:keyword as String), '%'))))
            order by v.queueDate desc, v.denkhambenhluc desc nulls last, v.id
            """,
            countQuery = """
            select count(v)
            from Visit v
            join Patient p on p.id = v.patientId
            where v.doctorAccountId = :doctorId and v.queueDate between :from and :to
              and (lower(p.mabenhnhan) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(function('unaccent', p.hoten))
                      like lower(function('unaccent', concat('%', cast(:keyword as String), '%'))))
            """)
    Page<ExaminedVisitRow> findExaminedVisits(
            @Param("doctorId") UUID doctorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** UC-34 totals of the period: examined visits and distinct patients (the keyword does not apply). */
    interface ExaminedTotals {
        long getVisits();

        long getPatients();
    }

    @Query("""
            select count(v) as visits, count(distinct v.patientId) as patients
            from Visit v
            where v.doctorAccountId = :doctorId and v.queueDate between :from and :to
            """)
    ExaminedTotals countExamined(
            @Param("doctorId") UUID doctorId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
