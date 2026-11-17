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

    /** TT 32/2023/TT-BYT mẫu 15/BV1 mục I.13; CV 365 II.7, III.17. BR-129: only when referred by a facility. */
    @Column(name = "chandoancuanoigioithieu")
    private String chandoancuanoigioithieu;

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

    /** Null while the examination fee is unpaid; issued when it is paid (I1-D48). */
    @Column(name = "queue_number")
    private Integer queueNumber;

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
            Integer queueNumber,
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
            Integer queueNumber,
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

    /** BR-94: the referral source "Y tế" (TT 32/2023/TT-BYT mẫu 15/BV1 mục I.13). */
    public static final String REFERRAL_HEALTH_FACILITY = "CO_SO_Y_TE";

    public boolean isReferredByHealthFacility() {
        return REFERRAL_HEALTH_FACILITY.equals(noigioithieuLoai);
    }

    /** BR-129: kept only when the visit was referred by a health facility; cleared otherwise. */
    public void updateReferralDiagnosis(String diagnosis, Instant now) {
        this.chandoancuanoigioithieu = isReferredByHealthFacility() ? diagnosis : null;
        touch(now);
    }

    public void updateReasonForVisit(String reasonForVisit, Instant now) {
        this.lydovaovien = reasonForVisit;
        touch(now);
    }

    /** BR-15, I1-D48: the examination fee is paid; the visit joins the waiting list with its new queue number. */
    public void markAsWaiting(int queueNumber) {
        if (this.status != VisitStatus.WAITING_FEE_PAYMENT) {
            throw new IllegalStateException(
                    "Lượt khám không ở trạng thái chờ thanh toán"
            );
        }

        this.queueNumber = queueNumber;
        this.status = VisitStatus.WAITING;
    }

    /** A valid-card insurance visit: billed with the BR-27 split. */
    public boolean isInsuranceCovered() {
        return doituongbnLoai == VisitDoituongbnLoai.BHYT
                && insuranceVerifyStatus == VisitInsuranceVerifyStatus.VALID
                && insuranceCoverageRate != null;
    }

    /**
     * BR-27: the applied coverage rate is recalculated while the visit is open and fixed once the doctor confirms
     * the conclusion (Completed), or when the visit ends otherwise.
     */
    public boolean isCoverageOpen() {
        return isInsuranceCovered()
                && status != VisitStatus.COMPLETED
                && status != VisitStatus.CANCELLED
                && status != VisitStatus.ABANDONED;
    }

    /**
     * BR-15, BR-26: an insurance visit pays its examination and paraclinical fees only after the conclusion, when
     * the applied coverage rate is fixed (decision I1-D43).
     */
    public boolean awaitsConclusionBeforePayment() {
        return doituongbnLoai == VisitDoituongbnLoai.BHYT && status != VisitStatus.COMPLETED;
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

    /**
     * UC-67, BR-41 (V14, S5): the last paraclinical service of the visit has finished, so the visit joins the
     * conclusion waiting list of its room.
     */
    public void markAsWaitingConclusion(Instant now) {
        if (this.status != VisitStatus.WAITING_PARACLINICAL) {
            throw new IllegalStateException("Visit is not waiting for paraclinical services");
        }
        this.status = VisitStatus.WAITING_CONCLUSION;
        touch(now);
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

    /** UC-58 BR-58-04: the Doctor confirmed the conclusion and closed the record (BR-32, V19). */
    public void complete(Instant now) {
        if (isSkipped
                || (status != VisitStatus.IN_EXAMINATION && status != VisitStatus.WAITING_CONCLUSION)) {
            throw new IllegalStateException("Visit cannot be completed from " + status);
        }
        status = VisitStatus.COMPLETED;
        completedAt = now;
        touch(now);
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
