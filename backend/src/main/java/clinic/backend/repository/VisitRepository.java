package clinic.backend.repository;

import clinic.backend.entity.Visit;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import jakarta.persistence.LockModeType;
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
}
