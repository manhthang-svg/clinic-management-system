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

    @Column(name = "insurance_social_number", length = 10)
    private String insuranceSocialNumber;

    /** CV 365 I.23. */
    @Column(name = "tungaybhyt")
    private LocalDate tungaybhyt;

    /** CV 365 I.24. */
    @Column(name = "denngaybhyt")
    private LocalDate denngaybhyt;

    /** CV 365 I.26. */
    @Column(name = "noidangkykcbbd", length = 255)
    private String noidangkykcbbd;

    @Column(name = "insurance_primary_facility_code", length = 5)
    private String insurancePrimaryFacilityCode;

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


    public Visit(
            UUID id,
            Instant createdAt,
            String sovaovien,
            UUID patientId,
            UUID roomId,
            UUID registeredByAccountId,
            Instant thoigianvaovien,
            VisitDoituongbnLoai doituongbnLoai,
            String mabhyt,
            String lydotiepnhan,
            String noigioithieuLoai,
            String lydovaovien,
            boolean isPriority,
            PriorityReason priorityReason,
            LocalDate queueDate,
            int queueNumber,
            VisitStatus status,
            VisitInsuranceVerifyStatus insuranceVerifyStatus
    ) {
        this(
                id, createdAt, sovaovien, patientId, roomId, registeredByAccountId, thoigianvaovien,
                doituongbnLoai, mabhyt, null, null, null, null, null, null, null, lydotiepnhan,
                noigioithieuLoai, lydovaovien, isPriority, priorityReason, queueDate, queueNumber,
                status, insuranceVerifyStatus);
    }

    public Visit(
            UUID id,
            Instant createdAt,
            String sovaovien,
            UUID patientId,
            UUID roomId,
            UUID registeredByAccountId,
            Instant thoigianvaovien,
            VisitDoituongbnLoai doituongbnLoai,
            String mabhyt,
            String insuranceSocialNumber,
            LocalDate tungaybhyt,
            LocalDate denngaybhyt,
            String insurancePrimaryFacilityCode,
            String noidangkykcbbd,
            Short insuranceBenefitCode,
            Short insuranceCoverageRate,
            String lydotiepnhan,
            String noigioithieuLoai,
            String lydovaovien,
            boolean isPriority,
            PriorityReason priorityReason,
            LocalDate queueDate,
            int queueNumber,
            VisitStatus status,
            VisitInsuranceVerifyStatus insuranceVerifyStatus
    ) {
        super(id, createdAt);

        this.sovaovien = sovaovien;
        this.patientId = patientId;
        this.roomId = roomId;
        this.registeredByAccountId = registeredByAccountId;
        this.thoigianvaovien = thoigianvaovien;
        this.doituongbnLoai = doituongbnLoai;
        this.mabhyt = mabhyt;
        this.insuranceSocialNumber = insuranceSocialNumber;
        this.tungaybhyt = tungaybhyt;
        this.denngaybhyt = denngaybhyt;
        this.insurancePrimaryFacilityCode = insurancePrimaryFacilityCode;
        this.noidangkykcbbd = noidangkykcbbd;
        this.insuranceBenefitCode = insuranceBenefitCode;
        this.insuranceCoverageRate = insuranceCoverageRate;
        this.lydotiepnhan = lydotiepnhan;
        this.noigioithieuLoai = noigioithieuLoai;
        this.lydovaovien = lydovaovien;
        this.isPriority = isPriority;
        this.priorityReason = priorityReason;
        this.queueDate = queueDate;
        this.queueNumber = queueNumber;
        this.status = status;
        this.insuranceVerifyStatus = insuranceVerifyStatus;
        this.insuranceVerifiedAt = insuranceVerifyStatus == VisitInsuranceVerifyStatus.VALID ? createdAt : null;
        this.isSkipped = false;
    }

    public void linkFollowUpAppointment(UUID appointmentId) {
        this.followupAppointmentId = appointmentId;
    }

    public void updateReasonForVisit(String reasonForVisit, Instant now) {
        this.lydovaovien = reasonForVisit;
        touch(now);
    }

    public void markAsWaiting() {
        if (this.status != VisitStatus.WAITING_FEE_PAYMENT) {
            throw new IllegalStateException(
                    "Lượt khám không ở trạng thái chờ thanh toán"
            );
        }

        this.status = VisitStatus.WAITING;
    }

    public void markAsWaitingParaclinical() {
        this.status = VisitStatus.WAITING_PARACLINICAL;
    }

    public void startExamination(UUID doctorAccountId, Instant startedAt) {
        if (this.status != VisitStatus.WAITING) {
            throw new IllegalStateException("Visit is not waiting for examination");
        }
        this.doctorAccountId = doctorAccountId;
        this.status = VisitStatus.IN_EXAMINATION;
        this.denkhambenhluc = startedAt;
        touch(startedAt);
    }

    /**
     * UC-48 / V11: the Doctor sends the called-in patient to another examination room. The visit is reused and
     * waits there with the new room's queue number; the next Doctor who calls it takes it over.
     */
    public void transferTo(UUID destinationRoomId, int destinationQueueNumber, Instant transferredAt) {
        if (this.status != VisitStatus.IN_EXAMINATION || this.isSkipped) {
            throw new IllegalStateException("Only a visit in examination can be transferred");
        }
        if (this.roomId.equals(destinationRoomId)) {
            throw new IllegalArgumentException("The destination room must differ from the current room");
        }
        this.roomId = destinationRoomId;
        this.queueNumber = destinationQueueNumber;
        this.status = VisitStatus.WAITING;
        this.doctorAccountId = null;
        this.denkhambenhluc = null;
        touch(transferredAt);
    }

    public void continueConclusion(UUID doctorAccountId, Instant continuedAt) {
        if (this.status != VisitStatus.WAITING_CONCLUSION) {
            throw new IllegalStateException("Visit is not waiting for conclusion");
        }
        this.doctorAccountId = doctorAccountId;
        touch(continuedAt);
    }

    public void markSkipped(Instant skippedAt) {
        if (this.status != VisitStatus.WAITING && this.status != VisitStatus.WAITING_CONCLUSION) {
            throw new IllegalStateException("Visit is not waiting for a Doctor call");
        }
        if (this.isSkipped) {
            throw new IllegalStateException("Visit is already skipped");
        }
        this.isSkipped = true;
        this.skippedAt = skippedAt;
        touch(skippedAt);
    }

    /**
     * Accepts a patient who returned after being skipped and resumes the interrupted stage.
     */
    public void acceptSkipped(UUID acceptingDoctorAccountId, Instant acceptedAt) {
        if (!isSkipped) {
            throw new IllegalStateException("Visit is not skipped");
        }

        switch (status) {
            case WAITING -> startExamination(acceptingDoctorAccountId, acceptedAt);
            case WAITING_CONCLUSION -> {
                if (doctorAccountId == null) {
                    throw new IllegalStateException("Visit waiting for conclusion has no assigned Doctor");
                }
                touch(acceptedAt);
            }
            default -> throw new IllegalStateException("Skipped visit is not in a resumable stage");
        }

        isSkipped = false;
        skippedAt = null;
    }

    /** End-of-day job: the patient left before paying the examination fee (BR-22, V04). */
    public void cancelAtEndOfDay(Instant now) {
        if (status != VisitStatus.WAITING_FEE_PAYMENT) {
            throw new IllegalStateException("Visit is not waiting for fee payment");
        }
        status = VisitStatus.CANCELLED;
        touch(now);
    }

    /**
     * End-of-day job: an unfinished visit ends without a conclusion (BR-22, V09, V20). The skip flag is kept as
     * history.
     */
    public void abandonAtEndOfDay(Instant now) {
        switch (status) {
            case WAITING, IN_EXAMINATION, WAITING_PARACLINICAL, WAITING_CONCLUSION -> status = VisitStatus.ABANDONED;
            default -> throw new IllegalStateException("Visit cannot be abandoned from " + status);
        }
        touch(now);
    }
}
