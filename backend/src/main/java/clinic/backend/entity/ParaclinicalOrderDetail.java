package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One ordered service on a paraclinical order (V.2.6).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "paraclinical_order_details", uniqueConstraints = {
        @UniqueConstraint(name = "uq_paraclinical_order_details_machidinh", columnNames = "machidinh")
})
public class ParaclinicalOrderDetail extends VersionedEntity {

    /** Cancel reason of a service abandoned with its visit (docs/state-transitions.md, `BO_KHAM`). */
    public static final String ABANDONED_REASON = "BO_KHAM";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private ParaclinicalOrder order;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    /** CV 365 V.2.6.1, VI.2.2, VII.2.13.1. */
    @Column(name = "machidinh", nullable = false, updatable = false, length = 255)
    private String machidinh;

    /** CV 365 V.2.6.2. */
    @Column(name = "ngaychidinh", nullable = false)
    private Instant ngaychidinh;

    /** CV 365 V.2.6.10. */
    @Column(name = "tenchidinh", nullable = false, length = 255)
    private String tenchidinh;

    /** CV 365 V.2.6.5. */
    @Column(name = "loaichidinh", length = 255)
    private String loaichidinh;

    /** CV 365 V.2.6.6. */
    @Column(name = "loaimachidinh", length = 255)
    private String loaimachidinh;

    /** CV 365 V.2.6.9. */
    @Column(name = "soluong", nullable = false, precision = 15, scale = 2)
    private BigDecimal soluong;

    /** CV 365 V.2.6.4. */
    @Column(name = "dongia", nullable = false, precision = 15, scale = 0)
    private BigDecimal dongia;

    /** CV 365 V.2.6.11. */
    @Column(name = "thanhtien", nullable = false, precision = 15, scale = 0)
    private BigDecimal thanhtien;

    @Column(name = "bhyt_chitra", nullable = false, precision = 15, scale = 0)
    private BigDecimal bhytChitra;

