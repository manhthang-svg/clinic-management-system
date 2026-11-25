package clinic.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.enums.FollowUpAppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpAppointmentRepository extends JpaRepository<FollowUpAppointment, UUID> {

    List<FollowUpAppointment> findByPatientIdAndStatusOrderByFollowUpDateAsc(
            UUID patientId, FollowUpAppointmentStatus status);

    /** BR-124: scheduled appointments dated before {@code date}, the candidates for "Lỡ hẹn". */
    List<FollowUpAppointment> findByStatusAndFollowUpDateBefore(FollowUpAppointmentStatus status, LocalDate date);

    /** At most one appointment per visit record (uq_follow_up_appointments_medical_record_id). */
    Optional<FollowUpAppointment> findByMedicalRecordId(UUID medicalRecordId);

    /**
     * UC-75: scheduled appointments of {@code followUpDate} without a reminder whose patient has an email address
     * (idx_follow_up_appointments_reminder_due).
     */
    @Query("""
            select appointment.id from FollowUpAppointment appointment, Patient patient
            where patient.id = appointment.patientId
              and appointment.status = clinic.backend.enums.FollowUpAppointmentStatus.SCHEDULED
              and appointment.followUpDate = :followUpDate
              and appointment.reminderSentAt is null
              and patient.email is not null and trim(patient.email) <> ''
            order by appointment.id
            """)
    List<UUID> findReminderDueIds(@Param("followUpDate") LocalDate followUpDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from FollowUpAppointment appointment where appointment.id = :id")
    Optional<FollowUpAppointment> findByIdForUpdate(@Param("id") UUID id);
}
