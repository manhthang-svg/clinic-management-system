package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.PriorityReason;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
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
 * One visit of a patient, from registration to completion (I.2 sovaovien joins every export group).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "visits", uniqueConstraints = {
        @UniqueConstraint(name = "uq_visits_sovaovien", columnNames = "sovaovien")
})
public class Visit extends VersionedEntity {

    /** CV 365 I.2, II.1, IV.1, V.1, VI.1, VII.1. */
    @Column(name = "sovaovien", nullable = false, updatable = false, length = 100)
    private String sovaovien;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "doctor_account_id")
    private UUID doctorAccountId;

    @Column(name = "followup_appointment_id")
    private UUID followupAppointmentId;

    @Column(name = "registered_by_account_id", nullable = false)
    private UUID registeredByAccountId;

    /** CV 365 I.7. */
    @Column(name = "thoigianvaovien", nullable = false)
    private Instant thoigianvaovien;

    /** CV 365 II.15. */
    @Column(name = "denkhambenhluc")
    private Instant denkhambenhluc;

    /** CV 365 I.39. */
    @Enumerated(EnumType.STRING)
    @Column(name = "doituongbn_loai", nullable = false, length = 4)
    private VisitDoituongbnLoai doituongbnLoai;

    /** CV 365 I.25. */
    @Column(name = "mabhyt", length = 15)
    private String mabhyt;

    /** CV 365 I.23. */
    @Column(name = "tungaybhyt")
    private LocalDate tungaybhyt;

    /** CV 365 I.24. */
    @Column(name = "denngaybhyt")
    private LocalDate denngaybhyt;

    /** CV 365 I.26. */
    @Column(name = "noidangkykcbbd", length = 255)
    private String noidangkykcbbd;

    @Column(name = "insurance_benefit_code")
    private Short insuranceBenefitCode;

    @Column(name = "insurance_coverage_rate")
    private Short insuranceCoverageRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_verify_status", length = 20)
    private VisitInsuranceVerifyStatus insuranceVerifyStatus;

    @Column(name = "insurance_verified_at")
    private Instant insuranceVerifiedAt;

    /** CV 365 I.43. */
    @Column(name = "lydotiepnhan")
    private String lydotiepnhan;

    /** CV 365 I.44. */
    @Column(name = "noigioithieu_loai", length = 200)
    private String noigioithieuLoai;

    /** CV 365 II.16. */
    @Column(name = "lydovaovien", nullable = false)
    private String lydovaovien;

    @Column(name = "is_priority", nullable = false)
    private boolean isPriority;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_reason", length = 20)
    private PriorityReason priorityReason;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "queue_number", nullable = false)
    private int queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VisitStatus status;

    @Column(name = "is_skipped", nullable = false)
    private boolean isSkipped;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
