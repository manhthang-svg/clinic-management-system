package clinic.backend.repository;

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

    /** At most one appointment per visit record (uq_follow_up_appointments_medical_record_id). */
    Optional<FollowUpAppointment> findByMedicalRecordId(UUID medicalRecordId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from FollowUpAppointment appointment where appointment.id = :id")
    Optional<FollowUpAppointment> findByIdForUpdate(@Param("id") UUID id);
}
