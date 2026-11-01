package clinic.backend.repository;

import clinic.backend.entity.Visit;
import clinic.backend.enums.VisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    List<VisitStatus> FINISHED_STATUSES = List.of(
            VisitStatus.COMPLETED, VisitStatus.CANCELLED, VisitStatus.ABANDONED);

    boolean existsBySovaovien(String sovaovien);

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

    /** Number of queued visits of one room per status and skip flag (UC-42). */
    interface RoomQueueCount {
        UUID getRoomId();

        VisitStatus getStatus();

        Boolean getSkipped();

        long getTotal();
    }
}
