package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.FollowUpAppointmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A follow-up appointment created at conclusion (BR-34).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "follow_up_appointments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_follow_up_appointments_medical_record_id", columnNames = "medical_record_id"),
        @UniqueConstraint(name = "uq_follow_up_appointments_so_giay_hen", columnNames = "so_giay_hen")
})
public class FollowUpAppointment extends VersionedEntity {

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(name = "so_giay_hen", length = 50)
    private String soGiayHen;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_account_id", nullable = false)
    private UUID doctorAccountId;

    @Column(name = "maicd", length = 10)
    private String maicd;

    @Column(name = "follow_up_date", nullable = false)
    private LocalDate followUpDate;

    /** CV 365 III.31.2. */
    @Column(name = "loidanbacsi")
    private String loidanbacsi;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FollowUpAppointmentStatus status;

    @Column(name = "arrived_visit_id")
    private UUID arrivedVisitId;

    @Column(name = "printed_at")
    private Instant printedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_by_account_id", nullable = false)
    private UUID createdByAccountId;

    public void markArrived(UUID visitId, Instant now) {
        if (status != FollowUpAppointmentStatus.SCHEDULED) {
            throw new IllegalStateException("Follow-up appointment is no longer scheduled");
        }
        status = FollowUpAppointmentStatus.ARRIVED;
        arrivedVisitId = visitId;
        touch(now);
    }

    public void cancel(String reason, Instant now) {
        if (status != FollowUpAppointmentStatus.SCHEDULED) {
            throw new IllegalStateException("Follow-up appointment is no longer scheduled");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
        status = FollowUpAppointmentStatus.CANCELLED;
        cancelReason = reason.strip();
        touch(now);
    }
}