    /** CV 365 V.2.6.3. */
    @Column(name = "benhnhanphaitra", nullable = false, precision = 15, scale = 0)
    private BigDecimal benhnhanphaitra;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "queue_number")
    private Integer queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParaclinicalOrderDetailStatus status;

    @Column(name = "is_skipped", nullable = false)
    private boolean isSkipped;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "performer_account_id")
    private UUID performerAccountId;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "performed_at")
    private Instant performedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_by_account_id")
    private UUID cancelledByAccountId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** MRI screening: metal object in the body; null when not asked (V32). */
    @Column(name = "has_metal_implant")
    private Boolean hasMetalImplant;

    /** MRI screening: earlier lab and imaging results (V32). */
    @Column(name = "prior_results")
    private String priorResults;

    public ParaclinicalOrderDetail(
            UUID id,
            Instant createdAt,
            ParaclinicalOrder order,
            UUID serviceId,
            String machidinh,
            Instant ngaychidinh,
            String tenchidinh,
            String loaichidinh,
            String loaimachidinh,
            BigDecimal soluong,
            BigDecimal dongia,
            BigDecimal thanhtien,
            BigDecimal bhytChitra,
            BigDecimal benhnhanphaitra,
            UUID roomId,
            ParaclinicalOrderDetailStatus status
    ) {
        super(id, createdAt);
        this.order = order;
        this.serviceId = serviceId;
        this.machidinh = machidinh;
        this.ngaychidinh = ngaychidinh;
        this.tenchidinh = tenchidinh;
        this.loaichidinh = loaichidinh;
        this.loaimachidinh = loaimachidinh;
        this.soluong = soluong;
        this.dongia = dongia;
        this.thanhtien = thanhtien;
        this.bhytChitra = bhytChitra;
        this.benhnhanphaitra = benhnhanphaitra;
        this.roomId = roomId;
        this.status = status;
        this.isSkipped = false;
    }

    /** BR-27: keeps the order line's split equal to its receipt line after the visit's coverage rate changes. */
    public void syncAmounts(PaymentItem item) {
        if (item == null || !getId().equals(item.getOrderDetailId())) {
            throw new IllegalArgumentException("Dòng phiếu thu không thuộc chỉ định này");
        }
        this.bhytChitra = item.getBhytChitra();
        this.benhnhanphaitra = item.getBenhnhanphaitra();
    }

    /**
     * BR-25: a service can be cancelled while no Technician has accepted it, i.e. it is still pending payment or
     * waiting (including skipped). Whether its receipt allows it is checked by the receipt.
     */
    public boolean isCancellableBeforeAcceptance() {
        return status == ParaclinicalOrderDetailStatus.PENDING_PAYMENT
                || status == ParaclinicalOrderDetailStatus.WAITING;
    }

    /** BR-25, BR-41: the Doctor cancels a service the patient refused, with a mandatory reason. */
    public void cancelByDoctor(UUID doctorAccountId, String reason, Instant now) {
        if (!isCancellableBeforeAcceptance()) {
            throw new IllegalStateException("Paraclinical service can no longer be cancelled");
        }
        if (doctorAccountId == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancelling a paraclinical service needs the doctor and a reason");
        }
        status = ParaclinicalOrderDetailStatus.CANCELLED;
        cancelReason = reason.strip();
        cancelledByAccountId = doctorAccountId;
        cancelledAt = now;
        touch(now);
    }

    /** True once the service is performed, cancelled or abandoned. */
    public boolean isFinished() {
        return status == ParaclinicalOrderDetailStatus.COMPLETED
                || status == ParaclinicalOrderDetailStatus.CANCELLED
                || status == ParaclinicalOrderDetailStatus.ABANDONED;
    }

    /**
     * End-of-day job: an unperformed service of an abandoned visit is abandoned, paid or not (BR-22, O11); paid
     * services are not refunded (Q-10). No staff account cancels it, so the cancelling account and time stay empty.
     */
    public void abandonAtEndOfDay(Instant now) {
        if (isFinished()) {
            throw new IllegalStateException("Paraclinical service is already finished");
        }
        status = ParaclinicalOrderDetailStatus.ABANDONED;
        cancelReason = ABANDONED_REASON;
        touch(now);
    }

    public void release(Instant when) {
        if (this.status == ParaclinicalOrderDetailStatus.PENDING_PAYMENT) {
            this.status = ParaclinicalOrderDetailStatus.WAITING;
            this.releasedAt = when != null ? when : Instant.now();
        }
    }

    /**
     * UC-71 step 8: sends the paid service to its performing room (BR-28). A null room means no Active room
     * performs the service yet; the order still becomes Waiting (UC-71 AF-02).
     */
    public void releaseTo(UUID roomId, Integer queueNumber, Instant when) {
        if (this.status == ParaclinicalOrderDetailStatus.PENDING_PAYMENT) {
            this.roomId = roomId;
            this.queueNumber = queueNumber;
            this.status = ParaclinicalOrderDetailStatus.WAITING;
            this.releasedAt = when;
        }
    }

    public void accept(UUID performerAccountId, Instant when) {
        this.status = ParaclinicalOrderDetailStatus.IN_PROGRESS;
        this.acceptedAt = when;
        this.performerAccountId = performerAccountId;
        this.isSkipped = false;
        touch(when);
    }

    public void skip(Instant when) {
        this.isSkipped = true;
        this.skippedAt = when;
        touch(when);
    }

    public void unskip(Instant when) {
        this.isSkipped = false;
        this.skippedAt = null;
        touch(when);
    }

    /** UC-66: true when the service is In Progress and was accepted by this technician (EF-01). */
    public boolean isPerformedBy(UUID technicianAccountId) {
        return status == ParaclinicalOrderDetailStatus.IN_PROGRESS
                && performerAccountId != null
                && performerAccountId.equals(technicianAccountId);
    }

    /**
     * UC-66 step 3: the technician who accepted the service confirms it was performed; it waits for its result
     * (UC-64, UC-67).
     */
    public void completePerformance(UUID technicianAccountId, Instant when) {
        requirePerformedBy(technicianAccountId);
        this.status = ParaclinicalOrderDetailStatus.PENDING_RESULT;
        this.performedAt = when;
        touch(when);
    }

    /**
     * UC-66 AF-02: the service cannot be performed now, so it goes back to Waiting at its original place. The
     * queue number is kept, so the order is not moved to the end of the list; the reason is kept in the audit log.
     */
    public void returnToWaiting(UUID technicianAccountId, Instant when) {
        requirePerformedBy(technicianAccountId);
        this.status = ParaclinicalOrderDetailStatus.WAITING;
        this.performerAccountId = null;
        this.acceptedAt = null;
        touch(when);
    }

    /**
     * UC-67: true while the technician who performed the service may record its result: Performed (UC-66), or
     * still In Progress when the result is entered right away (UC-66 AF-01).
     */
    public boolean awaitsResultFrom(UUID technicianAccountId) {
        return (status == ParaclinicalOrderDetailStatus.PENDING_RESULT || status == ParaclinicalOrderDetailStatus.IN_PROGRESS)
                && performerAccountId != null
                && performerAccountId.equals(technicianAccountId);
    }

    /** UC-67 step 9: the result was submitted, so the service is Completed (O10). */
    public void completeWithResult(UUID technicianAccountId, Instant when) {
        if (!awaitsResultFrom(technicianAccountId)) {
            throw new IllegalStateException("Paraclinical service is not waiting for a result from this technician");
        }
        if (performedAt == null) {
            performedAt = when;
        }
        status = ParaclinicalOrderDetailStatus.COMPLETED;
        touch(when);
    }

    private void requirePerformedBy(UUID technicianAccountId) {
        if (!isPerformedBy(technicianAccountId)) {
            throw new IllegalStateException("Paraclinical service is not in progress for this technician");
        }
    }

    public void cancel(String reason, UUID cancelledByAccountId, Instant when) {
        if (isFinished()) {
            throw new IllegalStateException("Paraclinical service is already finished");
        }
        this.status = ParaclinicalOrderDetailStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledByAccountId = cancelledByAccountId;
        this.cancelledAt = when;
        touch(when);
    }
}
